package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.record_outcome(Source, Outcome)
 *
 * A reply has come back: accepted, tolerated or rejected here, or something like goal_achieved,
 * delayed or failed in a non-social setting. This is the only place real outside feedback enters.
 * It came from another agent, not from any table inside Alice, and it is what drives the update to
 * the mask she is currently wearing.
 */
public class record_outcome extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 2) return false;
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks == null) return true;
        masks.recordOutcome(args[0].toString(), args[1].toString());
        return true;
    }
}
