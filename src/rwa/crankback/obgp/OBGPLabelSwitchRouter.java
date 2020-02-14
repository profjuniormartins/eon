/*
 * Created on Feb 14, 2008.
 * Updated on 2011 - 2012.
 */
package rwa.crankback.obgp;

import event.Event;
import graph.Edge;
import graph.Graph;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Vector;

import ops.Packet;
import ops.Packet.Priority;

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
 * @author Gustavo S. Pavani, Andre Filipe M. Batista
 * @version 1.0
 * 
 */
public class OBGPLabelSwitchRouter extends OpticalNode {
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
		SEGMENT, INTER_SEGMENT,
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
	/** The flag indicating if this OBGP LSR is an ASBR **/
	protected boolean isASBR;
	/** The domain ID string **/
	protected String domainID;
	protected int maxInterReroutingAttempts;
	/** be verbose? **/
	private boolean verbose = false;

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
	 * @param isASBR
	 *            True if this node is an ASBR in its domain or False, otherwise
	 * @param domainID
	 *            The ID String of the Domain
	 */
	public OBGPLabelSwitchRouter(String identification,
			RoutingTable routingTable, LinkedHashMap<String, LinkState> links,
			Graph graph, WavelengthAssignment aWa, ReRouting behavior,
			int maxAttempts, int attempts, boolean isASBR, String domainID,
			int maxInterReroutingAttempts) {
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
		historyTable = new Hashtable<String, Vector<String>>();
		this.isASBR = isASBR;
		this.domainID = domainID;
		this.maxInterReroutingAttempts = maxInterReroutingAttempts;
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
		if (history == null) // no neighbor id in the list
			return new Vector<String>();
		else
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
		Boolean inter = false;
		Boolean intra = false;
		
		
		
		
		
		switch (header) {

		case RSVP_PATH: // path reservation

			// System.out.println(event.toString());
			// System.out.println(this.routingTable.toString());

			// get the packet
			rsvp = (CrankRSVP) packet;
			// Get the target node
			target = rsvp.getTarget();

			// Verify if its a interdomain or intradomain request
			// Check if the target is in the same domain of the current node
			if (getDomain(target).equals(getDomain(id))) {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Intra-domain request from: "
							+ rsvp.getSource() + " to: " + target);
				intra = true;

			} else {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Inter-domain request from "
							+ rsvp.getSource() + " to: " + target);
				inter = true;
			}
			nextHop = "";

			/*
			 * if (target.split(":")[0].equals(id.split(":")[0])) {
			 * System.out.println(rsvp.getFlowLabel() + " - " + id +
			 * " - Received an Intra-domain request from: " + rsvp.getSource() +
			 * " to: " + target); intra = true; } else {
			 * System.out.println(rsvp.getFlowLabel() + " - " + id +
			 * " - Received an Inter-domain request from " + rsvp.getSource() +
			 * " to: " + target); inter = true; } nextHop = "";
			 */

