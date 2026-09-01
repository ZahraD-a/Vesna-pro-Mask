package vesna.mask;

import java.util.*;

import jason.asSyntax.*;
import jason.pl.PlanLibrary;
import jason.NoValueException;

/**
 * The nine ways of helping, and the kind of person each one acts like.
 *
 * These numbers are a second copy of the temper() annotations in alice.asl. The copy is needed:
 * after each choice the learner has to ask "what would the other eight plans have been worth?",
 * and Java cannot read another plan's annotation at that moment. validate() compares the two
 * copies when the run starts and stops it if they disagree.
 *
 * Note what is NOT here: any table saying which style suits which circumstance. That is the thing
 * the agent is supposed to learn. It finds out by trying something and seeing how people react --
 * tell a joke at work, notice nobody laughs. Writing the answer into the reward would defeat the
 * whole experiment. What counts as out of place is stored in the receivers' own beliefs, where
 * Alice cannot read it.
 */
public final class PlanCatalog {

    public static final String[] OCEAN = {
        "o", "c", "e", "a", "n"
    };

    /** Uppercase initial for display. Traits are o/c/e/a/n throughout, as in the .jcm and the plans. */
    public static String abbrev(String trait) {
        return trait.isEmpty() ? "?" : trait.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /** The nine styles in a fixed order; the index is the CFR action index. */
    public static final String[] STYLES = {
        "drop_everything",  // abandon my own task, help completely
        "help_after_task",  // help, but finish what I was doing first
        "pair_up",          // do it together, side by side
        "teach",            // explain it so they can do it themselves
        "quick_tip",        // one fast pointer, then back to my own work
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
        // Each style described by the five OCEAN traits, from -1 to +1. This is the range the
        // original Temper already accepts for a plan annotation.
        //
        // -1 means the opposite of a trait, not a little of it. So ignore has a = -0.90, which is
        // active coldness, and polite_decline has e = -0.40, which is pulling away.
        //
        // The agent's own personality still runs 0 to 1, unchanged. A positive trait times a
        // negative number is still negative, so Alice, who is warm (a = 0.75), scores ignore at
        // -1.39 and polite_decline at -0.39. Plans with a negative score are never picked, so she
        // cannot do either until a mask makes her less agreeable. With all-positive numbers every
        // score came out positive and no plan was ever truly off-limits.
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
