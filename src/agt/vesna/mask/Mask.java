package vesna.mask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A mask is a vector of OCEAN deltas, one per trait, bound to a circumstance.
 *
 *     A_eff = clip( A_core + M , 0 , 1 )
 *
 * It starts at zero -- on episode 0 the agent is simply itself in every circumstance -- and is
 * the ONLY thing the framework learns. The core personality is never touched. Each delta is
 * clipped to [-delta, +delta] so a mask can bend the agent but not replace it: a reserved person
 * among trusted friends moves toward the middle, never to the maximum. This is the bounded,
 * Pirandellian reading agreed in the meeting -- one stable identity, many learned masks.
 *
 * OCEAN is stored with the single-letter keys o/c/e/a/n, the notation Andrea used in his own
 * sketch and the notation the .jcm and plan annotations use.
 */
public final class Mask {

    public static final String[] OCEAN = { "o", "c", "e", "a", "n" };

    private final String name;          // e.g. "mask_work"
    private final String circumstance;  // e.g. "work"
    private final double clip;          // per-trait bound on |delta|
    private final Map<String, Double> delta = new LinkedHashMap<>();

    public Mask(String name, String circumstance, double clip) {
        this.name = name;
        this.circumstance = circumstance;
        this.clip = clip;
        for (String t : OCEAN) delta.put(t, 0.0);
    }

    public String name()          { return name; }
    public String circumstance()  { return circumstance; }
    public double clip()          { return clip; }
    public double get(String t)   { return delta.getOrDefault(t, 0.0); }

    /** Move a delta toward a target by an exponential step, staying within [-clip, +clip]. */
    public void moveToward(String t, double target, double rate) {
        double bounded = Math.max(-clip, Math.min(clip, target));
        double next = (1.0 - rate) * get(t) + rate * bounded;
        delta.put(t, Math.max(-clip, Math.min(clip, next)));
    }

    /** L2 norm: how far this mask has moved from the identity. */
    public double norm() {
        double s = 0.0;
        for (double v : delta.values()) s += v * v;
        return Math.sqrt(s);
    }
}
