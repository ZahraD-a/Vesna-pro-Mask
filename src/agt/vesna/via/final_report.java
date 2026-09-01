package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.final_report
 *
 * Prints and saves the end-of-run report: the learned masks first (the object of study), then the
 * evidence that they changed behaviour, then the transfer/partner-independence result. Written to
 * results/mas/report.txt as well, since the MAS console window dies with the JVM at .stopMAS.
 */
public class final_report extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks != null) masks.finalReport();
        return true;
    }
}
