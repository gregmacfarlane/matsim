/* *********************************************************************** *
 * project: org.matsim.*
 * Dijkstra.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.priorityqueue.WrappedBinaryMinHeap;
import org.matsim.core.router.util.DijkstraNodeData;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.RouterPriorityQueue;
import org.matsim.core.utils.misc.Time;
import org.matsim.vehicles.Vehicle;


/**
 * Implementation of <a
 * href="http://en.wikipedia.org/wiki/Dijkstra%27s_algorithm">Dijkstra's
 * shortest-path algorithm</a> on a time-dependent network with arbitrary
 * non-negative cost functions (e.g. negative link cost are not allowed). So
 * 'shortest' in our context actually means 'least-cost'.
 *
 * <p>
 * For every router, there exists a class which computes some preprocessing data
 * and is passed to the router class constructor in order to accelerate the
 * routing procedure. The one used for Dijkstra is
 * {@link org.matsim.core.router.util.PreProcessDijkstra}.
 * </p>
 * <br>
 *
 * <h2>Code example:</h2>
 * <p>
 * <code>PreProcessDijkstra preProcessData = new PreProcessDijkstra();<br>
 * preProcessData.run(network);<br>
 * TravelCost costFunction = ...<br>
 * LeastCostPathCalculator routingAlgo = new Dijkstra(network, costFunction, preProcessData);<br>
 * routingAlgo.calcLeastCostPath(fromNode, toNode, startTime);</code>
 * </p>
 * <p>
 * If you don't want to preprocess the network, you can invoke Dijkstra as
 * follows:
 * </p>
 * <p>
 * <code> LeastCostPathCalculator routingAlgo = new Dijkstra(network, costFunction);</code>
 * </p>
 * 
 * <h2>Important note</h2>
 * This class is NOT thread-safe!
 *
 * @see org.matsim.core.router.util.PreProcessDijkstra
 * @see org.matsim.core.router.AStarEuclidean
 * @see org.matsim.core.router.AStarLandmarks
 * @author lnicolas
 * @author mrieser
 */
public class Dijkstra implements IntermodalLeastCostPathCalculator {

	private final static Logger log = Logger.getLogger(Dijkstra.class);

	/**
	 * The network on which we find routes.
	 */
	protected Network network;

	/**
	 * The cost calculator. Provides the cost for each link and time step.
	 */
	final TravelDisutility costFunction;

	/**
	 * The travel time calculator. Provides the travel time for each link and time step.
	 */
	final TravelTime timeFunction;

	final HashMap<Id, DijkstraNodeData> nodeData;

	/**
	 * Provides an unique id (loop number) for each routing request, so we don't
	 * have to reset all nodes at the beginning of each re-routing but can use the
	 * loop number instead.
	 */
	private int iterationID = Integer.MIN_VALUE + 1;

	/**
	 * Temporary field that is only used if dead ends are being pruned during
	 * routing and is updated each time a new route has to be calculated.
	 */
	private Node deadEndEntryNode;

	/**
	 * Determines whether we should mark nodes in dead ends during a
	 * pre-processing step so they won't be expanded during routing.
	 */
	/*package*/ final boolean pruneDeadEnds;

	/**
	 * Comparator that defines how to order the nodes in the pending nodes queue
	 * during routing.
	 */

	private final PreProcessDijkstra preProcessData;


	private String[] modeRestriction = null;
	
	private Person person = null;
	private Vehicle vehicle = null;

	/**
	 * Default constructor.
	 *
	 * @param network
	 *            The network on which to route.
	 * @param costFunction
	 *            Determines the link cost defining the cheapest route.
	 * @param timeFunction
	 *            Determines the travel time on links.
	 */
	public Dijkstra(final Network network, final TravelDisutility costFunction, final TravelTime timeFunction) {
		this(network, costFunction, timeFunction, null);
	}

