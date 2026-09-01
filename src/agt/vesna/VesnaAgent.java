package vesna;

import java.util.*;
import java.util.logging.Logger;

import jason.asSemantics.Agent;
import jason.asSemantics.Intention;
import jason.asSemantics.Option;
import jason.runtime.Settings;

/**
 * VesnaAgent for the mask scenario.
 *
 * This is a deliberately thin agent: it is the original VEsNA-Pro personality-driven plan
 * selection plus one seam for masks. selectOption / selectIntention delegate to the ORIGINAL
 * Temper exactly as upstream does. The only additions are:
 *
 *   - it reads the mask parameters from the .jcm and, if masks are on, builds a MaskLearner;
 *   - the MaskLearner pushes the effective personality into Temper (Temper.useEffective) whenever
 *     the agent changes circumstance, so the unchanged selection reads A_eff instead of A_core.
 *
 * The embodied machinery of the upstream VesnaAgent (the WebSocket body, sight/rcc handling) is
 * not here: this scenario has no physical body, only Jason agents exchanging KQML. That is a
 * different instantiation of the same framework, not a change to it.
 *
 * The receivers (Bob, Carol, Dave) use this same class with use_masks off, so they are plain
 * VEsNA-Pro temper agents -- they have a personality and pick among their reaction plans with it,
 * but they never wear or learn a mask.
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
            List<String> circumstances = new ArrayList<>();
            String cs = s.getUserParameter("circumstances");
            if (cs != null) for (String c : cs.split(",")) circumstances.add(c.trim());

            double delta = parseDouble(s.getUserParameter("mask_delta"), 0.5);
            double lr    = parseDouble(s.getUserParameter("mask_learning_rate"), 0.08);
            int    maxEp = (int) parseLong(s.getUserParameter("max_episodes"), 0);

            masks = new MaskLearner(temper, circumstances, delta, lr, maxEp);
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
