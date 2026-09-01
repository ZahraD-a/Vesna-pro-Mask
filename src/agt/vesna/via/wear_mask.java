package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.mask.PlanCatalog;
import vesna.VesnaAgent;

/**
 * vesna.via.wear_mask(MaskName)
 *
 * Tells the learner which mask the agent just put on. Deciding which masks fit and which one to
 * wear both happen in alice.asl, so those rules can be changed without touching Java. Here the
 * learner only works out the effective personality, clip(core + mask), and hands it to Temper.
 * Putting on a mask is not a learning step and earns no reward.
 */
public class wear_mask extends DefaultInternalAction {

    private static boolean validated = false;

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 1) return false;
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks == null) return true;
        if (!validated) {                       // plans are parsed by now, unlike at initAg()
            PlanCatalog.validate(ts.getAg().getPL());
            validated = true;
        }
        masks.wear(args[0].toString());
        return true;
    }
}
