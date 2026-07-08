package vesna;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for social interaction scenario.
 * Defines OCEAN trait annotations for all response plans.
 */
public class HelpScenarioConfig {

    private static final Map<String, Map<String, Double>> ACTION_TRAITS = new HashMap<>();

    static {
        // Formal/professional response (high C, low E)
        ACTION_TRAITS.put("formal", Map.of(
            "conscientiousness", 0.8, "extraversion", -0.6,
            "agreeableness", 0.2, "openness", -0.4, "neuroticism", -0.6));

        // Casual/relaxed response (high E, high A)
        ACTION_TRAITS.put("casual", Map.of(
            "extraversion", 0.6, "agreeableness", 0.6,
            "conscientiousness", -0.4, "openness", 0.2, "neuroticism", -0.2));

        // Enthusiastic/expressive response (high O, high E)
        ACTION_TRAITS.put("enthusiastic", Map.of(
            "openness", 0.8, "extraversion", 0.8,
            "agreeableness", 0.4, "conscientiousness", -0.6, "neuroticism", -0.4));

        // Reserved/quiet response (low E, low A)
        ACTION_TRAITS.put("reserved", Map.of(
            "extraversion", -0.8, "agreeableness", -0.4,
            "conscientiousness", 0.4, "openness", -0.6, "neuroticism", -0.8));
    }

    public static Map<String, Double> getActionTraits(String action) {
        return ACTION_TRAITS.get(action);
    }

    public static Map<String, Map<String, Double>> getAllActionTraits() {
        return ACTION_TRAITS;
    }

    public static String[] getActionsForPerson(String person) {
        return new String[]{"formal", "casual", "enthusiastic", "reserved"};
    }

    // ---- Reward model (single source of truth for the environment AND for CFR) ----

    /** Base reward of a response, independent of context. */
    public static double baseReward(String action) {
        switch (action) {
            case "formal":       return 0.3;
            case "casual":       return 0.5;
            case "enthusiastic": return 0.6;
            case "reserved":     return 0.2;
            default:             return 0.0;
        }
    }

    /** Context-dependent bonus/penalty added to the base reward. */
    public static double contextShaping(String context, String action) {
        switch (context) {
            case "work":     // professional: formal rewarded, expressive penalised
                if (action.equals("formal"))       return  0.3;
                if (action.equals("reserved"))     return  0.1;
                if (action.equals("enthusiastic")) return -0.2;
                if (action.equals("casual"))       return -0.1;
                return 0.0;
            case "home":     // relaxed: casual rewarded, stiff penalised
                if (action.equals("casual"))       return  0.3;
                if (action.equals("enthusiastic")) return  0.1;
                if (action.equals("formal"))       return -0.2;
                if (action.equals("reserved"))     return -0.1;
                return 0.0;
            case "concert":  // expressive: enthusiastic rewarded, formal penalised
                if (action.equals("enthusiastic")) return  0.3;
                if (action.equals("casual"))       return  0.1;
                if (action.equals("formal"))       return -0.3;
                if (action.equals("reserved"))     return -0.2;
                return 0.0;
            default:         // no dedicated shaping (e.g. the default mask / 'party')
                return 0.0;
        }
    }

    /** Total utility of a response in a context: base reward + context shaping. */
    public static double utility(String context, String action) {
        return baseReward(action) + contextShaping(context, action);
    }
}
