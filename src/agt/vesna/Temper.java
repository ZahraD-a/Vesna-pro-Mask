package vesna;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Random;
import java.util.Iterator;
import java.util.stream.Collectors;

import static jason.asSyntax.ASSyntax.*;
import jason.asSyntax.*;
import jason.asSemantics.*;
import jason.asSyntax.parser.ParseException;
import jason.NoValueException;

import java.io.*;
import java.nio.file.*;
import org.json.JSONObject;

/**
 * Temper: personality-driven plan selection with MASK WARDROBE + CFR learning.
 *
 * ARCHITECTURE:
 *   A_eff = clip(A_core + M_active, -1, +1)
 *   A_core = frozen identity (set at design time)
 *   M_active = learned mask for current context
 *   A_eff = effective personality used for plan selection
 *
 * @author Andrea Gatti (original temper system)
 * @author Zahra Daoui (CFR + mask wardrobe extension)
 */
public class Temper {

    // ==================== DECISION STRATEGY ====================

    private enum DecisionStrategy { MOST_SIMILAR, RANDOM }

    // ==================== CORE IDENTITY (FROZEN) ====================

    /** Core personality: WHO YOU TRULY ARE. Set at design time. NEVER modified by CFR. */
    private Map<String, Double> corePersonality;

    // ==================== MASK WARDROBE ====================

    /** The wardrobe: collection of all masks defined at design time. */
    private MaskWardrobe wardrobe;
    /** The currently active mask (selected by beliefs). */
    private Mask activeMask;
    /** Whether mask mode is enabled. */
    private boolean useMasks = false;
    /** Global divergence threshold: report when a mask's L2 norm exceeds this. Distinct from the per-trait clip. */
    private double deltaThreshold = 0.5;

    // ==================== MOOD (mutable, fast-changing) ====================

    private Map<String, Double> mood;

    // ==================== STRATEGY & RNG ====================

    private DecisionStrategy strategy;
    private Random dice = new Random();

    // ==================== CFR FIELDS ====================

    private boolean cfrEnabled = true;
    private double cfrLearningRate = 0.005;
    private double softmaxTemperature = 2.0;
    private static final double TEMPERATURE_DECAY = 0.995;
    private static final double MIN_TEMPERATURE = 0.5;

    public static class InformationSet {
        public final String name;
        public final Map<String, Double> cumulativeRegret;
        public final Map<String, Double> strategySum;
        public int visitCount;
        public InformationSet(String name) {
            this.name = name;
            this.cumulativeRegret = new HashMap<>();
            this.strategySum = new HashMap<>();
            this.visitCount = 0;
        }
    }

    private Map<String, InformationSet> informationSets = new HashMap<>();
    private List<TraceEntry> currentEpisodeDecisions = new ArrayList<>();
    private Map<String, String> lastActionPerPerson = new HashMap<>();
    private double totalEpisodeReward = 0.0;

    public static class TraceEntry {
        public final String trigger;
        public final List<String> options;
        public final int selectedIndex;
        public final Map<String, Double> personalityAtDecision;
        public final List<Double> weights;
        public final String maskName;  // which mask was active
        public double reward = 0.0;
        TraceEntry(String trig, List<String> opts, int sel,
                   Map<String, Double> pers, List<Double> wts, String mask) {
            trigger = trig; options = new ArrayList<>(opts); selectedIndex = sel;
            personalityAtDecision = new HashMap<>(pers); weights = new ArrayList<>(wts);
            maskName = mask;
        }
    }

    // Per-context historical performance tracking
    // Key = "context_action" (e.g., "work_casual", "home_enthusiastic")
    private Map<String, Double> planTotalReward = new HashMap<>();
    private Map<String, Integer> planAttempts = new HashMap<>();

