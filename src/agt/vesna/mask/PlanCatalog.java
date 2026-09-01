package vesna.mask;

import java.util.*;

import jason.asSyntax.*;
import jason.pl.PlanLibrary;
import jason.NoValueException;

/**
 * The nine socially different ways of achieving one goal, and the persona each projects.
 *
 * This is a Java-side mirror of the temper() annotations in alice.asl. The duplication is
 * deliberate and unavoidable: the counterfactual-regret update needs the trait vector of the plans
 * that were NOT executed, and Jason does not hand those to Java at update time. validate() is
 * called at startup to fail loudly if the two copies ever drift.
 *
 * There is no fit(circumstance, style) table here. What is appropriate where is something the agent
 * discovers from how the receivers react -- Andrea: "you make some jokes while you are taking a
 * coffee, it is your second day at work, and you see that nobody is laughing." A table inside the
 * reward function would short-circuit exactly the thing under study. Social norms live in the
 * receivers' belief bases (improper/2), opaque to Alice.
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
        // A style's projected persona, OCEAN in [-1,1] -- the range the ORIGINAL Temper already
        // accepts for a plan annotation (it validates [-1,1] in computeWeight). Following Andrea's
        // Definition 3.1, -1 is the REVERSE of the trait, not a small amount of it: ignore(a=-0.90)
        // projects active coldness, polite_decline(e=-0.40) projects withdrawal.
        //
        // The agent's personality stays in [0,1] exactly as upstream. A positive trait times a
        // negative annotation still gives a NEGATIVE compatibility score, so Alice's warm core
        // (a=0.75) scores ignore at -1.39 and polite_decline at -0.39: she cannot play them at all
        // until a mask lowers her agreeableness. That is what the [0,1] annotations could never
        // express, since every score they produced was positive.
        //
        // Values are the previous [0,1] numbers under the order-preserving remap v -> 2v-1, so the
        // relative shape of the style space is unchanged; only its origin moved to the neutral
        // point, which is what lets compatibility carry a sign.
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

    /**
     * Fail at startup if alice.asl's temper() annotations have drifted from the table above. The
     * two copies exist because the CFR update needs the traits of plans that were not executed;
     * this is what keeps them honest.
     */
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
     * How much of my own time and energy this style costs me, whatever anyone thinks of it.
     * This is Angelo's bathroom: same goal reached, but one path is longer than the other, so
     * afterwards you regret not having taken the shorter one.
     */
    public static double effort(String style) {
        return EFFORT.getOrDefault(style, 0.0);
    }
}