			// Get the lightpath request
			LightpathRequest request = (LightpathRequest) rsvp.getObject();
			if (verbose)
				System.out.println(id + " The current try is "
						+ request.getTry());
			// Get the label set object
			LabelSet labelSet = rsvp.getLabelSet();
			if ((labelSet != null) && (labelSet.size() == 0)) { // There is no
																// free
																// wavelengths
																// to allocate
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Out of Lambda!");
				// wavelength continuity constraint violated
				rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); // change
																	// header to
																	// problem
				// Reset no par origem destino. Agora o destino � o source do
				// caminho.
				rsvp.setSDPair(id, request.getSource());
				// Set the error
				error = new Error(Error.Code.RP_LABEL_SET);
				rsvp.setError(error);
				// Do not record the route anymore
				rsvp.resetRecordRoute();
				// Define error domain
				rsvp.setErrorDomain(id);
				// Get the next hop
				nextHop = rsvp.getBackwardNode();
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " -  " + id + " "
							+ nextHop);

				// if the request source is the same of the current node and
				// thereis no nextHop (when the source node does not have
				// lambdas)
				// so I need to set nextHop the current node
				if (request.getSource().equals(id) && nextHop == null) {
					// System.out.println("Tive que adaptar o nexthop");
					nextHop = id;
					// rsvp.setNode(nextHop);
					// response = event;
				}

				if (verbose)
					System.out.println("Out of lambda, going back to source "
							+ request.getSource());

			} else if (target.equals(id)) { // RSVP reached the target node
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " " + id + " - "
							+ "I`m the target!!!");
				// change header to reservation
				rsvp.setNextHeader(Packet.Header.RSVP_RESV);
				// Reset the SD pair to the new values
				rsvp.setSDPair(target, request.getSource());
				// Choose the first free wavelength
				// do the first fit
				int lambda = labelSet.getFirstLabel();
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " " + id
							+ " - using the lambda " + lambda);
				// Create a connection and add it as a object.
				Connection connectionEst = new Connection(rsvp.getPath(),
						lambda, rsvp.getFlowLabel(), request);
				// Set the start time of the connection, which starts after
				// arriving at the source node.
				// Because of that, it uses the round trip time as the time
				// to
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
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " " + id + " - "
							+ "reservating - going back to" + nextHop);
			} else { // Intermediate node
						// Get the next hop

				if (intra) {
					// its a intra-domain request
					// do like Gustavo`s original algorithm

					// this next rerouting option does not apply to this project
					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																	// node
																	// re-routing
						int routingTry = this.sizeHistoryTable(rsvp
								.getFlowLabel());
						nextHop = ((ExplicitRoutingTable) routingTable)
								.nextHop(rsvp, routingTry);

					} else { // None or end-to-end routing

						// --> INTRA-DOMAIN ROUTING PROCESSING STARTS HERE

						// nextHop = ((ExplicitRoutingTable) routingTable)
						// .nextHop(id, request.getTry());
						Packet pacote = new Packet(null, id, target,
								Priority.NORMAL, 0, 0);

						int routingTry = this.sizeHistoryTable(rsvp
								.getFlowLabel());
						nextHop = ((ExplicitRoutingTable) routingTable)
								.nextHop(pacote, request.getTry());
						request.resetTry();
						if (verbose)
							System.out.println(rsvp.getFlowLabel() + " - " + id
									+ " next hop is -->  " + nextHop);
					}

				} else if (inter) {
					// Its an interdomain request
					// There are some improvements here

					// this next rerouting option does not apply to this project
					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																	// node
																	// re-routing
						int routingTry = this.sizeHistoryTable(rsvp
								.getFlowLabel());
						// if(verbos)
						// System.out.println("A routingTry eh " + routingTry);
						// o nextHop nao deve vir dai, deve vir daqui <<VER
						// COM
						// O GUSTAVO>>
						// neste caso eu pego o nexthop
						nextHop = OBGPRoutingTable.getNextHop(id,
								rsvp.getTarget(), routingTry);

						// System.out.println("1-INTERDOMAIN NEXTHOP:   "
						// + nextHop);
						// nextHop =
						// ((ExplicitRoutingTable)routingTable).nextHop(rsvp,routingTry);

					} else { // None or end-to-end routing

						// -->>> INTERDOMAIN ROUTING PROCESSING STARTS HERE
						int routingTry;
						if (historyTable != null && historyTable.isEmpty()) {
							routingTry = 0;
						} else {
							routingTry = this.sizeHistoryTable(rsvp
									.getFlowLabel());
						}
						if (verbose)
							System.out.println(rsvp.getFlowLabel() + " " + id
									+ " -  The routingTry is " + routingTry);
						// obtains the next hop
						nextHop = OBGPRoutingTable.getNextHop(id,
								rsvp.getTarget(), request.getTry());
						// obtains the route for the request
						Vector<String> Route = OBGPRoutingTable.getASPath(id,
								target, request.getTry());
						/**
						 * Exclusao de loops quando da escolha do k caminho
						 * alternativo Preciso verificar se alem de eu ser o
						 * ultimo no no caminho, se existe mais uma ocorrencia
						 * minha em outra posicao caso sim, preciso remover todo
						 * os os nos ate a minha primeira ocorrencia eliminando,
						 * assim, o loop
						 */

						if (rsvp.getPath().containNode(id)
								&& rsvp.getPath().getNodePosition(id) != rsvp
										.getPath().size() - 1) {

							if (verbose)
								System.out
										.println("Opa tenho um loop no caminho "
												+ rsvp.getPath().toString());
							/*
							 * System.out.println("Estou na posicao " +
							 * rsvp.getPath().getNodePosition(id));
							 */
							int i = rsvp.getPath().getNodePosition(id);
							int size = rsvp.getPath().size();
							String source = rsvp.getPath().firstNode();

							/*
							 * System.out.println("O tamanho do vetor eh " +
							 * size);
							 */
							for (int a = size - 1; a > i; a--) {
								/*
								 * System.out
								 * .println("tentando remover o no na posicao "
								 * + a + " " + rsvp.getPath().getNode(a));
								 */
								rsvp.getPath().removeNodeAt(a);

							}
						}
						if (verbose)
							System.out.println("O novo caminho, sem loop, é "
									+ rsvp.getPath().toString());

						// NOTE: by definition, if OBGPRoutingTable.getASPath
						// return a vector of ASPATH, and the first AS on this
						// PATH
						// is the current node, it represents that we need
						// a interdomain routing
						// i.e, find the nexthop on the next domain
						if (nextHop.equals(id)) {
							// I need to go to anothert domain
							// nextHop will be the second element of Route,
							// which is
							// the next domain
							nextHop = Route.get(1); // pega o segundo
													// elemento
													// do ASPath, sempre o
													// outro
													// dominio
							if (verbose)
								System.out
										.println(rsvp.getFlowLabel()
												+ " - "
												+ id
												+ " Inter-domain Path Computation --> next hop is: "
												+ nextHop);
						}

						// reset a tentativa, pois de agora em
						// diante o
						// caminho eh correto
						// se o prox. hop esta em outro dominio
						// e eu nao sou o dominio que apresentou
						// erro
						if (!OBGPControlPlane.getDomain(nextHop).equals(
								OBGPControlPlane.getDomain(id))
								&& !OBGPControlPlane.getDomain(id).equals(
										OBGPControlPlane.getDomain(rsvp
												.getErrorDomain()))
								&& request.getTry() > 0) {
							if (verbose)
								System.out.println("reset na tentativa");
							request.resetTry();
						}

						if (verbose)
							System.out.println(rsvp.getFlowLabel() + " - " + id
									+ " The next hop is: " + nextHop);

						// nextHop =
						// ((ExplicitRoutingTable)routingTable).nextHop(rsvp,request.getTry());
					} // fim do processameto inter

				}

				// a partir de agora, os codigos abaixo executam tanto para
				// inter quanto para intra

				// tenho que ver se eh intradominio e se nao existe
				// conectividade direta
				if (inter) {
					if (OBGPControlPlane.getDomain(nextHop).equals(
							OBGPControlPlane.getDomain(id))
							& !OBGPControlPlane.hasConnectivity(id, nextHop)
							& !id.equals(nextHop)) {
						// tenho que fazer a expansao do PATH para o dominio
						// interno
						Packet pacote = new Packet(null, id, nextHop,
								Priority.NORMAL, 0, 0);
						nextHop = ((ExplicitRoutingTable) routingTable)
								.nextHop(pacote, this.sizeHistoryTable(rsvp
										.getFlowLabel()));
						if (verbose)
							System.out
									.println(rsvp.getFlowLabel()
											+ " - "
											+ "Intra-domain Path Computation: next hop is --> "
											+ nextHop);

					}
				}

				if (nextHop == null || (packet.getHopLimit() == 0)
						|| !(OBGPControlPlane.hasConnectivity(id, nextHop))) { // dead-end
					// System.out.println("I: "
					// + OBGPControlPlane.hasConnectivity(id, nextHop));
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
					// System.out.println("OS links de " + id + "para " +
					// nexhHop + "eh ": links.get(nextHop));
					rsvp.updateMask(links.get(nextHop).getMask());
					// Decrement the number of hops
					rsvp.decrementHopLimit();
				}
			}

			/*
			 * if (nextHop != null) {
			 * 
			 * // Set the next node rsvp.setNode(nextHop);
			 * 
			 * // Set the new time of the event due to transmission time
			 * System.out.println("links para " + nextHop + "com delay " +
			 * links.get(nextHop).getDelay());
			 * //System.out.println(links.get(nextHop));
			 * event.setTimeStamp(event.getTimeStamp() +
			 * links.get(nextHop).getDelay());
			 * 
			 * // System.out.println("O delay de mim " + id + " ate o nexthop "
			 * // + // nextHop + " eh " + links.get(nextHop)); // Return the
			 * response response = event;
			 * 
			 * }
			 */
			// Set the next node
			rsvp.setNode(nextHop);
			response = event;
			break;

		case RSVP_PATH_TEAR: // Forward direction

			rsvp = (CrankRSVP) packet;
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
				if (verbose)
					System.out.println(id + " removendo " + rsvp.getFlowLabel()
							+ " indo para " + nextHop);
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
					rsvp.setErrorDomain(id);
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
							&& (lRequest.getTry() <= this.maxInterReroutingAttempts)
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

					// --->>>>> EH AQUI QUE O TOPOLOGICO IRA FUNCIONAR <<<<<----

					if (target.equals(id)) { // RSVP reached the target node
						// Se chegou no target, que agora é a origem, muda o
						// *tipo* de problema
						if (verbose)
							System.out
									.println(rsvp.getFlowLabel()
											+ " "
											+ id
											+ " - Eu sou a origem. Marcando como lightpath_problem");
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						// Caso contrario, devolve para o nó anterior, ate que
						// se chegue na origem

						nextHop = rsvp.getBackwardNode();
						if (verbose)
							System.out.println(rsvp.getFlowLabel() + " -  "
									+ id + "  devolvendo para " + nextHop);

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
						try{
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
						}catch(Exception e) {
							System.out.println("ERRO");
						}
						
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
			if (verbose)
				System.out.println(rsvp.getFlowLabel() + " - " + id
						+ " realizando reserva com o objetivo de ir para "
						+ rsvp.getTarget());
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
					event.setType(Event.Type.LIGHTPATH_ESTABLISHED);
					if (verbose)
						System.out.println(rsvp.getFlowLabel() + " - " + id
								+ " RESERVADO!!!!");
				} else { // Intermediate node
					// See the next hop
					nextHop = rsvp.getBackwardNode();
					// In case of failure
					if (!(OBGPControlPlane.hasConnectivity(id, nextHop))) {
						System.err.println("connectivity:" + rsvp.toString());
						// Send a resvErr msg to the sender
						rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
						rsvp.setSDPair(id, connection.getTarget());
						// Add the error
						error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
						rsvp.setError(error);
						// Change the next hop
						nextHop = rsvp.getForwardNode();
					}
					if (verbose)
						System.out.println(rsvp.getFlowLabel() + " - " + id
								+ " nexthop da reserva eh " + nextHop);
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
		    //	Graph graph2 = OBGPControlPlane.getDomainGraphs().get(getDomain(id));
			// Identify the failure
			Failure failure = (Failure) packet.getPayload();
			// Flooding of the failure
			if (!failureID.contains(failure.getID())) { // First time
				// Mock local update of the topology
				
				((ExplicitRoutingTable) routingTable).updateFromTopology(graph,
						OBGPControlPlane.getPaths(getDomain(id)));
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
						try {
						double transmissionTime = this.links.get(neighId)
								.getDelay();
						double newTimeStamp = event.getTimeStamp()
								+ transmissionTime;
						// Add to the list of broadcast
						broadcast.add(new Event(newTimeStamp,
								Event.Type.PACKET_ARRIVAL, clonedFrom));
						}
						catch(Exception e) {
							System.out.println("ERRO");
						}
					
						
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

	/*
	 * Obtain the domain of a node Its an alias for OBGPControlPlane.getDomain
	 */
	protected String getDomain(String node) {
		return OBGPControlPlane.getDomain(node);
	}
	
	/**
	 * Este metodo atualiza o grafo do no atual. 
	 * @param graph
	 */
	protected void updateGraph(Graph graph) {
		this.graph = graph;
	}

}