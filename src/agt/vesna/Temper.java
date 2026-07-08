package vesna;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
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

    // DECISION STRATEGY

    private enum DecisionStrategy { MOST_SIMILAR, RANDOM }

    // CORE IDENTITY (FROZEN)

    /** Core personality: WHO YOU TRULY ARE. Set at design time. NEVER modified by CFR. */
    private Map<String, Double> corePersonality;

    // MASK WARDROBE

    /** The wardrobe: collection of all masks defined at design time. */
    private MaskWardrobe wardrobe;
    /** The currently active mask (selected by beliefs). */
    private Mask activeMask;

    /** Requested context/belief -> name of the mask actually worn (routing record). */
    private final Map<String, String> contextRouting = new LinkedHashMap<>();
    /** Contexts with no dedicated mask that fell back to the shared default mask. */
    private final Set<String> defaultRoutedContexts = new LinkedHashSet<>();
    /** Whether mask mode is enabled. */
    private boolean useMasks = false;
    /** Global divergence threshold: report when a mask's L2 norm exceeds this. Distinct from the per-trait clip. */
    private double deltaThreshold = 0.5;

    // MOOD (mutable, fast-changing)

    private Map<String, Double> mood;

    // STRATEGY & RNG

    private DecisionStrategy strategy;
    private Random dice = new Random();

    // CFR FIELDS

    private boolean cfrEnabled = true;
    private double cfrLearningRate = 0.02;
    private double softmaxTemperature = 2.0;
    private static final double TEMPERATURE_DECAY = 0.98;
    private static final double MIN_TEMPERATURE = 0.2;

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
    private int episodeIndex = 0;

    // ---- Vanilla CFR: regret matching, one information set per context ----
    /** The action set available at every decision node. */
    private static final String[] CFR_ACTIONS = {"formal", "casual", "enthusiastic", "reserved"};
    /** context (info set) -> cumulative counterfactual regret per action. */
    private final Map<String, double[]> regretSum = new HashMap<>();
    /** context (info set) -> cumulative strategy per action (for the average strategy). */
    private final Map<String, double[]> strategySum = new HashMap<>();

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

    // ---- CFR helpers ----
    private double[] regretSumFor(String ctx)   { return regretSum.computeIfAbsent(ctx, k -> new double[CFR_ACTIONS.length]); }
    private double[] strategySumFor(String ctx) { return strategySum.computeIfAbsent(ctx, k -> new double[CFR_ACTIONS.length]); }
    private int actionIndex(String a) {
        for (int i = 0; i < CFR_ACTIONS.length; i++) if (CFR_ACTIONS[i].equals(a)) return i;
        return -1;
    }
    private boolean isCfrActionSet(List<String> labels) {
        if (labels.size() != CFR_ACTIONS.length) return false;
        for (String l : labels) if (actionIndex(l) < 0) return false;
        return true;
    }
    private List<Double> toList(double[] a) { List<Double> l = new ArrayList<>(); for (double v : a) l.add(v); return l; }

    /** Regret matching: current strategy = normalize(max(0, regretSum)); uniform if all <= 0. */
    private double[] regretMatch(String ctx) {
        double[] r = regretSumFor(ctx);
        double[] s = new double[r.length];
        double sum = 0.0;
        for (int i = 0; i < r.length; i++) { s[i] = Math.max(0.0, r[i]); sum += s[i]; }
        if (sum > 0) for (int i = 0; i < s.length; i++) s[i] /= sum;
        else         for (int i = 0; i < s.length; i++) s[i] = 1.0 / s.length;
        return s;
    }

    /** Average strategy = normalize(strategySum); uniform if unseen. This is the converged output. */
    private double[] averageStrategy(String ctx) {
        double[] ss = strategySumFor(ctx);
        double[] s = new double[ss.length];
        double sum = 0.0;
        for (double v : ss) sum += v;
        if (sum > 0) for (int i = 0; i < ss.length; i++) s[i] = ss[i] / sum;
        else         for (int i = 0; i < ss.length; i++) s[i] = 1.0 / ss.length;
        return s;
    }

    // CONSTRUCTORS

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
            System.out.println("[MASK] Wardrobe created for contexts " + contexts
                + " (plus a default mask). Core personality: " + formatMap(corePersonality));
            System.out.println("[MASK] Initial masks (all start at zero, learned afterwards by CFR):");
            for (Mask m : wardrobe.getAllMasks()) System.out.println("  " + m);
            try {   // start each training run with a fresh log; episode 0 = all masks at zero
                Files.createDirectories(Path.of("results"));
                StringBuilder hdr = new StringBuilder("episode,total_reward");
                for (Mask m : wardrobe.getAllMasks()) hdr.append(",").append(m.getName());
                hdr.append("\n0,0.0000");
                for (Mask m : wardrobe.getAllMasks()) hdr.append(",").append(String.format("%.6f", m.norm()));
                hdr.append("\n");
                Files.writeString(Path.of("results", "mask_norms.csv"), hdr.toString());
            } catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
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

    // MASK CONTEXT SELECTION

    /** Set active mask by context name (records routing under the same name). */
    public void setActiveMask(String context) {
        setActiveMask(context, context);
    }

    /**
     * Set the active mask for a requested context, recording which mask the
     * context was routed to.
     *
     * When a context has no dedicated mask, the wardrobe returns the shared
     * default mask (the "true self", which also starts at [0,0,0,0,0] and is
     * itself learnable). We remember that fallback so we can later report
     * which contexts/beliefs ended up wearing — and training — the default mask.
     *
     * @param context          wardrobe key (e.g. "work"; "default" for the catch-all)
     * @param requestedContext the raw context/belief that led here (e.g. "party")
     */
    public void setActiveMask(String context, String requestedContext) {
        if (!useMasks) return;
        activeMask = wardrobe.getMask(context);
        contextRouting.put(requestedContext, activeMask.getName());
        if (activeMask == wardrobe.getDefaultMask() && !requestedContext.equals("default")) {
            defaultRoutedContexts.add(requestedContext);
        }
    }

    /**
     * Report which context/belief each interaction was routed to, and which
     * contexts fell back to the shared default mask because no dedicated mask
     * was defined for them.
     */
    public void printContextRouting() {
        if (!useMasks || contextRouting.isEmpty()) return;
        System.out.println("[MASK] Context -> mask routing:");
        for (Map.Entry<String, String> e : contextRouting.entrySet()) {
            boolean fallback = defaultRoutedContexts.contains(e.getKey());
            System.out.println("  context '" + e.getKey() + "' -> " + e.getValue()
                + (fallback ? "  (no dedicated mask -> learned default)" : ""));
        }
        if (!defaultRoutedContexts.isEmpty()) {
            System.out.println("[MASK] Undefined contexts trained onto the default mask: "
                + defaultRoutedContexts);
        }
    }

    public Mask getActiveMask() { return activeMask; }
    public MaskWardrobe getWardrobe() { return wardrobe; }

    // WEIGHT COMPUTATION

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

    // PLAN SELECTION

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
        // VANILLA CFR: when choosing among the response actions, PLAY the regret-matched
        // strategy for the current context (info set) and sample an action from it.
        if (useMasks && cfrEnabled) {
            List<String> labels = choices.stream()
                .map(c -> normalizePlanLabel(c.getLabel().getFunctor())).collect(Collectors.toList());
            if (isCfrActionSet(labels)) {
                String ctx = activeMask.getContext();
                double[] sigma = regretMatch(ctx);
                // Accumulate the strategy (reach prob = 1 here: single decision node, single player).
                double[] ss = strategySumFor(ctx);
                for (int i = 0; i < CFR_ACTIONS.length; i++) ss[i] += sigma[i];
                // Sample from sigma, aligned to the order Jason presents the options in.
                double[] p = new double[labels.size()];
                double psum = 0.0;
                for (int i = 0; i < labels.size(); i++) { p[i] = sigma[actionIndex(labels.get(i))]; psum += p[i]; }
                int chosenIdx = labels.size() - 1;
                double roll = dice.nextDouble() * (psum > 0 ? psum : 1.0), acc = 0.0;
                for (int i = 0; i < p.length; i++) { acc += p[i]; if (roll < acc) { chosenIdx = i; break; } }
                recordDecision(choices, chosenIdx, toList(p));
                return choices.get(chosenIdx);
            }
        }

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

    // CFR: DECISION RECORDING

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

    // CFR: RECORD OUTCOME

    public void recordHelpOutcome(String action, double reward, String person) {
        lastActionPerPerson.put(person.toLowerCase(), action);

        // Each context is its own info set and estimates its own action utilities.
        String context = useMasks ? activeMask.getContext() : "default";
        updateHistoricalPerformance(context, action, reward);

        if (cfrEnabled && useMasks) {
            // Vanilla-CFR regret update at this single-decision info set (FULL feedback:
            // every action's utility is known from the reward model, as in a tree traversal):
            //   u(a) = utility of action a in this context = base + context shaping
            //   v    = sum_a sigma(a) * u(a)          (value of the current strategy)
            //   regretSum(a) += u(a) - v              (counterfactual regret, accumulated)
            double[] sigma = regretMatch(context);
            double[] u = new double[CFR_ACTIONS.length];
            double v = 0.0;
            for (int i = 0; i < CFR_ACTIONS.length; i++) {
                u[i] = HelpScenarioConfig.utility(context, CFR_ACTIONS[i]);
                v += sigma[i] * u[i];
            }
            double[] r = regretSumFor(context);
            for (int i = 0; i < CFR_ACTIONS.length; i++) r[i] += (u[i] - v);
        } else if (cfrEnabled) {
            // Baseline (no masks): legacy per-action regret bookkeeping.
            InformationSet infoset = getInformationSet("help_" + person);
            String[] allActions = HelpScenarioConfig.getActionsForPerson(person);
            double expectedChosen = getHistoricalAverage(context, action);
            for (String alt : allActions) {
                double expectedAlt = getHistoricalAverage(context, alt);
                double regret = alt.equals(action) ? 0.0 : expectedAlt - expectedChosen;
                infoset.cumulativeRegret.put(alt, infoset.cumulativeRegret.getOrDefault(alt, 0.0) + regret);
            }
        }
        totalEpisodeReward += reward;
    }

    // MASK READOUT (projection of the CFR average strategy into personality space)

    /**
     * Refresh each mask as a READOUT of its context's regret-matched AVERAGE strategy:
     *
     *   mask(ctx) = sum_a ( avgStrategy(ctx, a) - 1/|A| ) * OCEAN(a)
     *
     * i.e. how far the learned policy pulls the persona away from the uniform baseline.
     * The learning itself (regret matching) happens in select() and recordHelpOutcome();
     * this only projects the converged strategy into personality space for interpretation.
     */
    public void updateMasksFromCFR() {
        if (!cfrEnabled || !useMasks) return;
        System.out.println("\n[MASK] Masks = readout of the regret-matched average strategy:");
        double uniform = 1.0 / CFR_ACTIONS.length;
        for (Mask mask : wardrobe.getAllMasks()) {
            String ctx = mask.getContext();
            double[] avg = averageStrategy(ctx);
            Map<String, Double> target = new HashMap<>();
            for (String trait : corePersonality.keySet()) target.put(trait, 0.0);
            for (int i = 0; i < CFR_ACTIONS.length; i++) {
                Map<String, Double> at = HelpScenarioConfig.getActionTraits(CFR_ACTIONS[i]);
                if (at == null) continue;
                double w = avg[i] - uniform;
                for (Map.Entry<String, Double> e : at.entrySet())
                    target.merge(e.getKey(), w * e.getValue(), Double::sum);
            }
            for (Map.Entry<String, Double> e : target.entrySet())
                mask.setTraitClipped(e.getKey(), e.getValue());
            System.out.println("  " + mask.getName() + " ctx=" + ctx
                + " avgStrategy=" + fmtStrategy(avg)
                + " ||M||=" + String.format("%.4f", mask.norm()));
        }
        saveAverageStrategy();
        printContextRouting();
        System.out.println();
    }

    private String fmtStrategy(double[] s) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < CFR_ACTIONS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(CFR_ACTIONS[i]).append("=").append(String.format("%.2f", s[i]));
        }
        return sb.append("}").toString();
    }

    /** Save the converged average strategy per context — the actual CFR output. */
    private void saveAverageStrategy() {
        try {
            StringBuilder sb = new StringBuilder("context");
            for (String a : CFR_ACTIONS) sb.append(",").append(a);
            sb.append("\n");
            for (Mask mask : wardrobe.getAllMasks()) {
                String ctx = mask.getContext();
                double[] avg = averageStrategy(ctx);
                sb.append(ctx);
                for (double v : avg) sb.append(",").append(String.format("%.4f", v));
                sb.append("\n");
            }
            Files.createDirectories(Path.of("results"));
            Files.writeString(Path.of("results", "average_strategy.csv"), sb.toString());
        } catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
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

    // EPISODE MANAGEMENT

    public void startNewEpisode() {
        if (useMasks) {
            System.out.println("[MASK] BEFORE: " + activeMask);
            updateMasksFromCFR();
            System.out.println("[MASK] AFTER:  " + activeMask);
        } else {
            updatePersonalityFromCFR();
        }
        savePersonality();
        logTrainingRow(totalEpisodeReward);
        currentEpisodeDecisions.clear();
        lastActionPerPerson.clear();
        totalEpisodeReward = 0.0;
        currentStage = "root";
        for (String key : mood.keySet()) mood.put(key, 0.0);
        softmaxTemperature = Math.max(MIN_TEMPERATURE, softmaxTemperature * TEMPERATURE_DECAY);
    }

    // PERSISTENCE

    private static final String PERSONALITY_FILE = "personality.json";

    /**
     * Append one training row per episode to results/mask_norms.csv:
     * episode index, total reward that episode, and the L2 norm of each mask.
     * The norms are the learning curves — they should grow from 0 as CFR adapts.
     */
    private void logTrainingRow(double episodeReward) {
        if (!useMasks || wardrobe == null) return;
        episodeIndex++;
        try {
            Path csv = Path.of("results", "mask_norms.csv");
            Files.createDirectories(csv.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append(episodeIndex).append(",").append(String.format("%.4f", episodeReward));
            for (Mask m : wardrobe.getAllMasks()) sb.append(",").append(String.format("%.6f", m.norm()));
            sb.append("\n");
            Files.writeString(csv, sb.toString(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
    }

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

    // GETTERS

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

    // HELPERS 

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
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[TEMPER] Core: ");
        corePersonality.forEach((k, v) -> sb.append(k + "=" + String.format("%.2f", v) + " "));
        if (useMasks) { sb.append("| Mask: ").append(activeMask.getName()).append(" "); activeMask.getTraits().forEach((k, v) -> sb.append(k + "=" + String.format("%.2f", v) + " ")); }
        return sb.toString();
    }
}
