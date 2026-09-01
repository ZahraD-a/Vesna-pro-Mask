package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.end_episode
 *
 * The agent decides WHEN an episode ends; that stays in alice.asl as the belief episode(N). What
 * ending one MEANS -- update every mask visited, write the logs, start again -- happens in
 * MaskLearner. This action joins the two.
 */
public class end_episode extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks != null) masks.endEpisode();
        return true;
    }
}