	/**
	 * Constructor.
	 *
	 * @param network
	 *            The network on which to route.
	 * @param costFunction
	 *            Determines the link cost defining the cheapest route.
	 * @param timeFunction
	 *            Determines the travel time on each link.
	 * @param preProcessData
	 *            The pre processing data used during the routing phase.
	 */
	public Dijkstra(final Network network, final TravelDisutility costFunction, final TravelTime timeFunction,
			final PreProcessDijkstra preProcessData) {

		this.network = network;
		this.costFunction = costFunction;
		this.timeFunction = timeFunction;
		this.preProcessData = preProcessData;

		this.nodeData = new HashMap<Id, DijkstraNodeData>((int)(network.getNodes().size() * 1.1), 0.95f);

		if (preProcessData != null) {
			if (preProcessData.containsData() == false) {
				this.pruneDeadEnds = false;
				log.warn("The preprocessing data provided to router class Dijkstra contains no data! Please execute its run(...) method first!");
				log.warn("Running without dead-end pruning.");
			} else {
				this.pruneDeadEnds = true;
			}
		} else {
			this.pruneDeadEnds = false;
		}
	}

	@Override
	public void setModeRestriction(final Set<String> modeRestriction) {
		if (modeRestriction == null) {
			this.modeRestriction = null;
		} else {
			this.modeRestriction = modeRestriction.toArray(new String[modeRestriction.size()]);
		}
	}

	/**
	 * Calculates the cheapest route from Node 'fromNode' to Node 'toNode' at
	 * starting time 'startTime'.
	 *
	 * @param fromNode
	 *            The Node at which the route should start.
	 * @param toNode
	 *            The Node at which the route should end.
	 * @param startTime
	 *            The time at which the route should start. <i>Note:</i> Using
	 *            {@link Time#UNDEFINED_TIME} does not imply "time is not relevant",
	 *            rather, {@link Path#travelTime} will return {@link Double#NaN}.
	 * @see org.matsim.core.router.util.LeastCostPathCalculator#calcLeastCostPath(org.matsim.core.network.Node,
	 *      org.matsim.core.network.Node, double)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Path calcLeastCostPath(final Node fromNode, final Node toNode, final double startTime, final Person person, final Vehicle vehicle) {

		augmentIterationId(); // this call makes the class not thread-safe
		this.person = person;
		this.vehicle = vehicle;

		if (this.pruneDeadEnds == true) {
			this.deadEndEntryNode = getPreProcessData(toNode).getDeadEndEntryNode();
		}

		RouterPriorityQueue<Node> pendingNodes = (RouterPriorityQueue<Node>) createRouterPriorityQueue();
		initFromNode(fromNode, toNode, startTime, pendingNodes);

		Node foundToNode = searchLogic(fromNode, toNode, pendingNodes);
		
		if (foundToNode == null) return null;
		else {
			DijkstraNodeData outData = getData(foundToNode);
			double arrivalTime = outData.getTime();
			
			// now construct and return the path
			return constructPath(fromNode, foundToNode, startTime, arrivalTime);			
		}
	}

	/**
	 * Allow replacing the RouterPriorityQueue.
	 */
	/*package*/ RouterPriorityQueue<? extends Node> createRouterPriorityQueue() {
		/*
		 * The (Wrapped)BinaryMinHeap replaces the PseudoRemovePriorityQueue as default
		 * PriorityQueue. It offers a decreaseKey method and uses less memory. As a result,
		 * the routing performance is increased by ~20%.
		 * 
		 * When an ArrayRoutingNetwork is used, an BinaryMinHeap can be used which uses
		 * the getArrayIndex() method of the ArrayRoutingNetworkNodes which further reduces
		 * the memory consumption and increases the performance by another ~10%.
		 */
		return new WrappedBinaryMinHeap<Node>(this.network.getNodes().size());
	}
	
	/**
	 * Logic that was previously located in the calcLeastCostPath(...) method.
	 * Can be overwritten in the MultiModalDijkstra.
	 * Returns the last node of the path. By default this is the to-node.
	 * The MultiNodeDijkstra returns the cheapest of all given to-nodes.
	 */
	/*package*/ Node searchLogic(final Node fromNode, final Node toNode, final RouterPriorityQueue<Node> pendingNodes) {
		
		boolean stillSearching = true;
		
		while (stillSearching) {
			Node outNode = pendingNodes.poll();

			if (outNode == null) {
				log.warn("No route was found from node " + fromNode.getId() + " to node " + toNode.getId());
				return null;
			}

			if (outNode == toNode) {
				stillSearching = false;
			} else {
				relaxNode(outNode, toNode, pendingNodes);
			}
		}
		return toNode;
	}
	
