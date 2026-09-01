// =====================================================================
//  ALICE  --  the agent that learns its masks
// =====================================================================
//
//  Alice is one agent among four. Bob, Carol and Dave are real, separate
//  agents in real, separate files; she reaches them only by sending
//  messages and can only observe what they choose to send back. Nothing
//  here simulates them. That is the whole point of this rewrite:
//
//      "if I open the configuration file of the multi-agent system there
//       should be Alice, Bob, Carol and David. Otherwise, if I see only
//       Alice, I will be lost. Alice cannot choose for Bob."
//
//  Time is symbolic: the agent believes episode(N) and flips it in its own
//  recursive life cycle. It does not know what an "episode" means for
//  learning -- that lives in MaskLearner.java, off the reasoning cycle.
// =====================================================================

{ include("mask_rules.asl") }

// ---------------------------------------------------------------------
//  BELIEFS
// ---------------------------------------------------------------------

episode(0).
next_id(0).

// Print the actual message traffic for the first few episodes so the exchange can
// be watched. 120 episodes x 27 interactions is far too much to read, so after
// dialogue_episodes the agents are told to be quiet and the run goes silent.
verbose.
dialogue_episodes(2).

// The circumstances Alice moves through. Each has a mask (mask_rules.asl).
circumstances([work, home, conference]).

// How many times she comes back to each partner inside one circumstance.
// Three rounds x three partners x three circumstances = 27 decisions per episode,
// which is enough for the per-circumstance style mix to be a usable statistic.
rounds_per_partner(3).

max_episodes(120).

// ---------------------------------------------------------------------
//  LIFE CYCLE
// ---------------------------------------------------------------------

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
        // results/mas/report.txt, if this runs out before you are finished.
        .print("report above is also in results/mas/report.txt -- closing in 90s");
        .wait(90000);
        .stopMAS.

// ---------------------------------------------------------------------
//  MOVING THROUGH CIRCUMSTANCES
// ---------------------------------------------------------------------

+!visit_all([]).
+!visit_all([C|Rest])
    <-  -+circumstance(C);
        !wear_mask(C);
        !say("-- now in ", C, " --");
        !meet_everyone;
        !visit_all(Rest).

// getMasks: ask the belief base which masks the circumstance permits (a SET; several
//           rules may hold at once). selectMask: put on the most specific one -- the
//           dedicated circumstance mask comes before the always-wearable default.
// Both halves are symbolic and live here, exactly as Angelo described; wear_mask only
// hands the chosen name to the learner, which computes A_eff and pushes it to Temper.
+!wear_mask(C)
    <-  .findall(M, wearable(M), Wearable);
        .nth(0, Wearable, Chosen);
        vesna.via.wear_mask(Chosen).

// Every other agent in the MAS, discovered at run time. Adding a fifth
// agent to the .jcm needs no change here -- which is exactly what makes a
// scalability experiment meaningful.
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

// ---------------------------------------------------------------------
//  NINE WAYS TO ACHIEVE ONE GOAL
// ---------------------------------------------------------------------
//
//  Every plan below achieves !manage(Partner, Task): the partner ends up
//  handled. What differs is the social shape of the act. All nine have an
//  empty context, so all nine are applicable at every single decision --
//  the choice is real, never forced by the plan library.
//
//      "you should always have plans that are applicable at the same time,
//       because otherwise it will be so deterministic that you will not
//       learn anything ... how to behave at work will not be the choice
//       between three plans, it will be the choice between ten ways to
//       achieve the same goal."
//
//  The temper() annotation (OCEAN in [-1,1]) is the persona the plan projects.
//  The ORIGINAL Temper weighs it against the effective personality
//  clip(core + active mask), so the SAME nine plans are sampled with different
//  probabilities depending on which mask is worn. These numbers mirror the
//  style table in PlanCatalog.java, which the reward machine reads.
//
//  THE SIGN MATTERS, and it is the ORIGINAL Temper that allows it: personalities
//  are validated in [0,1], but plan annotations in [-1,1]. Nothing here widens
//  anything -- we are simply the first to USE the signed half of the range the
//  framework always accepted. Its own demo never did: every annotation in the
//  upstream alice.asl is non-negative.
//
//  Following Andrea's Definition 3.1, -1 is the REVERSE of a trait, not a small
//  amount of it: ignore(a(-0.90)) projects active coldness, polite_decline
//  (e(-0.40)) projects withdrawal. A positive personality trait times a negative
//  annotation gives a NEGATIVE compatibility score, so Alice's warm core
//  (a(0.75)) scores ignore at -1.39 and polite_decline at -0.39 -- and the
//  original roulette gives a non-positive weight no probability at all.
//
//  That is what makes the mask do real work. Those two styles are not merely
//  unlikely for her, they are out of character: she cannot play them until a
//  learned work-mask lowers her agreeableness enough to bring them into reach.
//  That is the paper's claim -- a professionally guarded persona at work despite
//  a warm core -- and with all-positive annotations it was unstatable, because
//  every score was positive and all nine plans sat inside a 3.5x band.
// ---------------------------------------------------------------------

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
//  identical reaction would be a belief Alice already holds, no event
//  would be generated, and the interaction would silently vanish.

//  The circumstance travels with the offer because the partner reacts to the
//  whole situation, not to the act alone -- the same joke lands at home and
//  dies at work. Alice cannot see WHY it died; she only gets the cold reply.

+!offer(Ag, T, Style)
    :   next_id(I) & circumstance(C)
    <-  I1 = I + 1;
        -+next_id(I1);
        vesna.via.record_choice(Ag, Style);
        !say("   -> ", Ag, " : ", Style);
        .send(Ag, tell, offer(T, Style, C, I));
        .wait(20).

// The only place real external feedback enters. The outcome was produced by
// another agent's own personality choosing among its own reaction plans.
+outcome(I, Result)[source(Ag)]
    <-  .abolish(outcome(I, _));
        !say("   <- ", Ag, " : ", Result);
        vesna.via.record_outcome(Ag, Result).

// ---------------------------------------------------------------------
//  DIALOGUE TRACE
// ---------------------------------------------------------------------
//
//  Two plans per hook: the first fires while the agent still believes it is
//  verbose, the second is the catch-all that does nothing. Neither carries a
//  temper() annotation, so this never interferes with plan selection.

+!say(A, B, C) : verbose <- .print(A, B, C).
+!say(_, _, _).

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).

+!announce(E) : verbose <- .print("======== episode ", E, " ========").
+!announce(_).

// Stop the trace, and tell the others to stop theirs. Alice has no authority
// over their belief bases -- she can only send them a message and let their
// own plan decide what to do with it.
+!maybe_hush(E)
    :   dialogue_episodes(D) & E >= D & verbose
    <-  .print("---- dialogue trace off after ", D, " episodes; ",
               "learning continues silently ----");
        .abolish(verbose);
        .broadcast(tell, hush).
+!maybe_hush(_).
