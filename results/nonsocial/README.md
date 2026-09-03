# Non-social scenario

**Claim.** The mask mechanism does not depend on where reward comes from. Given the same three
circumstances, it learns from environmental outcomes the same way it learns from partners.

**Method.** A separate MAS, `vesna_nonsocial.jcm`: one agent, no partners, no messages. Alice
moves through work, home and conference and wears the same three masks, but instead of offering
help to a colleague she does a task on her own, and the environment reports `goal_achieved`,
`delayed` or `failed`.

    ./gradlew runNonSocial

The two scenarios share nothing at run time -- separate MAS, separate learner, separate plan
set, separate results directory -- so neither can affect the other's numbers. The social results
were verified byte-identical after every change here.

## Design note: why all nine plans are always applicable

Nine self-task styles, none with a context condition, exactly as the nine social styles have
none. What varies between circumstances is not which styles are available but how well each one
lands, which only the environment knows.

This is a correctness requirement, not symmetry. The regret update scores the chosen style
against a baseline averaged over all the others, so that set has to be the set that could
actually have been chosen. Three plans gated per circumstance would leave the baseline averaging
over six styles the agent could not have picked, and the resulting masks would be quietly wrong
while still looking healthy.

## Result

| mask | O | C | E | A | N | norm |
|---|---|---|---|---|---|---|
| mask_work | -0.13 | **+0.41** | -0.50 | -0.50 | -0.50 | 0.969 |
| mask_home | -0.50 | **-0.42** | -0.50 | -0.50 | -0.50 | 1.083 |
| mask_conference | -0.25 | -0.50 | **+0.30** | -0.05 | -0.50 | 0.809 |

Conscientiousness rises at work and falls at home; extraversion is the only trait that rises at a
conference. That tracks what each circumstance rewards: focused work, rest, and meeting people.
Nothing tells the agent this, and the table it is discovering lives in `self_outcome.java`, which
the learner never reads.

Behaviour follows at work, where `deep_work` goes 17.1% -> 31.0% and `network_actively` goes
37.0% -> 6.5%.

`../figures/fig_two_scenarios.pdf` puts the social and non-social trajectories side by side over
the same three circumstances.

## Limitation

At home the agent does not reach the styles the circumstance rewards. `rest_deeply`, `zone_out`
and `step_away` all sit far from her core on extraversion and agreeableness, and the mask is
bounded at 0.5 per trait, so their compatibility never becomes positive and they are never
played. Home therefore ends at -0.947 mean outcome with 76% failures, while work reaches +0.186.

This is the identity-preservation property showing its cost rather than a defect: a mask bends
the agent without replacing her, so an optimum that lies outside that bound stays out of reach.
The same saturation appears in the social scenario, where every mask drives agreeableness to the
clip.

One run, one seed. This is a demonstration that the mechanism is source-agnostic, not a measured
experiment; `results/exp1_ablation/` carries that weight.
