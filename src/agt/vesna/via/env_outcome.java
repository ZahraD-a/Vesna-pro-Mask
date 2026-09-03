package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;

import java.util.Random;

/**
 * vesna.via.env_outcome(Style, Energy, Outcome)
 *
 * The environment, standing where a partner stands in the social scenario. Given the way the agent
 * chose to restore its energy, it applies that choice and reports back one of the same symbolic
 * outcomes the reward machine already understands: goal_achieved, delayed or failed.
 *
 * Nothing about the learner changes. The agent still records a choice, still receives an outcome,
 * and the mask for the current circumstance still moves by the same regret update. Only the source
 * of the outcome is different, which is the point: the mechanism does not care where reward comes
 * from.
 *
 * The dynamics are deliberately simple. Each way of recovering restores a different amount of
 * energy and costs a different amount of time; recovering enough within the time budget is
 * goal_achieved, recovering enough but slowly is delayed, and not recovering enough is failed.
 * Gains are noisy, so the environment is stochastic in the same way a partner is.
 */
public class env_outcome extends DefaultInternalAction {

    private static final double TARGET      = 60.0;   // energy needed to count as recovered
    private static final double TIME_BUDGET = 3.0;    // recovering slower than this is late

    private static Random dice = new Random(1);

    /** Called from VesnaAgent so the environment follows the run seed like everything else. */
    public static void seed(long s) { dice = new Random(s); }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 3) return false;
        String style = args[0].toString();
        double energy = ((NumberTerm) args[1]).solve();

        double gain, time;
        switch (style) {
            case "push_through":     gain = 18.0; time = 1.0; break;  // cheap, rarely enough
            case "steady_recovery":  gain = 34.0; time = 2.0; break;  // usually enough, quick
            case "patient_recovery": gain = 52.0; time = 3.0; break;  // always enough, slowest
            default:                 gain =  0.0; time = 0.0; break;
        }
        gain += (dice.nextDouble() - 0.5) * 16.0;   // the environment is not perfectly predictable

        double after = Math.min(100.0, energy + gain);
        String outcome = after < TARGET ? "failed"
                       : (time > TIME_BUDGET ? "delayed" : "goal_achieved");

        return un.unifies(args[2], ASSyntax.createLiteral(outcome))
            && un.unifies(args.length > 3 ? args[3] : new VarTerm("_"),
                          ASSyntax.createNumber(Math.round(after * 100.0) / 100.0));
    }
}
