package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.record_outcome(Source, Outcome)
 *
 * The situation has responded with a symbolic outcome (accepted / tolerated / rejected in the
 * social scenario; goal_achieved / delayed / failed in a non-social one). This is the only point
 * where genuine external feedback enters -- it was produced outside the learning agent, not
 * sampled from a table inside it -- and it triggers the counterfactual regret update that moves
 * the active circumstance's mask.
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
