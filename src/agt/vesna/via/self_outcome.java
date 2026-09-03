package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;

import java.util.Random;

/**
 * vesna.via.self_outcome(Style, Circumstance, Outcome)
 *
 * The environment, standing where a partner stands in the social scenario. Alice does a task on her
 * own and this reports back goal_achieved, delayed or failed -- the same words the reward machine
 * already scores for a partner's reply.
 *
 * Every style is available in every circumstance, exactly as the nine social styles are. What
 * changes is how well each one lands: working through a stack of documents goes well at work and
 * badly at a conference. Nothing tells the agent this, and the table below is not visible to the
 * learner, which only ever sees the outcome that comes back.
 *
 * Keeping every style applicable everywhere matters for correctness, not just symmetry. The regret
 * update scores the chosen style against a baseline over all the others, so the set it averages
 * over has to be the set that could actually have been chosen.
 */
public class self_outcome extends DefaultInternalAction {

    private static Random dice = new Random(1);

    /** Seeded from the run seed so the environment is reproducible like everything else. */
    public static void seed(long s) { dice = new Random(s); }

    /** How well a style tends to land in a circumstance. Higher is better. */
    private static double fit(String circ, String style) {
        switch (circ) {
            case "work": switch (style) {
                case "deep_work":        return 0.80;
                case "handle_backlog":   return 0.65;
                case "quick_scan":       return 0.45;
                case "tidy_up":          return 0.35;
                case "take_notes":       return 0.35;
                case "network_actively": return 0.20;
                case "rest_deeply":      return 0.15;
                case "zone_out":         return 0.10;
                case "step_away":        return 0.10;
            } break;
            case "home": switch (style) {
                case "rest_deeply":      return 0.80;
                case "tidy_up":          return 0.65;
                case "step_away":        return 0.55;
                case "zone_out":         return 0.45;
                case "quick_scan":       return 0.30;
                case "handle_backlog":   return 0.25;
                case "deep_work":        return 0.20;
                case "take_notes":       return 0.20;
                case "network_actively": return 0.15;
            } break;
            case "conference": switch (style) {
                case "network_actively": return 0.80;
                case "take_notes":       return 0.70;
                case "quick_scan":       return 0.50;
                case "step_away":        return 0.35;
                case "deep_work":        return 0.25;
                case "rest_deeply":      return 0.20;
                case "handle_backlog":   return 0.15;
                case "tidy_up":          return 0.10;
                case "zone_out":         return 0.10;
            } break;
        }
        return 0.30;
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 3) return false;
        String style = args[0].toString();
        String circ  = args[1].toString();

        double p = fit(circ, style);
        double roll = dice.nextDouble();
        String outcome = roll < p ? "goal_achieved"
                       : (roll < p + 0.30 ? "delayed" : "failed");

        return un.unifies(args[2], ASSyntax.createLiteral(outcome));
    }
}
