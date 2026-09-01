// Receiver -- Bob, Carol and Dave. One file, three agents, each instantiated in
// vesna.jcm with its own personality and its own beliefs about what belongs where.
//
// A receiver does not learn and wears no mask. It has a personality and a handful
// of ways to react, and its personality picks among them, so its answer is neither
// scripted nor drawn from a table inside Alice.
//
// improper/2 is this agent's own opinion, invisible to Alice. She finds out the way
// anyone does -- Andrea: "you make some jokes while you are taking a coffee, it is
// your second day at work, and you see that nobody is laughing."
//
// Per-agent beliefs supplied by bob.asl / carol.asl / dave.asl: needs_help/1,
// likes_style/1, improper/2.

verbose.

+hush[source(_)]
    <-  .abolish(hush);
        .abolish(verbose).

// The shared part of the norm: what is out of place for everyone. Sharing the
// REJECTED set gives each circumstance one coherent direction, so the
// counterfactual gap says "a more conscientious mask would have worked here"
// rather than "everything is equally bad" -- which is what the mask needs to move.
// Partner variation lives in the accepted set instead (likes_style, per agent), so
// the mask learns the circumstance-level niche and transfers across partners while
// the feedback stays partner-dependent.

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

+offer(T, Style, Circ, I)[source(Ag)]
    <-  .abolish(offer(T, Style, Circ, I));
        !react(Ag, I, Style, Circ).

// How it lands. Appreciated and appropriate -> {accepted, tolerated} apply; not its
// taste but allowed -> {tolerated, rejected}; out of place here -> {rejected} only,
// whatever this agent's personality. Which of the applicable outcomes fires is
// decided by its own temper, so feedback is partner-dependent and stochastic.
//
// The vocabulary is deliberately general: an environment sensor would emit
// goal_achieved / delayed / failed into the same reward machine.

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

// No temper() on either plan, so this cannot disturb which reaction is selected.
+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
