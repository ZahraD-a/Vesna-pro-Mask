package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.Temper;
import vesna.VesnaAgent;

/**
 * Records outcome for CFR regret matching.
 * Includes context-dependent reward shaping:
 *   - work context: formal actions get bonus
 *   - home context: casual actions get bonus
 *   - concert context: enthusiastic actions get bonus
 *
 * Usage: vesna.via.record_outcome(success, 0.5, action_name, person).
 */
public class record_outcome extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 4) return false;

        VesnaAgent agent = (VesnaAgent) ts.getAg();
        Temper temper = agent.getTemper();

        String event = args[0].toString().toLowerCase().trim();
        double baseReward = ((NumberTerm) args[1]).solve();
        String action = args[2].toString();
        String person = args[3].toString().toLowerCase();

        // Start with base reward
        double enhancedReward = baseReward;

        // Context-dependent reward shaping
        // Different contexts reward different behaviors differently
        if (temper.isUseMasks() && temper.getActiveMask() != null) {
            String context = temper.getActiveMask().getContext();

            if (context.equals("work")) {
                // Work: formal/professional behavior is rewarded more
                if (action.contains("formal")) enhancedReward += 0.3;
                if (action.contains("reserved")) enhancedReward += 0.1;
                if (action.contains("enthusiastic")) enhancedReward -= 0.2;
                if (action.contains("casual")) enhancedReward -= 0.1;
            }
            else if (context.equals("home")) {
                // Home: casual/relaxed behavior is rewarded more
                if (action.contains("casual")) enhancedReward += 0.3;
                if (action.contains("enthusiastic")) enhancedReward += 0.1;
                if (action.contains("formal")) enhancedReward -= 0.2;
                if (action.contains("reserved")) enhancedReward -= 0.1;
            }
            else if (context.equals("concert")) {
                // Concert: enthusiastic/expressive behavior is rewarded more
                if (action.contains("enthusiastic")) enhancedReward += 0.3;
                if (action.contains("casual")) enhancedReward += 0.1;
                if (action.contains("formal")) enhancedReward -= 0.3;
                if (action.contains("reserved")) enhancedReward -= 0.2;
            }
        }

        // Record for CFR (reward depends only on the context, not the individual)
        temper.recordHelpOutcome(action, enhancedReward, person);

        return true;
    }
}
