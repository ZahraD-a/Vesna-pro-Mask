package vesna;

import java.util.*;

/**
 * Mask Wardrobe: the collection of all masks the agent owns.
 *
 * DESIGN-TIME DECISION:
 *   The developer decides how many masks exist based on the scenario.
 *   Each mask is for ONE context (e.g., work, home, concert).
 *   All masks START at [0,0,0,0,0] — no modification to core identity.
 *
 * MASK SELECTION:
 *   The agent picks the mask in ASL via the mask_for/2 rules
 *   (see mask_rules.asl) and passes its name to set_decision_context.
 *   No context match → mask_default (true self, no modification).
 *
 * EXAMPLE:
 *   contexts = ["work", "home", "concert"]
 *   → creates: mask_default, mask_work, mask_home, mask_concert
 *   → all start at [0,0,0,0,0]
 *   → CFR learns different values for each mask over time
 */
public class MaskWardrobe {

    private final Map<String, Mask> masks;
    private final Mask defaultMask;
    private final double maskClip;

    /**
     * Create a wardrobe with masks for each context.
     *
     * @param maskClip        δ: max absolute trait value per mask
     * @param contexts        context names (e.g., ["work", "home"])
     * @param creationEpisode episode when wardrobe is created (usually 0)
     */
    public MaskWardrobe(double maskClip, List<String> contexts, int creationEpisode) {
        this.maskClip = maskClip;
        this.masks = new LinkedHashMap<>();

        // Default mask: always wearable, no modification
        this.defaultMask = new Mask("mask_default", "default", maskClip, creationEpisode);
        masks.put("default", defaultMask);

        // One mask per context — all start at [0,0,0,0,0]
        for (String ctx : contexts) {
            masks.put(ctx, new Mask("mask_" + ctx, ctx, maskClip, creationEpisode));
        }
    }

    /** Get mask by context name. Returns default if not found. */
    public Mask getMask(String context) {
        return masks.getOrDefault(context, defaultMask);
    }

    /** Get the default mask (no modification). */
    public Mask getDefaultMask() {
        return defaultMask;
    }

    /** Get all masks. */
    public Collection<Mask> getAllMasks() {
        return masks.values();
    }

    /** Get masks that have diverged beyond a threshold. */
    public List<Mask> getSignificantlyAdapted(double threshold) {
        List<Mask> adapted = new ArrayList<>();
        for (Mask mask : masks.values()) {
            if (!mask.getName().equals("mask_default")
                && mask.hasSignificantAdaptation(threshold)) {
                adapted.add(mask);
            }
        }
        return adapted;
    }

    public double getMaskClip() { return maskClip; }
}
