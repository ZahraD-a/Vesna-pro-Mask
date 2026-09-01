package vesna;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Random;
import java.util.Iterator;
import java.util.stream.Collectors;

import static jason.asSyntax.ASSyntax.*;
import jason.asSyntax.*;
import jason.asSemantics.*;
import jason.asSyntax.parser.ParseException;
import jason.NoValueException;

/** This class implements the temper of the agent
 * <p>
 * The temper of an agent is subdivided into:
 * <ul>
 * <li> <b>personality:</b> for the moment it does never change. <i>In the future</i>, it could change based on mood but very slowly;
 * <li> <b>mood:</b> it changes applying plan post-actions if provided.
 * </ul>
 * The agent can apply two decision strategies:
 * <ul>
 * <li> <b>Most similar:</b> deterministic, it chooses always the plan with personality and mood more similar to the current ones;
 * <li> <b>Random:</b> undeterministic, it chooses with a weighted random based on the similarity between the plan annotations and the current temper.
 * </ul>
 */
public class Temper {

    /** Decision Strategy is an enumerable between most similar and random */
    private enum DecisionStrategy { MOST_SIMILAR, RANDOM };

    /** Personality is the persistent part of the agent temper */
    private Map<String, Double> personality;
    /** Mood is the mutable part of the agent temper */
    private Map<String, Double> mood;
    /** The agent decision strategy */
    private DecisionStrategy strategy;
    /** A dice necessary to generate random numbers */
    private Random dice = new Random();

    public Temper( String temper, String strategy ) throws IllegalArgumentException {

        // The temper should always be set at this point
        if ( temper == null )
            throw new IllegalArgumentException( "Temper cannot be null" );

        // Initialize the new personality
        personality = new HashMap<>();
        mood = new HashMap<>();

        try {
            // Load the personality into the Map
            Literal listLit = parseLiteral( temper );
            for ( Term term : listLit.getTerms() ) {
                Literal trait = ( Literal ) term;
                double value = ( double ) ( ( NumberTerm ) trait.getTerm( 0 ) ).solve();
                if ( trait.hasAnnot( createLiteral( "mood" ) ) ) {
                    if ( value < -1.0 || value > 1.0 )
                        throw new IllegalArgumentException( "Trait value for mood must be between -1 and 1, found:" + trait );
                    mood.put( trait.getFunctor().toString(), value );
                    continue;
                } else {
                    if ( value < 0.0 || value > 1.0 )
                        throw new IllegalArgumentException( "Trait value for personality must be between 0 and 1, found:" + trait );
                    personality.put( trait.getFunctor().toString(), value );
                }
            }
        } catch ( ParseException pe ) {
            throw new IllegalArgumentException( pe.getMessage() + " Maybe one of the terms of personality is mispelled" );
        } catch ( NoValueException nve ) {
            throw new IllegalArgumentException( nve.getMessage() + " Maybe one of the terms is mispelled and does not contain a number" );
        }

        // Load the strategy
        if ( strategy == null )
            this.strategy = DecisionStrategy.MOST_SIMILAR;
        if ( strategy.equals( "most_similar" ) )
            this.strategy = DecisionStrategy.MOST_SIMILAR;
        else if ( strategy.equals( "random" ) )
            this.strategy = DecisionStrategy.RANDOM;
        else
            throw new IllegalArgumentException( "Decision Strategy Unknown: " + strategy );
    }

    // ---------------------------------------------------------------------
    //  MASK SEAM  (the ONLY addition to the original VEsNA-Pro Temper)
    // ---------------------------------------------------------------------
    //  The mask framework does not touch plan selection, compatibility, or
    //  anything else in this class. It only swaps the personality vector that
    //  computeWeight() reads, replacing the core with the effective personality
    //  A_eff = clip(A_core + M_circumstance). Everything downstream -- weights,
    //  most-similar / weighted-random, effects -- runs exactly as before and is
    //  unaware masks exist. This is the modular extension point described in the
    //  proposal: the mask sits BETWEEN personality and compatibility.
    //
    //  getPersonality() lets the mask layer read the core once, at startup, so
    //  it never has to re-parse the .jcm literal.

    public java.util.Map<String, Double> getPersonality() {
        return new java.util.HashMap<>( personality );
    }

    public void useEffective( java.util.Map<String, Double> effective ) {
        this.personality = effective;
    }

