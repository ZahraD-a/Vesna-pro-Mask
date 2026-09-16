package vesna.mask;

import vesna.Temper;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * All the mask machinery in one class: the set of masks, how a mask changes which plan gets picked,
 * how the masks are updated from experience, and the logging.
 *
 * It reaches into the original Temper through a single method, useEffective(). When the agent
 * enters a circumstance, wear() works out the personality it will show -- the real one plus that
 * circumstance's mask -- and hands it over. Temper then chooses a plan exactly as it always did,
 * using this personality instead of the real one, and never knows a mask was involved.
 *
 * Only the masks change; the real personality never does. After each outcome the learner scores every
 * style that was available, and its regret is how much better or worse than expected it was:
 *
 *     regret[style] = what that style was worth - what the agent expected to get
 *     mask[trait]  += rate * sum over styles of regret[style] * style's annotation on trait
 *
 * Styles with positive regret pull the worn mask towards their annotations, styles with negative
 * regret push it away. Each trait is capped, so a mask bends the agent without turning it into
 * someone else.
 */
public final class MaskLearner {

    private final Temper temper;
    private final RewardMachine rewards = new RewardMachine();
    private final Map<String, Double> core;      // A_core, read once from Temper (o/c/e/a/n)
    private final double maskDelta;
    private final double learningRate;

    private final Map<String, Mask> wardrobe = new LinkedHashMap<>();  // circumstance -> mask
    private final Mask defaultMask;
    private Mask activeMask;

    private int episode = 0;

    private final Deque<Decision> pending = new ArrayDeque<>();

    private double episodeReward = 0.0;
    private int    episodeInteractions = 0;

    private final List<Map<String, int[]>> history = new ArrayList<>();
    private Map<String, int[]> currentCounts = new LinkedHashMap<>();
    private final Map<String, int[]>    byPartner  = new LinkedHashMap<>();
    private final Map<String, double[]> components = new LinkedHashMap<>();  // circ -> [outcome,auth,cost,n]
    private final Map<String, int[]>    outcomes   = new LinkedHashMap<>();  // circ -> [pos,neu,neg]

    private final Path OUT;

    public MaskLearner(Temper temper, double maskDelta, double learningRate, String outDir) {
        this.OUT           = Path.of(outDir == null || outDir.isBlank()
                                     ? "results/latest" : outDir.trim().replace("\"", ""));
        this.temper        = temper;
        this.core          = temper.getPersonality();   // the one read of the core
        this.maskDelta     = maskDelta;
        this.learningRate  = learningRate;

        this.defaultMask = new Mask("mask_default", "default", maskDelta);
        wardrobe.put("default", defaultMask);
        this.activeMask = defaultMask;

        System.out.println("[MASK] core personality " + fmt(core));
        System.out.println("[MASK] masks start at zero, one per circumstance entered");

        try {
            // Clear every file this run will write. Two of them are appended to, so a stale one
            // would silently continue a previous run; the rest are only written at the end, so a
            // run that dies would leave last run's report looking like this run's.
            Files.createDirectories(OUT);
            for (String f : new String[] { "episode_log.csv", "mask_trajectory.csv", "report.txt",
                                           "learned_masks.csv", "reward_components.csv",
                                           "style_shift.csv", "style_by_partner.csv", "core_samples.csv",
                                           "episode_outcomes.csv", "mask_steps.csv",
                                           "plot_mask_trajectory.png", "plot_entropy.png",
                                           "plot_style_shift.png", "plot_partner_mix.png" })
                Files.deleteIfExists(OUT.resolve(f));
            Files.writeString(OUT.resolve("episode_log.csv"),
                "episode,interactions,total_reward,mean_reward,entropy_work,entropy_home,entropy_conference\n");
            Files.writeString(OUT.resolve("mask_trajectory.csv"), "episode,mask,o,c,e,a,n,norm\n");
            Files.writeString(OUT.resolve("core_samples.csv"), "episode,o,c,e,a,n\n");
            Files.writeString(OUT.resolve("episode_outcomes.csv"), "episode,circumstance,interactions,outcome\n");
            // One row per mask update: which plan was played, how it landed, its regret against the
            // expectation, the mask after the step, and the step each trait actually took.
            Files.writeString(OUT.resolve("mask_steps.csv"),
                "update,episode,circumstance,mask,partner,style,outcome,chosen_regret,"
                + "o,c,e,a,n,step_o,step_c,step_e,step_a,step_n\n");
        } catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
        logMasks();
        logCore();
    }

