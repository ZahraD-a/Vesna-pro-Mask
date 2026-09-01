package vesna.mask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One mask: a small adjustment to each of the five traits, belonging to one circumstance. The
 * personality the agent shows is the real one plus this, kept within 0 and 1.
 *
 * Every mask starts at zero, so in the first episode the agent is simply itself everywhere and any
 * difference later on was learned. The masks are the only thing that changes; the real personality
 * never does. Each adjustment is capped, so a mask can bend the agent but not replace it: one
 * person, several faces.
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
