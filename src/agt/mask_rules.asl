// Which mask may be worn where.
//
// A circumstance is a group of situations where the same mask makes sense. Keeping one mask per
// circumstance rather than one per person is what stops the number of masks growing with the
// number of agents.
//
// Note that the social and the non-social circumstances have the same shape. work, home and
// conference come from where the agent is; depleted and rested come from the state of the agent
// itself, with no other agent involved. The mask machinery cannot tell them apart, and that is the
// point -- a circumstance is defined by what puts it in the belief base, not by what kind of thing
// it is.
//
// Several rules can hold at once. wearable/1 returns all of them; which to actually wear is
// decided in the agent.

// Non-social circumstances, derived from the agent's own state. In the social scenario there is
// no energy/1 belief, so neither of these can ever hold there.
circumstance(depleted)    :- energy(E) & E <  40.
circumstance(rested)      :- energy(E) & E >= 40.

wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).
wearable(mask_conference) :- circumstance(conference).
wearable(mask_depleted)   :- circumstance(depleted).
wearable(mask_rested)     :- circumstance(rested).

wearable(mask_default).