    // ----------------------------------------------------------------- wearing a mask

    /**
 * Put on a mask and give the resulting personality to Temper. The name comes from alice.asl, which
 * is where both "which masks fit here" and "which one to wear" are decided. A circumstance seen for
 * the first time gets a new mask of its own rather than quietly falling back to the default.
 */
    public void wear(String maskName) {
        activeCircumstance = maskName.startsWith("mask_") ? maskName.substring(5) : maskName;
        String key = sharedMask ? SHARED : activeCircumstance;
        activeMask = wardrobe.computeIfAbsent(key, c -> new Mask("mask_" + c, c, maskDelta));
        temper.useEffective(effective());
    }

    /**
     * Ablation: one mask for every circumstance. The agent still knows where it is, and outcomes are
     * still recorded per circumstance; only the mask is shared, so what is learned in one
     * circumstance is carried into all the others.
     */
    public void shareOneMask() { sharedMask = true; }

    /**
     * Variant under test, off by default: weight each plan's regret by the probability the agent
     * gives it. Plans it no longer picks then stop steering the mask on the strength of old
     * estimates it has no way to refresh.
     */
    public void weightRegretByPolicy() { policyWeighted = true; }
    private int updates = 0;
    private boolean policyWeighted = false;

    private static final String SHARED = "shared";
    private boolean sharedMask = false;
    private String activeCircumstance = "default";

    /** A_eff = clip(core + activeMask, -1, 1), keeping any non-OCEAN traits (mood) untouched. */
    private Map<String, Double> effective() {
        Map<String, Double> eff = new LinkedHashMap<>(core);
        for (String t : Mask.OCEAN) {
            if (!core.containsKey(t)) continue;
            double v = core.get(t) + activeMask.get(t);
            eff.put(t, Math.max(-1.0, Math.min(1.0, v)));
        }
        return eff;
    }

    public String activeCircumstance() { return activeCircumstance; }

    // ----------------------------------------------------------------- the policy

    /**
 * How likely the agent is to pick each style while wearing the current mask. This repeats exactly
 * what Temper does when it chooses a plan, so the learner measures each style against what the
 * agent really does rather than against a guess. A style can score below zero, meaning it goes
 * against who the agent is; those get no chance of being picked, which is what Temper does too.
 */
    private double[] policy() {
        double[] w = new double[PlanCatalog.STYLES.length];
        double sum = 0.0;
        for (int a = 0; a < w.length; a++) {
            w[a] = Math.max(0.0, temper.compatibility(PlanCatalog.traits(PlanCatalog.STYLES[a])));
            sum += w[a];
        }
        for (int a = 0; a < w.length; a++) w[a] = sum > 0 ? w[a] / sum : 1.0 / w.length;
        return w;
    }

    // ----------------------------------------------------------------- decisions

    private static final class Decision {
        final String circumstance, style;
        final Mask mask;                 // the mask worn when the style was chosen
        final double[] policy;
        String partner;
        Decision(String c, Mask m, String s, double[] p) { circumstance = c; mask = m; style = s; policy = p; }
    }

    /** The agent has just chosen a style for a partner (before the outcome is known). */
    public void recordChoice(String partner, String style) {
        pending.addLast(new Decision(activeCircumstance(), activeMask, style, policy()));
        Decision d = pending.peekLast();
        d.partner = partner.toLowerCase();
    }

