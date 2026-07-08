package vesna;

import java.util.*;
import java.io.*;
import java.nio.file.*;

/**
 * Self-play Counterfactual Regret Minimization for the two-player {@link SocialGame}.
 *
 * Each agent runs regret matching independently, one information set per context.
 * Both agents update simultaneously against a snapshot of the opponent's current
 * strategy (full-feedback CFR for a normal-form game). No-external-regret dynamics
 * guarantee that the empirical average joint strategy converges to a
 * coarse-correlated equilibrium (Hart & Mas-Colell, 2000).
 *
 * Three results are produced:
 *   1. Convergence         — average external regret -> 0 (the equilibrium guarantee).
 *   2. Heterogeneity       — different core personalities -> different equilibrium personas.
 *   3. Partner-dependence  — the SAME agent learns different personas with different partners
 *                            (the social coupling, i.e. why this is a game and not a bandit).
 *
 * Run:  ./gradlew runGame            (default 50000 iterations)
 *       ./gradlew runGame -Piters=100000
 */
public class SelfPlayCFR {

    static final String[] ACT = SocialGame.ACTIONS;
    static final int nA = ACT.length;
    static final String[] TRAITS = {"openness", "conscientiousness", "extraversion", "agreeableness", "neuroticism"};

    static final class Player {
        final String name;
        final Map<String, Double> core;
        final Map<String, double[]> regret = new HashMap<>();
        final Map<String, double[]> strat  = new HashMap<>();
        Player(String name, Map<String, Double> core) { this.name = name; this.core = core; }

        double[] regretFor(String ctx) { return regret.computeIfAbsent(ctx, k -> new double[nA]); }
        double[] stratFor(String ctx)  { return strat.computeIfAbsent(ctx, k -> new double[nA]); }

        double[] sigma(String ctx) {                 // current strategy (regret matching)
            double[] r = regretFor(ctx), s = new double[nA];
            double sum = 0;
            for (int i = 0; i < nA; i++) { s[i] = Math.max(0, r[i]); sum += s[i]; }
            if (sum > 0) for (int i = 0; i < nA; i++) s[i] /= sum;
            else         Arrays.fill(s, 1.0 / nA);
            return s;
        }
        double[] avg(String ctx) {                   // average strategy (equilibrium play)
            double[] ss = stratFor(ctx), s = new double[nA];
            double sum = 0;
            for (double v : ss) sum += v;
            if (sum > 0) for (int i = 0; i < nA; i++) s[i] = ss[i] / sum;
            else         Arrays.fill(s, 1.0 / nA);
            return s;
        }
        double maxRegret() {
            double m = 0;
            for (double[] r : regret.values()) for (double v : r) m = Math.max(m, v);
            return m;
        }
    }

    /** Run self-play between two fresh players; optionally log per-iteration convergence. */
    static Player[] play(String nameA, Map<String,Double> coreA,
                         String nameB, Map<String,Double> coreB,
                         int iters, StringBuilder convLog) {
        Player a = new Player(nameA, coreA), b = new Player(nameB, coreB);
        for (int t = 1; t <= iters; t++) {
            for (String ctx : SocialGame.CONTEXTS) {
                double[] sa = a.sigma(ctx), sb = b.sigma(ctx);   // snapshot, then update simultaneously
                accumulate(a, ctx, sa, sb);
                accumulate(b, ctx, sb, sa);
            }
            if (convLog != null && (t == 1 || t % 100 == 0 || t == iters)) {
                double avgReg = (a.maxRegret() + b.maxRegret()) / (2.0 * t);
                convLog.append(t).append(",").append(String.format("%.6f", avgReg)).append("\n");
            }
        }
        return new Player[]{a, b};
    }

    /** One regret-matching update for {@code self} against the opponent's snapshot strategy. */
    static void accumulate(Player self, String ctx, double[] selfSigma, double[] oppSigma) {
        double[] u = new double[nA];
        double v = 0;
        for (int x = 0; x < nA; x++) {
            double ux = 0;
            for (int y = 0; y < nA; y++)
                ux += oppSigma[y] * SocialGame.reward(ctx, self.core, ACT[x], ACT[y]);
            u[x] = ux;
            v += selfSigma[x] * ux;
        }
        double[] r = self.regretFor(ctx), st = self.stratFor(ctx);
        for (int x = 0; x < nA; x++) { r[x] += u[x] - v; st[x] += selfSigma[x]; }
    }

