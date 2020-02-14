/*
 * Created on Feb 14, 2008.
 */
package rwa.crankback.antnet;

import event.Event;
import graph.Edge;
import graph.Graph;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Vector;

import ops.Packet;

import antnet.StatisticalParametricModel;
import main.Failure;
import main.RoutingTable;
import main.Failure.Location;
import rwa.Connection;
import rwa.Error;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.WavelengthMask;
import rwa.crankback.CrankRSVP;
import rwa.crankback.LabelSet;
import rwa.crankback.LabelSwitchRouter;

/**
 * @author Gustavo S. Pavani
 * @version 1.0
 * 
 */
public class AntNetLSR extends LabelSwitchRouter {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** Statistical Parametric Model for global-traffic statistics. */
	protected StatisticalParametricModel parametricModel;
	/** Wavelength usage table for Most-used wavelength assignment. */
	protected WavelengthUsageTable lambdaTable;

	/**
	 * Creates a new AntNetLSR object.
	 * 
	 * @param identification
	 *            The identification of this node in the network.
	 * @param aRoutingTable
	 *            The routing table associated with this node.
	 * @param aLinks
	 *            The set of link states that belongs to this node.
	 * @param aGraph
	 *            The graph representing the network.
	 * @param aWa
	 *            The chosen wavelength assignment algorithm.
	 * @param behavior
	 *            The re-routing behavior.
	 * @param attempts
	 *            The number of re-routing attempts.
	 * @param aModel
	 *            The set of parametric models of this node.
	 * @param usageTable
	 *            The lambda usage table of this node.
	 */

	public AntNetLSR(String identification, RoutingTable routingTable,
			LinkedHashMap<String, LinkState> links, Graph graph,
			WavelengthAssignment aWa, ReRouting behavior, int maxAttempts,
			int attempts, StatisticalParametricModel aModel,
			WavelengthUsageTable usageTable) {
		super(identification, routingTable, links, graph, aWa, behavior,
				maxAttempts, attempts);
		this.parametricModel = aModel;
		this.lambdaTable = usageTable;
	}

