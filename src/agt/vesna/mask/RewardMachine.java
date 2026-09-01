package vesna.mask;

import java.util.*;

/**
 * Scores a choice:
 *
 *     reward = W_OUTCOME * how it was received
 *            - W_AUTH    * distance from the agent's real personality
 *            - W_COST    * effort
 *
 * Only the first term needs the plan to have been run. For the plans not chosen it uses the average
 * of past replies to that style, in that circumstance, from that partner, which is what lets the
 * regret update score them too.
 *
 * The distance is measured against the real personality, not the masked one. Against the mask, a
 * mask would always look right to itself.
 */
public final class RewardMachine {

    /**
 * The outcome term is the only one that varies by circumstance, so it has to outweigh the other two
 * or every mask converges on the same answer. At W_OUTCOME = 1.0 all three ended within 0.05.
 */
    public static final double W_OUTCOME = 2.50;
    public static final double W_AUTH    = 0.60;
    public static final double W_COST    = 0.30;

    /**
 * Score per reply. The words are generic so a sensor could report goal_achieved / delayed / failed
 * into the same table. Unknown words score zero rather than failing.
 */
    private static final Map<String, Double> OUTCOME = Map.ofEntries(
        Map.entry("accepted",      1.0),   // social: the help was welcomed
        Map.entry("cooperation",   1.0),   // internal / multi-party: it went well together
        Map.entry("goal_achieved", 1.0),   // environment: the world reached the target state
        Map.entry("tolerated",     0.0),   // allowed, but nothing gained
        Map.entry("neutral",       0.0),
        Map.entry("delayed",      -0.5),   // environment: goal reached, but late / costly
        Map.entry("rejected",     -1.0),   // social: the help was rebuffed
        Map.entry("conflict",     -1.0),   // internal / multi-party: it clashed
        Map.entry("failed",       -1.0));  // environment: the world did not reach the target

    /** circumstance + source + style -> running mean of observed outcome scores. */
    public static double outcomeScore(String outcome) {
        if (outcome == null) return 0.0;
        return OUTCOME.getOrDefault(outcome.toLowerCase(), 0.0);
    }

    /**
 * Average gap between a style and the agent's real personality, scaled to 0..1.
 */
    private final Map<String, double[]> history = new HashMap<>();  // [sum, count]

    private static String key(String circumstance, String source, String style) {
        return circumstance + "/" + source.toLowerCase() + "/" + style;
    }

    public void observe(String circumstance, String source, String style, double outcomeScore) {
        double[] acc = history.computeIfAbsent(key(circumstance, source, style), k -> new double[2]);
        acc[0] += outcomeScore;
        acc[1] += 1.0;
    }

    /** Expected outcome of this style, here, from this source. Zero until there is evidence. */
    public double expectedOutcome(String circumstance, String source, String style) {
        double[] acc = history.get(key(circumstance, source, style));
        return (acc == null || acc[1] == 0.0) ? 0.0 : acc[0] / acc[1];
    }

    /** A personality trait runs 0..1 and a plan trait -1..1, so the widest gap is 2. */
    private static final double TRAIT_SPAN = 2.0;

    public static double inauthenticity(String style, Map<String, Double> corePersonality) {
        double sum = 0.0;
        for (String t : PlanCatalog.OCEAN) {
            sum += Math.abs(PlanCatalog.trait(style, t) - corePersonality.getOrDefault(t, 0.0));
        }
        return sum / (PlanCatalog.OCEAN.length * TRAIT_SPAN);
    }

    /** The terms that do not require the plan to have been executed. */
    public static double intrinsic(String style, Map<String, Double> core) {
        return -W_AUTH * inauthenticity(style, core)
               -W_COST * PlanCatalog.effort(style);
    }

    /** Reward actually collected: intrinsic terms plus the outcome the source really produced. */
    public double realised(String style, Map<String, Double> core, double outcomeScore) {
        return intrinsic(style, core) + W_OUTCOME * outcomeScore;
    }

    /** Utility of a style that was not executed: intrinsic exactly, outcome from history. */
    public double counterfactual(String circumstance, String style, Map<String, Double> core, String source) {
        return intrinsic(style, core) + W_OUTCOME * expectedOutcome(circumstance, source, style);
    }
}
