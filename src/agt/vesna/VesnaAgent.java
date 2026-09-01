package vesna;

import java.util.*;
import vesna.mask.MaskLearner;
import java.util.logging.Logger;

import jason.asSemantics.Agent;
import jason.asSemantics.Intention;
import jason.asSemantics.Option;
import jason.runtime.Settings;

/**
 * The agent class every agent in this project uses.
 *
 * It is deliberately thin: the original personality-driven plan selection, plus one hook for masks.
 * Choosing a plan is handed to the original Temper, exactly as before. The only things added are:
 *
 *   - it reads the mask settings from vesna.jcm and, if masks are switched on, creates a learner;
 *   - that learner gives Temper the personality to show whenever the agent changes circumstance.
 *
 * The original version of this class also drove a 3D body over a WebSocket. That is left out here,
 * because this scenario has no body -- only agents sending each other messages.
 *
 * Bob, Carol and Dave use this same class with masks switched off, so they are ordinary
 * personality-driven agents: they choose their replies with their own personality, but they never
 * wear or learn a mask.
 */
public class VesnaAgent extends Agent {

    private Temper temper;
    private MaskLearner masks;
    protected transient Logger logger;

    @Override
    public void initAg() {
        super.initAg();
        logger = getTS().getLogger();

        Settings s = getTS().getSettings();
        String temperLiteral = s.getUserParameter("temper");
        if (temperLiteral == null) return;

        String strategy = s.getUserParameter("strategy");
        temper = new Temper(temperLiteral, strategy);   // ORIGINAL Temper, unchanged

        if ("true".equals(s.getUserParameter("use_masks"))) {
            double delta = parseDouble(s.getUserParameter("mask_delta"), 0.5);
            double lr    = parseDouble(s.getUserParameter("mask_learning_rate"), 0.08);
            masks = new MaskLearner(temper, delta, lr);
        }
    }

    // --- original VEsNA-Pro plan/intention selection, delegating to the (masked) Temper ---

    @Override
    public Option selectOption(List<Option> options) {
        if (options == null || options.isEmpty()) return null;
        if (temper == null || options.size() == 1 || !temper.hasOptionsAnnotation(options))
            return options.remove(0);
        Option selected = temper.selectOption(options);
        options.remove(selected);
        return selected;
    }

    @Override
    public Intention selectIntention(Queue<Intention> intentions) {
        if (temper == null || intentions.size() == 1 || !temper.hasIntentionsAnnotation(intentions))
            return super.selectIntention(intentions);
        Intention selected = temper.selectIntention(intentions);
        intentions.remove(selected);
        return selected;
    }

    public Temper getTemper()      { return temper; }
    public MaskLearner getMasks()  { return masks; }

    private static long   parseLong(String v, long d)     { try { return v == null ? d : Long.parseLong(v.trim()); }     catch (NumberFormatException e) { return d; } }
    private static double parseDouble(String v, double d) { try { return v == null ? d : Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return d; } }
}
