package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.Temper;
import vesna.VesnaAgent;

/**
 * Sets the current decision context for CFR tracking + active mask.
 *
 * Usage: vesna.via.set_decision_context(bob, mask_work).
 *
 * @param args[0] person name (e.g., "bob") — used for CFR infoset key
 * @param args[1] mask name (e.g., "mask_work") — which mask to wear
 */
public class set_decision_context extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        if (args.length < 2) return false;

        VesnaAgent agent = (VesnaAgent) ts.getAg();
        Temper temper = agent.getTemper();
        if (temper == null) return false;

        // Arg 0: person name → CFR infoset key
        String person = args[0].toString().toLowerCase();
        temper.setCurrentStage("help_" + person);

        // Arg 1: mask name -> which mask to wear
        // ASL passes "mask_concert" but wardrobe key is "concert"
        String maskName = args[1].toString().toLowerCase();
        String contextKey = maskName.replace("mask_", "");

        // Arg 2 (optional): the raw context/belief that led here, so the wardrobe
        // can record which contexts fall back to (and train) the default mask.
        String requestedContext = args.length >= 3
            ? args[2].toString().toLowerCase() : contextKey;

        if (temper.isUseMasks()) {
            temper.setActiveMask(contextKey, requestedContext);
            System.out.println("[MASK] Wearing " + maskName + " for interaction with " + person
                + " (context=" + requestedContext + ")");
        }

        return true;
    }
}
