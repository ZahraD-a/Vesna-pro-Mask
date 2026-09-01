package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.wear_mask(MaskName)
 *
 * selectMask, the second half of Angelo's getMasks / selectMask pair. getMasks (the .findall over
 * wearable/1) and the choice of which wearable mask to put on both happen in alice.asl, so the
 * decision is symbolic and overridable there. This action just tells the MaskLearner which mask
 * was chosen; the learner computes A_eff = clip(core + mask) and pushes it into the original
 * Temper. Wearing a mask is not a learning step and carries no reward.
 */
public class wear_mask extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 1) return false;
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks == null) return true;
        masks.wear(args[0].toString());
        return true;
    }
}