    private void updateHistoricalPerformance(String context, String plan, double reward) {
        String key = context + "_" + plan;
        planTotalReward.put(key, planTotalReward.getOrDefault(key, 0.0) + reward);
        planAttempts.put(key, planAttempts.getOrDefault(key, 0) + 1);
    }
    private double getHistoricalAverage(String context, String plan) {
        String key = context + "_" + plan;
        int a = planAttempts.getOrDefault(key, 0);
        return a == 0 ? 0.0 : planTotalReward.getOrDefault(key, 0.0) / a;
    }
    private InformationSet getInformationSet(String name) {
        return informationSets.computeIfAbsent(name, InformationSet::new);
    }
    private String normalizePlanLabel(String label) {
        if (label == null) return null;
        String c = label.replaceAll("^\"|\"$", "");
        return c.matches("p__\\d+") ? "p2" : c;
    }

    // ==================== CONSTRUCTORS ====================

    public Temper(String temper, String strategy) throws IllegalArgumentException {
        this(temper, strategy, -1, true, false, 0.5, 0.5, null);
    }

    public Temper(String temper, String strategy, long seed, boolean cfrEnabled)
            throws IllegalArgumentException {
        this(temper, strategy, seed, cfrEnabled, false, 0.5, 0.5, null);
    }

    /**
     * Full constructor with mask support.
     *
     * @param temper         Jason temper literal
     * @param strategy       "most_similar" or "random"
     * @param seed           RNG seed (-1 for random)
     * @param cfrEnabled     CFR learning active?
     * @param useMasks       use mask wardrobe?
     * @param maskClip       per-trait bound: max absolute value of each mask trait
     * @param deltaThreshold global divergence threshold on the mask's L2 norm
     * @param contexts       context names (e.g., ["work", "home"])
     */
    public Temper(String temper, String strategy, long seed, boolean cfrEnabled,
                  boolean useMasks, double maskClip, double deltaThreshold, List<String> contexts)
            throws IllegalArgumentException {

        if (temper == null) throw new IllegalArgumentException("Temper cannot be null");

        this.corePersonality = new HashMap<>();
        this.mood = new HashMap<>();
        this.cfrEnabled = cfrEnabled;
        this.useMasks = useMasks;

        if (seed >= 0) {
            this.dice = new Random(seed);
            System.out.println("[TEMPER] Using fixed seed: " + seed);
        }

        try {
            Literal listLit = parseLiteral(temper);
            for (Term term : listLit.getTerms()) {
                Literal trait = (Literal) term;
                double value = (double) ((NumberTerm) trait.getTerm(0)).solve();
                if (trait.hasAnnot(createLiteral("mood"))) {
                    if (value < -1.0 || value > 1.0)
                        throw new IllegalArgumentException("Mood out of range: " + trait);
                    mood.put(trait.getFunctor().toString(), value);
                } else {
                    if (value < -1.0 || value > 1.0)
                        throw new IllegalArgumentException("Personality out of range: " + trait);
                    corePersonality.put(trait.getFunctor().toString(), value);
                }
            }
        } catch (ParseException pe) { throw new IllegalArgumentException(pe.getMessage());
        } catch (NoValueException nve) { throw new IllegalArgumentException(nve.getMessage()); }

        if (strategy == null) this.strategy = DecisionStrategy.MOST_SIMILAR;
        else if (strategy.equals("most_similar")) this.strategy = DecisionStrategy.MOST_SIMILAR;
        else if (strategy.equals("random")) this.strategy = DecisionStrategy.RANDOM;
        else throw new IllegalArgumentException("Unknown strategy: " + strategy);

        if (useMasks) {
            if (contexts == null || contexts.isEmpty())
                throw new IllegalArgumentException("Mask mode requires contexts");
            this.wardrobe = new MaskWardrobe(maskClip, contexts, 0);
            this.activeMask = wardrobe.getDefaultMask();
            this.deltaThreshold = deltaThreshold;

            // All masks start at [0,0,0,0,0]
            // Each context tracks its OWN reward history
            // CFR learns DIFFERENT values per context because:
            //   - work context: formal responses get higher reward
            //   - home context: casual responses get higher reward
            //   - concert context: enthusiastic responses get higher reward
            System.out.println("[MASK] Wardrobe: " + contexts);
            System.out.println("[MASK] Core: " + formatMap(corePersonality));
            System.out.println("[MASK] All masks start at [0,0,0,0,0]");
            System.out.println("[MASK] Each context has its own reward history");
        }
    }

