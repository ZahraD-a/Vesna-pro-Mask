package vesna.via;

import jason.asSemantics.*;
import jason.asSyntax.*;
import vesna.Temper;
import vesna.VesnaAgent;

import java.io.*;
import java.util.*;

/**
 * End-of-episode hook: triggers CFR learning.
 *
 * In mask mode: updates the active mask (not the core!).
 * In baseline mode: updates the personality vector directly.
 *
 * Usage: vesna.via.cfr_episode.
 */
public class cfr_episode extends DefaultInternalAction {

    private static int episodeCounter = 0;
    public static int getCurrentEpisode() { return episodeCounter + 1; }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
        VesnaAgent agent = (VesnaAgent) ts.getAg();
        Temper temper = agent.getTemper();
        if (temper == null) return true;

        episodeCounter++;
        System.out.println("\n========== EPISODE " + episodeCounter + " COMPLETE ==========");
        System.out.println("[CFR] Total reward: " + String.format("%.2f", temper.getTotalEpisodeReward()));

        // Trigger learning (mask update or personality update)
        temper.startNewEpisode();

        // Log mask state if in mask mode
        if (temper.isUseMasks() && temper.getWardrobe() != null) {
            System.out.println("[MASK] Wardrobe state:");
            for (vesna.Mask mask : temper.getWardrobe().getAllMasks()) {
                System.out.println("  " + mask);
            }
        }

        System.out.println("=======================================\n");
        return true;
    }
}