    public double computeWeight( Pred label ) throws NoValueException {
        double choiceWeight = 0;

        Literal temperAnnot = label.getAnnot( "temper" );
        if ( temperAnnot == null )
            return choiceWeight;

        ListTerm choiceTemper = ( ListTerm ) temperAnnot.getTerm( 0 );
        for ( Term traitTerm : choiceTemper ) {
            Atom trait = ( Atom ) traitTerm;
            if ( ! mood.keySet().contains( trait.getFunctor().toString() ) && ! personality.keySet().contains( trait.getFunctor().toString() ) )
                continue;
            double traitTemper;
            if ( mood.keySet().contains( trait.getFunctor().toString() ) )
                traitTemper = mood.get( trait.getFunctor().toString() );
            else
                traitTemper = personality.get( trait.getFunctor().toString() );
            try {
                double traitValue = ( double ) ( (NumberTerm ) trait.getTerm( 0 ) ).solve();
                if ( traitValue < -1.0 || traitValue > 1.0 )
                    throw new IllegalArgumentException("Trait value out of range, found: " + trait + ". The value should be inside [0, 1].");
                if ( strategy == DecisionStrategy.RANDOM )
                    choiceWeight += traitTemper * traitValue;
                else if ( strategy == DecisionStrategy.MOST_SIMILAR )
                    choiceWeight += Math.abs( traitTemper - traitValue );
            } catch ( NoValueException nve ) {
                throw new NoValueException( "One of the plans has a mispelled annotation" );
            }
        }
        return choiceWeight;
    }

    public boolean hasOptionsAnnotation( List<Option> options ) {
    	List<OptionWrapper> wrappedOptions = options.stream()
    		.map( OptionWrapper::new )
    		.collect( Collectors.toList() );
    	return hasAnnotation( wrappedOptions );
    }

    public boolean hasIntentionsAnnotation( Queue<Intention> intentions ) {
    	List<IntentionWrapper> wrappedIntentions = intentions.stream()
    		.map( IntentionWrapper::new )
    		.collect( Collectors.toList() );
    	return hasAnnotation( wrappedIntentions );
    }

    private <T extends TemperSelectable> boolean hasAnnotation( List<T> choices ) {
        Literal annotPattern = createLiteral( "temper", new VarTerm( "X" ) );
        for ( T choice : choices ) {
            Pred l = choice.getLabel();
            if ( l.hasAnnot() ) {
                for ( Term t : l.getAnnots() ) {
                    if ( new Unifier().unifies( annotPattern, t ) )
                        return true;
                }
            }
        }
        return false;
    }

    public Option selectOption( List<Option> options ) {
    	List<OptionWrapper> wrappedOptions = options.stream()
			.map( OptionWrapper::new )
			.collect( Collectors.toList() );
		try {
			return select( wrappedOptions ).getOption();
		} catch ( NoValueException e ) {
			return null;
		}
    }

    public Intention selectIntention( Queue<Intention> intentions ) {
    	List<IntentionWrapper> wrappedIntentions = new ArrayList<>( intentions ).stream()
     		.map( IntentionWrapper::new )
     		.collect( Collectors.toList() );
       try {
        	Intention selected = select( wrappedIntentions ).getIntention();
         	Iterator<Intention> it = intentions.iterator();
          	while( it.hasNext() ) {
	           	if ( it.next() == selected ) {
	           		it.remove();
	             	break;
	           }
           }
           Literal effectList = selected.peek().getPlan().getLabel().getAnnot( "effects" );
           if ( effectList != null )
               updateDynTemper( effectList );
           return selected;
       } catch ( NoValueException e ) {
	       return null;
       }
    }

    public <T extends TemperSelectable> T select( List<T> choices ) throws NoValueException {
        List<Double> weights = new ArrayList<>();

        for ( T choice : choices ) {
            weights.add( computeWeight( choice.getLabel() ) );
        }

        T chosen = null;
        int chosenIdx = -1;
        if ( strategy == DecisionStrategy.RANDOM ) {
        	chosenIdx = getWeightedRandomIdx( weights );
            chosen = choices.get( chosenIdx );
        } else if ( strategy == DecisionStrategy.MOST_SIMILAR ) {
            chosenIdx = getMostSimilarIdx( weights );
            chosen = choices.get( chosenIdx );
        }
        if ( chosen == null ) {
        	chosenIdx = 0;
            chosen = choices.get( chosenIdx );
        }


        return chosen;
    }