    //  -----------EFFECTIVE PERSONALITY ----------------

    /**
     * A_eff = clip(A_core + M_active, -1, +1).
     * Baseline mode: A_eff = core personality.
     * Mask mode: A_eff = core + active mask delta.
     */
    public Map<String, Double> effectivePersonality() {
        if (!useMasks) return new HashMap<>(corePersonality);

        Map<String, Double> eff = new HashMap<>();
        for (String trait : corePersonality.keySet()) {
            double core = corePersonality.getOrDefault(trait, 0.0);
            double maskDelta = activeMask.getTrait(trait);
            eff.put(trait, Math.max(-1.0, Math.min(1.0, core + maskDelta)));
        }
        return eff;
    }

    // ==================== MASK CONTEXT SELECTION ====================

    /** Set active mask by context name. */
    public void setActiveMask(String context) {
        if (!useMasks) return;
        activeMask = wardrobe.getMask(context);
    }

    public Mask getActiveMask() { return activeMask; }
    public MaskWardrobe getWardrobe() { return wardrobe; }

    // ==================== WEIGHT COMPUTATION ====================

    public double computeWeight(Pred label) throws NoValueException {
        double choiceWeight = 0;
        Literal temperAnnot = label.getAnnot("temper");
        if (temperAnnot == null) return choiceWeight;

        Map<String, Double> active = effectivePersonality();
        ListTerm choiceTemper = (ListTerm) temperAnnot.getTerm(0);
        for (Term traitTerm : choiceTemper) {
            Atom trait = (Atom) traitTerm;
            String traitName = trait.getFunctor().toString();
            double traitTemper;
            if (mood.containsKey(traitName)) traitTemper = mood.get(traitName);
            else if (active.containsKey(traitName)) traitTemper = active.get(traitName);
            else continue;

            double traitValue = (double) ((NumberTerm) trait.getTerm(0)).solve();
            if (strategy == DecisionStrategy.RANDOM) choiceWeight += traitTemper * traitValue;
            else if (strategy == DecisionStrategy.MOST_SIMILAR) choiceWeight += Math.abs(traitTemper - traitValue);
        }
        return choiceWeight;
    }

    // ==================== PLAN SELECTION ====================

    public boolean hasOptionsAnnotation(List<Option> options) {
        return hasAnnotation(options.stream().map(OptionWrapper::new).collect(Collectors.toList()));
    }
    public boolean hasIntentionsAnnotation(Queue<Intention> intentions) {
        return hasAnnotation(new ArrayList<>(intentions).stream().map(IntentionWrapper::new).collect(Collectors.toList()));
    }
    private <T extends TemperSelectable> boolean hasAnnotation(List<T> choices) {
        Literal annotPattern = createLiteral("temper", new VarTerm("X"));
        for (T choice : choices) {
            Pred l = choice.getLabel();
            if (l.hasAnnot()) for (Term t : l.getAnnots()) if (new Unifier().unifies(annotPattern, t)) return true;
        }
        return false;
    }

    public Option selectOption(List<Option> options) {
        try {
            Option selected = select(options.stream().map(OptionWrapper::new).collect(Collectors.toList())).getOption();
            Literal effectList = selected.getPlan().getLabel().getAnnot("effects");
            if (effectList != null) updateDynTemper(effectList);
            return selected;
        } catch (NoValueException e) { return null; }
    }

    public Intention selectIntention(Queue<Intention> intentions) {
        try {
            List<IntentionWrapper> wrapped = new ArrayList<>(intentions).stream().map(IntentionWrapper::new).collect(Collectors.toList());
            Intention selected = select(wrapped).getIntention();
            Iterator<Intention> it = intentions.iterator();
            while (it.hasNext()) { if (it.next() == selected) { it.remove(); break; } }
            Literal effectList = selected.peek().getPlan().getLabel().getAnnot("effects");
            if (effectList != null) updateDynTemper(effectList);
            return selected;
        } catch (NoValueException e) { return null; }
    }

