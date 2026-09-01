package vesna.mask;

import java.util.*;

/**
 * RewardMachine: turns "which plan did I run, with whom, where, and how did it land" into a
 * scalar reward -- and into an ESTIMATE of what the plans I did not run would have been worth.
 *
 * WHERE EACH TERM COMES FROM
 * --------------------------
 * Every term below is something that was actually argued for in the 17/07 meeting. Nothing
 * else is in here, in particular no table telling the agent what is appropriate where.
 *
 *   R = W_OUTCOME * outcome(source)                how the situation responded
 *     - W_AUTH    * inauthenticity(style, core)    how far it was from who I am
 *     - W_COST    * effort(style)                  what it cost me to do it
 *
 * OUTCOME -- Angelo: "there is another agent that is leading me to choose if this reward has a
 *   good sign or not. For example, I help you, but you didn't respond to my help, so that's
 *   why I give the minus." And Andrea's joke: you tell one on your second day at work, nobody
 *   laughs, and the joke trait inside mask_work moves from 0 to -0.1. This is the only term
 *   that carries information about the circumstance. In the social scenario it arrives as a
 *   partner's reply; in a non-social one it would arrive as an environment transition. Either
 *   way it is a symbolic outcome scored by the table below -- the rest of the machine does not
 *   care which kind of source produced it.
 *
 * AUTHENTICITY -- Andrea: "how can I compute that if I am an open person, I regret less to eat
 *   outside with friends with respect to eat alone at home. They achieve the same goal, but
 *   they have a different regret depending on my personality." And the bus: "if you are not a
 *   helpful person you will not regret it; if you are a really helpful person ... you will
 *   regret it at least a bit."
 *
 *   Measured against the CORE personality, never against the masked one. Against the mask the
 *   term would be self-confirming -- the mask would justify itself. Against the core it
 *   creates the tension the mask has to resolve: approval pulls one way, being yourself pulls
 *   the other, and the learned mask is the bounded compromise. Angelo: "if you are not an open
 *   person, with your best friends you will go to zero, but not to one."
 *
 *   Andrea also floated a second reading -- score the plan's POST-EFFECTS against the
 *   personality rather than the persona the plan projects ("if I choose something that in the
 *   post-effects has something that is really in the opposite way of how my personality is,
 *   maybe I regret this choice a lot"). Both readings say regret is personality-relative; this
 *   implementation takes the first because the temper() annotation is already the plan's
 *   declared persona, whereas effects() only carries mood deltas. Worth confirming with them.
 *
 * COST -- Angelo's bathroom: "I could go to this bathroom, I could go to the one on the first
 *   floor. My goal is the same ... let's say each step I do is a minus one because I'm very
 *   lazy. I achieved the same goal, but I would regret not having picked the closer one."
 *   Here it is a per-style constant, which is a simplification: a full reward machine would
 *   accumulate it along the actual trace of sub-plans.
 *
 * THE PART THEY LEFT OPEN
 * -----------------------
 * Angelo, and nobody had an answer: "I choose one plan, I observe the effects ... but what if
 * I had chosen something else? That is much more difficult because I have no effects. I should
 * understand the effects of something that I didn't do."
 *
 * What follows is OUR proposal, not theirs. Split the reward into the part that is knowable
 * without acting and the part that is not. Authenticity and cost are properties of the agent
 * and the plan, so they can be evaluated for the entire applicable set at any time. Only the
 * partner's reaction requires having acted; for the plans not taken we substitute the running
 * mean of how that partner has reacted to that style IN THAT CIRCUMSTANCE, starting from zero
 * (no evidence = assume indifference). Counterfactual utility is therefore exact where it can
 * be and empirical where it cannot, and it sharpens as history accumulates.
 */
public final class RewardMachine {

    /**
     * The outcome term is the ONLY one that differs between circumstances -- authenticity and
     * cost are properties of the agent and the plan and are identical everywhere. So if the
     * outcome does not dominate, every mask converges on the same authenticity-and-cost optimum
     * and the circumstance stops mattering. Measured at W_OUTCOME = 1.0 the outcome contribution
     * averaged +0.06 to +0.16 per interaction against -0.36 for the other two terms combined,
     * and all three masks came out within 0.05 of each other. This weight is what makes the
     * agent care more about how the situation responded than about its own comfort.
     */
    public static final double W_OUTCOME = 2.50;
    public static final double W_AUTH    = 0.60;
    public static final double W_COST    = 0.30;

    /**
     * A symbolic outcome, mapped to a scalar.
     *
     * The outcome is deliberately NOT "warm/neutral/cold". Those words only make sense for a
     * person reacting to a social act, and Andrea's whole line of questioning in the meeting was
     * whether the mechanism survives outside conversation. It does, as long as the ONLY thing
     * that changes between instantiations is the source of the outcome:
     *
     *   - social:      a partner replies accepted / tolerated / rejected
     *   - environment: a sensor reports goal_achieved / delayed / failed
     *   - internal:    a self-check reports cooperation / conflict
     *
     * All of them are just labels on this table. The reward machine, the regret update and the
     * mask are identical in every case; swapping the social scenario for a battery-drain one
     * would touch only which agent (or environment action) emits the term. Unknown labels score
     * neutral rather than throwing, so a new outcome can be introduced by a receiver without a
     * recompile -- it simply carries no signal until it is added here.
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

    /** Scalar value of a symbolic outcome. Unknown outcome = neutral (0), never an error. */
    public static double outcomeScore(String outcome) {
        if (outcome == null) return 0.0;
        return OUTCOME.getOrDefault(outcome.toLowerCase(), 0.0);
    }

    /**
     * circumstance + source + style -> running mean of observed outcome scores.
     *
     * "source" is whoever produced the outcome -- a partner in the social scenario, but it could
     * as well be an environment channel. The circumstance has to be part of the key: without it
     * the agent could not tell that the same joke lands at home and dies at work, which is the
     * entire phenomenon under study. The source is part of the key too, but note the MASK never
     * sees it -- the mask update in EpisodeManager averages over sources, so what is learned is a
     * property of the circumstance, not of the source. (That is what the transfer result shows:
     * the work-mask learned against Bob applies unchanged to a new coworker.)
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
