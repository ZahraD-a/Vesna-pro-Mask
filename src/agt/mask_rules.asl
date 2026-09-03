// Which mask may be worn where.
//
// A circumstance is a group of situations where the same mask makes sense. Keeping one mask per
// circumstance rather than one per person is what stops the number of masks growing with the
// number of agents.
//
// Several rules can hold at once. wearable/1 returns all of them; which to actually wear is
// decided in the agent.

wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).
wearable(mask_conference) :- circumstance(conference).

wearable(mask_default).
