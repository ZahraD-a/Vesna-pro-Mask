// Shared behaviour for Bob, Carol and Dave.
//
// A receiver does not learn and wears no mask. It has a personality and a few ways
// to react, and its personality picks between them, so replies are neither scripted
// nor drawn from any table inside Alice.
//
// improper/2 is this agent's own view of what does not belong where. Alice cannot
// read it; she only finds out by trying something and getting a cold reply.
//
// bob.asl, carol.asl and dave.asl each add their own needs_help/1, likes_style/1
// and improper/2.

verbose.

+hush[source(_)]
    <-  .abolish(hush);
        .abolish(verbose).

// What is out of place here, shared by all three receivers.
//
// They agree on what is wrong but differ on what they like (likes_style, per agent).
// That split is deliberate: agreement gives each circumstance one clear direction to
// learn, while differing tastes keep the replies partner-dependent. The mask then
// learns the circumstance rather than the person, so it also works on a partner it
// has never met.

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

// How the offer lands:
//
//   liked and allowed here  -> accepted or tolerated
//   allowed but not liked   -> tolerated or rejected
//   out of place here       -> rejected, whatever the personality
//
// Where two replies are possible the agent's personality picks between them, so the
// answer varies by partner and is not fixed.

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

// No temper() annotation, so printing cannot affect which reaction is chosen.
+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
