// The circumstance is derived from what the agent believes about the world -- at/1, hour/1,
// colleagues_present, session_running, on_break -- never told to her. One mask per circumstance,
// not per partner, so the number of masks does not grow with the number of agents. Clause order
// is most specific first: the agent wears the first wearable mask it finds.

circumstance(conference)
    :-  at(venue) & session_running & not on_break.

circumstance(work)
    :-  at(office) & hour(H) & H >= 9 & H < 18 & colleagues_present.

circumstance(home)
    :-  at(home) & not colleagues_present.
circumstance(home)
    :-  at(office) & hour(H) & (H < 9 | H >= 18) & not colleagues_present.

circumstance(default)
    :-  not circumstance(conference) & not circumstance(work) & not circumstance(home).

wearable(mask_conference) :- circumstance(conference).
wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).

wearable(mask_default).
