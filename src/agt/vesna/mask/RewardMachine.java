package vesna.mask;

import java.util.*;

/**
 * Turns "which plan did I run, with whom, where, and how did it land" into a scalar reward, and
 * estimates what the plans I did not run would have been worth.
 *
 *     R = W_OUTCOME * outcome(source) - W_AUTH * inauthenticity(style, core) - W_COST * effort(style)
 *
 * Only the outcome requires having acted, so for the plans not taken it is replaced by the running
 * mean of how that source has reacted to that style in that circumstance (zero until there is
 * evidence). Authenticity and cost are properties of the agent and the plan and are exact for the
 * whole applicable set at any time.
 *
 * Authenticity is measured against the CORE personality, never the masked one: against the mask the
 * term would be self-confirming. Against the core it creates the tension the mask has to resolve.
 */
public final class RewardMachine {

    /**
 * The outcome is the only term that differs between circumstances, so if it does not dominate every
 * mask converges on the same authenticity-and-cost optimum and the circumstance stops mattering.
 * Measured at W_OUTCOME = 1.0 all three masks came out within 0.05 of each other.
 */
    public static final double W_OUTCOME = 2.50;
    public static final double W_AUTH    = 0.60;
    public static final double W_COST    = 0.30;

    /**
 * A symbolic outcome mapped to a scalar. The labels are deliberately generic: a partner may reply
 * accepted / tolerated / rejected, an environment sensor goal_achieved / delayed / failed. Only the
 * source changes between instantiations, never the machine. Unknown labels score neutral rather
 * than throwing, so a receiver can introduce one without a recompile.
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
 * Mean absolute distance between the persona a style projects and who the agent really is,
 * normalised to [0,1]. The core is in [0,1] but a plan's persona is in [-1,1], so a per-trait
 * distance runs to 2; dividing by the span keeps W_AUTH's calibration.
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