	/**
	 * Constructs the path after the algorithm has been run.
	 *
	 * @param fromNode
	 *            The node where the path starts.
	 * @param toNode
	 *            The node where the path ends.
	 * @param startTime
	 *            The time when the trip starts.
	 * @param preProcessData
	 *            The time when the trip ends.
	 */
	protected Path constructPath(Node fromNode, Node toNode, double startTime, double arrivalTime) {
		ArrayList<Node> nodes = new ArrayList<Node>();
		ArrayList<Link> links = new ArrayList<Link>();

		nodes.add(0, toNode);
		Link tmpLink = getData(toNode).getPrevLink();
		if (tmpLink != null) {
			while (tmpLink.getFromNode() != fromNode) {
				links.add(0, tmpLink);
				nodes.add(0, tmpLink.getFromNode());
				tmpLink = getData(tmpLink.getFromNode()).getPrevLink();
			}
			links.add(0, tmpLink);
			nodes.add(0, tmpLink.getFromNode());
		}

		DijkstraNodeData toNodeData = getData(toNode);
		Path path = new Path(nodes, links, arrivalTime - startTime, toNodeData.getCost());

		return path;
	}
	
	/**
	 * Initializes the first node of a route.
	 *
	 * @param fromNode
	 *            The Node to be initialized.
	 * @param toNode
	 *            The Node at which the route should end.
	 * @param startTime
	 *            The time we start routing.
	 * @param pendingNodes
	 *            The pending nodes so far.
	 */
	/*package*/ void initFromNode(final Node fromNode, final Node toNode, final double startTime,
			final RouterPriorityQueue<Node> pendingNodes) {
		DijkstraNodeData data = getData(fromNode);
		visitNode(fromNode, data, pendingNodes, startTime, 0, null);
	}

	/**
	 * Expands the given Node in the routing algorithm; may be overridden in
	 * sub-classes.
	 *
	 * @param outNode
	 *            The Node to be expanded.
	 * @param toNode
	 *            The target Node of the route.
	 * @param pendingNodes
	 *            The set of pending nodes so far.
	 */
	protected void relaxNode(final Node outNode, final Node toNode, final RouterPriorityQueue<Node> pendingNodes) {

		DijkstraNodeData outData = getData(outNode);
		double currTime = outData.getTime();
		double currCost = outData.getCost();
		if (this.pruneDeadEnds) {
			PreProcessDijkstra.DeadEndData ddOutData = getPreProcessData(outNode);

			for (Link l : outNode.getOutLinks().values()) {
				relaxNodeLogic(l, pendingNodes, currTime, currCost, toNode, ddOutData);
			}
		} else { // this.pruneDeadEnds == false
			for (Link l : outNode.getOutLinks().values()) {
				relaxNodeLogic(l, pendingNodes, currTime, currCost, toNode, null);
			}				
		}
	}
	
	/**
	 * Logic that was previously located in the relaxNode(...) method. 
	 * By doing so, the FastDijkstra can overwrite relaxNode without copying the logic. 
	 */
	/*package*/ void relaxNodeLogic(final Link l, final RouterPriorityQueue<Node> pendingNodes,
			final double currTime, final double currCost, final Node toNode,
			final PreProcessDijkstra.DeadEndData ddOutData) {
		if (this.pruneDeadEnds) {
			if (canPassLink(l)) {
				Node n = l.getToNode();
				PreProcessDijkstra.DeadEndData ddData = getPreProcessData(n);

				/* IF the current node n is not in a dead end
				 * OR it is in the same dead end as the fromNode
				 * OR it is in the same dead end as the toNode
				 * THEN we add the current node to the pending nodes */
				if ((ddData.getDeadEndEntryNode() == null)
						|| (ddOutData.getDeadEndEntryNode() != null)
						|| ((this.deadEndEntryNode != null)
								&& (this.deadEndEntryNode.getId() == ddData.getDeadEndEntryNode().getId()))) {
					
					addToPendingNodes(l, n, pendingNodes, currTime, currCost, toNode);
				}
			}
		} else {
			if (canPassLink(l)) {
				addToPendingNodes(l, l.getToNode(), pendingNodes, currTime, currCost, toNode);
			}
		}
	}
	
