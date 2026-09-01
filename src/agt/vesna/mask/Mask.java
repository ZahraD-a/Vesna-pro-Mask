package vesna.mask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A vector of OCEAN deltas bound to a circumstance, where A_eff = clip(A_core + M, 0, 1).
 *
 * Starts at zero -- on episode 0 the agent is simply itself everywhere -- and is the only thing
 * learned; the core is never touched. Each delta is clipped to [-clip, +clip] so a mask bends the
 * agent without replacing it: one stable identity, many learned masks.
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
