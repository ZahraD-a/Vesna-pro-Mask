package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.mask.MaskLearner;
import vesna.VesnaAgent;

/**
 * vesna.via.final_report
 *
 * Prints and saves the end-of-run summary: what each mask learned, evidence that it changed how
 * the agent behaved, and whether it carries over between partners. Also written to
 * results/latest/report.txt, because the console window closes as soon as the run stops.
 */
public class final_report extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        MaskLearner masks = ((VesnaAgent) ts.getAg()).getMasks();
        if (masks != null) masks.finalReport();
        return true;
    }
}
