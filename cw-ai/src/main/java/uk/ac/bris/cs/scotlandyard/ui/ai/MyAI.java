package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;

import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.model.*;

import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;

@ManagedAI("Vision")
public class MyAI implements PlayerFactory {

	@Override
	public Player createPlayer(Colour colour) {
		return new MyPlayer();
	}

	private static class MyPlayer implements Player {

		// Declaration of array to be used in the Floyd Warshall Algorithm.
		private int[][] dist = new int[200][200];

		// Makemove function selects the move that you wish to make and hands it back to the model via the accept method.
		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
							 Consumer<Move> callback) {

			// Declaration of the variables needed to make the choice of best move available.
			int detectivenumber = 1;
			int numberofdetectives = view.getPlayers().size() - 1;
			int distancebulider = 0;
			int finaldestination;
			Move greatestfound = new PassMove(Colour.BLACK);
			int greatestfound1 = -1;
			int valencyrectifier;
			int closetodetective = 0;
			int loopnumber = 0;
			floydwarshall(view);

			// Assesses the score for each move given.
			for (Move m : moves) {

				// Series of statements to extract the final destination of the move while maintaining readability.
				if (m instanceof DoubleMove) {
					finaldestination = ((DoubleMove) m).secondMove().destination();
				}
				else if (m instanceof TicketMove) {
					finaldestination = ((TicketMove) m).destination();
				}
				else {
					finaldestination = 0;
				}

				// Incorporates the distance to the detectives as a bias to different moves, forms an average distance of all detectives.
				while (detectivenumber <= numberofdetectives) {
					distancebulider += floydwarshallget((finaldestination), (view.getPlayerLocation(view.getPlayers().get(detectivenumber)))); //distance to mrx after move
					if(floydwarshallget((finaldestination), (view.getPlayerLocation(view.getPlayers().get(detectivenumber)))) < 2){ closetodetective++; }
					detectivenumber++;
				}

				// biases the chosen move towards nodes with high valency to facilitate escape if needed, stops MrX moving to one move from a detective.
				Graph<Integer, Transport> g = view.getGraph();
				valencyrectifier = g.getEdgesFrom(g.getNode(finaldestination)).size();
				if (greatestfound1 < distancebulider / numberofdetectives + valencyrectifier) {
					if(loopnumber == 0){
						greatestfound = m;
						greatestfound1 = distancebulider / numberofdetectives + valencyrectifier;
					}
					if(closetodetective == 0){
						greatestfound = m;
						greatestfound1 = distancebulider / numberofdetectives + valencyrectifier;
					}
				}
				detectivenumber = 1;
				distancebulider = 0;
				loopnumber++;
			}

			// Accept the best move that we could find in the list.
			callback.accept(greatestfound);
		}

		// Modified implementation of the Floyd Warshall distancing Algorithm, that produces a table of the shortest distances from every node to every other.
		private void floydwarshall (ScotlandYardView view) { // Adapted from https://www.geeksforgeeks.org/floyd-warshall-algorithm-dp-16/

			// Declaration of needed variables.
			int i, j, k;
			int V = 200;
			int max = 999999;
			int[][] graph = new int[V][V];

			// creation of a start point graph made from the graph from getgraph() that displays connected nodes.
			for (int t = 0; t < V; t++) {
				for (int y = 0; y < V; y++) {
					graph[t][y] = max;
				}
			}
			for (int q = 0; q < V; q++) {
				graph[q][q] = 0;
			}
			for (Edge<Integer, Transport> a : view.getGraph().getEdges()) {
				graph[a.destination().value()][a.source().value()] = 1;
				graph[a.source().value()][a.destination().value()] = 1;
			}
			for (i = 0; i < V; i++) {
				for (j = 0; j < V; j++) {
					dist[i][j] = graph[i][j];
				}
			}

			// Finds indirect paths that are improvements sand updates the dist array.
			for (k = 0; k < V; k++) {
				// Pick all vertices as source one by one
				for (i = 0; i < V; i++) {
					// Pick all vertices as destination for the
					// above picked source
					for (j = 0; j < V; j++) {
						// If vertex k is on the shortest path from
						// i to j, then update the value of dist[i][j]
						if (dist[i][k] + dist[k][j] < dist[i][j]) {
							dist[i][j] = dist[i][k] + dist[k][j];
						}
					}
				}
			}
		}

		// Lookup for the array to find the shortest path length between two places on the board.
		private int floydwarshallget (int mrxloc, Optional<Integer> detectloc) {
			if (detectloc.isEmpty()) {
				throw new IllegalArgumentException("Detective location concealed!");
			}
			return (dist[mrxloc][detectloc.get()]);
		}
	}
}

// Arun Steward & Junoh Park 2019
