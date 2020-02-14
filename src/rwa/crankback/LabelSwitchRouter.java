/*
 * Created on Feb 14, 2008.
 */
package rwa.crankback;

import event.Event;
import graph.Edge;
import graph.Graph;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Vector;

import ops.Packet;

import main.Failure;
import main.RoutingTable;
import main.Failure.Location;
import rwa.Connection;
import rwa.Error;
import rwa.ExplicitRoutingTable;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.OpticalNode;
import rwa.WavelengthMask;

/**
 * 
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 * 
 */
public class LabelSwitchRouter extends OpticalNode {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;

	/** Sets the desire of re-routing in case of LSP establishment failure. */
	public enum ReRouting {
		/** No re-route retry after LSP establishment failure. */
		NONE,
		/** Only the ingress node re-routes after LSP establishment failure. */
		END_TO_END,
		/** Area border router re-routes after LSP establishment failure. */
		BOUNDARY,
		/** Any intermediate node re-routes after LSP establishment failure. */
		SEGMENT
	}

	/** Sets the wavelength assignment algorithm. */
	public enum WavelengthAssignment {
		/** First-fit */
		FIRST_FIT,
		/** Most used */
		MOST_USED,
		/** Least used */
		LEAST_USED
	}

	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Local number of re-routing attempts. */
	protected int reroutingAttempts;
	/** Maximum number of re-routing attempts. */
	protected int maxReroutingAttempts;
	/** The chosen wavelength assignment algorithm. */
	protected WavelengthAssignment wa;
	/** The table for temporarily storing Label Set objects. */
	protected Hashtable<String, LabelSet> labelSetTable;
	/** The history table serving as tabu in segment re-routing. */
	protected Hashtable<String, Vector<String>> historyTable;

	/**
	 * Creates a new LabelSwitchRouter object.
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
	 * @param maxAttempts
	 *            The global number of re-routing attempts.
	 * @param attempts
	 *            The local number of re-routing attempts.
	 */
	public LabelSwitchRouter(String identification, RoutingTable routingTable,
			LinkedHashMap<String, LinkState> links, Graph graph,
			WavelengthAssignment aWa, ReRouting behavior, int maxAttempts,
			int attempts) {
		super(identification, routingTable, links, graph);
		// Set the local variables.
		this.wa = aWa;
		this.rerouting = behavior;
		this.maxReroutingAttempts = maxAttempts;
		this.reroutingAttempts = attempts;
		if (this.rerouting.equals(ReRouting.SEGMENT)) {
			labelSetTable = new Hashtable<String, LabelSet>();
			historyTable = new Hashtable<String, Vector<String>>();
		}
		//GATO remover as duas linhas abaixo 
		labelSetTable = new Hashtable<String, LabelSet>();
		historyTable = new Hashtable<String, Vector<String>>();
	}

	/**
	 * Associates the neighbor id of a failed routed node to a flow label.
	 * 
	 * @param label
	 *            The label of the RSVP message.
	 * @param neigh
	 *            The id of the visited neighbor.
	 */
	protected void putHistoryTable(String label, String neigh) {
		Vector<String> history = historyTable.get(label);
		if (history == null) // no neighbor id in the list
			history = new Vector<String>();
		// Add the neighbor id to the list
		if (!history.contains(neigh)) // no repeated elements
			history.add(neigh);
		this.historyTable.put(label, history);
	}

	/**
	 * Gets the number of already visited neighbor nodes by the RSVP Path
	 * message.
	 * 
	 * @param label
	 *            The label of the RSVP message.
	 * @return The number of already visited neighbor nodes by the RSVP Path
	 *         message.
	 */
	protected int sizeHistoryTable(String label) {
		Vector<String> history = historyTable.get(label);
		if (history == null) // no neighbor id in the list
			return 0;
		else
			// return the number of labels in the history table
			return history.size();
	}

