package vesna.mask;

import java.util.*;

/**
 * Turns "what did I do, to whom, where, and how did it go" into a single number, and also estimates
 * what the plans that were not chosen would have been worth.
 *
 *     reward = W_OUTCOME * how it was received
 *            - W_AUTH    * how unlike me the plan was
 *            - W_COST    * how much effort it took
 *
 * Only the first part needs the plan to have actually been run. For the plans not chosen it is
 * replaced by the average of how that partner has reacted to that style here before, starting at
 * zero when there is nothing to go on. The other two parts depend only on the agent and the plan,
 * so they can be worked out for every plan at any time.
 *
 * "How unlike me" is measured against the real personality, never the masked one. Measured against
 * the mask, a mask would always look right to itself. Measured against the real personality it
 * pulls against wanting approval, and the learned mask is the compromise between the two.
 */
public final class RewardMachine {

    /**
 * How it was received is the only part that changes from one circumstance to another. The other two
 * are the same everywhere, so unless this part is the strongest every mask drifts to the same
 * answer and the circumstance stops making any difference. At W_OUTCOME = 1.0 all three masks came
 * out within 0.05 of each other.
 */
    public static final double W_OUTCOME = 2.50;
    public static final double W_AUTH    = 0.60;
    public static final double W_COST    = 0.30;

    /**
 * Puts a number on each possible reply. The words are kept general on purpose: a person might reply
 * accepted / tolerated / rejected, while a sensor might report goal_achieved / delayed / failed.
 * Only where the reply comes from changes, never this class. An unrecognised word scores zero
 * instead of causing an error, so a new one can be added without recompiling.
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
 * How far a style is from who the agent really is: the average gap across the five traits, scaled
 * so that 0 means exactly myself and 1 means my complete opposite. The personality runs 0 to 1 and
 * a plan runs -1 to 1, so one trait can be up to 2 apart; dividing by 2 keeps W_AUTH meaning what
 * it did before.
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

    /**
     * Mean absolute distance between the persona a style projects and who the agent really is,
     * normalised to [0,1].
     *
     * The core is in [0,1] but a plan's projected persona is in [-1,1], so a per-trait distance
     * runs to 2 and the raw mean would too -- silently doubling this term's weight against
     * W_OUTCOME and W_COST once the annotations became signed. Dividing by the span keeps 0 =
     * exactly myself and 1 = my precise opposite, so W_AUTH keeps the calibration it was tuned
     * with.
     */
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
