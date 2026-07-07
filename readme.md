# Vesna-Pro-Mask: Context-Dependent Personality Learning via CFR

**Vesna-Pro-Mask** extends [Vesna-Pro](https://github.com/VEsNA-ToolKit/vesna-pro) with a **Mask Wardrobe** architecture: agents maintain a frozen core identity and wear context-specific behavioral masks that evolve independently via Counterfactual Regret Minimization (CFR). inspired by pirandello [pirandello](https://en.wikipedia.org/wiki/One,_No_One_and_One_Hundred_Thousand). 

## Core Idea

An agent has:
- **Core Identity (A_core)**: Immutable personality traits (OCEAN model), set at design time
- **Masks (M_κ)**: Per-context behavioral overlays, all starting at `[0,0,0,0,0]`, learned via CFR
- **Effective Personality**: `A_eff = clip(A_core + M_active, -1.0, +1.0)`

The same agent behaves differently depending on context:
- At **work**: wears `mask_work` → learns professional behavior  
- At **home**: wears `mask_home` → learns relaxed behavior  
- At **concert**: wears `mask_concert` → learns expressive behavior  

Each context tracks its **own reward history**, so CFR learns different values per mask.

 

## Mask Selection via Prolog Rules

Mask wearability is defined in `mask_rules.asl` using Prolog-style rules:

```prolog
mask_for( Ctx, MaskName ) :- Ctx == work & MaskName = mask_work.
mask_for( Ctx, MaskName ) :- Ctx == home & MaskName = mask_home.
mask_for( Ctx, MaskName ) :- Ctx == concert & MaskName = mask_concert.
mask_for( Ctx, MaskName ) :- MaskName = mask_default.
```

The agent queries beliefs to select the active mask before each decision.

## Configuration

In `vesna.jcm`:

```jason
agent alice:main.asl {
    ag-class:       vesna.VesnaAgent
    temper:         temper(openness(0.3), conscientiousness(-0.2), extraversion(0.1), agreeableness(0.5), neuroticism(-0.4), stress(0.0)[mood], satisfaction(0.0)[mood], social_energy(0.0)[mood])
    strategy:       random
    seed:           0
    cfr_learning:   true
    goals:          start
    use_masks:      true
    mask_delta:     0.5
    mask_contexts:  "work,home,concert"
}
```

- `use_masks`: Enable mask wardrobe mode
- `mask_delta`: δ — maximum absolute value per mask trait (bounds identity drift)
- `mask_contexts`: Comma-separated context names

## Plan Annotation

Plans are annotated with OCEAN traits:

```jason
@formal[temper([conscientiousness(0.8), extraversion(-0.6), agreeableness(0.2), openness(-0.4), neuroticism(-0.6)]), effects([satisfaction(+0.05)[mood]])]
+!choose_response <- +strategy(formal).
```
 
 
## Running

```bash
# Build and run
./gradlew run

# Or on Windows PowerShell
.\gradlew.bat run
```

The agent runs 50 episodes, interacting in 3 contexts per episode. Masks start at `[0,0,0,0,0]` and learn through CFR.

## Output

After training, the wardrobe shows diverged masks:

```
Mask[mask_work]    ||M||=0.062 {C=+0.042, E=-0.029, O=-0.031}  ← professional
Mask[mask_home]    ||M||=0.065 {E=+0.056, C=-0.027, O=+0.013}  ← relaxed
Mask[mask_concert] ||M||=0.107 {E=+0.087, O=+0.040, C=-0.045}  ← expressive
```

Same agent, three learned identities.

## References

- [Jason BDI Agent Platform](https://github.com/jason-lang/jason)
- [Vesna-Pro](https://github.com/VEsNA-ToolKit/vesna-pro)
- [pirandello](https://en.wikipedia.org/wiki/One,_No_One_and_One_Hundred_Thousand)
- Gatti et al. (2026), Pro-AgentSpeak(L), AAMAS 2026
