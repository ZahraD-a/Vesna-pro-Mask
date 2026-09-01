package vesna.mask;

import java.util.*;

/**
 * PlanCatalog: the socially-different ways of achieving ONE goal.
 *
 * WHY NINE PLANS AND NOT THREE
 * ----------------------------
 * The goal is always the same: "a partner needs help with T". What varies is HOW the
 * agent goes about it. Andrea's point in the 17/07 meeting was that with three near
 * identical plans the choice is effectively forced, so nothing can be learned:
 *
 *     "you should define scenarios in which you achieve the same goal in different ways
 *      that are psychologically far apart ... otherwise it will be so deterministic that
 *      you will not learn anything"
 *
 * So every style below is simultaneously APPLICABLE (no restrictive plan context) and
 * psychologically distinct. The trait vector of each style mirrors, one-for-one, the
 * temper() annotation on the corresponding plan in alice.asl -- that annotation is what
 * Jason's option selection actually reads, this table is what the reward machine and the
 * mask update read. Temper.validateAgainstCatalog() checks at runtime that they agree.
 *
 * WHY THERE IS NO fit(circumstance, style) TABLE HERE
 * ---------------------------------------------------
 * An earlier version of this class carried one: a table stating that help_after_task is
 * worth +0.35 at work, joke_deflect -0.30, and so on. It has been removed, because nothing
 * in the 17/07 discussion puts such a table inside the learner. What is appropriate in a
 * circumstance is something the agent DISCOVERS from how the people around it react:
 *
 *     "you make some jokes while you are taking a coffee, it is your second day at work,
 *      and you see that nobody is laughing. Maybe this will put your trait telling jokes
 *      inside your mask work from 0 to minus 0.1."
 *
 * Nobody hands that agent a number saying jokes are worth -0.30 at work. It tells a joke and
 * the room stays silent. A table inside the reward function short-circuits exactly the thing
 * we are trying to observe: with it, half of every "learned" mask was really dictated by the
 * designer. Social norms now live in the receivers' belief bases (improper/2 in bob.asl,
 * carol.asl, dave.asl), where they are opaque to Alice and reach her only as cold replies.
 *
 * What remains here is what genuinely belongs to the acting agent: the persona each plan
 * projects, and what each plan costs to carry out.
 */
public final class PlanCatalog {

    public static final String[] OCEAN = {
        "o", "c", "e", "a", "n"
    };

    /**
     * Uppercase form for display. The traits are named o/c/e/a/n throughout -- in the .jcm
     * temper literal, in every plan annotation, and in these maps -- because five full trait
     * names on one line is unreadable, and because that is the notation Andrea used when he
     * sketched the receiver plans: @p1[o(0.5), c(0.2)]. The initials are unambiguous, no two
     * of the five sharing one. Reports print them uppercase; AgentSpeak functors must start
     * lowercase, so the stored form is lowercase.
     */
    public static String abbrev(String trait) {
        return trait.isEmpty() ? "?" : trait.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /** The nine ways of achieving !manage/2, in a fixed order (index = CFR action index). */
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
