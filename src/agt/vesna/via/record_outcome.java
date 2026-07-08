package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.Temper;
import vesna.VesnaAgent;
import vesna.HelpScenarioConfig;

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
        String action = args[2].toString();
        String person = args[3].toString().toLowerCase();

        // Observed reward comes from the shared reward model (HelpScenarioConfig.utility),
        // so the environment and CFR can never disagree about payoffs. The 'neutral' branch
        // (the ~30% environment-noise outcome in the .asl) yields zero this time.
        String context = (temper.isUseMasks() && temper.getActiveMask() != null)
            ? temper.getActiveMask().getContext() : "default";
        double observedReward = event.equals("neutral")
            ? 0.0 : HelpScenarioConfig.utility(context, action.toLowerCase());

        temper.recordHelpOutcome(action, observedReward, person);

        return true;
    }
}
