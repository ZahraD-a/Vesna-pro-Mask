// Alice, the agent that learns its masks.
//
// One agent among four. Bob, Carol and Dave are separate agents in separate
// files; Alice reaches them only by message and sees only what they send back.
// Nothing here simulates them -- Angelo: "Alice cannot choose for Bob."
//
// Time is symbolic: she believes episode(N) and advances it herself. What an
// episode means for learning lives in MaskLearner, off the reasoning cycle.

{ include("mask_rules.asl") }

// Alice's own state. Everything else she reasons over -- circumstances,
// rounds_per_partner, max_episodes, dialogue_episodes, verbose -- is experiment
// configuration and lives in vesna.jcm.
episode(0).
next_id(0).

+!start
    <-  .print("alice: starting");
        !life_cycle.

+!life_cycle
    :   episode(E) & max_episodes(M) & E < M & circumstances(Cs)
    <-  !announce(E);
        !visit_all(Cs);
        .wait(60);                  // let the last replies land
        vesna.via.end_episode;      // fold regret into the masks, log, reset
        E1 = E + 1;
        -+episode(E1);
        !maybe_hush(E1);
        !life_cycle.

+!life_cycle
    :   episode(E) & max_episodes(M) & E >= M
    <-  .print("alice: done after ", E, " episodes");
        vesna.via.final_report;
        // The MAS console window is destroyed when .stopMAS brings the JVM down, so
        // hold it open long enough to read the report. It is also on disk, in
        // results/latest/report.txt, if this runs out before you are finished.
        .print("report above is also in results/latest/report.txt -- closing in 90s");
        .wait(90000);
        .stopMAS.

+!visit_all([]).
+!visit_all([C|Rest])
    <-  -+circumstance(C);
        !wear_mask(C);
        !say("-- now in ", C, " --");
        !meet_everyone;
        !visit_all(Rest).

// getMasks then selectMask: wearable/1 returns a set, .nth(0) takes the most
// specific. Both halves stay symbolic and overridable here; wear_mask only hands
// the chosen name to the learner.
+!wear_mask(C)
    <-  .findall(M, wearable(M), Wearable);
        .nth(0, Wearable, Chosen);
        vesna.via.wear_mask(Chosen).

// Partners are discovered at run time, so adding a fifth agent to the .jcm
// needs no change here.
+!meet_everyone
    :   rounds_per_partner(K)
    <-  .my_name(Me);
        .all_names(All);
        .delete(Me, All, Partners);
        !repeat_rounds(K, Partners).

+!repeat_rounds(0, _).
+!repeat_rounds(K, Partners)
    :   K > 0
    <-  !ask_each(Partners);
        K1 = K - 1;
        !repeat_rounds(K1, Partners).

+!ask_each([]).
+!ask_each([Ag|Rest])
    <-  !interact(Ag);
        !ask_each(Rest).

+!interact(Ag)
    <-  .send(Ag, askOne, needs_help(T), Reply);
        !say("   asks ", Ag, " : ", Reply);
        !on_reply(Ag, Reply).

+!on_reply(Ag, needs_help(T))
    <-  !manage(Ag, T).
+!on_reply(_, _).                   // nothing needed, or no answer

// Nine ways to achieve !manage(Partner, Task). All have an empty context, so all
// nine are applicable at every decision -- Andrea: "you should always have plans
// that are applicable at the same time, because otherwise it will be so
// deterministic that you will not learn anything."
//
// temper() is the persona the plan projects, OCEAN in [-1,1] -- the range the
// original Temper already accepts for annotations. The sign matters: -1 is the
// REVERSE of a trait, not a little of it, so ignore(a(-0.90)) is active coldness.
// Against Alice's warm core (a(0.75)) that scores -1.39, and a non-positive weight
// gets no probability -- she cannot play it until a mask lowers her agreeableness.
// These numbers must match PlanCatalog.java; PlanCatalog.validate() checks that.

@drop_everything[temper([o(0.20), c(-0.50), e(0.40), a(0.90), n(0.10)]),
                 effects([social_energy(-0.10)[mood], satisfaction(0.10)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, drop_everything).

@help_after_task[temper([o(-0.10), c(0.80), e(0.00), a(0.40), n(-0.20)]),
                 effects([satisfaction(0.05)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, help_after_task).

@pair_up[temper([o(0.60), c(0.20), e(0.80), a(0.70), n(-0.30)]),
         effects([social_energy(0.10)[mood], satisfaction(0.10)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, pair_up).

@teach[temper([o(0.70), c(0.70), e(0.20), a(0.50), n(-0.20)]),
       effects([satisfaction(0.08)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, teach).

@quick_tip[temper([o(0.30), c(0.10), e(0.30), a(0.30), n(-0.10)])]
+!manage(Ag, T) <- !offer(Ag, T, quick_tip).

@delegate[temper([o(0.00), c(0.50), e(0.20), a(-0.20), n(0.00)])]
+!manage(Ag, T) <- !offer(Ag, T, delegate).

@joke_deflect[temper([o(0.80), c(-0.60), e(0.70), a(0.10), n(-0.40)]),
              effects([social_energy(0.10)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, joke_deflect).

@polite_decline[temper([o(-0.30), c(0.40), e(-0.40), a(-0.30), n(0.20)])]
+!manage(Ag, T) <- !offer(Ag, T, polite_decline).

@ignore[temper([o(-0.60), c(-0.30), e(-0.80), a(-0.90), n(0.40)]),
        effects([social_energy(-0.05)[mood], satisfaction(-0.05)[mood]])]
+!manage(Ag, T) <- !offer(Ag, T, ignore).

// ---------------------------------------------------------------------
//  ACTING, AND WAITING TO SEE HOW IT LANDED
// ---------------------------------------------------------------------
//
//  The identifier makes each exchange unique. Without it a second
// The identifier makes each exchange unique: without it a repeated reaction would
// be a belief Alice already holds, no event would fire, and the exchange would
// silently vanish.

// The circumstance travels with the offer: the partner reacts to the whole
// situation, not the act alone. Alice never sees why -- only the reply.

+!offer(Ag, T, Style)
    :   next_id(I) & circumstance(C)
    <-  I1 = I + 1;
        -+next_id(I1);
        vesna.via.record_choice(Ag, Style);
        !say("   -> ", Ag, " : ", Style);
        .send(Ag, tell, offer(T, Style, C, I));
        .wait(20).

// The only place external feedback enters, produced by another agent's own
// personality choosing among its own reaction plans.
+outcome(I, Result)[source(Ag)]
    <-  .abolish(outcome(I, _));
        !say("   <- ", Ag, " : ", Result);
        vesna.via.record_outcome(Ag, Result).

// Dialogue trace. Two plans per hook: verbose, then a catch-all that does
// nothing. Neither carries temper(), so this cannot disturb plan selection.

+!say(A, B, C) : verbose <- .print(A, B, C).
+!say(_, _, _).

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).

+!announce(E) : verbose <- .print("======== episode ", E, " ========").
+!announce(_).

// Alice has no authority over the others' belief bases; she sends a message and
// their own plan decides what to do with it.
+!maybe_hush(E)
    :   dialogue_episodes(D) & E >= D & verbose
    <-  .print("---- dialogue trace off after ", D, " episodes; ",
               "learning continues silently ----");
        .abolish(verbose);
        .broadcast(tell, hush).
+!maybe_hush(_).
