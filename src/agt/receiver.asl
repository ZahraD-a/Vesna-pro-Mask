// =====================================================================
//  RECEIVER  --  Bob, Carol and Dave
// =====================================================================
//
//  One file, three agents. Each is instantiated in vesna.jcm with its own
//  personality, and each carries its own taste and its own sense of what
//  is out of place where:
//
//      "Yes, this can be used by any agent, so we can have four different
//       ASL files with the same code."
//
//  A receiver does not learn and wears no mask. It has a personality and a
//  handful of ways to react, and its personality picks among them -- so its
//  answer is neither scripted nor sampled from a table living inside Alice.
//
//  WHERE THE SOCIAL NORMS LIVE
//  ---------------------------
//  improper(Style, Circumstance) is a belief of THIS agent about what does
//  not belong here. Alice cannot read it. She finds out the way anybody
//  finds out:
//
//      "you make some jokes while you are taking a coffee, it is your
//       second day at work, and you see that nobody is laughing."
//
//  The three receivers do not agree with each other -- Dave will laugh at a
//  joke at work, Bob will not -- so there is no oracle anywhere in the
//  system, only three opinions Alice has to average over experience.
//
//  Beliefs supplied per agent: needs_help/1, likes_style/1, improper/2.

// Trace its own replies until Alice broadcasts hush. Alice has no power over
// this belief base -- she sends a message, and this plan decides to comply.
verbose.

+hush[source(_)]
    <-  .abolish(hush);
        .abolish(verbose).

// ---------------------------------------------------------------------
//  THE NICHE OF EACH CIRCUMSTANCE  --  what does NOT belong here
// ---------------------------------------------------------------------
//
//  improper/2 is SHARED by all three receivers: at work, impulsive and
//  dismissive help is out of place for everyone; at home, procedural
//  distance is; at a conference, hiding is. Sharing the rejected set is a
//  deliberate design choice for a regret learner -- it gives each
//  circumstance one CLEAR, coherent direction, so the counterfactual gap
//  says "a more conscientious / more social / more open mask would have
//  worked here", not "everything is equally bad". That directed gap is what
//  the mask needs to move.
//
//  Partner variation lives in the ACCEPTED set instead: likes_style differs
//  per agent (bob.asl / carol.asl / dave.asl), so which of the appropriate
//  styles are met warmly vs merely tolerated changes from partner to
//  partner. The mask still learns the shared, circumstance-level niche, so
//  it transfers across partners -- the scalability result -- while the
//  feedback stays partner-dependent and stochastic.

// work: professional. Impulsive rescue and clowning and vanishing are out.
improper(drop_everything, work).
improper(joke_deflect,    work).
improper(ignore,          work).
improper(polite_decline,  work).

// home: warm. Bureaucratic distance is out.
improper(delegate,        home).
improper(help_after_task, home).
improper(polite_decline,  home).
improper(ignore,          home).

// conference: visible and communicative. Hiding and dropping out are out.
improper(ignore,          conference).
improper(polite_decline,  conference).
improper(drop_everything, conference).
improper(delegate,        conference).

// Would this agent be glad to be helped this way, here? (its taste AND appropriate)
approve(Style, Circ)   :- likes_style(Style) & not improper(Style, Circ).

// Would it at least be fine with it? (appropriate, even if not its taste)
tolerate(Style, Circ)  :- not improper(Style, Circ).

// ---------------------------------------------------------------------
//  SOMEONE OFFERS HELP
// ---------------------------------------------------------------------

+offer(T, Style, Circ, I)[source(Ag)]
    <-  .abolish(offer(T, Style, Circ, I));
        !react(Ag, I, Style, Circ).

// ---------------------------------------------------------------------
//  HOW THAT LANDS  --  a symbolic outcome, not "warm/cold"
// ---------------------------------------------------------------------
//
//  Appreciated and appropriate  -> {accepted, tolerated} are applicable
//  Not its taste, but allowed   -> {tolerated, rejected}
//  Out of place here            -> {rejected} only, whatever this agent's
//                                  personality is: the room goes quiet
//
//  The outcome vocabulary is deliberately general -- the same accepted /
//  tolerated / rejected terms (scored in RewardMachine) would be emitted by
//  an environment sensor in a non-social scenario. Which of the applicable
//  outcomes actually fires is decided by this agent's own temper, weighted
//  at random: an agreeable extravert accepts far more readily than a guarded,
//  conscientious one, so the feedback is partner-dependent AND stochastic.
//
//  Both plan annotations and the agent's own personality (in the .jcm) are in
//  [0,1], so the original Temper's weighted-random selection stays a genuine
//  mix (its roulette needs positive weights).

@react_accept[temper([o(0.65), c(0.50), e(0.75), a(0.90), n(0.35)])]
+!react(Ag, I, Style, Circ)
    :   approve(Style, Circ)
    <-  .send(Ag, tell, outcome(I, accepted));
        !say(Style, " in ", Circ, "  ->  accepted").

@react_tolerate[temper([o(0.50), c(0.75), e(0.45), a(0.50), n(0.50)])]
+!react(Ag, I, Style, Circ)
    :   tolerate(Style, Circ)
    <-  .send(Ag, tell, outcome(I, tolerated));
        !say(Style, " in ", Circ, "  ->  tolerated").

@react_reject[temper([o(0.30), c(0.50), e(0.20), a(0.15), n(0.80)])]
+!react(Ag, I, Style, Circ)
    :   not approve(Style, Circ)
    <-  .send(Ag, tell, outcome(I, rejected));
        !say(Style, " in ", Circ, "  ->  rejected").

// ---------------------------------------------------------------------
//  DIALOGUE TRACE
// ---------------------------------------------------------------------
//  No temper() annotation on either plan, so this cannot disturb which
//  reaction gets selected.

+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
