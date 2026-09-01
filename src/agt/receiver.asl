// Shared behaviour for Bob, Carol and Dave. One file, three agents: each is set up
// in vesna.jcm with its own personality and has its own opinions in its own file.
//
// A receiver does not learn and wears no mask. It has a personality and a few ways
// to react, and its personality decides between them. So its reply is not scripted,
// and it does not come from any table inside Alice.
//
// improper/2 is this agent's private opinion about what does not belong where.
// Alice cannot read it. She only finds out by trying something and getting a cold
// reply -- like telling a joke at work and noticing nobody laughs.
//
// Each of bob.asl, carol.asl and dave.asl adds its own needs_help/1, likes_style/1
// and improper/2.

verbose.

+hush[source(_)]
    <-  .abolish(hush);
        .abolish(verbose).

// What is out of place here for everyone, shared by all three receivers.
//
// They agree on what is WRONG but differ on what they LIKE (likes_style, set per
// agent). That split is deliberate. If they disagreed about what is wrong too, the
// feedback would point in no particular direction and the mask would have nothing
// to aim at. Because they agree, each circumstance has one clear direction to
// learn, and because they like different things the replies still vary by partner.
// The mask ends up learning the circumstance, not the person, which is why it
// works on a partner it has never met.

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

// How the offer lands. Three cases:
//
//   liked and allowed here  -> accepted or tolerated
//   allowed but not liked   -> tolerated or rejected
//   out of place here       -> rejected, whatever this agent's personality
//
// Where two replies are possible, the agent's own personality picks between them,
// so the answer varies by partner and is not always the same.
//
// The words accepted / tolerated / rejected are kept general on purpose. In a
// non-social setting a sensor could report goal_achieved / delayed / failed
// instead, and the reward machine would not need to change.

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

// Neither plan has a temper() annotation, so printing cannot affect which reaction
// is chosen.
+!say(A, B, C, D) : verbose <- .print(A, B, C, D).
+!say(_, _, _, _).
