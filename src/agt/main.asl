{ include("mask_rules.asl") }

// Alice meets someone across several contexts; CFR learns one mask per context.
// work/home/concert each have a dedicated mask (see mask_rules.asl + .jcm mask_contexts).
// 'party' has NO dedicated mask: it falls back to mask_default (the true self, starts
// at [0,0,0,0,0]), which itself learns and records that 'party' routed to it.
contexts([work, home, concert, party]).
interactions_per_context(10).
max_episodes(150).

+!start
    <-  +episode(0);
        !episode.

// Run every context once, let CFR update the masks, then move to the next episode.
+!episode
    :   episode(N) & max_episodes(M) & N < M & contexts(Cs) & interactions_per_context(K)
    <-  .print("episode ", N);
        !run_contexts(Cs, K);
        vesna.via.cfr_episode;
        -episode(N);
        +episode(N + 1);
        !episode.

+!episode
    :   episode(N) & max_episodes(M) & N >= M
    <-  .print("Simulation complete: ", M, " episodes run.");
        .wait(600000);
        .stopMAS.

// Wear the mask bound to the context (mask_for/2), then interact K times.
+!run_contexts([], _).
+!run_contexts([Ctx|Rest], K)
    <-  +context(Ctx);
        ?mask_for(Ctx, Mask);
        !run_interactions(K, Mask);
        -context(Ctx);
        !run_contexts(Rest, K).

+!run_interactions(0, _).
+!run_interactions(K, Mask)
    :   K > 0
    <-  !social_interaction(Mask);
        !run_interactions(K - 1, Mask).

// One exchange: wear the mask, pick a response via temper, record the outcome.
+!social_interaction(Mask) 
    :   context(Ctx)
    <-  -strategy(_);
        vesna.via.set_decision_context(social, Mask, Ctx);
        !choose_response;
        !execute_response.

// Response options, annotated with the OCEAN traits Vesna uses to select one.
@formal[temper([conscientiousness(0.8), extraversion(-0.6), agreeableness(0.2), openness(-0.4), neuroticism(-0.6)]), effects([satisfaction(0.05)[mood]])]
+!choose_response <- +strategy(formal).

@casual[temper([extraversion(0.6), agreeableness(0.6), conscientiousness(-0.4), openness(0.2), neuroticism(-0.2)]), effects([satisfaction(0.1)[mood], social_energy(0.1)[mood]])]
+!choose_response <- +strategy(casual).

@enthusiastic[temper([openness(0.8), extraversion(0.8), agreeableness(0.4), conscientiousness(-0.6), neuroticism(-0.4)]), effects([satisfaction(0.15)[mood], social_energy(0.15)[mood]])]
+!choose_response <- +strategy(enthusiastic).

@reserved[temper([extraversion(-0.8), agreeableness(-0.4), conscientiousness(0.4), openness(-0.6), neuroticism(-0.8)])]
+!choose_response <- +strategy(reserved).

// Base reward per response; the context shapes it further in record_outcome.
reward(formal, 0.3).
reward(casual, 0.5).
reward(enthusiastic, 0.6).
reward(reserved, 0.2).

+!execute_response
    :   strategy(S)
    <-  .random(R);
        !result(S, R).

+!result(S, R)
    :   R >= 0.3 & reward(S, W)
    <-  vesna.via.record_outcome(success, W, S, social).
+!result(S, R)
    :   R < 0.3
    <-  vesna.via.record_outcome(neutral, 0.0, S, social).