    /** The situation responded. Close the loop and fold the regret into this circumstance's mask. */
    public void recordOutcome(String source, String outcome) {
        String p = source.toLowerCase();
        Decision d = null;
        for (Iterator<Decision> it = pending.iterator(); it.hasNext(); ) {
            Decision cand = it.next();
            if (p.equals(cand.partner)) { d = cand; it.remove(); break; }
        }
        if (d == null) { System.err.println("[MASK] outcome from " + p + " with no open decision"); return; }

        String chosen = d.style;
        double score = RewardMachine.outcomeScore(outcome);
        rewards.observe(d.circumstance, p, chosen, score);
        double realised = rewards.realised(chosen, core, score);
        episodeReward += realised;
        episodeInteractions++;

        double[] eo = episodeOutcome.computeIfAbsent(d.circumstance, k -> new double[2]);
        eo[0] += RewardMachine.W_OUTCOME * score;
        eo[1] += 1.0;

        double[] comp = components.computeIfAbsent(d.circumstance, k -> new double[4]);
        comp[0] += RewardMachine.W_OUTCOME * score;
        comp[1] -= RewardMachine.W_AUTH * RewardMachine.inauthenticity(chosen, core);
        comp[2] -= RewardMachine.W_COST * PlanCatalog.effort(chosen);
        comp[3] += 1.0;
        outcomes.computeIfAbsent(d.circumstance, k -> new int[3])
                [score > 0 ? 0 : (score < 0 ? 2 : 1)]++;

        // utility of every style: exact for the executed one, counterfactual estimate for the rest
        int nStyles = PlanCatalog.STYLES.length;
        double[] u = new double[nStyles];
        for (int a = 0; a < nStyles; a++) {
            u[a] = PlanCatalog.STYLES[a].equals(chosen)
                 ? realised
                 : rewards.counterfactual(d.circumstance, PlanCatalog.STYLES[a], core, p);
        }
        double v = 0.0;
        for (int a = 0; a < nStyles; a++) v += d.policy[a] * u[a];

        // Regret rho(p) = u(p) - v, signed. Each trait moves by the regret-weighted sum of the plans'
        // annotations: plans that would have done better pull the mask towards them, plans that
        // would have done worse push it away. One bounded step, on the mask worn when the plan was
        // chosen and on no other.
        Mask worn = d.mask;
        StringBuilder stepLog = new StringBuilder();
        stepLog.append(String.format(Locale.ROOT, "%d,%d,%s,%s,%s,%s,%s,%.4f",
            ++updates, episode + 1, d.circumstance, worn.name(), p, chosen, outcome, realised - v));
        StringBuilder moved = new StringBuilder();
        for (String t : Mask.OCEAN) {
            double g = 0.0;
            for (int a = 0; a < nStyles; a++)
                g += (policyWeighted ? d.policy[a] : 1.0) * (u[a] - v) * PlanCatalog.trait(PlanCatalog.STYLES[a], t);
            double before = worn.get(t);
            worn.step(t, learningRate * g);
            stepLog.append(String.format(Locale.ROOT, ",%.5f", worn.get(t)));
            moved.append(String.format(Locale.ROOT, ",%.6f", worn.get(t) - before));
        }
        append("mask_steps.csv", stepLog.append(moved).append("\n").toString());
        // Selection reads the mask as it is now, so a change to the one being worn applies at once.
        if (worn == activeMask) temper.useEffective(effective());

        count(currentCounts, d.circumstance, chosen);
        count(byPartner, p, chosen);
    }

    private static void count(Map<String, int[]> m, String key, String style) {
        int a = PlanCatalog.index(style);
        if (a >= 0) m.computeIfAbsent(key, k -> new int[PlanCatalog.STYLES.length])[a]++;
    }

    // ----------------------------------------------------------------- episode boundary

