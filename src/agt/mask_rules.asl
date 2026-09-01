// Which masks may be worn where. A mask is wearable when the agent believes it is
// in the matching circumstance.
//
// CIRCUMSTANCE, not context: in the 17/07 meeting "context" was reserved for its
// BDI meaning (a plan's context condition), and "where I am, who I am with, what is
// going on" was renamed -- Andrea: "a circumstance is just a set of conversations,
// places, situations in which the same mask can be applied."
//
// That is also what makes masks scale. One mask per partner would mean 999 masks
// for 1000 agents; one mask per circumstance covers the whole common room.
//
// Several rules may hold at once. wearable/1 is a QUERY returning a set; which of
// them to put on is decided separately in alice.asl, so the two halves can be
// overridden independently.

wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).
wearable(mask_conference) :- circumstance(conference).

// Always wearable: the true self, no modification. Anything with no
// dedicated mask falls back here -- and the default mask learns too, so
// we can see what an unmodelled circumstance does to the agent.
wearable(mask_default).

 