    public <T extends TemperSelectable> T select(List<T> choices) throws NoValueException {
        List<Double> weights = new ArrayList<>();
        for (T choice : choices) weights.add(computeWeight(choice.getLabel()));

        T chosen = null;
        int chosenIdx = -1;
        if (strategy == DecisionStrategy.RANDOM) { chosenIdx = getWeightedRandomIdx(weights); chosen = choices.get(chosenIdx); }
        else if (strategy == DecisionStrategy.MOST_SIMILAR) { chosenIdx = getMostSimilarIdx(weights); chosen = choices.get(chosenIdx); }
        if (chosen == null) { chosenIdx = 0; chosen = choices.get(chosenIdx); }

        if (cfrEnabled) recordDecision(choices, chosenIdx, weights);
        return chosen;
    }

    // ==================== CFR: DECISION RECORDING ====================

    private String currentStage = "root";
    public void setCurrentStage(String stage) { this.currentStage = stage; }

    private <T extends TemperSelectable> void recordDecision(List<T> choices, int selectedIdx, List<Double> weights) {
        List<String> optionLabels = choices.stream().map(c -> normalizePlanLabel(c.getLabel().getFunctor())).collect(Collectors.toList());
        String maskName = useMasks ? activeMask.getName() : "none";
        TraceEntry entry = new TraceEntry(currentStage, optionLabels, selectedIdx, effectivePersonality(), weights, maskName);
        currentEpisodeDecisions.add(entry);

        InformationSet infoset = getInformationSet(currentStage);
        infoset.visitCount++;
        String chosenAction = optionLabels.get(selectedIdx);
        infoset.strategySum.put(chosenAction, infoset.strategySum.getOrDefault(chosenAction, 0.0) + 1.0);

        System.out.println("[CFR] Stage=" + currentStage + " #" + currentEpisodeDecisions.size()
            + " selected=" + chosenAction + " mask=" + (useMasks ? activeMask.getName() : "none"));
    }

    // ==================== CFR: RECORD OUTCOME ====================

    public void recordHelpOutcome(String action, double reward, String person) {
        lastActionPerPerson.put(person.toLowerCase(), action);
        InformationSet infoset = getInformationSet("help_" + person);

        // Track reward per context (mask) — each context has its own history
        String context = useMasks ? activeMask.getContext() : "default";
        updateHistoricalPerformance(context, action, reward);

        // Compute regret using per-context historical averages
        String[] allActions = HelpScenarioConfig.getActionsForPerson(person);
        double expectedChosen = getHistoricalAverage(context, action);
        for (String alt : allActions) {
            double expectedAlt = getHistoricalAverage(context, alt);
            double regret = alt.equals(action) ? 0.0 : expectedAlt - expectedChosen;
            double current = infoset.cumulativeRegret.getOrDefault(alt, 0.0);
            infoset.cumulativeRegret.put(alt, current + regret);
        }
        totalEpisodeReward += reward;
    }

    // ==================== CFR: MASK UPDATE (KEY METHOD) ====================

