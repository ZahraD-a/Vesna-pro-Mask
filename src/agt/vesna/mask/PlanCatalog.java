package vesna.mask;

import java.util.*;

import jason.asSyntax.*;
import jason.pl.PlanLibrary;
import jason.NoValueException;

/**
 * The nine ways of helping and the personality each one projects.
 *
 * A second copy of the temper() annotations in alice.asl, needed because after each choice the
 * learner has to score the eight plans it did not pick, and Java cannot read their annotations at
 * that point. validate() stops the run if the two copies disagree.
 *
 * There is deliberately no table of which style suits which circumstance: that is what the agent
 * has to learn. Those norms live in the receivers' beliefs, out of Alice's reach.
 */
public final class PlanCatalog {

    /** The five trait keys, in report order. Defined once in Mask. */
    public static final String[] OCEAN = Mask.OCEAN;

    /** Uppercase initial, for the report. */
    public static String abbrev(String trait) {
        return trait.isEmpty() ? "?" : trait.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /** Fixed order: the index is the action index used by the regret update. */
    public static final String[] STYLES = {
        "drop_everything",  // drop everything else and help
        "help_after_task",  // help, but finish the current task first
        "pair_up",          // do it together, side by side
        "teach",            // explain it so they can do it themselves
        "quick_tip",        // one fast pointer, then back to work
        "delegate",         // send them to somebody better suited
        "joke_deflect",     // make a joke, help only half-seriously
        "polite_decline",   // say no, but warmly
        "ignore"            // say nothing at all
    };

    private static final Map<String, Map<String, Double>> TRAITS = new LinkedHashMap<>();
    private static final Map<String, Double> EFFORT = new LinkedHashMap<>();

    private static void style(String name, double o, double c, double e, double a, double n, double effort) {
        Map<String, Double> t = new LinkedHashMap<>();
        t.put("o", o);
        t.put("c", c);
        t.put("e", e);
        t.put("a", a);
        t.put("n", n);
        TRAITS.put(name, t);
        EFFORT.put(name, effort);
    }

    static {
        //     name                O     C     E     A     N    effort
        // Five OCEAN traits per style, -1 to +1, the range the original Temper already accepts for
        // plan annotations. -1 is the opposite of a trait, not a small amount of it: ignore has
        // a = -0.90, active coldness.
        //
        // A plan is scored by multiplying the agent's traits by the plan's and summing, so a
        // negative annotation can make the total negative -- warm Alice (a = 0.75) scores ignore at
        // -1.39. Negative-scoring plans are never picked, so she cannot ignore anyone until a mask
        // lowers her agreeableness. With all-positive numbers no plan was ever out of reach.
        style("drop_everything",  0.20, -0.50,  0.40,  0.90,  0.10, 1.00);
        style("help_after_task", -0.10,  0.80,  0.00,  0.40, -0.20, 0.50);
        style("pair_up",          0.60,  0.20,  0.80,  0.70, -0.30, 0.70);
        style("teach",            0.70,  0.70,  0.20,  0.50, -0.20, 0.60);
        style("quick_tip",        0.30,  0.10,  0.30,  0.30, -0.10, 0.20);
        style("delegate",         0.00,  0.50,  0.20, -0.20,  0.00, 0.10);
        style("joke_deflect",     0.80, -0.60,  0.70,  0.10, -0.40, 0.20);
        style("polite_decline",  -0.30,  0.40, -0.40, -0.30,  0.20, 0.00);
        style("ignore",          -0.60, -0.30, -0.80, -0.90,  0.40, 0.00);
    }

    private PlanCatalog() {}

    /** Stop the run if alice.asl's temper() numbers no longer match the table above. */
    public static void validate(PlanLibrary pl) {
        for (Plan p : pl.getPlans()) {
            Pred label = p.getLabel();
            if (label == null || !isStyle(label.getFunctor())) continue;
            Literal annot = label.getAnnot("temper");
            if (annot == null) continue;
            String style = label.getFunctor();
            for (Term t : (ListTerm) annot.getTerm(0)) {
                Literal trait = (Literal) t;
                double declared;
                try {
                    declared = ((NumberTerm) trait.getTerm(0)).solve();
                } catch (NoValueException e) {
                    throw new IllegalStateException("bad temper annotation on @" + style, e);
                }
                double expected = trait(style, trait.getFunctor());
                if (Math.abs(declared - expected) > 1e-9)
                    throw new IllegalStateException(String.format(
                        "@%s in alice.asl declares %s(%.2f) but PlanCatalog says %.2f -- "
                        + "the two must match", style, trait.getFunctor(), declared, expected));
            }
        }
    }

    public static int index(String style) {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(style)) return i;
        return -1;
    }

    public static boolean isStyle(String s) { return index(s) >= 0; }

    /** OCEAN vector of a style, i.e. the persona it projects. Never null for a known style. */
    public static Map<String, Double> traits(String style) {
        Map<String, Double> t = TRAITS.get(style);
        return t == null ? Collections.emptyMap() : t;
    }

    public static double trait(String style, String traitName) {
        return traits(style).getOrDefault(traitName, 0.0);
    }

    /**
     * How much time and energy this style costs, regardless of how it is received. Two routes can
     * reach the same goal with different effort, and the cheaper one is worth preferring, so
     * afterwards you regret not having taken the shorter one.
     */
    public static double effort(String style) {
        return EFFORT.getOrDefault(style, 0.0);
    }
}