    private int getWeightedRandomIdx( List<Double> weights ) {
        // BUG FIX vs upstream VEsNA-Pro: the original declared `int currentMin = 0` and then did
        // `currentMin += weights.get(i)` with double weights, truncating the running cumulative to
        // an integer. The roulette intervals then stopped tiling [min,max] and every roll past the
        // last (truncated) boundary fell through to `return 0`, biasing selection heavily toward
        // the FIRST plan. Harmless upstream (guards usually leave 1-2 applicable plans) but fatal
        // here, where nine plans are always applicable: it pinned ~45% of choices on plan 0
        // regardless of personality, so the mask could not steer behaviour. Fixed to a proper
        // cumulative roulette with a double accumulator. Nothing else in selection changes.
        //
        // BUG FIX (2) -- SIGNED weights. The personality stays in [0,1] exactly as upstream, but a
        // plan's temper() annotation may be negative -- the original already allows that, and
        // validates annotations at [-1,1] just above in computeWeight. A positive trait times a
        // negative annotation is a NEGATIVE compatibility score, and the original min_bound /
        // max_bound scheme cannot express that. A negative weight makes the running cumulative move BACKWARDS, so
        // that plan's interval has zero width, the following intervals re-cover ground already
        // covered, and the scan ends well short of max_bound. The leftover range is a dead zone
        // that every remaining roll falls through: on a realistic signed weight vector it swallowed
        // 99% of the rolls and dumped them all on a single plan. THAT is the real cause of the
        // plan-0 domination previously worked around by confining every trait to [0,1] -- the
        // range was never the problem, this scan was.
        //
        // A negative dot product means the plan projects a persona OPPOSED to who the agent is
        // ("being conscientious with weight -1 means being sloppy and unreliable"). Such a plan is
        // not merely unlikely, it is out of character, so it gets zero probability. This is exactly
        // the distribution MaskLearner.policy() already models, so the counterfactual-regret
        // baseline is now scored against the distribution the agent really plays. If nothing at all
        // is compatible, fall back to uniform rather than always conceding to the same plan.
        double total = 0.0;
        for ( double weight : weights )
            total += Math.max( 0.0, weight );

        if ( total <= 0.0 )
            return dice.nextInt( weights.size() );

        double roll = dice.nextDouble() * total;
        double cumulative = 0.0;
        for ( int i = 0; i < weights.size(); i++ ) {
            cumulative += Math.max( 0.0, weights.get( i ) );
            if ( roll < cumulative )
                return i;
        }
        return weights.size() - 1;
    }

    private int getMostSimilarIdx( List<Double> weights ) {
        double min = Double.MAX_VALUE;
        int minIdx = -1;
        for ( int i = 0; i < weights.size(); i++ ) {
            if ( weights.get( i ) < min ) {
                min = weights.get( i );
                minIdx = i;
            }
        }
        return minIdx;
    }

    private void updateDynTemper( Literal effectList ) throws NoValueException {
        ListTerm effects = ( ListTerm ) effectList.getTerm( 0 );
        for ( Term effectTerm : effects ) {
            Literal effect = ( Literal ) effectTerm;
            if ( personality.keySet().contains( effect.getFunctor().toString() ) && !effect.hasAnnot( createLiteral( "mood" ) ) )
                throw new IllegalArgumentException( "You used a Personality trait in the post-effects! Use only mood traits. In case of ambigous name use the annotation [mood]." );
            if ( mood.get( effect.getFunctor().toString() ) == null )
                continue;
            double moodValue = mood.get( effect.getFunctor().toString() );
            try {
                double effectValue = ( double ) ( ( NumberTerm ) effect.getTerm( 0 ) ).solve();
                if ( effectValue < - 1.0 || effectValue > 1.0 )
                    throw new IllegalArgumentException("Effect value out of range: " + effectValue + ". It should be between [-100,100].");
                if ( moodValue + effectValue > 1.0 )
                    mood.put( effect.getFunctor().toString(), 1.0 );
                else if ( moodValue + effectValue < -1.0 )
                    mood.put( effect.getFunctor().toString(), 0.0 );
                else
                    mood.put( effect.getFunctor().toString(), moodValue + effectValue );
            } catch ( NoValueException nve ) {
                throw new NoValueException( "One of the plans has a mispelled annotation" );
            }
        }
    }

}
