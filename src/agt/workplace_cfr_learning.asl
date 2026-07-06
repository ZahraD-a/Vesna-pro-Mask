// Include mask wearability rules
{include("mask_rules.asl")}

// =============================================================
// MASK WARDROBE: Same Agent, Different Contexts
// =============================================================
// Alice meets someone in THREE different situations:
//   1. At WORK     -> wears mask_work     -> professional
//   2. At HOME     -> wears mask_home     -> relaxed
//   3. At CONCERT  -> wears mask_concert  -> expressive
//
// CFR learns different masks for each context over time.
// =============================================================

interactions_per_context(10).
max_episodes(50).

// =============================================================
// INITIALIZATION
// =============================================================

+!start
    <-  .print("========================================");
        .print("MASK WARDROBE LEARNING");
        .print("Same agent, different contexts");
        .print("========================================");
        .print("");
        +episode(0);
        !episode.

// =============================================================
// EPISODE LOOP
// =============================================================

+!episode
    :   episode(N) & max_episodes(M) & N < M & interactions_per_context(K)
    <-  .print("");
        .print("--- Episode ", N, " ---");

        // ---- CONTEXT 1: WORK ----
        .print("");
        .print(">> CONTEXT: WORK");
        +context(work);
        ?context(Ctx1);
        ?mask_for(Ctx1, Mask1);
        !run_interactions(K, Mask1);
        -context(work);

        // ---- CONTEXT 2: HOME ----
        .print("");
        .print(">> CONTEXT: HOME");
        +context(home);
        ?context(Ctx2);
        ?mask_for(Ctx2, Mask2);
        !run_interactions(K, Mask2);
        -context(home);

        // ---- CONTEXT 3: CONCERT ----
        .print("");
        .print(">> CONTEXT: CONCERT");
        +context(concert);
        ?context(Ctx3);
        ?mask_for(Ctx3, Mask3);
        !run_interactions(K, Mask3);
        -context(concert);

        // End of episode: CFR updates masks
        vesna.via.cfr_episode;

        -episode(N);
        N1 = N + 1;
        +episode(N1);
        !check_done.

+!check_done
    :   episode(N) & max_episodes(M) & N >= M
    <-  .print("");
        .print("========================================");
        .print("TRAINING COMPLETE");
        .print("========================================");
        .wait(5000);
        .stopMAS.

+!check_done
    :   episode(N)
    <-  !episode.

// Multi-interaction dispatch
+!run_interactions(0, _) <- true.
+!run_interactions(K, Mask) : K > 0
    <-  !social_interaction(Mask);
        !run_interactions(K-1, Mask).

// =============================================================
// SOCIAL INTERACTION: Alice meets someone
// =============================================================
// This is a general social interaction, not a help request.
// The context determines how Alice should behave.
// =============================================================

+!social_interaction(MaskName)
    <-  -strategy(_);
        vesna.via.set_decision_context(social, MaskName);
        .print("[OTHER] Hi Alice, how are you?");
        !choose_response;
        !execute_response.

// ---- Plan options: different social responses ----

// Formal/professional response (high C, low E)
@formal[temper([conscientiousness(0.8), extraversion(-0.6), agreeableness(0.2), openness(-0.4), neuroticism(-0.6)]), effects([satisfaction(+0.05)[mood]])]
+!choose_response <- +strategy(formal).

// Casual/relaxed response (high E, high A)
@casual[temper([extraversion(0.6), agreeableness(0.6), conscientiousness(-0.4), openness(0.2), neuroticism(-0.2)]), effects([satisfaction(+0.1)[mood], social_energy(+0.1)[mood]])]
+!choose_response <- +strategy(casual).

// Enthusiastic/expressive response (high O, high E)
@enthusiastic[temper([openness(0.8), extraversion(0.8), agreeableness(0.4), conscientiousness(-0.6), neuroticism(-0.4)]), effects([satisfaction(+0.15)[mood], social_energy(+0.15)[mood]])]
+!choose_response <- +strategy(enthusiastic).

// Reserved/quiet response (low E, low A)
@reserved[temper([extraversion(-0.8), agreeableness(-0.4), conscientiousness(0.4), openness(-0.6), neuroticism(-0.8)])]
+!choose_response <- +strategy(reserved).

// ---- Execute responses ----

+!execute_response : strategy(formal)
    <-  .print("  [Alice] Good day. I am well, thank you.");
        .random(R); !formal_result(R).

+!execute_response : strategy(casual)
    <-  .print("  [Alice] Hey! I am doing great, thanks for asking!");
        .random(R); !casual_result(R).

+!execute_response : strategy(enthusiastic)
    <-  .print("  [Alice] Oh hi! I am fantastic! This is amazing!");
        .random(R); !enthusiastic_result(R).

+!execute_response : strategy(reserved)
    <-  .print("  [Alice] Fine.");
        .random(R); !reserved_result(R).

// ---- Outcomes ----

+!formal_result(R) : R < 0.3
    <-  vesna.via.record_outcome(neutral, 0.0, formal, social).
+!formal_result(R) : R >= 0.3
    <-  vesna.via.record_outcome(success, 0.3, formal, social).

+!casual_result(R) : R < 0.3
    <-  vesna.via.record_outcome(neutral, 0.0, casual, social).
+!casual_result(R) : R >= 0.3
    <-  vesna.via.record_outcome(success, 0.5, casual, social).

+!enthusiastic_result(R) : R < 0.3
    <-  vesna.via.record_outcome(neutral, 0.0, enthusiastic, social).
+!enthusiastic_result(R) : R >= 0.3
    <-  vesna.via.record_outcome(success, 0.6, enthusiastic, social).

+!reserved_result(R) : R < 0.3
    <-  vesna.via.record_outcome(neutral, 0.0, reserved, social).
+!reserved_result(R) : R >= 0.3
    <-  vesna.via.record_outcome(success, 0.2, reserved, social).
