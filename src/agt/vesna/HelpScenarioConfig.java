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

    public static void initBehavioralMemory(BehavioralMemory memory) {
        memory.addPerson("social", "Social", 0.5, 0.5);
        System.out.println("[MEMORY] Social interaction partner initialized");
    }
}
