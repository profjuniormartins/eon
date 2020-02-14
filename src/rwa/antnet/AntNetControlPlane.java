/*
 * Created on 20/12/2005.
 */
package rwa.antnet;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Vector;
import java.util.logging.Logger;

import antnet.AntPacket;
import antnet.NeighborAttr;
import antnet.StatisticalParametricModel;

import ops.Accounting;
import ops.Packet;
import random.MersenneTwister;
import rwa.Connection;
import rwa.Error;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.OpticalNode;
import rwa.RSVP;
import event.Event;
import graph.Edge;
import graph.Graph;
import main.Config;
import main.ControlPlane;
import main.Failure;
import main.Link;
import main.RoutingTableEntry;
import main.SimulationAccounting;

/**
 * This class is a control plane for the AntNet framework, but for the RWA
 * problem.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 * 
 */
public class AntNetControlPlane extends ControlPlane {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** Workaround for transporting static fields. */
	StaticTransporter transporter;
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String, AntNetColony> nodes;
	/** The set of links of this network. */
	private LinkedHashMap<String, Link> links;
	/** The accounting of the simulation results. */
	Accounting accounting;
	/** The number of retries when a LSP is disrupted. */
	protected int alternative;
	/** The delay for resending a path message for a re-routed LSP. */
	protected double holdoff;
	/** The rate for launching ants during the hold-off timer. */
	protected double rerouteAntRate;
	/** The maximum hop limit for a packet. */
	protected int hopLimit;
	/** The counter for identifying a request. */
	protected long counterLightpath = 0L;
	/** The time of the last event. */
	protected double lastTime;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
	/** Time necessary to localize a failure. */
	protected double faultLocalizationTime;
	/** The length in bytes for the identification of a link or a node. */
	protected int identificationLength;
	/**
	 * The collection of LSP disrupted by a failure, which are eligible for full
	 * re-routing.
	 */
	protected Hashtable<String, LightpathRequest> disruptedLSP;
	/** The collection of LSP successfully re-routed after a failure. */
	protected Hashtable<String, Connection> reroutedLSP;
	/** Random number generator for ants. */
	public static MersenneTwister rngAnt;
	/** The logging generator. */
	private static Logger logger = Logger.getLogger(AntNetControlPlane.class
			.getName());