	/**
	 * Gets the
	 * 
	 * @param label
	 * @return
	 */
	protected Vector<String> getHistoryTable(String label) {
		Vector<String> history = historyTable.get(label);
		//System.out.println(historyTable.toString());
		if (history == null) // no neighbor id in the list
		{
			//System.out.println("EH NULL");
			return new Vector<String>();
		} else
			return historyTable.get(label);
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
		// Inspects its header and decides what to do with it.
		Packet.Header header = packet.getNextHeader();
		Event response = null; // The response to the processing
		String nextHop; // The next hop in the path
		CrankRSVP rsvp; // RSVP message
		String target; // Target node
		Error error; // The associated error specification
		switch (header) {
		case RSVP_PATH: // path reservation
			// System.out.println(event.toString());
			// System.out.println(this.routingTable.toString());
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
					int routingTry = this.sizeHistoryTable(rsvp.getFlowLabel());
					nextHop = ((ExplicitRoutingTable) routingTable).nextHop(
							rsvp, routingTry);
				} else { // None or end-to-end routing
					nextHop = ((ExplicitRoutingTable) routingTable).nextHop(
							rsvp, request.getTry());
				}
				if (nextHop == null || (packet.getHopLimit() == 0)
						|| !(CrankControlPlane.hasConnectivity(id, nextHop))) { // dead-end
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
					if (labelSet == null)
						rsvp.setLabelSet(new LabelSet(this.links.get(nextHop)
								.getNumberWavelengths()));
					// System.out.println(links.get(nextHop).getMask().toString());
					rsvp.updateMask(links.get(nextHop).getMask());
					// Decrement the number of hops
					rsvp.decrementHopLimit();
				}
			}
			// Set the next node
			//System.out.println("Definindo nextHop para um caminho ja percorrido de tamanho: " + rsvp.getPath().toString());
			rsvp.setNode(nextHop);
			// Set the new time of the event due to transmission time
			// System.out.println(event.toString());
			event.setTimeStamp(event.getTimeStamp()
					+ links.get(nextHop).getDelay());
			// Return the response
			response = event;
			break;
		case RSVP_PATH_TEAR: // Forward direction
			
