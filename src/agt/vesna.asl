// RCC Rules
ntpp( A, B ) :- map_ntpp( A, B ). // load from map
// A region A is contained in a region B if exists C superegion of A that is contained in B
ntpp( A, B ) :- map_ntpp( A, C ) & ntpp( C, B ).

sd( A, B ) :- map_sd( A, L ) & .member( B, L ).
sd( A, B ) :- map_sd( A, L1 ) & .member( X, L1 ) & map_sd( X, L2 ) & .member( B, L2 ).

ec( A, B ) :- map_ec( A, B ). // load from map
ec( A, B ) :- map_ec( B, A ). // load from map

po( A, B ) :- map_po( A, B ). // load from map
po( A, B ) :- map_po( B, A ). // load from map

my_region( A ) :- .my_name( Me ) & ntpp( Me, A ).
same_super_region( A, B, R ) :- ntpp( A, R ) & ntpp( B, R ).

// As convention I will use S (Start) and T (Target)

path( S, S, [], [S] ).
path( S, T, [T], [S] ) :- ntpp( T, S ) & not sd( S, T ).
path(S, T, [], [S] ) :- ntpp( S, T ) | sd( T, S ).
path( S, T, [T], [S] ) :- ec( S, T ).
path( S, T, [T], [S] ) :- po( S, T ).

rpath( S, T, [], Visited, Visited ) :-
    path( S, T, [], _ ).

rpath( S, T, [T], Visited, Visited ) :-
    path( S, T, [T], _ ).

rpath( S, T, [Step | Path], VisitedSoFar, FinalVisited ) :-
    path( S, Step, _, _ ) &
    not .member( Step, VisitedSoFar ) &
    not ( ntpp( S, Step ) & ntpp( T, Step ) ) &
    rpath( Step, T, Path, [Step | VisitedSoFar], FinalVisited ).

path( S, T, Path ) :- rpath( S, T, Path, [S], _ ).

shortest_path( [], Best, Best ).
shortest_path( [P|Rest], BestSoFar, Result ) :-
    .length( P, L ) &
    .length( BestSoFar, BestL ) &
    L < BestL &
    shortest_path( Rest, P, Result ).
shortest_path( [P|Rest], BestSoFar, Result ) :-
    shortest_path( Rest, BestSoFar, Result ).

path_shortest( S, T, Shortest ) :-
    .findall( Path, path(S, T, Path), [First|Rest] ) &
    shortest_path( Rest, First, Shortest ).

+!go_to( T )
	:	my_region( S ) & path_shortest( S, T, [] )
	<-	.print( "Arrived." ).

+!go_to( T )
	:	my_region( S ) & path_shortest( S, T, [ Step | Path ] )
	<-	!walk( Step );
	 	!go_to( T ).

+ntpp( Me, Region )
	:	.my_name( Me ) & .desire( walk( Region ) )
	<-	.succeed_goal( walk( Region ) ).

+!walk( T )
	<-	vesna.walk( T );
		.wait( { +movement( completed, destination_reached ) }).// | ntpp( Me, T ) ).

+!grab( Object, Anchor )
	:	my_region( Object ) | ( my_region( S ) & ntpp( Object, S ) )
	<-	vesna.grab( Object, Anchor );
		.wait( { +object_lifted( Object, _ ) } ).

-ntpp( Me, Region )
	:	.my_name( Me ) & not ntpp( Me, AnotherRegion ) & ntpp( Region, SuperRegion )
	<-	+ntpp( Me, SuperRegion ).

// ARTIFACT INTERACTIONS
+!use( ArtName )
    :   .my_name( Me ) & ntpp( Me, MyRegion )
    <-  lookupArtifact( ArtName, ArtId );
        focus( ArtId );
        use( MyRegion )[ artifact_id( ArtId ) ].

-!use( ArtName )
    <-  .print( "I cannot use ", ArtName ).

+!free( ArtName )
    <-  lookupArtifact( ArtName, ArtId );
        stopFocus( ArtId );
        free[ artifact_id( ArtId ) ].

+!grab( ArtName )
    :   .my_name( Me ) & ntpp( Me, MyRegion )
    <-  lookupArtifact( ArtName, ArtId );
        grab( MyRegion )[ artifact_id( ArtId ) ].

-!grab( ArtName )
    <-  .print( "I cannot grab ", ArtName ).

+!release( ArtName )
    :   .my_name( Me ) & ntpp( Me, MyRegion )
    <-  lookupArtifact( ArtName, ArtId );
        release( MyRegion )[ artifact_id( ArtId ) ].

-!release( ArtName )
    <-  .print( "Cannot release ", ArtName ).

find_path( Start, Target, Path ) :- find_path_recursive( Start, Target, [ Start ], Path ).

find_path_recursive( Target, Target, Visited, Visited ).
find_path_recursive( Current, Target, Visited, Path ) :- ( po( Current, Next ) | ec( Current, Next ) ) & not .member( Next, Visited ) & find_path_recursive( Next, Target, [ Next | Visited ], Path ).

{ include("$jacamoJar/templates/common-cartago.asl") }
{ include("$jacamoJar/templates/common-moise.asl") }
