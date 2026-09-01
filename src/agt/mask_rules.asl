// =====================================================================
//  WEARABILITY RULES  --  getMasks(BeliefBase)
// =====================================================================
//
//  A mask is wearable when the agent believes it is in the matching
//  circumstance. Note the vocabulary: CIRCUMSTANCE, not context. In the
//  17/07 meeting the word "context" was reserved for its BDI meaning --
//  the context condition of a plan -- and the general notion of "where I
//  am, who I am with, what is going on" was renamed circumstance:
//
//      "a circumstance is just a set of conversations, places, situations
//       in which the same mask can be applied"
//
//  That is also what makes masks scale. In the ARIA formulation you need
//  one mask per partner, so 1000 agents means 999 masks. Here one mask
//  covers a whole circumstance, so all the PhD students in the common
//  room share a single mask and the number of masks stops tracking the
//  number of agents.
//
//  Several rules may hold at once -- that is deliberate. wearable/1 is a
//  QUERY that returns a SET; deciding which of them to actually put on is
//  a separate step (vesna.via.wear_mask), so the two halves can be
//  overridden independently.
//
//  Circumstances are GIVEN, and so is the circumstance-to-mask binding.
//  Only the VALUES inside each mask are learned. Learning which mask to
//  wear is the next step of the roadmap, not this one.
// =====================================================================

wearable(mask_work)       :- circumstance(work).
wearable(mask_home)       :- circumstance(home).
wearable(mask_conference) :- circumstance(conference).

// Always wearable: the true self, no modification. Anything with no
// dedicated mask falls back here -- and the default mask learns too, so
// we can see what an unmodelled circumstance does to the agent.
wearable(mask_default).

 