	/**
	 * Process the specified event.
	 * 
	 * @param event
	 *            The event to be processed.
	 * @return The processed event.
	 */
	@Override
	public Event process(Event event) {
		// Get the packet associated to this event
		Packet packet = (Packet) event.getContent();
		// if (packet.getFlowLabel() != null &&
		// packet.getFlowLabel().equals("210"))
		// System.out.println(packet.toString());
		// Inspects its header and decides what to do with it.
		Packet.Header header = packet.getNextHeader();
		Event response = null; // The response to the processing
		String nextHop; // The next hop in the path
		Ant ant; // Ant packet
		Error error; // The associated error specification
		String target; // Target node
		CrankRSVP rsvp; // RSVP message
		switch (header) {
		case ANT_FORWARD:
			ant = (Ant) packet;
			// Get the target node
			target = ant.getTarget();
			// Verify if the ant has reached the target node.
			if (target.equals(id)) { // Ant reached the target node
				// Turn it into a backward ant
				ant.toBackward();
				// Set the next node as the last visited before the target
				nextHop = ant.getBackwardNode();
			} else { // Not arrived in the target node
				// Decide the next hop based on the information of the routing
				// table
				// and on the information of the optical buffers
				nextHop = ((AntNetCrankRoutingTable) routingTable).nextHop(ant,
						links);
				if ((nextHop == null) || (packet.getHopLimit() == 0)) { // Ant
																		// killed
																		// due
																		// to
																		// loop
																		// or
																		// expired
																		// hop
																		// limit
					event.setType(Event.Type.ANT_KILLED);
					response = event;
					break;
				} else {
					// Decrement the number of hops
					ant.decrementHopLimit();
					// Add the wavelength mask to the ant payload
					WavelengthMask mask = links.get(nextHop).getMask();
					ant.addMask(mask);
				}
			}
			// Set the next hop in the packet
			ant.setNode(nextHop);
			// Return the response
			response = event;
			break;
		case ANT_BACKWARD:
			ant = (Ant) packet;
			/* Update the pheromone routing table */
			((AntNetCrankRoutingTable) routingTable).update(ant,
					parametricModel);
			// Verify if the ant reached the source node.
			if (ant.getSource().equals(id)) {
				// Add the memory of the ant to the wavelength usage table
				if (!ant.getLoopFlag()) // Not looped
					lambdaTable.update(ant.getCollector(), ant.getTarget());
				// Set the ant as routed
				event.setType(Event.Type.ANT_ROUTED);
			} else {
				// See the next hop
				nextHop = ant.getBackwardNode();
				// Verify if the link is not damaged.
				if (!AntNetCrankControlPlane.hasConnectivity(id, nextHop)) {
					event.setType(Event.Type.ANT_KILLED);
					response = event;
					// System.out.println("Back: "+event.toString());
					break;
				}
				// Set the next node in the path
				ant.setNode(nextHop);
			}
			response = event;
			break;
		case RSVP_PATH:
			rsvp = (CrankRSVP) packet;
			// Get the target node
			target = rsvp.getTarget();
			// Get the lightpath request
			LightpathRequest request = (LightpathRequest) rsvp.getObject();
			// Get the label set object
			LabelSet labelSet = rsvp.getLabelSet();
			if ((labelSet != null) && (labelSet.size() == 0)) { // There is no
																// free
																// wavelengths
																// to allocate
				// wavelength continuity constraint violated
				rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); // change
																	// header to
																	// problem
				// Reset the SD pair to the new values
				rsvp.setSDPair(id, request.getSource());
				// Set the error
				error = new Error(Error.Code.RP_LABEL_SET);
				rsvp.setError(error);
				// Do not record the route anymore
				rsvp.resetRecordRoute();
				// Get the next hop
				nextHop = rsvp.getBackwardNode();
				rsvp.addEffectiveHop();
				// System.out.println(rsvp.toString());
			} else if (target.equals(id)) { // RSVP reached the target node
				// change header to reservation
				rsvp.setNextHeader(Packet.Header.RSVP_RESV);
				// Reset the SD pair to the new values
				rsvp.setSDPair(target, request.getSource());
				// Choose the first free wavelength
				int lambda = labelSet.getFirstLabel();
				// Create a connection and add it as a object.
				Connection connectionEst = new Connection(rsvp.getPath(),
						lambda, rsvp.getFlowLabel(), request);
				// Set the start time of the connection, which starts after
				// arriving at the source node.
				// Because of that, it uses the round trip time as the time to
				// start it
				connectionEst.setStartTime(event.getTimeStamp()
						+ (event.getTimeStamp() - event.getInitialTimeStamp()));
				// Set the object to the message
				rsvp.setObject(connectionEst);
				// Add the connection to the table of active connections.
				activeConnections.put(rsvp.getFlowLabel(), connectionEst);
				// System.out.println(activeConnections.toString());
				// Do not record the route anymore
				rsvp.resetRecordRoute();
				// Get the next hop
				nextHop = rsvp.getBackwardNode();
			} else { // Intermediate node
				// Get the next hop
				if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																// node
																// re-routing
					nextHop = ((AntNetCrankRoutingTable) routingTable).nextHop(
							rsvp, this.getHistoryTable(rsvp.getFlowLabel()));
				} else { // None or end-to-end routing
					nextHop = ((AntNetCrankRoutingTable) routingTable).nextHop(
							rsvp, new Vector<String>());
				}
				if (nextHop == null
						|| (packet.getHopLimit() == 0)
						|| !(AntNetCrankControlPlane.hasConnectivity(id,
								nextHop))) { // dead-end
					// request.addTry(); //add to the counter of tries
					rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); // change
																		// header
																		// to
																		// problem
					// Reset the SD pair to the new values
					rsvp.setSDPair(id, request.getSource());
					// Create the error information
					error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
					rsvp.setError(error);
					// Do not record the route anymore
					rsvp.resetRecordRoute();
					if (!id.equals(request.getSource())) {
						// Get the next hop (backward)
						nextHop = rsvp.getBackwardNode();
						rsvp.addEffectiveHop();
					} else { // Failure after the first link of the node
						response = event;
						break;
					}
				} else {
					// Store the label set, if applicable
					if ((labelSet != null)
							&& (this.rerouting.equals(ReRouting.SEGMENT)))
						labelSetTable.put(rsvp.getFlowLabel(),
								(LabelSet) labelSet.clone());
					// Updates the mask
					if (labelSet == null) {
						if (wa.equals(WavelengthAssignment.FIRST_FIT))
							labelSet = new LabelSet(this.links.get(nextHop)
									.getNumberWavelengths());
						else if (wa.equals(WavelengthAssignment.MOST_USED))
							// labelSet = new
							// LabelSet(getMostUsed(lambdaTable.get(target).getUsage()));
							labelSet = new LabelSet(getMostUsed(this.links.get(
									nextHop).getNumberWavelengths()));
						else if (wa.equals(WavelengthAssignment.LEAST_USED))
							// labelSet = new
							// LabelSet(getLeastUsed(lambdaTable.get(target).getUsage()));
							labelSet = new LabelSet(getLeastUsed(this.links
									.get(nextHop).getNumberWavelengths()));
						rsvp.setLabelSet(labelSet);
					}
					// System.out.println(links.get(nextHop).getMask().toString());
					rsvp.updateMask(links.get(nextHop).getMask());
					// Decrement the number of hops
					rsvp.decrementHopLimit();
					rsvp.addEffectiveHop();
				}
			}
			// Set the next node
			rsvp.setNode(nextHop);
			// Set the new time of the event due to transmission time
			// System.out.println(event.toString());
			event.setTimeStamp(event.getTimeStamp()
					+ links.get(nextHop).getDelay());
			// Return the response
			response = event;
			break;
		case RSVP_PATH_TEAR:
			response = super.process(event);
			break;
		case RSVP_PATH_ERR:
			response = super.process(event);
			break;
		case RSVP_RESV:
			response = super.process(event);
			break;
		case RSVP_RESV_TEAR:
			response = super.process(event);
			break;
		case RSVP_RESV_ERR:
			response = super.process(event);
			break;
		case LINK_FAILURE: // Packet associated with control plane (separated
							// channel)
			// Identify the failure
			Failure failure = (Failure) packet.getPayload();
			// Flooding of the failure
			if (!failureID.contains(failure.getID())) { // First time
				// Mock local update of the topology
				routingTable.updateFromTopology(graph);
				// Broadcast the failure to the neighbors
				String previousHop = packet.getSource();
				Vector<String> neighbors = graph.adjacentNodes(id);
				Vector<Event> broadcast = new Vector<Event>();
				// Adds the flooding information
				for (String neighId : neighbors) {
					if (!neighId.equals(previousHop)) { // not visited
						// Retransmit the packet
						Packet clonedFrom = (Packet) packet.clone();
						clonedFrom.retransmit(id, neighId);
						clonedFrom.setNode(neighId);
						// Set new time stamp
						double transmissionTime = this.links.get(neighId)
								.getDelay();
						double newTimeStamp = event.getTimeStamp()
								+ transmissionTime;
						// Add to the list of broadcast
						broadcast.add(new Event(newTimeStamp,
								Event.Type.PACKET_ARRIVAL, clonedFrom));
					}
				}
				// Add the failure to the list of processed ones.
				failureID.add(failure.getID());
				/* Remove the link states affected by the failure. */
				if (((Edge) failure.getInformation()).getSource().equals(id)) {
					links.remove(((Edge) failure.getInformation())
							.getDestination());
				}
				/* Treatment the failure by the neighbor nodes */
				// Now, if it is the closest node upstream to the failure
				if (id.equals(((Edge) failure.getInformation()).getSource())) {
					// Adds the notification of broken LSP to the event
					for (String activeID : activeConnections.keySet()) {
						// Gets the connection
						Connection active = activeConnections.get(activeID);
						// Locate the failure
						Location location = failure
								.locate(id, active.getPath());
						if (!location.equals(Failure.Location.NOT_APPLICABLE)) {
							// Create the PathErr packet
							CrankRSVP pathErr = new CrankRSVP(active,
									Packet.Header.RSVP_PATH_ERR, id,
									active.getSource());
							// Create the error with path remove flag
							error = new Error(Error.Code.LSP_FAILURE, true);
							pathErr.setError(error);
							pathErr.setFlowLabel(activeID);
							// System.out.println(pathErr.toString());
							double transmissionTime = 0;
							// Not the source node to treat the failure
							if (!id.equals(active.getPath().firstNode())) {
								nextHop = active.getPath().getPreviousNode(id);
								pathErr.setNode(nextHop);
								// Set new time stamp
								transmissionTime = this.links.get(nextHop)
										.getDelay();
							}
							double newTimeStamp = event.getTimeStamp()
									+ transmissionTime + DELTA_TIME;
							broadcast.add(new Event(newTimeStamp,
									Event.Type.PACKET_ARRIVAL, pathErr));
						}
					}
					// Now, if it is the closest node downstream to the failure
				} else if (id.equals(((Edge) failure.getInformation())
						.getDestination())) {
					// Adds the notification of broken LSP to the event
					for (String activeID : activeConnections.keySet()) {
						// Gets the connection
						Connection active = activeConnections.get(activeID);
						// Locate the failure
						Location location = failure
								.locate(id, active.getPath());
						if (!location.equals(Failure.Location.NOT_APPLICABLE)) {
							// Create the PathErr packet
							CrankRSVP pathTear = new CrankRSVP(active,
									Packet.Header.RSVP_PATH_TEAR, id,
									active.getTarget());
							// Create the error
							error = new Error(Error.Code.LSP_FAILURE);
							pathTear.setError(error);
							pathTear.setFlowLabel(activeID);
							// System.out.println(pathTear.toString());
							// Not the target node to treat the failure
							double transmissionTime = 0;
							// Not the last node to tackle the failure
							if (!id.equals(active.getPath().lastNode())) {
								nextHop = active.getPath().getNextNode(id);
								pathTear.setNode(nextHop);
								transmissionTime = this.links.get(nextHop)
										.getDelay();
							}
							// Set new time stamp
							double newTimeStamp = event.getTimeStamp()
									+ transmissionTime + DELTA_TIME;
							// Add to the list of broadcast
							broadcast.add(new Event(newTimeStamp,
									Event.Type.PACKET_ARRIVAL, pathTear));
						}
					}
				}
				// System.out.println(broadcast.toString());
				// Return the multiple packets associated with the failure
				response = new Event(event.getTimeStamp(), Event.Type.MULTIPLE,
						broadcast);
			} else { // Already processed the failure.
				event.setType(Event.Type.IGNORE);
				response = event;
			}
			break;
		}
		return response;
	}

	/**
	 * Returns the most used label set for wavelength assignment.
	 * 
	 * @param usage
	 *            The usage vector.
	 * @return The most used label set for wavelength assignment.
	 */
	protected int[] getMostUsed(long[] usage) {
		int[] order = new int[usage.length];
		for (int i = 0; i < usage.length; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for (int j = 0; j < usage.length; j++) {
				if (usage[j] > max) {
					max = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MIN_VALUE;
		}
		return order;
	}

	protected int[] getMostUsed(int w) {
		// Gets the total usage for all destinations
		WavelengthUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage, 0L);
		for (int i = 0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for (int j = 0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		// Now order from the most used to the least used
		int[] order = new int[w];
		for (int i = 0; i < w; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for (int j = 0; j < w; j++) {
				if (totalUsage[j] > max) {
					max = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MIN_VALUE;
		}
		return order;
	}

	/**
	 * Returns the least used label set for wavelength assignment.
	 * 
	 * @param usage
	 *            The usage vector.
	 * @return The least used label set for wavelength assignment.
	 */
	protected int[] getLeastUsed(long[] usage) {
		int[] order = new int[usage.length];
		for (int i = 0; i < usage.length; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for (int j = 0; j < usage.length; j++) {
				if (usage[j] < min) {
					min = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MAX_VALUE;
		}
		return order;
	}

	protected int[] getLeastUsed(int w) {
		// Gets the total usage for all destinations
		WavelengthUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage, 0L);
		for (int i = 0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for (int j = 0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		// Now order from the least used to the most used
		int[] order = new int[w];
		for (int i = 0; i < w; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for (int j = 0; j < w; j++) {
				if (totalUsage[j] < min) {
					min = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MAX_VALUE;
		}
		return order;
	}
}
