package vesna;

import java.util.HashMap;
import java.util.Map;

/**
 * MASK = behavioral overlay on top of the agent's core identity.
 *
 * HOW IT WORKS:
 * ─────────────
 *   Core identity (A_core) = WHO YOU ARE (set at design time, never changes)
 *   Mask (M)               = HOW YOU ADAPT in a specific context (learned via CFR)
 *
 *   Effective personality = A_eff = clip(A_core + M, -1.0, +1.0)
 *
 * EXAMPLE:
 * ────────
 *   A_core = [O=0.3, C=-0.2, E=0.1, A=0.5, N=-0.4]  (your true self)
 *   M_work = [O=0.1, C=0.3,  E=0.0, A=0.2, N=-0.3]  (learned adaptation at work)
 *   A_eff  = [O=0.4, C=0.1,  E=0.1, A=0.7, N=-0.7]  (how you behave at work)
 *
 * DESIGN-TIME DECISION:
 * ─────────────────────
 *   Each mask is created at design time for a specific context.
 *   All masks START at [0,0,0,0,0] (no modification to core).
 *   Through CFR learning, masks EVOLVE to adapt the agent per context.
 *
 * DELTA (δ):
 * ──────────
 *   Each mask trait is clipped to [-δ, +δ] to prevent extreme drift.
 *   When ||M|| (L2 norm) exceeds a threshold, the mask has diverged
 *   significantly from the agent's core — this is reported for analysis.
 */
public class Mask {

    // ─── OCEAN trait names ───────────────────────────────────────────
    public static final String[] OCEAN = {
        "openness", "conscientiousness", "extraversion", "agreeableness", "neuroticism"
    };

    // ─── Fields ──────────────────────────────────────────────────────
    private final String name;              // e.g., "mask_work"
    private final String context;           // e.g., "work" (the situation this mask is for)
    private final String wearabilityRule;   // belief name: "at_work", "at_home", etc.
    private Map<String, Double> traits;     // OCEAN delta values (start at 0)
    private final double deltaClip;         // max absolute value per trait (δ)
    private final int creationEpisode;      // episode when mask was created

    // ─── Constructor ─────────────────────────────────────────────────

    /**
     * Create a new mask for a specific context.
     * All traits START at 0.0 (no modification to core identity).
     *
     * @param name            unique ID, e.g., "mask_work"
     * @param context         situation name, e.g., "work"
     * @param wearabilityRule belief that enables this mask, e.g., "at_work"
     * @param deltaClip       δ: max absolute trait value (e.g., 0.5)
     * @param creationEpisode episode when this mask was created
     */
    public Mask(String name, String context, String wearabilityRule,
                double deltaClip, int creationEpisode) {
        this.name = name;
        this.context = context;
        this.wearabilityRule = wearabilityRule;
        this.deltaClip = deltaClip;
        this.creationEpisode = creationEpisode;

        // ALL MASKS START AT ZERO — no modification to core identity
        this.traits = new HashMap<>();
        for (String trait : OCEAN) {
            this.traits.put(trait, 0.0);
        }
    }

    // ─── Trait Manipulation ──────────────────────────────────────────

    /**
     * Set a trait to a specific value (for initial configuration).
     * Clips to [-δ, +δ].
     */
    public void setTrait(String trait, double value) {
        traits.put(trait, Math.max(-deltaClip, Math.min(deltaClip, value)));
    }

    /**
     * Update a single trait, clipping to [-δ, +δ].
     * This is called by the CFR update loop.
     */
    public void updateTrait(String trait, double delta) {
        double current = traits.getOrDefault(trait, 0.0);
        double updated = Math.max(-deltaClip, Math.min(deltaClip, current + delta));
        traits.put(trait, updated);
    }

    /** Get current delta value for a trait. */
    public double getTrait(String trait) {
        return traits.getOrDefault(trait, 0.0);
    }

    /** Get all traits as a map. */
    public Map<String, Double> getTraits() {
        return new HashMap<>(traits);
    }

    /**
     * Compute L2 norm of this mask's trait vector.
     * ||M|| = sqrt(Σ M_trait²)
     */
    public double norm() {
        double sum = 0.0;
        for (double v : traits.values()) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    /**
     * Has this mask diverged significantly from zero?
     * Used for analysis and sharing decisions.
     */
    public boolean hasSignificantAdaptation(double threshold) {
        return norm() >= threshold;
    }

    /** Reset all traits to zero (fresh start). */
    public void reset() {
        for (String trait : OCEAN) {
            traits.put(trait, 0.0);
        }
    }

    /** Deep copy. */
    public Mask copy() {
        Mask copy = new Mask(name, context, wearabilityRule, deltaClip, creationEpisode);
        copy.traits = new HashMap<>(this.traits);
        return copy;
    }

    // ─── Getters ─────────────────────────────────────────────────────

    public String getName()             { return name; }
    public String getContext()          { return context; }
    public String getWearabilityRule()  { return wearabilityRule; }
    public double getDeltaClip()        { return deltaClip; }
    public int getCreationEpisode()     { return creationEpisode; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Mask[%s] ctx=%s ||M||=%.4f {", name, context, norm()));
        boolean first = true;
        for (String trait : OCEAN) {
            if (!first) sb.append(", ");
            sb.append(String.format("%s=%+.3f", trait, traits.get(trait)));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