    /** Masks are updated after each outcome; the episode boundary only logs and resets counters. */
    public void endEpisode() {
        if (!pending.isEmpty()) { pending.clear(); }
        episode++;

        logEpisode();
        logMasks();
        logEpisodeOutcomes();
        if (episode % CORE_SAMPLE_EVERY == 0) logCore();
        history.add(currentCounts);
        currentCounts = new LinkedHashMap<>();
        episodeReward = 0.0;
        episodeInteractions = 0;

        if (episode == 1 || episode % 20 == 0) {
            System.out.printf("[MASK] episode %d%n", episode);
            for (Mask m : wardrobe.values())
                if (m.norm() > 1e-6) System.out.printf("    %-16s ||M||=%.3f%n", m.name(), m.norm());
        }
    }

    public int episode() { return episode; }

    // ----------------------------------------------------------------- logging

    private static final int ENTROPY_WINDOW = 10;

    private int[] window(String circ) {
        int[] acc = new int[PlanCatalog.STYLES.length];
        int[] now = currentCounts.get(circ);
        if (now != null) for (int a = 0; a < acc.length; a++) acc[a] += now[a];
        for (int i = history.size() - 1, taken = 1; i >= 0 && taken < ENTROPY_WINDOW; i--, taken++) {
            int[] past = history.get(i).get(circ);
            if (past != null) for (int a = 0; a < acc.length; a++) acc[a] += past[a];
        }
        return acc;
    }

    private static double entropy(int[] counts) {
        if (counts == null) return 0.0;
        double n = 0.0;
        for (int c : counts) n += c;
        if (n == 0) return 0.0;
        double h = 0.0;
        for (int c : counts) { if (c == 0) continue; double p = c / n; h -= p * (Math.log(p) / Math.log(2)); }
        return h;
    }

    private double lastReward = 0.0;
    private int    lastInteractions = 0;

    private void logEpisode() {
        lastReward = episodeReward;
        lastInteractions = episodeInteractions;
        double mean = episodeInteractions == 0 ? 0.0 : episodeReward / episodeInteractions;
        append("episode_log.csv", String.format(Locale.ROOT, "%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f%n",
            episode, episodeInteractions, episodeReward, mean,
            entropy(window("work")), entropy(window("home")), entropy(window("conference"))));
    }

    private void logMasks() {
        StringBuilder sb = new StringBuilder();
        for (Mask m : wardrobe.values()) {
            sb.append(episode).append(",").append(m.name());
            for (String t : Mask.OCEAN) sb.append(String.format(Locale.ROOT, ",%.5f", m.get(t)));
            sb.append(String.format(Locale.ROOT, ",%.5f%n", m.norm()));
        }
        append("mask_trajectory.csv", sb.toString());
    }

    // Mean outcome term per circumstance for the episode just ended, in the same units as the
    // outcome column of reward_components.csv. This is what shows a change of norms over time.
    private final Map<String, double[]> episodeOutcome = new LinkedHashMap<>();  // circ -> [sum, n]

