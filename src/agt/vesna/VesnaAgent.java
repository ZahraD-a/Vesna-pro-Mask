package vesna;

import java.net.URI;

import java.util.logging.Logger;
import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Iterator;
import java.rmi.RemoteException;

import org.json.JSONObject;

import jason.RevisionFailedException;
import jason.asSemantics.Agent;
import jason.asSemantics.Message;
import jason.asSemantics.Intention;
import jason.asSemantics.Option;
import jason.asSemantics.Event;
import jason.asSemantics.Unifier;
import jason.asSemantics.IntendedMeans;
import jason.runtime.Settings;
import jason.asSyntax.*;
import jason.asSyntax.parser.ParseException;
import jason.architecture.AgArch;
import jason.infra.local.LocalAgArch;
import jason.infra.local.RunLocalMAS;

import static jason.asSyntax.ASSyntax.*;

public class VesnaAgent extends Agent {

	private WsClient body;
	private Temper temper;
	protected transient Logger logger;
	private List<String> debugs;

	// INIT METHODS
	@Override
	public void initAg() {
		super.initAg();

		Settings stts = ts.getSettings();

		logger = ts.getLogger();

		initDebug( stts );
		initTemper( stts );
		initBody( stts );
	}

	private void initDebug( Settings stts ) {
		String debugStts = stts.getUserParameter( "vesnadebug" );
		debugs = new ArrayList<>();
		if ( debugStts == null )
			return;
		try {
			Literal debugList = parseLiteral( debugStts );
			debugs = debugList.getTerms().stream().map( Term::toString ).collect( Collectors.toList() );
		} catch ( ParseException e ) {
			logger.warning( "Invalid debug list: " + debugStts );
		}
		//logger.info( "Loaded debugs:" + debugs );
	}

	private void initTemper( Settings stts ) {
		String temperStts = stts.getUserParameter( "temper" );
		String strategy = stts.getUserParameter( "strategy" );
		if ( temperStts == null )
			return;
		temper = new Temper( temperStts, strategy );
	}

	private void initBody( Settings stts ) {
		String address = stts.getUserParameter( "address" );
		int port = Integer.parseInt( stts.getUserParameter( "port" ) );
		try {
			URI bodyAddress = new URI( "ws://" + address + ":" + port );
			body = new WsClient( bodyAddress );
			body.setMsgHandler( new WsClientMsgHandler() {
				@Override public void handleMsg( String msg ) { myHandleMsg( msg ); };
				@Override public void handleError( Exception e ) { myHandleError( e ); };
			});
			body.connect();
		} catch ( Exception e ) {
			stop( e.getMessage() );
		}

		//logger.info( "My body is at " + address + ":" + port );
	}

	//BODY METHODS
	private void sense( Literal perception ) {
		try {
			Message signal = new Message( "signal", ts.getAgArch().getAgName(), ts.getAgArch().getAgName() , perception );
			ts.getAgArch().sendMsg( signal );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	public void myHandleMsg( String msg ){
		JSONObject log = new JSONObject( msg );
		String sender = log.getString( "sender" );
		String receiver = log.getString( "receiver" );
		String type = log.getString( "type" );
		JSONObject data = log.getJSONObject( "data" );
		switch( type ) {
			case "signal":
				handleEvent( data );
				break;
			case "sight":
				handleSight( data );
				break;
			case "rcc":
				handleRcc( data );
				break;
			case "exploration":
				handleExploration( data );
				break;
			default:
				logger.warning( "Unknown message type: " + type );
		}
	}

	private void handleEvent( JSONObject event ) {
		String event_type = event.getString( "type" );
		String event_status = event.getString( "status" );
		String event_reason = event.getString( "reason" );
		Literal perception = createLiteral( event_type, createLiteral( event_status ), createLiteral( event_reason ) );
		sense(perception);
	}

	private void handleExploration( JSONObject data ) {
		String type = data.getString( "type" );
		String obj = data.getString( "object" );
		sense( createLiteral( type, createLiteral( obj ) ) );
	}

	private void handleSight( JSONObject sight ) {
		String object = sight.getString( "sight" );
		long id = sight.getLong( "id" );
		Literal sight_belief = createLiteral( "sight", createLiteral( object ), createNumber( id ) );
		try{
			addBel( sight_belief );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	private void handleRcc( JSONObject data ) {
		String region = data.getString( "region" );
		if ( data.getString( "type" ).equals( "region_exited" ) ) {
			// logger.info( "Region exited: " + region );
			Literal agRegion = createLiteral( "ntpp", createLiteral( ts.getAgArch().getAgName() ), createLiteral( region ) );
			// logger.info( "Deleting region belief: " + agRegion );
			try {
				abolish( agRegion, new Unifier() );
				return;
			} catch ( Exception e ) {
				e.printStackTrace();
			}
		}
		Literal agNewRegion = createLiteral( "ntpp", createLiteral( ts.getAgArch().getAgName() ), createLiteral( region ) );
		Literal agOldRegion = createLiteral( "ntpp", createLiteral( ts.getAgArch().getAgName() ), new VarTerm( "X") );
		try {
			Unifier oldUnifier = new Unifier();
			Literal agOldRegionBel = findBel( agOldRegion, oldUnifier );
			Literal oldRegion = (Literal) oldUnifier.get( "X" );
			Literal po = createLiteral( "po", createLiteral( region ), oldRegion );
			if ( agOldRegionBel != null && !believes( po, new Unifier() ) ) {
				// logger.info( "Removing : " + agOldRegion );
				abolish( agOldRegionBel, new Unifier() );
			}
			addBel( agNewRegion );
		} catch ( Exception e ) {
			e.printStackTrace();
		}
	}

	public void myHandleError( Exception e ){
		stop( e.getMessage() );
	}

	private void stop( String reason ) {
		//logger.info( reason );
		kill_agent();
	}

	private void kill_agent() {
		logger.severe( "Killing myself" );
		if ( body != null )
			body.close();
		AgArch arch = ts.getAgArch();
		while( arch != null ) {
			if ( arch instanceof LocalAgArch ) {
				( (LocalAgArch) arch ).stopAg();
				break;
			}
			arch = arch.getNextAgArch();
		}
	}

	public void perform( String action ) {
		body.send( action );
	}

	// VESNA REASONING CYCLE

	@Override
	public Option selectOption( List<Option> options ) {
		if ( options == null || options.isEmpty() )
			return null;
		if ( !hasTemper() || options.size() == 1 || !temper.hasOptionsAnnotation( options ) )
			return options.remove( 0 );
		Option selected = temper.selectOption( options );
		options.remove( selected );
		return selected;
	}

	@Override
	public Intention selectIntention( Queue<Intention> intentions ) {
		if ( temper == null )
			return super.selectIntention( intentions );
		if ( intentions.size() == 1 || !temper.hasIntentionsAnnotation( intentions ) )
			return super.selectIntention( intentions );
		Intention selected = temper.selectIntention( intentions );
		intentions.remove( selected );
		return selected;
	}

	private boolean hasTemper() {
		return temper != null;
	}

	private String predicateIndicator2Trigger( PredicateIndicator pi ) {
		if ( pi.getArity() == 0 )
			return pi.getFunctor().toString();
		String ans = pi.getFunctor() + "(";
		for ( int i=0; i<pi.getArity(); i++ )
			ans += "_, ";
		ans = ans.substring( 0, ans.length() - 2 ) + ")";
		return ans;
	}

}
