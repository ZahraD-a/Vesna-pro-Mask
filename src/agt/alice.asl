// Alice is the only agent here that learns.
//
// Bob, Carol and Dave are separate agents with their own files. Alice can only send
// them messages and read the replies; nothing in this file stands in for them.
//
// She counts episodes with the belief episode(N). What an episode means for
// learning happens in MaskLearner.

{ include("mask_rules.asl") }

// Alice's own state. Experiment settings live in vesna.jcm.
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

// Ask which masks fit, then pick one: wearable/1 can return several, .nth(0) takes
// the most specific. wear_mask only passes the chosen name to the learner.
+!wear_mask(C)
    <-  .findall(M, wearable(M), Wearable);
        .nth(0, Wearable, Chosen);
        vesna.via.wear_mask(Chosen).

// Partners are looked up at run time, so adding a fifth agent needs no change here.
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

// Nine ways to do the same thing. None has a context condition, so all nine are
// always applicable: if only one fitted at a time the choice would be forced and
// there would be nothing to learn.
//
// temper() is the personality each plan projects, five OCEAN traits from -1 to +1,
// where -1 is the opposite of a trait rather than a small amount of it.
//
// Plans are scored by multiplying the agent traits by the plan traits and summing.
// Alice is warm (a(0.75)), so ignore scores -1.39, and negative-scoring plans are
// never picked: she cannot ignore anyone until a mask lowers her agreeableness.
//
// These numbers must match PlanCatalog.java, which validate() checks at startup.

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
// The id keeps each exchange distinct. Without it a repeated reply would already be
// a held belief, no event would fire, and the exchange would vanish.

// The circumstance is sent with the offer: the partner reacts to the situation, not
// the act alone. Alice is never told why, only what.

+!offer(Ag, T, Style)
    :   next_id(I) & circumstance(C)
    <-  I1 = I + 1;
        -+next_id(I1);
        vesna.via.record_choice(Ag, Style);
        !say("   -> ", Ag, " : ", Style);
        .send(Ag, tell, offer(T, Style, C, I));
        .wait(20).

// The only point where outside feedback arrives, chosen by another agent with its
// own personality.
+outcome(I, Result)[source(Ag)]
    <-  .abolish(outcome(I, _));
        !say("   <- ", Ag, " : ", Result);
        vesna.via.record_outcome(Ag, Result).

// Printing. Two plans each: one while verbose is believed, one that does nothing.
// No temper() annotation, so printing cannot affect plan choice.

+!say(A, B, C) : verbose <- .print(A, B, C).
+!say(_, _, _).

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).

+!announce(E) : verbose <- .print("======== episode ", E, " ========").
+!announce(_).

// Alice cannot change what the others believe; she sends a message and each decides
// what to do with it.
+!maybe_hush(E)
    :   dialogue_episodes(D) & E >= D & verbose
    <-  .print("---- dialogue trace off after ", D, " episodes; ",
               "learning continues silently ----");
        .abolish(verbose);
        .broadcast(tell, hush).
+!maybe_hush(_).
