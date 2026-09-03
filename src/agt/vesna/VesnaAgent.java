package vesna;

import java.util.*;
import vesna.mask.MaskLearner;
import vesna.mask.PlanCatalog;

import jason.asSemantics.Agent;
import jason.asSemantics.Intention;
import jason.asSemantics.Option;
import jason.runtime.Settings;

/**
 * The agent class used by every agent here. Plan choice is handed to the original Temper unchanged;
 * the only additions are reading the mask settings from vesna.jcm and, when masks are on, creating
 * the learner that supplies Temper with the personality to show.
 *
 * The original class also drove a 3D body over a WebSocket. That is left out: this scenario has no
 * body, only agents exchanging messages.
 *
 * Bob, Carol and Dave use the same class with masks off, so they choose replies with their own
 * personality but never wear or learn a mask.
 */
public class VesnaAgent extends Agent {

    private Temper temper;
    private MaskLearner masks;

    @Override
    public void initAg() {
        super.initAg();

        Settings s = getTS().getSettings();
        String temperLiteral = s.getUserParameter("temper");
        if (temperLiteral == null) return;

        String strategy = s.getUserParameter("strategy");
        temper = new Temper(temperLiteral, strategy);   // ORIGINAL Temper, unchanged

        // One seed value in the .jcm gives every agent its own reproducible stream: the agent name
        // is mixed in, so all four can carry the same seed line and still behave differently.
        temper.setCompatibility(s.getUserParameter("compat"));   // absent -> dot, the measured baseline

        String seed = s.getUserParameter("seed");
        if (seed != null) temper.setSeed(Long.parseLong(seed.trim()) * 31L + getTS().getAgArch().getAgName().hashCode());

        if ("true".equals(s.getUserParameter("use_masks"))) {
            PlanCatalog.use(s.getUserParameter("domain"));  // absent -> social, the measured default

            double delta = parseDouble(s.getUserParameter("mask_delta"), 0.5);
            double lr    = parseDouble(s.getUserParameter("mask_learning_rate"), 0.08);
            masks = new MaskLearner(temper, delta, lr, s.getUserParameter("results_dir"));
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

    private static double parseDouble(String v, double d) { try { return v == null ? d : Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return d; } }
}
