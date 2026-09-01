package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.end_episode
 *
 * The agent's life-cycle plan decides WHEN an episode is over -- that stays symbolic, as the
 * belief episode(N) in alice.asl. What an episode MEANS for learning (fold the accumulated regret
 * into every visited mask, write the logs, reset) is machinery and lives in the MaskLearner. This
 * action is the seam between the two.
 */
public class end_episode extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks != null) masks.endEpisode();
        return true;
    }
}