	/**
	 * Adds some parameters to the given Node then adds it to the set of pending
	 * nodes.
	 *
	 * @param l
	 *            The link from which we came to this Node.
	 * @param n
	 *            The Node to add to the pending nodes.
	 * @param pendingNodes
	 *            The set of pending nodes.
	 * @param currTime
	 *            The time at which we started to traverse l.
	 * @param currCost
	 *            The cost at the time we started to traverse l.
	 * @param toNode
	 *            The target Node of the route.
	 * @return true if the node was added to the pending nodes, false otherwise
	 * 		(e.g. when the same node already has an earlier visiting time).
	 */
	protected boolean addToPendingNodes(final Link l, final Node n,
			final RouterPriorityQueue<Node> pendingNodes, final double currTime,
			final double currCost, final Node toNode) {

		double travelTime = this.timeFunction.getLinkTravelTime(l, currTime, person, vehicle);
		double travelCost = this.costFunction.getLinkTravelDisutility(l, currTime, this.person, this.vehicle);
		DijkstraNodeData data = getData(n);
		double nCost = data.getCost();
		if (!data.isVisited(getIterationId())) {
			visitNode(n, data, pendingNodes, currTime + travelTime, currCost
					+ travelCost, l);
			return true;
		}
		double totalCost = currCost + travelCost;
		if (totalCost < nCost) {
			revisitNode(n, data, pendingNodes, currTime + travelTime, totalCost, l);
			return true;
		}

		return false;
	}

	/**
	 * @param link
	 * @return <code>true</code> if the link can be passed with respect to a possible mode restriction set
	 *
	 * @see #setModeRestriction(Set)
	 */
	protected boolean canPassLink(final Link link) {
		if (this.modeRestriction == null) {
			return true;
		}
		for (String mode : this.modeRestriction) {
			if (link.getAllowedModes().contains(mode)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Changes the position of the given Node n in the pendingNodes queue and
	 * updates its time and cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The link from which we came visiting n.
	 */
	void revisitNode(final Node n, final DijkstraNodeData data,
			final RouterPriorityQueue<Node> pendingNodes, final double time, final double cost,
			final Link outLink) {
		data.visit(outLink, cost, time, getIterationId());
		pendingNodes.decreaseKey(n, getPriority(data));
	}

	/**
	 * Inserts the given Node n into the pendingNodes queue and updates its time
	 * and cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The node from which we came visiting n.
	 */
	protected void visitNode(final Node n, final DijkstraNodeData data,
			final RouterPriorityQueue<Node> pendingNodes, final double time, final double cost,
			final Link outLink) {
		data.visit(outLink, cost, time, getIterationId());
		pendingNodes.add(n, getPriority(data));
	}

	/**
	 * Augments the iterationID and checks whether the visited information in
	 * the nodes in the nodes have to be reset.
	 */
	protected void augmentIterationId() {
		if (getIterationId() == Integer.MAX_VALUE) {
			this.iterationID = Integer.MIN_VALUE + 1;
			resetNetworkVisited();
		} else {
			this.iterationID++;
		}
	}

	/**
	 * @return iterationID
	 */
	/*package*/ int getIterationId() {
		return this.iterationID;
	}

	/**
	 * Resets all nodes in the network as if they have not been visited yet.
	 */
	private void resetNetworkVisited() {
		for (Node node : this.network.getNodes().values()) {
			DijkstraNodeData data = getData(node);
			data.resetVisited();
		}
	}

	/**
	 * The value used to sort the pending nodes during routing.
	 * This implementation compares the total effective travel cost
	 * to sort the nodes in the pending nodes queue during routing.
	 */
	protected double getPriority(final DijkstraNodeData data) {
		return data.getCost();
	}

	/**
	 * Returns the data for the given node. Creates a new NodeData if none exists
	 * yet.
	 *
	 * @param n
	 *            The Node for which to return the data.
	 * @return The data for the given Node
	 */
	protected DijkstraNodeData getData(final Node n) {
		DijkstraNodeData r = this.nodeData.get(n.getId());
		if (null == r) {
			r = new DijkstraNodeData();
			this.nodeData.put(n.getId(), r);
		}
		return r;
	}

	protected PreProcessDijkstra.DeadEndData getPreProcessData(final Node n) {
		return this.preProcessData.getNodeData(n);
	}

	protected final Person getPerson() {
		return this.person;
	}

	protected final void setPerson(final Person person) {
		this.person = person;
	}
	
	protected final Vehicle getVehicle() {
		return this.vehicle;
	}
	
}