    public static void main(String[] args) throws Exception {
        int iters = 50000;
        if (args.length > 0) try { iters = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {}

        // Roster of distinct personalities (OCEAN cores).
        Map<String,Double> alice = SocialGame.core( 0.3, -0.2,  0.1,  0.5, -0.4);  // warm, open, agreeable
        Map<String,Double> bob   = SocialGame.core(-0.3,  0.6, -0.5, -0.2,  0.2);  // reserved, conscientious
        Map<String,Double> cara  = SocialGame.core( 0.7, -0.6,  0.9,  0.3, -0.5);  // exuberant extravert

        Path dir = Path.of("results", "multiagent");
        Files.createDirectories(dir);

        // Experiment 1: Alice vs Bob — convergence + heterogeneity.
        StringBuilder conv = new StringBuilder("iter,avg_external_regret\n");
        Player[] ab = play("alice", alice, "bob", bob, iters, conv);
        Files.writeString(dir.resolve("convergence.csv"), conv.toString());
        writeStrategies(dir.resolve("equilibrium_strategy.csv"), ab);
        writeMasks(dir.resolve("equilibrium_masks.csv"), ab);

        // Experiment 2: same Alice, different partner (Cara) — partner-dependence.
        Player[] ac = play("alice", alice, "cara", cara, iters, null);
        writePartnerDependence(dir.resolve("partner_dependence.csv"), ab[0], ac[0]);

        // Console summary.
        System.out.println("\n[GAME] Experiment 1 — Alice vs Bob (heterogeneous equilibrium):");
        printPersonas(ab);
        System.out.println("\n[GAME] Experiment 2 — Alice's persona depends on her partner:");
        System.out.println("  (Alice-with-Bob vs Alice-with-Cara, dominant response per context)");
        for (String ctx : SocialGame.CONTEXTS)
            System.out.println("    " + ctx + ":  with Bob -> " + top(ab[0].avg(ctx))
                + "   |   with Cara -> " + top(ac[0].avg(ctx)));
        System.out.println("\n[GAME] Wrote results to " + dir.toAbsolutePath());
    }

    static void writeStrategies(Path path, Player[] players) throws IOException {
        StringBuilder sb = new StringBuilder("player,context");
        for (String a : ACT) sb.append(",").append(a);
        sb.append("\n");
        for (Player p : players)
            for (String ctx : SocialGame.CONTEXTS) {
                sb.append(p.name).append(",").append(ctx);
                for (double v : p.avg(ctx)) sb.append(",").append(String.format("%.4f", v));
                sb.append("\n");
            }
        Files.writeString(path, sb.toString());
    }

    static void writeMasks(Path path, Player[] players) throws IOException {
        StringBuilder sb = new StringBuilder("player,context");
        for (String t : TRAITS) sb.append(",").append(t);
        sb.append("\n");
        for (Player p : players)
            for (String ctx : SocialGame.CONTEXTS) {
                Map<String,Double> mask = maskOf(p.avg(ctx));
                sb.append(p.name).append(",").append(ctx);
                for (String t : TRAITS) sb.append(",").append(String.format("%.4f", mask.get(t)));
                sb.append("\n");
            }
        Files.writeString(path, sb.toString());
    }

    static void writePartnerDependence(Path path, Player aliceWithBob, Player aliceWithCara) throws IOException {
        StringBuilder sb = new StringBuilder("context,partner");
        for (String a : ACT) sb.append(",").append(a);
        sb.append("\n");
        for (String ctx : SocialGame.CONTEXTS) {
            sb.append(ctx).append(",bob");
            for (double v : aliceWithBob.avg(ctx)) sb.append(",").append(String.format("%.4f", v));
            sb.append("\n").append(ctx).append(",cara");
            for (double v : aliceWithCara.avg(ctx)) sb.append(",").append(String.format("%.4f", v));
            sb.append("\n");
        }
        Files.writeString(path, sb.toString());
    }

    static Map<String,Double> maskOf(double[] avg) {
        Map<String,Double> mask = new LinkedHashMap<>();
        for (String t : TRAITS) mask.put(t, 0.0);
        double uniform = 1.0 / nA;
        for (int i = 0; i < nA; i++) {
            Map<String,Double> at = HelpScenarioConfig.getActionTraits(ACT[i]);
            double w = avg[i] - uniform;
            for (String t : TRAITS) mask.merge(t, w * at.getOrDefault(t, 0.0), Double::sum);
        }
        return mask;
    }

    static void printPersonas(Player[] players) {
        for (Player p : players) {
            System.out.println("  " + p.name + " (core=" + fmt(p.core) + ")");
            for (String ctx : SocialGame.CONTEXTS) {
                double[] avg = p.avg(ctx);
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < nA; i++) { if (i > 0) s.append(", "); s.append(ACT[i]).append("=").append(String.format("%.2f", avg[i])); }
                System.out.println("    " + ctx + ": {" + s + "}");
            }
        }
    }

    static String top(double[] avg) {
        int best = 0;
        for (int i = 1; i < nA; i++) if (avg[i] > avg[best]) best = i;
        return ACT[best] + " (" + String.format("%.0f%%", 100 * avg[best]) + ")";
    }

    static String fmt(Map<String, Double> m) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (!first) sb.append(",");
            sb.append(e.getKey().substring(0, 1).toUpperCase()).append("=").append(String.format("%.1f", e.getValue()));
            first = false;
        }
        return sb.toString();
    }
}