    /**
     * Update MASKS from CFR regret.
     * Routes gradients to per-context masks (not the core!).
     */
    public void updateMasksFromCFR() {
        if (currentEpisodeDecisions.isEmpty()) return;
        if (!cfrEnabled) return;

        System.out.println("\n========== CFR: MASK UPDATE ==========");

        // Collect which masks were used in this episode
        Map<String, List<TraceEntry>> decisionsByMask = new HashMap<>();
        for (TraceEntry entry : currentEpisodeDecisions) {
            String mask = entry.maskName;
            if (!decisionsByMask.containsKey(mask)) {
                decisionsByMask.put(mask, new ArrayList<>());
            }
            decisionsByMask.get(mask).add(entry);
        }

        System.out.println("[MASK] Masks used this episode: " + decisionsByMask.keySet());

        // Update each mask separately based on decisions made while wearing it
        for (Map.Entry<String, List<TraceEntry>> maskEntry : decisionsByMask.entrySet()) {
            String maskName = maskEntry.getKey();
            List<TraceEntry> decisions = maskEntry.getValue();

            // Find the mask object
            Mask mask = null;
            if (maskName.equals("mask_default")) {
                mask = wardrobe.getDefaultMask();
            } else {
                String ctx = maskName.replace("mask_", "");
                mask = wardrobe.getMask(ctx);
            }
            if (mask == null) continue;

            // Compute this mask's effective personality: A_core + M_this_mask
            Map<String, Double> maskEff = new HashMap<>();
            for (String trait : corePersonality.keySet()) {
                double core = corePersonality.getOrDefault(trait, 0.0);
                double maskDelta = mask.getTrait(trait);
                maskEff.put(trait, Math.max(-1.0, Math.min(1.0, core + maskDelta)));
            }

            // Compute gradients from decisions made while wearing this mask
            Map<String, Double> traitGradients = new HashMap<>();
            for (String trait : corePersonality.keySet()) traitGradients.put(trait, 0.0);
            int decisionCount = 0;

            for (TraceEntry decision : decisions) {
                // Get the action that was selected
                String chosenAction = decision.options.get(decision.selectedIndex);
                Map<String, Double> chosenTraits = HelpScenarioConfig.getActionTraits(chosenAction);
                if (chosenTraits == null) continue;

                // Compute per-context historical averages
                String ctx = mask.getContext();
                double chosenAvg = getHistoricalAverage(ctx, chosenAction);

                // For each unchosen action, compute per-context regret
                for (int i = 0; i < decision.options.size(); i++) {
                    if (i == decision.selectedIndex) continue;
                    String altAction = decision.options.get(i);
                    double altAvg = getHistoricalAverage(ctx, altAction);
                    double regret = altAvg - chosenAvg;
                    if (regret <= 0) continue;

                    Map<String, Double> altTraits = HelpScenarioConfig.getActionTraits(altAction);
                    if (altTraits == null) continue;

                    // Gradient: regret * (A_alt - A_eff(this mask))
                    for (Map.Entry<String, Double> te : altTraits.entrySet()) {
                        String traitName = te.getKey();
                        double grad = regret * (te.getValue() - maskEff.getOrDefault(traitName, 0.0));
                        traitGradients.put(traitName, traitGradients.getOrDefault(traitName, 0.0) + grad);
                    }
                }
                decisionCount++;
            }

            // Apply gradients to this specific mask
            if (decisionCount > 0) {
                // Normalize gradients by number of decisions
                for (String trait : traitGradients.keySet()) {
                    traitGradients.put(trait, traitGradients.get(trait) / decisionCount);
                }
                System.out.println("[MASK] Updating " + maskName + " (" + decisionCount + " decisions):");
                for (String trait : traitGradients.keySet()) {
                    double gradient = traitGradients.get(trait);
                    if (Math.abs(gradient) < 0.001) continue;
                    double oldVal = mask.getTrait(trait);
                    mask.updateTrait(trait, cfrLearningRate * gradient);
                    System.out.println("  " + trait + ": " + String.format("%+.4f", oldVal)
                        + " -> " + String.format("%+.4f", mask.getTrait(trait)));
                }
            }

            double norm = mask.norm();
            System.out.println("[MASK] ||" + maskName + "||=" + String.format("%.4f", norm)
                + " (threshold=" + deltaThreshold + ")");
            if (norm >= deltaThreshold) {
                System.out.println("[MASK] *** DELTA EXCEEDED on " + maskName + "! ***");
            }
        }

        // Print final wardrobe state
        System.out.println("[MASK] Wardrobe after update:");
        for (Mask m : wardrobe.getAllMasks()) {
            System.out.println("  " + m);
        }
        System.out.println("=====================================\n");
    }