	/**
	 * Creates a new AntNetControlPlane object.
	 * 
	 * @param aConfig
	 *            The XML configuration file.
	 * @param aAccounting
	 *            The accounting for the simulation results.
	 */
	public AntNetControlPlane(Config aConfig, SimulationAccounting aAccounting) {
		super(aConfig);
		this.accounting = (Accounting) aAccounting;
		// Get the simulation parameters
		Hashtable<String, Vector<String>> parameters = config
				.getSimulationParameters();
		// Get the links of this network
		links = config.getLinks();
		// Create the nodes of this network
		nodes = new LinkedHashMap<String, AntNetColony>();
		// Create the storage of disrutped connections by failure
		disruptedLSP = new Hashtable<String, LightpathRequest>();
		reroutedLSP = new Hashtable<String, Connection>();
		// Get the simulation parameters
		hopLimit = Integer.parseInt(parameters.get("/OPS/Hop/@limit")
				.firstElement());
		alternative = Integer.parseInt(parameters.get(
				"/RWA/Routing/@alternative").firstElement());
		holdoff = Double.parseDouble(parameters.get("/Ant/Holdoff/@timer")
				.firstElement());
		rerouteAntRate = Double.parseDouble(parameters.get(
				"/Ant/Holdoff/@antRate").firstElement());
		long seedAnt = Long.parseLong(parameters.get("/Ant/Seed/@value")
				.firstElement());
		rngAnt = new MersenneTwister(seedAnt);
		// The parametric model characteristics
		double exponentialFactor = Double.parseDouble(parameters.get(
				"/Ant/Parametric/@factor").firstElement());
		double reductor = Double.parseDouble(parameters.get(
				"/Ant/Parametric/@reductor").firstElement());
		// The pheromone routing table parameters
		double confidenceLevel = Double.parseDouble(parameters.get(
				"/Ant/Pheromone/@confidence").firstElement());
		double firstWeight = Double.parseDouble(parameters.get(
				"/Ant/Pheromone/@firstWeight").firstElement());
		double secondWeight = Double.parseDouble(parameters.get(
				"/Ant/Pheromone/@secondWeight").firstElement());
		double amplifier = Double.parseDouble(parameters.get(
				"/Ant/Pheromone/@amplifier").firstElement());
		// Gets the decay constant to correct the influence of the occupancy in
		// the buffer
		double decayConstant = Double.parseDouble(parameters.get(
				"/Ant/Criticality/@decay").firstElement());
		// Gets the correction (alfa) parameter for routing forward ants.
		double correction = Double.parseDouble(parameters.get(
				"/Ant/Routing/@correction").firstElement());
		// Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get(
				"/Outputs/Transient/@timeSlice").firstElement());
		actualTimeSlice = timeSlice;
		// Gets the time necessary for fault localization.
		faultLocalizationTime = Double.parseDouble(parameters.get(
				"/Failure/Timing/@localization").firstElement());
		identificationLength = Integer.parseInt(parameters.get(
				"/OPS/Hop/@bytes").firstElement());
		// Initialize the state of each node
		for (String id : graph.nodes()) {
			// Create the pheromone routing table for this node
			AntNetRoutingTable art = new AntNetRoutingTable(id,
					confidenceLevel, firstWeight, secondWeight, amplifier,
					correction, decayConstant);
			art.updateFromTopology(graph);
			// Create the local parametric view for all destinations of this
			StatisticalParametricModel model = new StatisticalParametricModel(
					id, graph, exponentialFactor, reductor);
			// Create the links adjacent to this node.
			LinkedHashMap<String, LinkState> antLinks = new LinkedHashMap<String, LinkState>();
			Vector<String> adjacent = graph.adjacentNodes(id);
			// for each adjacent node do
			for (String adjId : adjacent) {
				Link link = links.get(id + "-" + adjId);
				LinkState antLink = new LinkState(link);
				antLinks.put(adjId.toString(), antLink);
			}
			// Create the node and put it into the table.
			AntNetColony colony = new AntNetColony(id, art, antLinks, model,
					graph);
			nodes.put(id, colony);
		}
	}

	/**
	 * Updates the configuration of this control plane.
	 * 
	 * @param aConfig
	 *            The new configuration XML file instance.
	 */
	public void updateConfig(Config aConfig) {
		super.updateConfig(aConfig);
		alternative = Integer.parseInt(parameters.get(
				"/RWA/Routing/@alternative").firstElement());
		holdoff = Double.parseDouble(parameters.get("/Ant/Holdoff/@timer")
				.firstElement());
		rerouteAntRate = Double.parseDouble(parameters.get(
				"/Ant/Holdoff/@antRate").firstElement());
		timeSlice = Double.parseDouble(parameters.get(
				"/Outputs/Transient/@timeSlice").firstElement());
	}

	/**
	 * Process the specified event.
	 * 
	 * @param event
	 *            The event to be processed.
	 * @return The processed event. Null, if nothing else is to be returned to
	 *         the scheduler.
	 */
	@Override
	public Event process(Event event) {
		// The id of the processing node
		String id;
		// Update the time stamp of the last event to be processed processed.
		lastTime = event.getTimeStamp();
		// Do transient accounting, if applicable
		if (lastTime > actualTimeSlice) {
			// Update the actual time slice
			actualTimeSlice = actualTimeSlice + timeSlice;
			System.out.print(".");
			// Updates the transient accounting, if applicable
			try {
				Method updateInstantaneous = accounting.getClass().getMethod(
						"setInstantaneousValues", links.getClass());
				updateInstantaneous.invoke(accounting, links);
			} catch (Exception e) {
			} // do nothing - method not implemented
		}
		// For each event type
		switch (event.getType()) {
		case LIGHTPATH_REQUEST: // Lightpath request
			LightpathRequest request = (LightpathRequest) event.getContent();
			// Send RSVP Path message
			RSVP rsvpPath = new RSVP(request, hopLimit, counterLightpath);
			this.counterLightpath++; // Increment the counter
			// Create a new event for setting up the lightpath
			return new Event(event.getTimeStamp(), Event.Type.PACKET_ARRIVAL,
					rsvpPath);
		case LIGHTPATH_ESTABLISHED: // Lightpath established
			RSVP rsvpConfirm = (RSVP) event.getContent();
			// Gets the connection object.
			Connection connectionEst = (Connection) rsvpConfirm.getObject();
			// Gets the duration of the lightpath
			double duration = connectionEst.getRequest().getDuration();
			// Accounts the successfull lightpath establishment
			accounting.addSuccesful(rsvpConfirm);
			// See if it is a succesful re-routing of a failed LSP
			if (rsvpConfirm.isReRouting()) {
				reroutedLSP.put(rsvpConfirm.getFlowLabel(), connectionEst);
			}
			// System.out.println(event.toString());
			// Return a new event for tearing down the lightpath when
			// appropriate
			return new Event((event.getTimeStamp() + duration),
					Event.Type.LIGHTPATH_TEARDOWN, connectionEst);
		case LIGHTPATH_PROBLEM:
			RSVP rsvpErr = (RSVP) event.getContent();
			LightpathRequest lRequest;
			// Get the error status
			Error error = rsvpErr.getError();
			// Get the label
			int label = Integer.parseInt(rsvpErr.getFlowLabel());
			RSVP rsvpRetry = null; // new Rsvp message
			Error.Code errorCode = error.getErrorCode();
			// Allocation of wavelength contention problem
			if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {
				lRequest = (LightpathRequest) ((Connection) rsvpErr.getObject())
						.getRequest();
				// System.out.println("Contention: "+lRequest.toString());
				rsvpRetry = new RSVP(lRequest, hopLimit, label);
				if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {
					rsvpRetry.setReRouting(); // set the flag of re-routing
					// System.out.println(event.toString());
				}
				// Wavelength continuity constraint violated or no link
				// available, use alternate path
			} else if (errorCode.equals(Error.Code.RP_LABEL_SET)
					|| errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE)) {
				lRequest = (LightpathRequest) rsvpErr.getObject();
				lRequest.addTry(); // add a try to the counter of tries
				if (lRequest.tryAgain()
						&& disruptedLSP.containsKey(rsvpErr.getFlowLabel())) { // resend
																				// the
																				// request
																				// (only
																				// for
																				// rerouted
																				// LSP!)
					rsvpRetry = new RSVP(lRequest, hopLimit, label);
					rsvpRetry.setReRouting(); // set the flag of re-routing
					Vector<Event> multiple = new Vector<Event>();
					// add the hold-off timer for resending the message.
					multiple.add(new Event(holdoff + event.getTimeStamp(),
							Event.Type.PACKET_ARRIVAL, rsvpRetry));
					// System.out.println(event.toString());
					int times = (int) (holdoff * rerouteAntRate);
					double delay = 0.0; // Delay between two consecutive ants
					int bytesHop = Integer.parseInt(parameters.get(
							"/OPS/Hop/@bytes").firstElement());
					for (int i = 0; i < times; i++) {
						AntPacket ant = new AntPacket(lRequest.getSource(),
								lRequest.getTarget(), hopLimit, bytesHop);
						multiple.add(new Event(delay + event.getTimeStamp(),
								Event.Type.PACKET_ARRIVAL, ant));
						delay = delay + (1.0 / rerouteAntRate);
					}
					// Return the multiple events to the simulator
					return new Event(lastTime, Event.Type.MULTIPLE, multiple);
				} else {
					// Accounts the failed lightpath request
					accounting.addFailed(rsvpErr);
					// if (disruptedLSP.containsKey(rsvpErr.getFlowLabel()))
					// System.out.println("Failed:"+event.toString());
				}
				// LSP failure forward or backward
			} else if (errorCode.equals(Error.Code.LSP_FAILURE)) {
				Connection disrupted = (Connection) rsvpErr.getObject();
				// System.out.println("Disrupted: "+disrupted.toString());
				lRequest = (LightpathRequest) disrupted.getRequest();
				// reset the counter of retries
				lRequest.resetTry();
				// Needed for serialized simulations
				lRequest.setMaxTries(alternative);
				// Calculates the rest of time of the connection
				double residualDuration = lRequest.getDuration()
						- (event.getInitialTimeStamp() - disrupted
								.getStartTime());
				// System.out.println("residual: "+residualDuration);
				lRequest.setDuration(residualDuration);
				// Create a new path message
				rsvpRetry = new RSVP(lRequest, hopLimit, label);
				// Set the label indicating to tackle the failure
				rsvpRetry.setReRouting();
				// Adds the connection to the list of disrupted LSP
				disruptedLSP.put(rsvpErr.getFlowLabel(), lRequest);
				System.out.println("Adding LSP failure: "
						+ rsvpErr.getFlowLabel() + " ," + disrupted.toString());
			}
			// Now, return the result.
			if (rsvpRetry != null)
				return new Event(event.getTimeStamp(),
						Event.Type.PACKET_ARRIVAL, rsvpRetry);
			else
				return null;
		case LIGHTPATH_TEARDOWN: // Remove connection
			Connection connectionTear = (Connection) event.getContent();
			String flowLabel = connectionTear.getID();
			if ((disruptedLSP.get(flowLabel) == null)
					|| ((reroutedLSP.get(flowLabel) != null) && (reroutedLSP
							.get(flowLabel).getPath().equals(connectionTear
							.getPath())))) {
				// Send RSVP PathTear message
				RSVP rsvpTear = new RSVP(connectionTear,
						Packet.Header.RSVP_PATH_TEAR,
						connectionTear.getSource(), connectionTear.getTarget());
				// System.out.println(rsvpTear.toString());
				return new Event(event.getTimeStamp(),
						Event.Type.PACKET_ARRIVAL, rsvpTear);
			} else { // Ignore the teardown associated to a failed LSP, since it
						// is already cleaned and rerouted.
				return null;
			}
		case LIGHTPATH_REMOVED: // Confirmation of connection removal
			// System.out.println(event.toString());
			return null;
		case PACKET_ARRIVAL: // Ant
			// Get the packet
			Packet packet = (Packet) event.getContent();
			// Get the node associated to this packet
			id = packet.getNode();
			// Give the packet to the right node
			OpticalNode procNode = nodes.get(id);
			if (procNode != null) { // Node functioning
				// Process the event
				Event response = procNode.process(event);
				if (response.getType().equals(Event.Type.ANT_ROUTED)) {
					accounting.addSuccesful((Packet) response.getContent());
					return null;
				} else if (response.getType().equals(Event.Type.ANT_KILLED)) {
					accounting.addFailed((Packet) response.getContent());
					return null;
				} else if (response.getType().equals(Event.Type.IGNORE)) {
					return null;
				} else {
					return response;
				}
			} else { // Failed node
				accounting.addFailed(packet);
				return null;
			}
		case FAILURE_LINK: // For link failure
			// Get the edge associated with the failure
			String sEdge = (String) event.getContent();
			Edge edge = links.get(sEdge).getEdge();
			// Remove the failure edge from the graph
			try { // Do it only if it is not a node failure
				if (nodes.containsKey(edge.getSource())
						&& nodes.containsKey(edge.getDestination())) {
					graph.removeEdge(edge.getSource(), edge.getDestination());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Notifies the end nodes of the failure after the localization time
			double timeNotification = lastTime + this.faultLocalizationTime;
			int lengthFailure = 2 * this.identificationLength;
			// Creates the packets of notification
			Packet failureTo = new Packet(Packet.Header.LINK_FAILURE,
					edge.getDestination(), edge.getDestination(),
					Packet.Priority.HIGH, lengthFailure, graph.size());
			Packet failureFrom = new Packet(Packet.Header.LINK_FAILURE,
					edge.getSource(), edge.getSource(), Packet.Priority.HIGH,
					lengthFailure, graph.size());
			// Adds the edge to the packets.
			Failure failureLinkAdv = new Failure(edge);
			failureTo.setPayload(failureLinkAdv);
			failureFrom.setPayload(failureLinkAdv);
			// Add to the vector of events
			Vector<Event> failuresLink = new Vector<Event>();
			failuresLink.add(new Event(timeNotification,
					Event.Type.PACKET_ARRIVAL, failureFrom));
			failuresLink.add(new Event(timeNotification,
					Event.Type.PACKET_ARRIVAL, failureTo));
			return new Event(timeNotification, Event.Type.MULTIPLE,
					failuresLink);
		case FAILURE_NODE: // For node failure
			// Get the node associated with the failure
			id = (String) event.getContent();
			Vector<Event> failuresNode = new Vector<Event>();
			// Gets the neighbors of this graph
			Vector<String> neighbors = graph.adjacentNodes(id);
			for (String neighId : neighbors) {
				// Add the edge "from" the removed node
				failuresNode.add(new Event(lastTime, Event.Type.FAILURE_LINK,
						new String(id + "-" + neighId)));
				// Add the edge "to" the removed node
				failuresNode.add(new Event(lastTime, Event.Type.FAILURE_LINK,
						new String(neighId + "-" + id)));
			}
			// Remove the failure node from the graph
			try {
				graph.removeNode(id);
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Remove the node from the list of nodes
			nodes.remove(id);
			// Return the response containing the failure of the multiple links
			return new Event(lastTime, Event.Type.MULTIPLE, failuresNode);
		default:
			logger.severe("Unknown event: " + event.toString());
			return null;
		}
	}

	/**
	 * Returns the next hop with the highest level of pheromone.
	 * 
	 * @param neighborhood
	 *            The routing table of a given destination.
	 * @param rsvp
	 *            The RSVP message.
	 * @return The next hop with the highest level of pheromone.
	 */
	public static String select(RoutingTableEntry neighborhood, RSVP rsvp) {
		int size; // Number of neighbors
		// Get the last edge visited.
		String lastVisited = null;
		if (rsvp.getPathLength() > 0) { // not first hop
			lastVisited = rsvp.getPath().getLastEdge().getSource();
			size = neighborhood.size() - 1;
		} else { // First hop
			size = neighborhood.size();
		}
		if (size == 0) {
			return null;
		}
		String maxId = null; // id of the neighbor with biggest level
		double maxLevel = Double.NEGATIVE_INFINITY; // biggest pheromone level
		// For each neighbor do
		for (String neighId : neighborhood.neighborhood()) {
			if (!neighId.equals(lastVisited)) { // Avoids the rsvp message to
												// come back.
				double level = ((NeighborAttr) neighborhood.getEntry(neighId))
						.getPheromoneLevel();
				if (maxLevel < level) { // Neighbor with biggest level of
										// pheromone
					maxLevel = level;
					maxId = neighId;
				}
			}
		}
		// Return the id with the maximum level found
		return maxId;
	}

	/**
	 * Selects the next hop based ONLY on the probabilities of the routing
	 * table. It is not allowed to come back!
	 * 
	 * @param neighborhood
	 *            The routing table of a given destination.
	 * @return The id of the next hop.
	 */
	public static String select(RoutingTableEntry neighborhood, Packet packet) {
		int count = 0; // Counter for the number of neighbors
		int size; // Number of neighbors
		// Get the last edge visited.
		String lastVisited = null;
		if (packet.getPathLength() > 0) { // not first hop
			lastVisited = packet.getPath().getLastEdge().getSource();
			size = neighborhood.size() - 1;
		} else { // First hop
			size = neighborhood.size();
		}
		if (size == 0) {
			return null;
		}
		double[] probabilityDistribution = new double[size]; // Probability
																// distribution
		String[] keys = new String[size]; // For storing the ids of the nodes.
		double totalLevel = 0.0; // Sum of probabilities
		// For each neighbor do
		for (String neighId : neighborhood.neighborhood()) {
			if (!neighId.equals(lastVisited)) {
				double level = ((NeighborAttr) neighborhood.getEntry(neighId))
						.getPheromoneLevel();
				probabilityDistribution[count] = level;
				totalLevel = totalLevel + level;
				keys[count] = neighId;
				count++;
			}
		}
		// Normalize the values
		for (int i = 0; i < size; i++) {
			probabilityDistribution[i] = probabilityDistribution[i]
					/ totalLevel;
		}
		// Spins the wheel
		double sample = rngAnt.nextDouble();
		// Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return keys[n];
	}

	/**
	 * Selects the next hop based on the probabilities of the routing table and
	 * on the local statistics (free wavelength ratio).
	 * 
	 * @param neighborhood
	 *            The routing table of a given destination.
	 * @param links
	 *            The state of the neighbor links.
	 * @param ant
	 *            The packet ant.
	 * @param alpha
	 *            Trade-off between shortest-path and heuristic correction
	 *            (congestion).
	 * @return The id of the next hop.
	 */
	public static String select(RoutingTableEntry neighborhood,
			LinkedHashMap<String, LinkState> links, AntPacket ant, double alpha) {
		// Gets the total number of free points between the neighbors.
		double totalFreeWavelengths = 0.0; // Total number of free wavelengths
		double totalPheromoneLevel = 0.0;
		// Gets the neighbors that are not in the tabu list.
		Vector<String> availableNeighbors = new Vector<String>();
		for (String neighId : neighborhood.neighborhood()) { // Uses
																// neighborhood
																// instead of
																// opticalBuffers
																// because of
																// failure
																// tolerance
			if (!ant.isTabu(neighId)) { // not in tabu list
				availableNeighbors.add(neighId);
				// Get the total number of free wavelengths
				totalFreeWavelengths = totalFreeWavelengths
						+ links.get(neighId).getMask().freeWavelengths();
				// And the total pheromone level
				totalPheromoneLevel = totalPheromoneLevel
						+ ((NeighborAttr) neighborhood.getEntry(neighId))
								.getPheromoneLevel();
			}
		}
		// Verify the routing decision policy
		if ((availableNeighbors.size() == 0)) { // all neighbors already visited
												// - doing loop!
			// Proceed like a data packet
			String nextHop = null;
			if (neighborhood.size() > 1) {
				nextHop = select(neighborhood, ant);
			} else {
				// nextHop = ant.getLastVisited();
				return null;
			}
			// Destroy the loop
			// System.out.println("Loop: "+ant.toString());
			int loopSize = ant.destroyLoop(nextHop);
			ant.setPayloadLength(ant.getPayloadLength() - loopSize
					* ant.getBytesHop());
			// Return the next hop
			return nextHop;
		} else { // There are other nodes not already visited.
			/*
			 * Now, use the pheromone values with the local heuristic to
			 * calculate the next hop.
			 */
			int size = availableNeighbors.size(); // Number of neighbors
			double[] probabilityDistribution = new double[size]; // Probability
																	// distribution
			String[] keys = new String[size]; // For storing the ids of the
												// nodes.
			int count = 0; // Counter for the number of neighbors
			// For each neighbor do
			for (String neighId : availableNeighbors) {
				// Get the appropriate neighbor and the associated link state
				keys[count] = neighId;
				NeighborAttr neigh = (NeighborAttr) neighborhood
						.getEntry(neighId);
				LinkState state = links.get(neighId);
				// Now calculate the probability
				double freeLambdas = state.getMask().freeWavelengths();
				//
				probabilityDistribution[count] = ((neigh.getPheromoneLevel() / totalPheromoneLevel) + alpha
						* (freeLambdas / totalFreeWavelengths))
						/ (1.0 + alpha);
				// Increment the index
				count++;
			}
			// Spins the wheel
			double sample = rngAnt.nextDouble();
			// Set sum to the first probability
			double sum = probabilityDistribution[0];
			int n = 0;
			while (sum < sample) {
				n = n + 1;
				sum = sum + probabilityDistribution[n];
			}
			return keys[n];
		}
	}

	/**
	 * Selects the next hop based on the probabilities of the routing table and
	 * on the local statistics (free wavelength ratio).
	 * 
	 * @param neighborhood
	 *            The routing table of a given destination.
	 * @param links
	 *            The state of the neighbor links.
	 * @param ant
	 *            The packet ant.
	 * @return The id of the next hop.
	 */
	/*
	 * public String select(RoutingTableEntry neighborhood,
	 * LinkedHashMap<String,LinkState> links, AntPacket ant){ //Gets the total
	 * number of free points between the neighbors. double
	 * totalFreeWavelengthRatio = 0.0; //Total number of free wavelengths double
	 * totalPheromoneLevel = 0.0; //Gets the neighbors that are not in the tabu
	 * list. Vector<String> availableNeighbors = new Vector<String>(); for
	 * (String neighId: neighborhood.neighborhood()) { //Uses neighborhood
	 * instead of opticalBuffers because of failure tolerance if
	 * (!ant.isTabu(neighId)) { //not in tabu list
	 * availableNeighbors.add(neighId); //Get the total number of free
	 * wavelengths totalFreeWavelengthRatio = totalFreeWavelengthRatio +
	 * this.decay(links.get(neighId).getMask().freeWavelengthRatio()); //And the
	 * total pheromone level totalPheromoneLevel = totalPheromoneLevel +
	 * ((NeighborAttr)neighborhood.getEntry(neighId)).getPheromoneLevel(); } }
	 * //Verify the routing decision policy if ((availableNeighbors.size() ==
	 * 0)) { //all neighbors already visited - doing loop! //Proceed like a data
	 * packet String nextHop = null; if (neighborhood.size() > 1) { nextHop =
	 * select(neighborhood,ant); } else { //nextHop = ant.getLastVisited();
	 * return null; } //Destroy the loop //
	 * System.out.println("Loop: "+ant.toString()); int loopSize =
	 * ant.destroyLoop(nextHop); ant.setPayloadLength(ant.getPayloadLength() -
	 * loopSize * ant.getBytesHop()); //Return the next hop return nextHop; }
	 * else { //There are other nodes not already visited. // Now, use the
	 * pheromone values with the local heuristic to calculate the next hop. int
	 * size = availableNeighbors.size(); //Number of neighbors double[]
	 * probabilityDistribution = new double[size]; //Probability distribution
	 * String[] keys = new String[size]; //For storing the ids of the nodes. int
	 * count=0; //Counter for the number of neighbors //For each neighbor do for
	 * (String neighId: availableNeighbors) { //Get the appropriate neighbor and
	 * the associated link state keys[count] = neighId; NeighborAttr neigh =
	 * (NeighborAttr) neighborhood.getEntry(neighId); LinkState state =
	 * links.get(neighId); //Now calculate the probability double criticality =
	 * this.decay(state.getMask().freeWavelengthRatio());
	 * probabilityDistribution[count] = ((neigh.getPheromoneLevel() /
	 * totalPheromoneLevel) + alpha*(criticality/totalFreeWavelengthRatio)) /
	 * (1.0 + alpha); //Increment the index count++; } //Spins the wheel double
	 * sample = AntNetControlPlane.getRandomDouble(); //Set sum to the first
	 * probability double sum = probabilityDistribution[0]; int n = 0; while
	 * (sum < sample) { n = n + 1; sum = sum + probabilityDistribution[n]; }
	 * return keys[n]; } }
	 */
	/**
	 * Prints the last simulation time.
	 */
	public void updateValues() {
		System.out.println("LastTime: " + lastTime);
		System.out.println("Disrupted: "
				+ this.disruptedLSP.keySet().toString());
		System.out.println("Total disrupted: " + disruptedLSP.size());
		System.out.println("Rerouted: " + reroutedLSP.toString());
	}

	/**
	 * Writes this class to a serialized object.
	 * 
	 * @param s
	 * @throws IOException
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {
		transporter = new StaticTransporter(graph, random, rngAnt);
		s.defaultWriteObject();
	}

	/**
	 * Reads the class from the serialization.
	 * 
	 * @param s
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream s) throws IOException,
			ClassNotFoundException {
		s.defaultReadObject();
		// customized deserialization code
		AntNetControlPlane.graph = transporter.graph;
		AntNetControlPlane.random = transporter.random;
		AntNetControlPlane.rngAnt = transporter.randomAnt;
	}

}

/**
 * Wrapper-class for transporting static variables.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 * 
 */
class StaticTransporter implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	Graph graph;
	MersenneTwister random;
	MersenneTwister randomAnt;

	public StaticTransporter(Graph g, MersenneTwister r, MersenneTwister ant) {
		this.graph = g;
		this.random = r;
		this.randomAnt = ant;
	}
}
