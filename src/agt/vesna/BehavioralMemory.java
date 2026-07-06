package vesna;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Behavioral memory: per-colleague relationship tracking.
 *
 * Tracks for each colleague:
 * - reciprocity: how much they reciprocate Alice's help
 * - relationship: overall relationship quality
 * - isExploitative: whether they take advantage of Alice
 *
 * Also contains PersonMemory for CFR-learning colleagues (like Carol).
 */
public class BehavioralMemory {

    private Map<String, PersonMemory> people = new HashMap<>();

    /** Add a static colleague (no CFR learning). */
    public void addPerson(String key, String name, double reliability, double reciprocity) {
        PersonMemory pm = new PersonMemory(key, name, reliability, reciprocity, false);
        people.put(key, pm);
    }

    /** Add a CFR-learning colleague with initial personality. */
    public void addPersonWithPersonality(String key, String name,
            double reliability, double reciprocity,
            double o, double c, double e, double a, double n) {
        PersonMemory pm = new PersonMemory(key, name, reliability, reciprocity, true);
        pm.personality.put("openness", o);
        pm.personality.put("conscientiousness", c);
        pm.personality.put("extraversion", e);
        pm.personality.put("agreeableness", a);
        pm.personality.put("neuroticism", n);
        people.put(key, pm);
    }

    /** Get PersonMemory for a colleague. */
    public PersonMemory getPersonMemory(String key) {
        return people.get(key.toLowerCase());
    }

    /** Update behavioral memory after an interaction. */
    public void update(String person, boolean helped, Random dice) {
        PersonMemory pm = people.get(person.toLowerCase());
        if (pm == null) return;

        if (helped) {
            pm.relationship = Math.min(1.0, pm.relationship + 0.05);
            pm.reciprocityRatio = (pm.reciprocityRatio * pm.interactions + 1.0)
                                / (pm.interactions + 1);
        } else {
            pm.relationship = Math.max(0.0, pm.relationship - 0.02);
            pm.reciprocityRatio = (pm.reciprocityRatio * pm.interactions)
                                / (pm.interactions + 1);
        }
        pm.interactions++;

        // Check if exploitative
        pm.isExploitative = pm.reciprocityRatio < 0.3 && pm.interactions >= 5;
    }

    /** Get a behavioral value for a colleague. */
    public double getValue(String person, String metric) {
        PersonMemory pm = people.get(person.toLowerCase());
        if (pm == null) return 0.0;
        switch (metric) {
            case "reciprocity":     return pm.reciprocity;
            case "relationship":    return pm.relationship;
            case "is_exploitative": return pm.isExploitative ? 1.0 : 0.0;
            case "adapted_recip":   return pm.adaptedReciprocity;
            default: return 0.0;
        }
    }

    /**
     * Per-colleague state: reciprocity, relationship, and optional CFR learning.
     */
    public static class PersonMemory {
        public final String key;
        public final String name;
        public final double reciprocity;       // innate reciprocity
        public double adaptedReciprocity;      // adapted over time
        public double relationship;            // relationship quality
        public double reciprocityRatio;        // observed ratio
        public int interactions;
        public boolean isExploitative;
        public final boolean learnsViaCFR;

        // CFR learning state (for Carol-like agents)
        public Map<String, Double> personality = new HashMap<>();
        public Map<String, Double> cumulativeRegret = new HashMap<>();
        public Map<String, Double> strategySum = new HashMap<>();
        private double cfrLearningRate = 0.005;

        public PersonMemory(String key, String name, double reliability,
                           double reciprocity, boolean learnsViaCFR) {
            this.key = key;
            this.name = name;
            this.reciprocity = reciprocity;
            this.adaptedReciprocity = reciprocity;
            this.relationship = reliability;
            this.reciprocityRatio = reciprocity;
            this.interactions = 0;
            this.isExploitative = false;
            this.learnsViaCFR = learnsViaCFR;
        }

        /** Record a decision outcome for CFR learning. */
        public void recordDecisionOutcome(String action, double reward) {
            double current = cumulativeRegret.getOrDefault(action, 0.0);
            cumulativeRegret.put(action, current + reward);
            strategySum.put(action, strategySum.getOrDefault(action, 0.0) + 1.0);
        }

        /** Update personality from accumulated regret. */
        public void updatePersonalityFromRegret() {
            if (cumulativeRegret.isEmpty()) return;

            double totalPos = 0.0;
            for (double r : cumulativeRegret.values()) {
                if (r > 0) totalPos += r;
            }
            if (totalPos < 0.001) return;

            Map<String, Double> gradients = new HashMap<>();
            for (String trait : personality.keySet()) {
                gradients.put(trait, 0.0);
            }

            for (Map.Entry<String, Double> entry : cumulativeRegret.entrySet()) {
                double regret = entry.getValue();
                if (regret <= 0) continue;
                double weight = regret / totalPos;
                // Simplified: push toward positive traits
                for (String trait : personality.keySet()) {
                    double grad = weight * 0.1; // small positive push
                    gradients.put(trait, gradients.get(trait) + grad);
                }
            }

            for (String trait : personality.keySet()) {
                double old = personality.get(trait);
                double updated = old + cfrLearningRate * gradients.get(trait);
                personality.put(trait, Math.max(-1.0, Math.min(1.0, updated)));
            }
        }

        /** Adapt reciprocity based on whether Alice declined. */
        public void adaptReciprocity(boolean aliceDeclined) {
            if (aliceDeclined) {
                adaptedReciprocity = Math.min(1.0, adaptedReciprocity + 0.02);
            } else {
                adaptedReciprocity = Math.max(0.0, adaptedReciprocity - 0.01);
            }
        }
    }
}