			rsvp = (CrankRSVP) packet;
			//System.out.println(rsvp.getFlowLabel() + " - CAMINHO " + rsvp.getPath().toString());
			//System.err.println(id +  " - finalizando reservada do pedido nro. " +  rsvp.getFlowLabel());
			// Remove this connection from the list of active connections
			activeConnections.remove(rsvp.getFlowLabel());
			// Get the target node
			target = rsvp.getTarget();
			// Verify if the RSVP message has reached the target node.
			if (target.equals(id)) { // RSVP reached the target node
				event.setType(Event.Type.LIGHTPATH_REMOVED);
			} else {
				// Clean the temporary label set and the history table, if
				// applicable
				if (this.rerouting.equals(ReRouting.SEGMENT)) {
					labelSetTable.remove(rsvp.getFlowLabel());
					historyTable.remove(rsvp.getFlowLabel());
				}
				// Gets the connection
				Connection teared = (Connection) rsvp.getObject();
				nextHop = teared.getPath().getNextNode(id);
				LinkState link = links.get(nextHop);
				if (link != null) { // Maybe the state was removed due to
									// failure
					// Get the associated mask
					WavelengthMask linkMaskTear = link.getMask();
					// Clear the wavelength
					linkMaskTear.clearWavelength(teared.getWavelength());
				} else {
					System.out.print(event.toString());
				}
				// Set the next hop in the packet
				
				rsvp.setNode(nextHop);
				// Set the new time of the event due to transmission time
				event.setTimeStamp(event.getTimeStamp()
						+ links.get(nextHop).getDelay());
			}
			// Return the response
			response = event;
			break;
		case RSVP_PATH_ERR: // Backward direction
			rsvp = (CrankRSVP) packet;
			// System.out.println(rsvp.toString());
			// Get the error
			error = rsvp.getError();
			// Remove this connection from the list of active connections
			// if the remove flag is enabled
			if (error.getRemoveFlag()) {
				activeConnections.remove(rsvp.getFlowLabel());
				Connection removed_perr = (Connection) rsvp.getObject();
				String forwardHop = removed_perr.getPath().getNextNode(id);
				LinkState link = links.get(forwardHop);
				if (link != null) { // Maybe the state was removed due to
									// failure
					// Get the associated mask
					WavelengthMask linkMaskRem = link.getMask();
					// Clear the wavelength
					linkMaskRem.clearWavelength(removed_perr.getWavelength());
				}
			}
			// Gets the target node
			target = rsvp.getTarget();
			LightpathRequest lRequest = null;
			// Process the message
			Error.Code code = error.getErrorCode();
			switch (code) {
			case LSP_FAILURE:
				if (target.equals(id)) { // RSVP reached the target node
					event.setType(Event.Type.LIGHTPATH_PROBLEM);
				} else { // intermediate nodes
					nextHop = ((Connection) rsvp.getObject()).getPath()
							.getPreviousNode(id);
					// Set the next hop in the packet
					rsvp.setNode(nextHop);
					// Set the new time of the event due to transmission time
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				}
				break;
			case ADMISSION_CONTROL_FAILURE:
				if (this.rerouting.equals(ReRouting.SEGMENT)) {
					// Remove the connection from the history table
					this.historyTable.remove(rsvp.getFlowLabel());
				}
				if (target.equals(id)) { // RSVP reached the target node
					event.setType(Event.Type.LIGHTPATH_PROBLEM);
				} else { // intermediate nodes
					nextHop = rsvp.getBackwardNode();
					// Set the next hop in the packet
					rsvp.setNode(nextHop);
					// Set the new time of the event due to transmission time
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				}
				break;
			case RP_LABEL_SET:
				if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																// node
																// re-routing
					// Remove the last visited node from the record route
					String last = rsvp.removeLastVisited();
					// Put it in the history table
					String label = rsvp.getFlowLabel();
					this.putHistoryTable(label, last);
					// System.out.println("ProcLA: "+id+" last: "+last);
					// Gets the request and update the try counter
					lRequest = (LightpathRequest) rsvp.getObject();
					lRequest.addTry();
					// Verify if we can make another re-routing attempt
					int currentAttempt = this.sizeHistoryTable(label);
					// Gets the number of available neighbors
					int neighbors;
					if (id.equals(lRequest.getSource()))
						neighbors = this.links.size();
					else
						neighbors = this.links.size() - 1;
					// Allow a re-routing if there is allowed attempts and
					// sufficient neighbors
					if ((currentAttempt <= this.reroutingAttempts)
							&& (currentAttempt < neighbors)
							&& (lRequest.getTry() <= this.maxReroutingAttempts)
							&& (packet.getHopLimit() > 0)) {
						
						//marca que esta tentando fazer rerouting.
						rsvp.setRerouting(true);
						
						// Set as path message
						rsvp.setNextHeader(Packet.Header.RSVP_PATH);
						// Sets the previous label set
						rsvp.setLabelSet(labelSetTable.get(rsvp.getFlowLabel()));
						// Set the record route in the RSVP
						rsvp.setRecordRoute();
						// Reset the error
						rsvp.setError(null);
						// Reset the source-destination pair
						rsvp.setSDPair(lRequest.getSource(),
								lRequest.getTarget());
					} else { // Limit exceeded!
						// Set the error
						error = new Error(
								Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
						rsvp.setError(error);
						if (id.equals(lRequest.getSource())) {
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
						} else {
							// Set the backward node
							nextHop = rsvp.getBackwardNode();
							rsvp.addEffectiveHop();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
							// Remove the entry in the history table
							this.historyTable.remove(rsvp.getFlowLabel());
						}
					}
				} else { // None or end-to-end routing
					if (target.equals(id)) { // RSVP reached the target node
						System.out.println(id + " Marcando como LIGHTPATH_PROBLEM");
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						nextHop = rsvp.getBackwardNode();
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
				}
				break;
			case RP_NO_ROUTE_AVAILABLE:
				if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																// node
																// re-routing
					// Remove the last visited node from the record route
					
					String last = rsvp.removeLastVisited();
					if(rsvp.getPathLength() == 0) {
						rsvp.getPath().addNode(last);
					}
					// Put it in the history table
					this.putHistoryTable(rsvp.getFlowLabel(), last);
					// System.out.println("ProcRT: "+id+" last: "+last);
					// Gets the request and update the try counter
					lRequest = (LightpathRequest) rsvp.getObject();
					lRequest.addTry();
					// Verify if we can make another re-routing attempt
					int currentAttempt = this.sizeHistoryTable(rsvp
							.getFlowLabel());
					// System.out.println("Cur: "+currentAttempt+" max: "+this.reroutingAttempts);
					// Gets the number of available neighbors
					int neighbors;
					if (id.equals(lRequest.getSource()))
						neighbors = this.links.size();
					else
						neighbors = this.links.size() - 1;
					// Allow a re-routing if there is allowed attempts and
					// sufficient neighbors
					if ((currentAttempt <= this.reroutingAttempts)
							&& (currentAttempt < neighbors)
							&& (lRequest.getTry() <= this.maxReroutingAttempts)
							&& (packet.getHopLimit() > 0)) {
						// Set as path message
						rsvp.setNextHeader(Packet.Header.RSVP_PATH);
						// Sets the previous label set
						rsvp.setLabelSet(labelSetTable.get(rsvp.getFlowLabel()));
						// Set the record route in the RSVP
						rsvp.setRecordRoute();
						// Reset the error
						rsvp.setError(null);
						// Reset the source-destination pair
						rsvp.setSDPair(lRequest.getSource(),
								lRequest.getTarget());
					} else { // Limit exceeded!
						// Set the error
						error = new Error(
								Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
						rsvp.setError(error);
						if (id.equals(lRequest.getSource())) {
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
						} else {
							// Set the backward node
							nextHop = rsvp.getBackwardNode();
							rsvp.addEffectiveHop();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
							// Remove the entry in the history table
							this.historyTable.remove(rsvp.getFlowLabel());
						}
					}
				} else { // None or end-to-end routing
					if (target.equals(id)) { // RSVP reached the target node
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						nextHop = rsvp.getBackwardNode();
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
				}
				break;
			case RP_REROUTING_LIMIT_EXCEEDED: // Only for segment re-routing
				// Remove the last visited node from the record route
				String last = rsvp.removeLastVisited();
				// Put it in the history table
				this.putHistoryTable(rsvp.getFlowLabel(), last);
				// System.out.println("ProcRE: "+id+" last: "+last);
				// Gets the request and update the try counter
				lRequest = (LightpathRequest) rsvp.getObject();
				lRequest.addTry();
				// Verify if we can make another re-routing attempt
				int currentAttempt = this.sizeHistoryTable(rsvp.getFlowLabel());
				// System.out.println("Cur: "+currentAttempt);
				// Gets the number of available neighbors
				int neighbors;
				if (id.equals(lRequest.getSource()))
					neighbors = this.links.size();
				else
					neighbors = this.links.size() - 1;
				// Allow a re-routing if there is allowed attempts and
				// sufficient neighbors
				if ((currentAttempt <= this.reroutingAttempts)
						&& (currentAttempt < neighbors)
						&& (lRequest.getTry() <= this.maxReroutingAttempts)
						&& (packet.getHopLimit() > 0)) {
					// Set as path message
					rsvp.setNextHeader(Packet.Header.RSVP_PATH);
					// Sets the previous label set
					rsvp.setLabelSet(labelSetTable.get(rsvp.getFlowLabel()));
					// Set the record route in the RSVP
					rsvp.setRecordRoute();
					// Reset the error
					rsvp.setError(null);
					// Reset the source-destination pair
					rsvp.setSDPair(lRequest.getSource(), lRequest.getTarget());
				} else { // Limit exceeded!
					if (id.equals(lRequest.getSource())) {
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
						// if (rsvp.getPath().size() == 0)
						// rsvp.setNode(lRequest.getSource());
					} else { // Not in the source node
						// Set the backward node
						nextHop = rsvp.getBackwardNode();
						rsvp.addEffectiveHop();
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
						// Remove the entry in the history table
						this.historyTable.remove(rsvp.getFlowLabel());
					}
				}
				break;
			}
			response = event;
			break;
		case RSVP_RESV: // Backward direction
			rsvp = (CrankRSVP) packet;
			
			//System.out
			//		.println(rsvp.getFlowLabel() + " tentando reservar " + id);
			// System.out.println(rsvp.toString());
			/* Update the the wavelength mask of this node. */
			Connection connection = (Connection) rsvp.getObject();
			// Get the forward node
			String fwdId = rsvp.getForwardNode();
			// Get the associated mask
			WavelengthMask linkMask = links.get(fwdId).getMask();
			// See the status of the wavelength
			if (linkMask.testWavelength(connection.getWavelength())) {
				// System.out.println("Adding conn: "+rsvp.getFlowLabel()+" with: "+connection.toString()+" to intermediate node: "+id);
				activeConnections.put(rsvp.getFlowLabel(), connection);
				// Set the wavelength
				linkMask.setWavelength(connection.getWavelength());
				if (this.rerouting.equals(ReRouting.SEGMENT)) {
					// Remove the connection from the history table
					this.historyTable.remove(rsvp.getFlowLabel());
				}
				// Verify if the resv message reached the source node.
				target = rsvp.getTarget();
				if (target.equals(id)) {
					System.out.println("RESERVADO!");
					event.setType(Event.Type.LIGHTPATH_ESTABLISHED);
				} else { // Intermediate node
					// See the next hop
					nextHop = rsvp.getBackwardNode();
					// In case of failure
					if (!(CrankControlPlane.hasConnectivity(id, nextHop))) {
					//	System.err.println("connectivity:" + rsvp.toString());
						// Send a resvErr msg to the sender
						rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
						rsvp.setSDPair(id, connection.getTarget());
						// Add the error
						error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
						rsvp.setError(error);
						// Change the next hop
						nextHop = rsvp.getForwardNode();
					}
					// Set the next hop in the packet
					rsvp.setNode(nextHop);
					// Set the new time of the event due to transmission time
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				}
			} else { // Contention problem!
				// Send a resvErr msg to the sender
				rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
				rsvp.setSDPair(id, connection.getTarget());
				// Set the error
				error = new Error(Error.Code.ADMISSION_CONTROL_FAILURE);
				rsvp.setError(error);
				// See the next hop
				nextHop = rsvp.getForwardNode();
				// Set the next hop in the packet
				rsvp.setNode(nextHop);
				// Set the new time of the event due to transmission time
				event.setTimeStamp(event.getTimeStamp()
						+ links.get(nextHop).getDelay());
			}
			response = event;
			break;
		case RSVP_RESV_TEAR: // Backward direction (Not used!)
			rsvp = (CrankRSVP) packet;
			/* Update the the wavelength mask of this node. */
			// Get the backward node
			String forwardId = rsvp.getForwardNode();
			// Get the associated mask
			WavelengthMask linkMaskTear = links.get(forwardId).getMask();
			// Clear the wavelength
			int clear = ((Connection) rsvp.getObject()).getWavelength();
			linkMaskTear.clearWavelength(clear);
			// Verify if the RSVP reached the source node.
			if (rsvp.getSource().equals(id)) {
				event.setType(Event.Type.LIGHTPATH_REMOVED);
			} else {
				// See the next hop
				nextHop = rsvp.getBackwardNode();
				rsvp.setNode(nextHop);
			}
			response = event;
			break;
		case RSVP_RESV_ERR: // Forward direction
			rsvp = (CrankRSVP) packet;
			// Remove the connection from the table of connections
			activeConnections.remove(rsvp.getFlowLabel());
			Connection removed_rerr = (Connection) rsvp.getObject();
			target = rsvp.getTarget();
			if (target.equals(id)) { // Now, send a PathErr to the ingress node
				// Convert to pathErr msg
				rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR);
				// System.out.println("Creating: "+rsvp.toString());
				// Reset the SD pair to the new values
				rsvp.setSDPair(id, removed_rerr.getSource());
			} else {
				nextHop = rsvp.getForwardNode();
				// Get the associated mask
				WavelengthMask linkMaskErr = links.get(nextHop).getMask();
				// Clear the wavelength
				linkMaskErr.clearWavelength(removed_rerr.getWavelength());
				// Set the next hop in the packet
				rsvp.setNode(nextHop);
				// Set the new time of the event due to transmission time
				event.setTimeStamp(event.getTimeStamp()
						+ links.get(nextHop).getDelay());
			}
			response = event;
			break;
		case LINK_FAILURE: // Packet associated with control plane (separated
							// channel)
			// Identify the failure
			Failure failure = (Failure) packet.getPayload();
			// Flooding of the failure
			if (!failureID.contains(failure.getID())) { // First time
				// Mock local update of the topology
				((ExplicitRoutingTable) routingTable).updateFromTopology(graph,
						CrankControlPlane.getPaths());
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

}
