# Vesna-Pro-Masks

Learned, circumstance-dependent personality masks on top of
[VEsNA-Pro](https://github.com/VEsNA-ToolKit/vesna-pro). An agent keeps one fixed core
identity and learns a behavioural mask per circumstance, so it acts differently at work
than at home while remaining recognisably itself. The framing is Pirandello's
*One, No One and One Hundred Thousand*.

## Idea

    A_eff = clip( A_core + M_circumstance , 0 , 1 )

`A_core` (OCEAN, set in `vesna.jcm`) never changes. Each `M_circumstance` starts at zero
-- on episode 0 the agent is simply itself everywhere -- and is moved by counterfactual
regret minimisation from the outcomes other agents actually return. Only the masks learn.

## Running

    ./gradlew run

Agent output goes to Jason's MAS console window, not the terminal (see
`logging.properties`). Results are written to `results/latest/`; the end-of-run summary
is `results/latest/report.txt`. Plots: `python scripts/plot_results.py`.

## The agents

Four agents, each in its own file. Alice learns; Bob, Carol and Dave do not. They are the
social environment that pushes back, and they disagree with each other, so there is no
oracle anywhere in the system.

| file | role |
|---|---|
| `src/agt/alice.asl` | the learning agent: life cycle, nine ways to help, mask selection |
| `src/agt/receiver.asl` | shared receiver behaviour |
| `src/agt/bob.asl`, `carol.asl`, `dave.asl` | per-agent taste (`likes_style`) and norms (`improper`) |
| `src/agt/mask_rules.asl` | which mask is wearable in which circumstance |

Experiment settings -- episodes, rounds, circumstances, verbosity -- are in `vesna.jcm`
under `beliefs:`, never in the `.asl`. A different experiment means a different `.jcm`.

## Compatibility measures

How well a plan suits the agent is scored by one of three measures, set per agent with
`compat:` in `vesna.jcm`:

| value | formula | reads as |
|---|---|---|
| `dot` (default) | sum of trait x annotation | polarity: opposite signs score negative |
| `l1` | sum of (1 - abs difference) | closeness: highest when traits match one for one |
| `cosine` | dot / (norm x norm) | direction only, magnitude ignored |

`dot` is the default and is what every committed result was measured under; omitting the
parameter selects it. The measure is defined once, in `Temper.combine`, and both plan
selection and the mask learner's policy model read it from there, so the learner can never
score against a distribution the agent is not playing.

## Code layout

    src/agt/vesna/        unchanged from VEsNA-Pro: Temper, wrappers, VesnaAgent
    src/agt/vesna/mask/   this project: Mask, MaskLearner, PlanCatalog, RewardMachine
    src/agt/vesna/via/    internal actions bridging AgentSpeak to the learner

### What was changed in the original

`Temper.java` differs from upstream in exactly two places:

1. **Added** `getPersonality()` / `useEffective()` -- the mask seam. Upstream writes
   `personality` only in its constructor; this is the one write path that did not exist.
2. **Fixed** `getWeightedRandomIdx`. It accumulated `double` weights into an `int`, and its
   interval scan could not represent a negative weight. Neither bug is reachable upstream
   -- both its configurations use `most_similar`, and its plan annotations are all
   non-negative -- so the method had never run.

Everything else, including `OptionWrapper`, `IntentionWrapper` and `TemperSelectable`, is
byte-identical to the original.

Personalities stay in `[0,1]` and plan annotations in `[-1,1]`: the two ranges the original
already validates. Using the signed half of the annotation range is what lets a style score
*negative* against the core, so a plan opposed to who the agent is gets no probability at
all until a mask brings it into reach.

## Scope and limitations

Mask learning is enabled on a single focal agent, Alice, while Bob, Carol and Dave provide a
fixed, personality-differentiated environment. The architecture does not preclude multi-agent
learning: `use_masks` is per-agent by design, and the learner is a per-agent instance holding
its own wardrobe and reward machine.

The current implementation does block it, for one specific reason. `PlanCatalog` is a global
shared across every agent in the JVM and permits only one active style set at a time. Alice's
nine helping styles and a receiver's three reaction styles are distinct sets, so enabling
learning on a receiver as well requires refactoring the catalog to per-learner style sets. That
is a bounded engineering change, not an architectural one.

Beyond the implementation change, characterising the joint dynamics that emerge when several
agents' masks evolve against one another needs game-theoretic treatment of the resulting
non-stationary environment, which is outside this paper's scope.

## Results

See `results/README.md` for the file-by-file map and the range ablation kept in
`results/archive/`.
