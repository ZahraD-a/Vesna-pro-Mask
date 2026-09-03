// Alice with a goal that involves nobody else.
//
// She has to keep her own energy up. No partner is asked, no message is sent, nobody reacts. The
// outcome comes from the environment instead, reported as goal_achieved, delayed or failed -- the
// same words the reward machine already scores. Everything else is the code the social scenario
// runs: the same mask rules, the same seam into Temper, the same regret update, the same logging.
//
// The circumstances are states of herself rather than social settings, and they are derived by the
// same wearable/1 rules in mask_rules.asl. When she is depleted only the slow, thorough recovery
// gets her back over the line, so that circumstance should reward a conscientious persona. When she
// is rested everything works and the cheap option costs least, so it should reward the opposite.
// Nothing tells her this; she has to find it out from what the environment reports back.

{ include("mask_rules.asl") }

episode(0).
energy(70).

+!start
    <-  .print("alice: starting in the energy domain, no partners");
        !life_cycle.

+!life_cycle
    :   episode(E) & max_episodes(M) & E < M
    <-  !announce(E);
        !rounds;
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

// One episode is a few attempts to stay topped up. Energy drains first, and that is what decides
// which circumstance she is in.
+!rounds : rounds_per_episode(K) <- !repeat(K).

+!repeat(0).
+!repeat(K)
    :   K > 0
    <-  !drain;
        !wear_mask;
        !restore_energy;
        K1 = K - 1;
        !repeat(K1).

+!drain : energy(E)
    <-  .random(R);
        Loss = 34 + R * 34;
        E1 = E - Loss;
        !set_energy(E1).

+!set_energy(E) : E < 0 <- -+energy(0).
+!set_energy(E)         <- -+energy(E).

// Identical to the social agent: ask which masks fit, wear the most specific.
+!wear_mask
    <-  .findall(M, wearable(M), Wearable);
        .nth(0, Wearable, Chosen);
        vesna.via.wear_mask(Chosen).

// Three ways to get back on her feet. All three are always applicable, so the choice is real.
// temper() is the persona each one projects, and these must match the energy domain in
// PlanCatalog.java, which validate() checks at startup.

@push_through[temper([o(0.30), c(-0.40), e(0.70), a(0.20), n(0.50)])]
+!restore_energy <- !recover(push_through).

@steady_recovery[temper([o(0.20), c(0.60), e(0.10), a(0.30), n(-0.20)])]
+!restore_energy <- !recover(steady_recovery).

@patient_recovery[temper([o(0.50), c(0.80), e(-0.20), a(0.40), n(-0.60)])]
+!restore_energy <- !recover(patient_recovery).

// The only place feedback enters, and it comes from the environment rather than from anybody.
+!recover(Style)
    :   energy(E)
    <-  vesna.via.record_choice(environment, Style);
        vesna.via.env_outcome(Style, E, Outcome, E2);
        -+energy(E2);
        !say("   ", Style, " -> ", Outcome);
        vesna.via.record_outcome(environment, Outcome).

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
+!announce(E) : verbose <- .print("======== episode ", E, " ========").
+!announce(_).
+!maybe_hush(E) : dialogue_episodes(D) & E >= D & verbose <- .abolish(verbose).
+!maybe_hush(_).
