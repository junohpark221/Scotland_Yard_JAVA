package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import ch.qos.logback.core.net.SyslogOutputStream;
import com.google.common.collect.ImmutableSet;
import com.sun.javafx.iio.ImageLoadListener;
import javafx.collections.SetChangeListener;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;
import uk.ac.bris.cs.scotlandyard.ui.controller.BoardPlayers;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

	// Declaration of all used class variables.
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private ArrayList<ScotlandYardPlayer> configurations = new ArrayList<>();
	private ArrayList<Spectator> spectators = new ArrayList<>();
	private Set<Integer> set = new HashSet<>();
	private Set<Colour> colset = new HashSet<>();
	private boolean mrxwin = false;
	private boolean mrxloss = false;
	private int currentplayer = 0;
	private int round = 0;
	private int mrxlastlocation = 0;
	private Set<Move> moves = new HashSet<>();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
							 PlayerConfiguration mrX, PlayerConfiguration firstDetective,
							 PlayerConfiguration... restOfTheDetectives) {

		// Checking for a null rounds variable being used, throw error if needed.
		this.rounds = requireNonNull(rounds);
		if (rounds.isEmpty()) {
			throw new IllegalArgumentException("Empty Rounds!");
		}

		// Checking for a null graph being used to initialise, throw error if needed.
		this.graph = requireNonNull(graph);
		if (graph.isEmpty()) {
			throw new IllegalArgumentException("Empty Graph!");
		}

		// Add all players into the configurations list to allow for iterability.
		configurations.add(0, new ScotlandYardPlayer(mrX.player, mrX.colour, mrX.location, mrX.tickets));
		configurations.add(1, new ScotlandYardPlayer(firstDetective.player, firstDetective.colour, firstDetective.location, firstDetective.tickets));
		for (PlayerConfiguration configuration : restOfTheDetectives) {
			configurations.add(new ScotlandYardPlayer(configuration.player, configuration.colour, configuration.location, configuration.tickets));
		}

		// Test for null MrX being used, throw error if needed.
		ScotlandYardPlayer mrX1 = requireNonNull(configurations.get(0));
		if (mrX1.colour() != BLACK) {
			throw new IllegalArgumentException("mrX must be BLACK!");
		}

		// Add players to location set and colour set, checking for duplicates and throwing error if needed, also performing checks for illegal tickets.
		for (ScotlandYardPlayer configuration : configurations) {
			if (set.contains(configuration.location()))
				throw new IllegalArgumentException("Duplicate location!");
			set.add(configuration.location());
			if (colset.contains(configuration.colour()))
				throw new IllegalArgumentException("Duplicate colour!");
			colset.add(configuration.colour());
			if (!(configuration.tickets().containsKey(TAXI) && configuration.tickets().containsKey(BUS) && configuration.tickets().containsKey(UNDERGROUND) && configuration.tickets().containsKey(SECRET) && configuration.tickets().containsKey(DOUBLE))) {
				throw new IllegalArgumentException("Invalid player tickets!");
			}
			boolean taxidef = configuration.tickets().containsKey(TAXI);
			boolean busdef = configuration.tickets().containsKey(BUS);
			boolean underdef = configuration.tickets().containsKey(UNDERGROUND);
			boolean secdef = configuration.tickets().containsKey(SECRET);
			boolean doubledef = configuration.tickets().containsKey(DOUBLE);
			if (!taxidef || !busdef || !underdef || !secdef || !doubledef) {
				throw new IllegalArgumentException("Invalid Player tickets!");
			}
			if (configuration.isDetective()){
				if ((configuration.hasTickets(DOUBLE) || configuration.hasTickets(SECRET))) {
					throw new IllegalArgumentException("Invalid detective tickets!");
				}
			}
		}

		// Performs a check to see if the game is over.
		checkGameOver(0);
	}

	// Checks if the given node has a detective on it.
	public boolean nodehasdetective (int location) {
		boolean x = false;
		for (ScotlandYardPlayer p : configurations) {
			if (p.isDetective()) {
				if (p.location() == location) {
					x = true;
				}
			}
		}
		return x;
	}

	// Populates a list of moves with all legal moves of both types depending on the players tickets and position.
	public Set<Move> populatemoves (ScotlandYardPlayer p) {
		Graph<Integer, Transport> g = getGraph();
		for (Edge<Integer, Transport> a : g.getEdgesFrom(g.getNode(p.location()))) {
			if (p.hasTickets(fromTransport(a.data()))) {
				if (!nodehasdetective(a.destination().value())) {
					moves.add(new TicketMove(p.colour(), fromTransport(a.data()), a.destination().value()));
				}
			}
		}
		if (p.isMrX()) {
			for (Edge<Integer, Transport> a : g.getEdgesFrom(g.getNode(p.location()))) {
				if (p.hasTickets(SECRET) && !nodehasdetective(a.destination().value())) {
					moves.add(new TicketMove(p.colour(), SECRET, a.destination().value()));
				}
			}
			if (p.hasTickets(DOUBLE) && (round < rounds.size() - 1)) {
				int one;
				int two;
				Ticket first;
				Ticket second;
				for (Edge<Integer, Transport> a : g.getEdgesFrom(g.getNode(p.location()))) {
					if (p.hasTickets(fromTransport(a.data())) && !nodehasdetective(a.destination().value())) {
						one = a.destination().value();
						first = fromTransport(a.data());
						for (Edge<Integer, Transport> b : g.getEdgesFrom(g.getNode(one))) {
							if (p.hasTickets(fromTransport(b.data())) && !nodehasdetective(b.destination().value())) {
								two = b.destination().value();
								if (fromTransport(b.data()).equals(first)) {
									if (p.hasTickets(first,2)) {
										second = fromTransport(b.data());
										moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
									}
								}
								else {
									second = fromTransport(b.data());
									moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
								}
								if (p.hasTickets(SECRET)) {
									second = SECRET;
									moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
								}
							}
							else if (p.hasTickets(SECRET) && !nodehasdetective(b.destination().value())) {
								two = b.destination().value();
								second = fromTransport(b.data());
								moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
							}
						}
						if (p.hasTickets(SECRET)) {
							first =SECRET;
							for (Edge<Integer, Transport> b : g.getEdgesFrom(g.getNode(one))) {
								if (p.hasTickets(fromTransport(b.data())) && !nodehasdetective(b.destination().value())) {
									two = b.destination().value();
									second = fromTransport(b.data());
									moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
									if (p.hasTickets(SECRET,2)) {
										second = SECRET;
										moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
									}
								}
								else if (p.hasTickets(SECRET,2) && !nodehasdetective(b.destination().value())) {
									two = b.destination().value();
									second = SECRET;
									moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
								}
							}
						}
					}
					else if (p.hasTickets(SECRET) && !nodehasdetective(a.destination().value())) {
						one = a.destination().value();
						first = SECRET;
						for (Edge<Integer, Transport> b : g.getEdgesFrom(g.getNode(one))) {
							if (p.hasTickets(fromTransport(b.data())) && !nodehasdetective(b.destination().value())) {
								two = b.destination().value();
								second = fromTransport(b.data());
								moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
								if (p.hasTickets(SECRET, 2)) {
									second = SECRET;
									moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
								}
							}
							else if (p.hasTickets(SECRET, 2) && !nodehasdetective(b.destination().value())) {
								two = b.destination().value();
								second = SECRET;
								moves.add(new DoubleMove(p.colour(), new TicketMove(p.colour(), first, one), new TicketMove(p.colour(), second, two)));
							}
						}
					}
				}
			}
		}
		return ImmutableSet.copyOf(moves);
	}

	// Concrete Visitor for a ticketmove move type.
	@Override
	public void visit (TicketMove m) {
		System.out.println("visit");
		configurations.get(currentplayer).removeTicket(m.ticket());
		configurations.get(currentplayer).location(m.destination());
		System.out.println("HERE");
		if (configurations.get(currentplayer).isDetective()) {
			configurations.get(0).addTicket(m.ticket());
		}

	}

	// Concrete Visitor for a Doublemove, notifies all spectators appropriately.
	@Override
	public void visit (DoubleMove m) {
		System.out.println(m.firstMove().destination());
		visit(m.firstMove());
		round++;
		rectifier2();
		for (Spectator s : spectators) {
			s.onRoundStarted(this, round);
			if (getRounds().get(round - 1)) {
				s.onMoveMade(this, m.firstMove());
			}
			else {
				s.onMoveMade(this, new TicketMove(m.colour(), m.firstMove().ticket(), mrxlastlocation));
			}
		}
		rectifier1();
		visit(m.secondMove());
		round++;
		rectifier2();
		for (Spectator s : spectators) {
			s.onRoundStarted(this, round);
			if (getRounds().get(round - 1)) {
				s.onMoveMade(this, m.secondMove());
			}
			else if (getRounds().get(round - 2)) {
				s.onMoveMade(this, new TicketMove(m.colour(), m.secondMove().ticket(), m.firstMove().destination()));
			}
			else {
				s.onMoveMade(this, new TicketMove(m.colour(), m.secondMove().ticket(), mrxlastlocation));
			}
		}
		rectifier1();
	}

	// Concrete Visitor for a Passmove move type, does not need to do anything.
	@Override
	public void visit (PassMove m) {
		System.out.println("Move passed!");
	}

	// Function to decrement the round number safely.
	private void rectifier1 () {
		if (currentplayer == 0) {
			currentplayer = configurations.size() - 1;
		}
		else {
			currentplayer--;
		}
	}

	//Function to increment the round safely.
	private void rectifier2 () {
		currentplayer = (currentplayer + 1) % configurations.size();
	}

	// Tests a transport for its type and returns it.
	public Ticket transportInEdge (Transport t) {
		if (t == Transport.BUS) {
			return BUS;
		}
		else if (t == Transport.TAXI) {
			return TAXI;
		}
		else if (t == Transport.UNDERGROUND) {
			return UNDERGROUND;
		}
		else {
			return SECRET;
		}
	}

	// Logic to check if the game is over and to adjust appropriate class variables to reflect the current state.
	public void checkGameOver(int i) {
		Graph<Integer, Transport> g = getGraph();
		if (configurations.get(0).location() == configurations.get(currentplayer % configurations.size()).location() && currentplayer != 0) {
			mrxloss = true;
			mrxwin = false;
		}
		int a = 0, b = 0, c = 0;
		for(ScotlandYardPlayer p : configurations) {
			if (p.isDetective()) {
				if (!p.hasTickets(BUS) && !p.hasTickets(TAXI) && !p.hasTickets(UNDERGROUND)) {
					c++;
				}
				if (c >= configurations.size() - 1) {
					mrxwin = true;
					mrxloss = false;
				}
			}
		}
		if ((i == 1) && currentplayer % configurations.size() == configurations.size() - 1) {
			for (Edge<Integer, Transport> e : g.getEdgesFrom(g.getNode(configurations.get(0).location()))) {
				if (nodehasdetective(e.destination().value()) || (!(configurations.get(0).hasTickets(transportInEdge(e.data())) || (configurations.get(0).hasTickets(SECRET))))) {
					a++;
				}
				b++;
			}
			if (a == b) {
				mrxloss = true;
				mrxwin = false;
			}
		}
		if (currentplayer == configurations.size() - 1 && getCurrentRound() == rounds.size()) {
			mrxloss = false;
			mrxwin = true;
		}
	}

	// Accept method to facilitate the callback of the makemove command in the consumer pattern, also handles the notification of spectators appropriately in conjunction with the Doublemove visitor. Finally testing for a gameover state.
	@Override
	public void accept(Move m) {
		requireNonNull(m);
		boolean moveillegitimate = populatemoves(configurations.get(currentplayer % configurations.size())).contains(m);
		if (!moveillegitimate) {
			throw new IllegalArgumentException("Invalid move selected!");
		}
		moves.clear();
		rectifier2();
		System.out.println("---------------------------");
		if(spectators.isEmpty()){
			if(m instanceof DoubleMove){
				configurations.get(0).removeTicket(DOUBLE);
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
			}
			else if(currentplayer == 1){
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
				round++;
			}
			else{
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
			}
		}
		else{
			System.out.println(currentplayer);
			System.out.println(round);
			System.out.println("---------------------------");
			if (m instanceof DoubleMove) {
				System.out.println("CHOICE 1");
				configurations.get(0).removeTicket(DOUBLE);
				for (Spectator s : spectators) {
					if (getRounds().get(round) && getRounds().get(round + 1)) {
						s.onMoveMade(this, m);
					} else if (getRounds().get(round) && !getRounds().get(round + 1)) {
						s.onMoveMade(this, new DoubleMove(m.colour(), ((DoubleMove) m).firstMove(), new TicketMove(m.colour(), ((DoubleMove) m).secondMove().ticket(), ((DoubleMove) m).firstMove().destination())));
					} else if (!getRounds().get(round) && getRounds().get(round + 1)) {
						s.onMoveMade(this, new DoubleMove(m.colour(), new TicketMove(m.colour(), ((DoubleMove) m).firstMove().ticket(), mrxlastlocation), ((DoubleMove) m).secondMove()));
					} else {
						s.onMoveMade(this, new DoubleMove(m.colour(), new TicketMove(m.colour(), ((DoubleMove) m).firstMove().ticket(), mrxlastlocation), new TicketMove(m.colour(), ((DoubleMove) m).secondMove().ticket(), mrxlastlocation)));
					}
				}
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
			}
			else if(currentplayer == 1){
				System.out.println("CHOICE 2");
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
				round++;
				for (Spectator s : spectators) {
					s.onRoundStarted(this, round);
					if (getRounds().get(round - 1)) {
						s.onMoveMade(this, m);
					} else {
						s.onMoveMade(this, new TicketMove(m.colour(), ((TicketMove) m).ticket(), mrxlastlocation));
					}
				}
			}
			else {
				System.out.println("CHOICE 3");
				rectifier1();
				m.visit(this);
				checkGameOver(1);
				rectifier2();
				for (Spectator s : spectators) {
					s.onMoveMade(this, m);
				}
			}
		}
		rectifier1();
		currentplayer = (currentplayer + 1) % configurations.size();
		System.out.println("---------END-----------");
		if (isGameOver()) {
			for (Spectator s :spectators) {
				s.onGameOver(this, getWinningPlayers());
			}
		}
		else if (currentplayer != 0) {
			startRotate();
		}
		else if (!isGameOver()) {
			for (Spectator s : spectators) {
				s.onRotationComplete(this);
			}
		}
	}

	// Registers a spectator to the list of spectators eligible for notifications, checks against duplicates.
	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (!spectators.contains(spectator)) {
			spectators.add(spectator);
		}
		else {
			throw new IllegalArgumentException("Spectator already added!");
		}
	}

	// Removes an already registered spectator from the list of spectators eligible for notifications.
	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if (spectators.contains(spectator)) {
			spectators.remove(spectator);
		}
		else {
			throw new IllegalArgumentException("Spectator does not exist!");
		}
	}

	// The main engine of the game that handles the first half of the move cycle ands hands back to accept() via the consumer pattern callback mechanism. Also adds passmoves to players if no move possible.
	@Override
	public void startRotate() {
		if(round == 0 && isGameOver()){
			throw new IllegalStateException("Game is already over");
		}
		ScotlandYardPlayer p = configurations.get(currentplayer % configurations.size());
		populatemoves(p);
		if (moves.isEmpty() && p.isDetective()) {
			moves.add(new PassMove(p.colour()));
		}
		if (moves.isEmpty() && p.isMrX()) {
			mrxloss = true;
		}
		if (currentplayer % configurations.size() == 0 && !p.isMrX()) {
			throw new NoSuchElementException("MrX Must Play First!");
		}
		p.player().makeMove(this, p.location(), moves, this);
	}

	// Returns an immutable copy of the current list of spectators.
	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(spectators);
	}

	// Returns an immutable version of the list of all players by colour.
	@Override
	public List<Colour> getPlayers() {
		List<Colour> newcolset = new ArrayList<>();
		for (ScotlandYardPlayer p : configurations) {
			newcolset.add(p.colour());
		}
		return Collections.unmodifiableList(newcolset);
	}

	// Returns an immutable set of all winning players.
	@Override
	public Set<Colour> getWinningPlayers() {
		Set<Colour> winners = new HashSet<>();
		if (mrxwin) {
			winners.add(BLACK);
		}
		if (mrxloss) {
			for (ScotlandYardPlayer p : configurations) {
				if (p.isDetective()) {
					winners.add(p.colour());
				}
			}
		}
		return Collections.unmodifiableSet(winners);
	}

	// Returns the Optional location of the requested player, if MrX must be concealed, returns empty.
	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		requireNonNull(colour);
		if (!colset.contains(colour)) {
			return Optional.empty();
		}
		if (colour.equals(BLACK)) {
			if(round == 0){
				return Optional.of(mrxlastlocation);
			}
			else{
				if(getCurrentRound() > rounds.size()) {
					round = getCurrentRound() % rounds.size();
				}
				if(getRounds().get(getCurrentRound() - 1)) {
					mrxlastlocation = configurations.get(0).location();
					return Optional.of(configurations.get(0).location());
				}
				else{
					return Optional.of(mrxlastlocation);
				}
			}
		}
		else {
			for (ScotlandYardPlayer p : configurations) {
				if (p.colour().equals(colour)) {
					return Optional.of(p.location());
				}
			}
		}
		return Optional.empty();
	}

	// Returns the Optional number of the specified type of ticket a specified player by colour has, unless 0 then returns empty.
	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		requireNonNull(colour);
		requireNonNull(ticket);
		for (ScotlandYardPlayer p : configurations) {
			if (p.colour().equals(colour)) {
				return Optional.of(p.tickets().get(ticket));
			}
		}
		return Optional.empty();
	}

	// Boolean flag to display if the game is over according to the class variables.
	@Override
	public boolean isGameOver() {
		boolean over = false;
		if (mrxloss || mrxwin) {
			over = true;
		}
		return over;
	}

	// Returns the colour of the current player.
	@Override
	public Colour getCurrentPlayer() {
		// TODO
		return configurations.get(currentplayer).colour();
	}

	// Returns the current round number.
	@Override
	public int getCurrentRound() {
		return round;
	}

	// Returns an immutable version of the Boolean rounds list that dictates whether MrX is revealed or not.
	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	// Returns an immutable version of the game's graph of nodes and edges.
	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph<Integer, Transport>(graph);
	}

}

// Arun Steward & Junoh Park 2019.