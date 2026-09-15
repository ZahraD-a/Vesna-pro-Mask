// Which circumstance Alice is in, and which mask that makes wearable.
//
// A circumstance is a group of situations where the same mask makes sense. Keeping one mask per
// circumstance rather than one per person is what stops the number of masks growing with the
// number of agents.
//
// The circumstance is not told to the agent. It is derived here from what she believes about the
// world, so "at work" is a conclusion with conditions behind it, not a label she is handed. The
// vocabulary the world speaks in:
//
//     at(office) | at(home) | at(venue)     where she is
//     hour(H)                               clock, 0..23
//     colleagues_present                    somebody who can see her is around
//     session_running                       a conference session is under way
//     on_break                              the session has paused
//
// Those facts are asserted by the agent from situations/1 in the .jcm; nothing here assumes any
// particular one of them holds.

// At the venue while a session is actually running. A coffee break at the same venue is not the
// conference circumstance: the audience is gone, and so is the reason for the mask.
circumstance(conference)
    :-  at(venue) & session_running & not on_break.

// At the office, inside working hours, with colleagues who can see her. All three are needed:
// the building alone is not work, and neither is the hour.
circumstance(work)
    :-  at(office) & hour(H) & H >= 9 & H < 18 & colleagues_present.

// Off duty and unobserved. Two ways to be there, so two clauses: actually at home, or still at
// the office after hours with nobody left. Same mask, because it is the same situation.
circumstance(home)
    :-  at(home) & not colleagues_present.
circumstance(home)
    :-  at(office) & hour(H) & (H < 9 | H >= 18) & not colleagues_present.

// Nothing recognised -- at the venue during a break, at the office out of hours with colleagues
// still there. Keeps circumstance/1 total, so asking is always safe.
circumstance(default)
    :-  not circumstance(conference) & not circumstance(work) & not circumstance(home).

// Several rules can hold at once. wearable/1 returns all of them; which to actually wear is
// decided in the agent, which takes the first, so clause order here is most specific first.
wearable(mask_conference) :- circumstance(conference).
wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).

// Always wearable: the true self, no modification. Anything with no dedicated mask falls back
// here, and the default mask learns too, so an unmodelled circumstance is visible rather than
// silently absorbed.
wearable(mask_default).