    private void logEpisodeOutcomes() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, double[]> e : episodeOutcome.entrySet())
            sb.append(String.format(Locale.ROOT, "%d,%s,%d,%.4f%n", episode, e.getKey(),
                (int) e.getValue()[1], e.getValue()[0] / e.getValue()[1]));
        append("episode_outcomes.csv", sb.toString());
        episodeOutcome.clear();
    }

    // The core is read once and never written, so every row here should be identical. Logged
    // anyway, so the claim is checked against the run and not only against the code.
    private static final int CORE_SAMPLE_EVERY = 30;

    private void logCore() {
        StringBuilder sb = new StringBuilder().append(episode);
        for (String t : Mask.OCEAN) sb.append(String.format(Locale.ROOT, ",%.5f", core.getOrDefault(t, 0.0)));
        append("core_samples.csv", sb.append("\n").toString());
    }

    private void append(String file, String text) {
        try { Files.writeString(OUT.resolve(file), text, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
    }

    // ----------------------------------------------------------------- final report

    private final StringBuilder report = new StringBuilder();
    private void p(String fmt, Object... a) { report.append(String.format(Locale.ROOT, fmt, a)); }
    private void pln(String s)              { report.append(s).append(System.lineSeparator()); }
    private void pln()                      { report.append(System.lineSeparator()); }

    /** Lead with the masks: they are the object of study. Then the evidence they did something. */
    public void finalReport() {
        int n = history.size();
        if (n == 0) { System.out.println("[REPORT] nothing recorded"); return; }
        int w = Math.max(1, n / 5);

        pln("\n=================== WHAT THE MASKS LEARNED ===================");
        p("%d episodes. Every mask started at zero: on episode 0 the agent was%n", n);
        pln("simply herself in every circumstance. All of the following was learned.\n");

        p("    %-16s", "");
        for (String t : Mask.OCEAN) p("%8s", t.toUpperCase());
        pln("      ||M||");
        p("    %-16s", "core identity");
        for (String t : Mask.OCEAN) p("%+8.2f", core.getOrDefault(t, 0.0));
        pln("        --");
        pln("                    O openness   C conscientiousness   E extraversion   A agreeableness   N neuroticism");

        StringBuilder mcsv = new StringBuilder("circumstance,mask,trait,core,mask_offset,effective\n");
        for (Mask m : wardrobe.values()) {
            p("    %-16s", m.name());
            for (String t : Mask.OCEAN) p("%+8.2f", m.get(t));
            p("%10.3f%n", m.norm());
            for (String t : Mask.OCEAN) {
                double c0 = core.getOrDefault(t, 0.0), off = m.get(t);
                mcsv.append(String.format(Locale.ROOT, "%s,%s,%s,%.4f,%.4f,%.4f%n",
                    m.circumstance(), m.name(), t, c0, off, Math.max(-1, Math.min(1, c0 + off))));
            }
        }

        pln("\n    who she becomes, trait by trait (core -> core + mask):");
        for (Mask m : wardrobe.values()) {
            if (m.norm() < 1e-6) { p("    %-16s unchanged (circumstance never visited)%n", m.name()); continue; }
            p("    %-16s", m.name());
            String big = Mask.OCEAN[0]; double best = 0;
            for (String t : Mask.OCEAN) if (Math.abs(m.get(t)) > Math.abs(best)) { best = m.get(t); big = t; }
            for (String t : Mask.OCEAN) {
                if (Math.abs(m.get(t)) < 0.05) continue;
                double c0 = core.getOrDefault(t, 0.0);
                p(" %s %.2f->%.2f ", t.toUpperCase(), c0, Math.max(-1, Math.min(1, c0 + m.get(t))));
            }
            p("%n                     strongest shift: %s %+.2f%n", big.toUpperCase(), best);
        }

        pln("\n--- where the learning signal came from (mean per interaction) ---");
        pln("    Only the outcome term carries information the agent did not already have;");
        pln("    authenticity and cost it could compute for every style without acting.");
        StringBuilder ccsv = new StringBuilder("circumstance,outcome,authenticity,cost,positive_pct,neutral_pct,negative_pct\n");
        for (Map.Entry<String, double[]> e : components.entrySet()) {
            double[] v = e.getValue();
            if (v[3] == 0) continue;
            int[] r = outcomes.getOrDefault(e.getKey(), new int[3]);
            double rn = Math.max(1, r[0] + r[1] + r[2]);
            p("    %-12s outcome %+.3f   authenticity %+.3f   cost %+.3f   |  + %2.0f%%  0 %2.0f%%  - %2.0f%%%n",
                e.getKey(), v[0] / v[3], v[1] / v[3], v[2] / v[3], 100 * r[0] / rn, 100 * r[1] / rn, 100 * r[2] / rn);
            ccsv.append(String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                e.getKey(), v[0] / v[3], v[1] / v[3], v[2] / v[3], r[0] / rn, r[1] / rn, r[2] / rn));
        }

        pln("\n--- did the mask change what she actually does? ---");
        p("    share of each plan, first %d episodes -> last %d%n", w, w);
        Map<String, int[]> early = fold(0, w), late = fold(n - w, n);
        StringBuilder scsv = new StringBuilder("circumstance,style,early_pct,late_pct,shift\n");
        // The known social circumstances first, in their long-standing order, then anything else
        // the run actually visited. Without the second part a new domain reports nothing here.
        List<String> order = new ArrayList<>(Arrays.asList("work", "home", "conference", "default"));
        for (String c : wardrobe.keySet()) if (!order.contains(c)) order.add(c);
        for (String c : order) {
            int[] e = early.get(c), l = late.get(c);
            if (e == null && l == null) continue;
            p("%n    circumstance: %s%n", c);
            for (int a = 0; a < PlanCatalog.STYLES.length; a++) {
                double pe = pct(e, a), pl = pct(l, a);
                scsv.append(String.format(Locale.ROOT, "%s,%s,%.4f,%.4f,%.4f%n", c, PlanCatalog.STYLES[a], pe, pl, pl - pe));
                if (pe < 0.005 && pl < 0.005) continue;
                p("        %-16s %5.1f%%  ->  %5.1f%%   %+5.1f%n", PlanCatalog.STYLES[a], pe * 100, pl * 100, (pl - pe) * 100);
            }
        }

        pln("\n--- transfer: the mask is a property of the circumstance, not of the partner ---");
        pln("    One mask is learned per circumstance and indexed by nothing else, so the rows");
        pln("    below -- her style mix with each partner -- should look ALIKE. The work-mask");
        pln("    learned while dealing with Bob transfers unchanged to Carol, Dave and any new");
        pln("    coworker: mask count does not grow with the number of agents.");
        StringBuilder pcsv = new StringBuilder("partner,style,share\n");
        for (Map.Entry<String, int[]> en : byPartner.entrySet()) {
            p("    %-8s", en.getKey());
            for (int a = 0; a < PlanCatalog.STYLES.length; a++) {
                double share = pct(en.getValue(), a);
                if (share >= 0.08) p(" %s=%.0f%%", PlanCatalog.STYLES[a], share * 100);
                pcsv.append(String.format(Locale.ROOT, "%s,%s,%.4f%n", en.getKey(), PlanCatalog.STYLES[a], share));
            }
            pln();
        }
        pln("==============================================================\n");

        System.out.print(report);
        try {
            Files.writeString(OUT.resolve("report.txt"), report.toString());
            Files.writeString(OUT.resolve("learned_masks.csv"), mcsv.toString());
            Files.writeString(OUT.resolve("reward_components.csv"), ccsv.toString());
            Files.writeString(OUT.resolve("style_shift.csv"), scsv.toString());
            Files.writeString(OUT.resolve("style_by_partner.csv"), pcsv.toString());
        } catch (IOException e) { System.err.println("[LOG] " + e.getMessage()); }
    }

    private Map<String, int[]> fold(int from, int to) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (int i = from; i < to && i < history.size(); i++)
            for (Map.Entry<String, int[]> e : history.get(i).entrySet()) {
                int[] acc = out.computeIfAbsent(e.getKey(), k -> new int[PlanCatalog.STYLES.length]);
                for (int a = 0; a < acc.length; a++) acc[a] += e.getValue()[a];
            }
        return out;
    }

    private static double pct(int[] counts, int a) {
        if (counts == null) return 0.0;
        double n = 0.0;
        for (int c : counts) n += c;
        return n == 0 ? 0.0 : counts[a] / n;
    }

    private static String fmt(Map<String, Double> m) {
        StringBuilder sb = new StringBuilder("{");
        for (String t : Mask.OCEAN) if (m.containsKey(t)) sb.append(t.toUpperCase()).append("=").append(String.format(Locale.ROOT, "%.2f ", m.get(t)));
        return sb.append("}").toString();
    }
}
