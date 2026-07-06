package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.Temper;
import vesna.VesnaAgent;

/**
 * Initialize behavioral memory for the help scenario.
 *
 * Usage: vesna.via.init_behavioral_memory.
 */
public class init_behavioral_memory extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        VesnaAgent agent = (VesnaAgent) ts.getAg();
        Temper temper = agent.getTemper();
        if (temper == null) return false;

        temper.initBehavioralMemory();
        return true;
    }
}
