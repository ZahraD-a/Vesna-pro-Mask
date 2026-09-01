package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.record_choice(Partner, Style)
 *
 * The agent has chosen a plan style for a partner, before the outcome is known. The plan itself
 * was selected by the ORIGINAL Temper (weighing the nine styles against the masked personality);
 * this records which style was chosen and for whom, and the MaskLearner snapshots the mixed
 * strategy the mask induced, so the counterfactual regret can later be scored against it.
 */
public class record_choice extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 2) return false;
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks == null) return true;
        masks.recordChoice(args[0].toString(), args[1].toString());
        return true;
    }
}
