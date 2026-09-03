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

    /**
     * Which set of styles is in play. A domain is one goal with its own way of achieving it: the
     * social domain is helping a colleague, the energy domain is restoring the agent's own energy
     * with no partner involved. The mask machinery does not know the difference -- the same regret
     * update runs over whichever set is active, which is the point of having more than one.
     *
     * social is the default. Selecting nothing leaves the catalogue exactly as it was, so results
     * measured before the second domain existed still reproduce.
     */
    public static final String SOCIAL = "social";

    private static final Map<String, List<String>> DOMAIN_STYLES = new LinkedHashMap<>();
    private static final Map<String, Map<String, Map<String, Double>>> DOMAIN_TRAITS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Double>> DOMAIN_EFFORT = new LinkedHashMap<>();

    // SINGLE DOMAIN PER JVM. active is global, so every learner in a run shares one style set.
    //
    // This is what stops a second agent learning alongside Alice today. A receiver chooses among
    // react_accept / react_tolerate / react_reject, which are not in the social set, so index()
    // returns -1 for every choice it makes, recordChoice skips it, and its mask sits at zero for
    // the whole run. Nothing throws and every output file is still written, so the failure looks
    // exactly like a successful run with a flat mask.
    //
    // Supporting it means giving each learner its own style set rather than reading this field.
    private static String active = SOCIAL;

    /** Fixed order: the index is the action index used by the regret update. */
    public static String[] STYLES = new String[0];

    /**
     * Choose the domain. Must be called before the learner is built, because the regret arrays are
     * sized from STYLES. Unknown or null leaves the social domain in place.
     */
    public static void use(String domain) {
        if (domain != null && DOMAIN_STYLES.containsKey(domain.trim().toLowerCase(Locale.ROOT)))
            active = domain.trim().toLowerCase(Locale.ROOT);
        STYLES = DOMAIN_STYLES.get(active).toArray(new String[0]);
    }

    public static String domain() { return active; }

    private static void style(String domain, String name,
                              double o, double c, double e, double a, double n, double effort) {
        Map<String, Double> t = new LinkedHashMap<>();
        t.put("o", o);
        t.put("c", c);
        t.put("e", e);
        t.put("a", a);
        t.put("n", n);
        DOMAIN_STYLES.computeIfAbsent(domain, k -> new ArrayList<>()).add(name);
        DOMAIN_TRAITS.computeIfAbsent(domain, k -> new LinkedHashMap<>()).put(name, t);
        DOMAIN_EFFORT.computeIfAbsent(domain, k -> new LinkedHashMap<>()).put(name, effort);
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
        style(SOCIAL, "drop_everything",  0.20, -0.50,  0.40,  0.90,  0.10, 1.00);
        style(SOCIAL, "help_after_task", -0.10,  0.80,  0.00,  0.40, -0.20, 0.50);
        style(SOCIAL, "pair_up",          0.60,  0.20,  0.80,  0.70, -0.30, 0.70);
        style(SOCIAL, "teach",            0.70,  0.70,  0.20,  0.50, -0.20, 0.60);
        style(SOCIAL, "quick_tip",        0.30,  0.10,  0.30,  0.30, -0.10, 0.20);
        style(SOCIAL, "delegate",         0.00,  0.50,  0.20, -0.20,  0.00, 0.10);
        style(SOCIAL, "joke_deflect",     0.80, -0.60,  0.70,  0.10, -0.40, 0.20);
        style(SOCIAL, "polite_decline",  -0.30,  0.40, -0.40, -0.30,  0.20, 0.00);
        style(SOCIAL, "ignore",          -0.60, -0.30, -0.80, -0.90,  0.40, 0.00);


        use(SOCIAL);
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
        Map<String, Double> t = DOMAIN_TRAITS.get(active).get(style);
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
        return DOMAIN_EFFORT.get(active).getOrDefault(style, 0.0);
    }
}
