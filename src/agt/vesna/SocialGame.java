package vesna;

import java.util.*;

/**
 * Two-player social interaction game (general-sum, simultaneous move).
 *
 * Two personality agents meet in a context and each chooses a response style.
 * Each agent's reward for its OWN response depends on THREE things:
 *
 *   reward_i = w_context   * contextFit(context, own)      // is this response appropriate here?
 *            + w_rapport    * rapport(own, other)           // does it click with the partner?  (COUPLING)
 *            + w_authentic  * authenticity(core_i, own)     // is it true to who I am?
 *
 * The rapport term couples the two agents' choices, turning the problem into a
 * genuine game (each agent's best response depends on the other). The authenticity
 * term makes the equilibrium PERSONALITY-DEPENDENT: agents with different cores
 * converge to different personas, because acting against your nature is costly.
 */
public class SocialGame {

    public static final String[] ACTIONS  = {"formal", "casual", "enthusiastic", "reserved"};
    public static final String[] CONTEXTS = {"work", "home", "concert"};

    // Relative importance of each reward component (tunable).
    // Authenticity is weighted highest so that an agent's core personality genuinely
    // resists context/social pressure — this is what makes equilibria personality-dependent.
    public static double W_CONTEXT   = 1.0;
    public static double W_RAPPORT   = 0.5;
    public static double W_AUTHENTIC = 1.8;

    /** Expressive styles vs restrained styles — used for the rapport (vibe-matching) term. */
    private static final Set<String> EXPRESSIVE = new HashSet<>(Arrays.asList("casual", "enthusiastic"));

    /**
     * Rapport between two response styles (symmetric):
     *   identical style      -> +0.30  (mirroring builds strong rapport)
     *   same "vibe" group    -> +0.15  (both expressive, or both restrained)
     *   opposite vibe groups -> -0.20  (a clash: e.g. one loud, one stiff)
     */
    public static double rapport(String own, String other) {
        if (own.equals(other)) return 0.30;
        boolean eo = EXPRESSIVE.contains(own), et = EXPRESSIVE.contains(other);
        return (eo == et) ? 0.15 : -0.20;
    }

    /** How appropriate a response is for a context (base reward + context shaping). */
    public static double contextFit(String context, String action) {
        return HelpScenarioConfig.utility(context, action);
    }

    /**
     * Authenticity: cosine similarity between the action's OCEAN signature and the
     * agent's core personality, scaled. High when the response matches who the agent is;
     * negative when the agent must "fake" a persona far from its true self.
     */
    public static double authenticity(Map<String, Double> core, String action) {
        Map<String, Double> at = HelpScenarioConfig.getActionTraits(action);
        if (at == null) return 0.0;
        double dot = 0, na = 0, nc = 0;
        for (String trait : core.keySet()) {
            double c = core.get(trait);
            double v = at.getOrDefault(trait, 0.0);
            dot += c * v; na += v * v; nc += c * c;
        }
        double denom = Math.sqrt(na) * Math.sqrt(nc);
        double cos = denom > 0 ? dot / denom : 0.0;   // in [-1, 1]
        return 0.5 * cos;                              // scale to a comparable range
    }

    /** Total reward agent i gets for playing {@code own} while its partner plays {@code other}. */
    public static double reward(String context, Map<String, Double> core, String own, String other) {
        return W_CONTEXT   * contextFit(context, own)
             + W_RAPPORT   * rapport(own, other)
             + W_AUTHENTIC * authenticity(core, own);
    }

    /** Convenience: build an OCEAN core-personality map. */
    public static Map<String, Double> core(double o, double c, double e, double a, double n) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("openness", o);
        m.put("conscientiousness", c);
        m.put("extraversion", e);
        m.put("agreeableness", a);
        m.put("neuroticism", n);
        return m;
    }
}
