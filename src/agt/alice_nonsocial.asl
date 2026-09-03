// Alice on her own.
//
// The same three circumstances as the social scenario and the same three masks, but nobody else is
// present. She does her own tasks and the environment reports how they went, as goal_achieved,
// delayed or failed -- the same words a partner's reply is scored with.
//
// This is a separate run from the social scenario. Separate MAS, separate learner, separate plan
// set. Nothing is shared at run time, so neither scenario can affect the other's numbers; the point
// is that the same code produces sensible masks in both.
//
// All nine plans are applicable in every circumstance, exactly as the nine social plans are. What
// each one is worth depends on where she is, and only the environment knows that.

{ include("mask_rules.asl") }

episode(0).

+!start
    <-  .print("alice: starting alone, no partners");
        !life_cycle.

+!life_cycle
    :   episode(E) & max_episodes(M) & E < M & circumstances(Cs)
    <-  !announce(E);
        !visit_all(Cs);
        vesna.via.end_episode;
        E1 = E + 1;
        -+episode(E1);
        !maybe_hush(E1);
        !life_cycle.

+!life_cycle
    :   episode(E) & max_episodes(M) & E >= M
    <-  .print("alice: done after ", E, " episodes");
        vesna.via.final_report;
        !close_down.

+!close_down : close_delay(D) & D > 0 <- .wait(D); .stopMAS.
+!close_down <- .stopMAS.

+!visit_all([]).
+!visit_all([C|Rest])
    <-  -+circumstance(C);
        !wear_mask;
        !say("-- now in ", C, " --");
        !work_through(C);
        !visit_all(Rest).

// Same two steps as the social agent: ask which masks fit, wear the most specific.
+!wear_mask
    <-  .findall(M, wearable(M), Wearable);
        .nth(0, Wearable, Chosen);
        vesna.via.wear_mask(Chosen).

+!work_through(C) : tasks_per_circumstance(K) <- !repeat(K, C).

+!repeat(0, _).
+!repeat(K, C)
    :   K > 0
    <-  !self_task(C);
        K1 = K - 1;
        !repeat(K1, C).

// Nine ways to spend the time. None has a context condition, so all nine are applicable
// everywhere and the choice is always real. These must match the selftask domain in
// PlanCatalog.java, which validate() checks at startup.

@deep_work[temper([o(0.60), c(0.80), e(-0.70), a(-0.10), n(-0.80)])]
+!self_task(C) <- !attempt(C, deep_work).

@handle_backlog[temper([o(0.10), c(0.70), e(-0.40), a(0.00), n(-0.30)])]
+!self_task(C) <- !attempt(C, handle_backlog).

@quick_scan[temper([o(0.00), c(-0.50), e(0.40), a(0.10), n(0.20)])]
+!self_task(C) <- !attempt(C, quick_scan).

@tidy_up[temper([o(-0.20), c(0.70), e(-0.40), a(0.30), n(-0.60)])]
+!self_task(C) <- !attempt(C, tidy_up).

@rest_deeply[temper([o(-0.10), c(-0.40), e(-0.70), a(0.00), n(-0.80)])]
+!self_task(C) <- !attempt(C, rest_deeply).

@zone_out[temper([o(-0.60), c(-0.70), e(-0.20), a(-0.30), n(0.10)])]
+!self_task(C) <- !attempt(C, zone_out).

@take_notes[temper([o(0.60), c(0.70), e(-0.60), a(0.10), n(-0.50)])]
+!self_task(C) <- !attempt(C, take_notes).

@network_actively[temper([o(0.30), c(-0.20), e(0.80), a(0.70), n(-0.40)])]
+!self_task(C) <- !attempt(C, network_actively).

@step_away[temper([o(-0.10), c(-0.30), e(-0.70), a(-0.40), n(0.40)])]
+!self_task(C) <- !attempt(C, step_away).

// The only place feedback enters, and it comes from the environment rather than from anybody.
+!attempt(C, Style)
    <-  vesna.via.record_choice(environment, Style);
        vesna.via.self_outcome(Style, C, Outcome);
        !say("   ", Style, " -> ", Outcome);
        vesna.via.record_outcome(environment, Outcome).

+!say(A, B, C) : verbose <- .print(A, B, C).
+!say(_, _, _).

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
+!announce(E) : verbose <- .print("======== episode ", E, " ========").
+!announce(_).
+!maybe_hush(E) : dialogue_episodes(D) & E >= D & verbose <- .abolish(verbose).
+!maybe_hush(_).
