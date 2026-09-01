// Which mask may be worn where. A mask is wearable when the agent believes it is
// in the matching circumstance.
//
// The word is CIRCUMSTANCE, not context, because in AgentSpeak "context" already
// means a plan's context condition. A circumstance is a group of places and
// situations where the same mask makes sense: at work, at home, at a conference.
//
// Grouping this way is what keeps the number of masks small. One mask per person
// would mean 999 masks in a group of 1000. One mask per circumstance covers
// everyone in the room at once.
//
// More than one rule can be true at the same time. wearable/1 is a question that
// returns all of them; choosing which to actually wear happens in alice.asl, so
// the two decisions can be changed separately.

wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).
wearable(mask_conference) :- circumstance(conference).

// Always wearable: the true self, no modification. Anything with no
// dedicated mask falls back here -- and the default mask learns too, so
// we can see what an unmodelled circumstance does to the agent.
wearable(mask_default).

 