    /** Baseline update (no masks) — merges ALL gradients into ONE vector. */
    public void updatePersonalityFromCFR() {
        if (currentEpisodeDecisions.isEmpty()) return;
        if (!cfrEnabled) return;

        Map<String, Double> traitGradients = new HashMap<>();
        for (String trait : corePersonality.keySet()) traitGradients.put(trait, 0.0);

        for (InformationSet infoset : informationSets.values()) {
            if (infoset.cumulativeRegret.isEmpty()) continue;
            double totalPos = 0;
            for (Double r : infoset.cumulativeRegret.values()) if (r > 0) totalPos += r;
            if (totalPos < 0.001) continue;

            for (Map.Entry<String, Double> entry : infoset.cumulativeRegret.entrySet()) {
                double regret = entry.getValue();
                if (regret <= 0) continue;
                double weight = regret / totalPos;
                Map<String, Double> actionTraits = HelpScenarioConfig.getActionTraits(entry.getKey());
                if (actionTraits == null) continue;
                for (Map.Entry<String, Double> te : actionTraits.entrySet()) {
                    String tn = te.getKey();
                    double grad = weight * (te.getValue() - corePersonality.getOrDefault(tn, 0.0));
                    traitGradients.put(tn, traitGradients.getOrDefault(tn, 0.0) + grad);
                }
            }
        }

        for (String trait : traitGradients.keySet()) {
            double g = traitGradients.get(trait);
            if (Math.abs(g) < 0.001) continue;
            double old = corePersonality.getOrDefault(trait, 0.0);
            corePersonality.put(trait, Math.max(-1.0, Math.min(1.0, old + cfrLearningRate * g)));
        }
    }

    // ==================== EPISODE MANAGEMENT ====================

    public void startNewEpisode() {
        System.out.println("\n========== EPISODE COMPLETE ==========");
        if (useMasks) {
            System.out.println("[MASK] BEFORE: " + activeMask);
            updateMasksFromCFR();
            System.out.println("[MASK] AFTER:  " + activeMask);
        } else {
            updatePersonalityFromCFR();
        }
        savePersonality();
        currentEpisodeDecisions.clear();
        lastActionPerPerson.clear();
        totalEpisodeReward = 0.0;
        currentStage = "root";
        for (String key : mood.keySet()) mood.put(key, 0.0);
        softmaxTemperature = Math.max(MIN_TEMPERATURE, softmaxTemperature * TEMPERATURE_DECAY);
    }

    // ==================== PERSISTENCE ====================

    private static final String PERSONALITY_FILE = "personality.json";

    private void savePersonality() {
        try {
            JSONObject root = new JSONObject();
            JSONObject persJson = new JSONObject();
            for (Map.Entry<String, Double> e : corePersonality.entrySet())
                persJson.put(e.getKey(), Math.round(e.getValue() * 1000.0) / 1000.0);
            root.put("personality", persJson);
            JSONObject moodJson = new JSONObject();
            for (Map.Entry<String, Double> e : mood.entrySet())
                moodJson.put(e.getKey(), Math.round(e.getValue() * 1000.0) / 1000.0);
            root.put("mood", moodJson);
            if (useMasks && wardrobe != null) {
                JSONObject masksJson = new JSONObject();
                for (Mask mask : wardrobe.getAllMasks()) {
                    JSONObject mj = new JSONObject();
                    for (Map.Entry<String, Double> e : mask.getTraits().entrySet())
                        mj.put(e.getKey(), Math.round(e.getValue() * 1000.0) / 1000.0);
                    mj.put("norm", Math.round(mask.norm() * 10000.0) / 10000.0);
                    masksJson.put(mask.getName(), mj);
                }
                root.put("masks", masksJson);
            }
            Files.writeString(Path.of(PERSONALITY_FILE), root.toString(2));
        } catch (IOException e) { System.err.println("[PERSIST] " + e.getMessage()); }
    }

    public static Map<String, Object> loadPersonalityFromFile() {
        try {
            File f = new File(PERSONALITY_FILE);
            if (!f.exists()) return null;
            String content = Files.readString(Path.of(PERSONALITY_FILE));
            JSONObject root = new JSONObject(content);
            Map<String, Object> result = new HashMap<>();
            Map<String, Double> persMap = new HashMap<>();
            if (root.has("personality")) for (String k : root.getJSONObject("personality").keySet()) persMap.put(k, root.getJSONObject("personality").getDouble(k));
            result.put("personality", persMap);
            Map<String, Double> moodMap = new HashMap<>();
            if (root.has("mood")) for (String k : root.getJSONObject("mood").keySet()) moodMap.put(k, root.getJSONObject("mood").getDouble(k));
            result.put("mood", moodMap);
            return result;
        } catch (Exception e) { return null; }
    }

