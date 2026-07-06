// =============================================================
// MASK WEARABILITY RULES
// =============================================================
// Context -> Mask mapping (Prolog-style)
// First matching rule wins.
// =============================================================

// Rule 1: work context -> professional behavior
mask_for( Ctx, MaskName ) :- Ctx == work & MaskName = mask_work.

// Rule 2: home context -> relaxed behavior
mask_for( Ctx, MaskName ) :- Ctx == home & MaskName = mask_home.

// Rule 3: concert context -> expressive behavior
mask_for( Ctx, MaskName ) :- Ctx == concert & MaskName = mask_concert.

// Default: no context -> true self
mask_for( Ctx, MaskName ) :- MaskName = mask_default.
