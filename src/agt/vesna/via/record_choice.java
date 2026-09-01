package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.record_choice(Partner, Style)
 *
 * Notes which style the agent chose, and for whom, before any reply has arrived. The choice itself
 * was made by the original Temper. The learner also saves how likely each style was at that moment,
 * so that when the reply comes it can work out whether the choice was better or worse than average.
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