    // ==================== GETTERS ====================

    public Map<String, Double> getPersonality() { return effectivePersonality(); }
    public Map<String, Double> getCorePersonality() { return new HashMap<>(corePersonality); }
    public boolean isCfrEnabled() { return cfrEnabled; }
    public boolean isUseMasks() { return useMasks; }
    public Map<String, Double> getMood() { return new HashMap<>(mood); }
    public double getTotalEpisodeReward() { return totalEpisodeReward; }
    public Random getDice() { return dice; }
    public String getLastActionForPerson(String p) { return lastActionPerPerson.getOrDefault(p.toLowerCase(), ""); }

    public Map<String, Double> getHelpCumulativeRegrets() {
        Map<String, Double> r = new HashMap<>();
        for (String a : HelpScenarioConfig.getAllActionTraits().keySet()) r.put(a, 0.0);
        for (InformationSet is : informationSets.values())
            for (Map.Entry<String, Double> e : is.cumulativeRegret.entrySet())
                r.put(e.getKey(), r.getOrDefault(e.getKey(), 0.0) + e.getValue());
        return r;
    }

    // ==================== HELPERS 

    private String formatMap(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (!first) sb.append(" ");
            sb.append(e.getKey()).append("=").append(String.format("%.3f", e.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    private double[] computeActionProbabilities(List<Double> weights) {
        double[] probs = new double[weights.size()];
        double minW = Double.MAX_VALUE;
        for (double w : weights) minW = Math.min(minW, w);
        double sum = 0;
        for (int i = 0; i < weights.size(); i++) { probs[i] = Math.exp((weights.get(i) - minW) / softmaxTemperature); sum += probs[i]; }
        if (sum > 0) for (int i = 0; i < probs.length; i++) probs[i] /= sum;
        else for (int i = 0; i < probs.length; i++) probs[i] = 1.0 / probs.length;
        return probs;
    }

    private int getWeightedRandomIdx(List<Double> weights) {
        double[] probs = computeActionProbabilities(weights);
        double[] cum = new double[weights.size()];
        double t = 0; for (int i = 0; i < weights.size(); i++) { t += probs[i]; cum[i] = t; }
        double roll = dice.nextDouble();
        for (int i = 0; i < cum.length; i++) if (roll < cum[i]) return i;
        return weights.size() - 1;
    }

    private int getMostSimilarIdx(List<Double> weights) {
        double min = Double.MAX_VALUE; int idx = -1;
        for (int i = 0; i < weights.size(); i++) if (weights.get(i) < min) { min = weights.get(i); idx = i; }
        return idx;
    }

    private void updateDynTemper(Literal effectList) throws NoValueException {
        ListTerm effects = (ListTerm) effectList.getTerm(0);
        for (Term effectTerm : effects) {
            Literal effect = (Literal) effectTerm;
            String name = effect.getFunctor().toString();
            if (corePersonality.containsKey(name) && !effect.hasAnnot(createLiteral("mood")))
                throw new IllegalArgumentException("Cannot use personality trait '" + name + "' in effects.");
            if (mood.get(name) == null) continue;
            double old = mood.get(name);
            double delta = (double) ((NumberTerm) effect.getTerm(0)).solve();
            double nv = Math.max(-1.0, Math.min(1.0, old + delta));
            mood.put(name, nv);
            System.out.println("[TEMPER] Mood: " + name + " " + String.format("%.2f", old) + " -> " + String.format("%.2f", nv));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[TEMPER] Core: ");
        corePersonality.forEach((k, v) -> sb.append(k + "=" + String.format("%.2f", v) + " "));
        if (useMasks) { sb.append("| Mask: ").append(activeMask.getName()).append(" "); activeMask.getTraits().forEach((k, v) -> sb.append(k + "=" + String.format("%.2f", v) + " ")); }
        sb.append("| Mood: "); mood.forEach((k, v) -> sb.append(k + "=" + String.format("%.2f", v) + " "));
        return sb.toString();
    }
}
