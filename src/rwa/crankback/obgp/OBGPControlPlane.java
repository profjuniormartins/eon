/*
 * Created on 15/02/2008.
 */
package rwa.crankback.obgp;

import event.Event;
import graph.Dijkstra;
import graph.Edge;
import graph.Graph;
import graph.Path;
import graph.YEN;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Vector;

import main.Config;
import main.ControlPlane;
import main.Failure;
import main.Link;
import main.RoutingTableEntry;
import main.SimulationAccounting;
import ops.Accounting;
import ops.Packet;
import rwa.Connection;
import rwa.Error;
import rwa.ExplicitRoutingTable;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.crankback.obgp.OBGPLabelSwitchRouter.ReRouting;
import rwa.crankback.obgp.OBGPLabelSwitchRouter.WavelengthAssignment;

/**
 * 
 * A BGP-like Distributed Control Plane for Routing and Wavelength Assignment,
 * with crankback routing capabilities.
 * 
 * @author Gustavo S. Pavani, Andre Filipe de Moraes Batista
 * @version 1.0
 * 
 */
public class OBGPControlPlane extends ControlPlane {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String, OBGPLabelSwitchRouter> nodes;
	/** The set of links of this network. */
	private LinkedHashMap<String, Link> links;
	/** The maximum hop limit for a packet. */
	protected int hopLimit;
	/** The counter for identifying a request. */
	protected long counterLightpath = 0L;
	/** The accounting of the simulation results. */
	Accounting accounting;
	/** The number of alternative paths of the network. */
	int alternative;

	/** The number of alternative interdomain path of the network. */
	protected int maxInterReroutingAttempts;
	/** The time of the last event. */
	protected double lastTime;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
	/** Time necessary to localize a failure. */
	protected double faultLocalizationTime;
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Maximum number of re-routing attempts allowed. */
	protected int maxReroutingAttempts;
	/** Number of re-routing attempts per LSR. */
	protected int reroutingAttempts;
	/** The chosen wavelength assignment algorithm. */
	protected WavelengthAssignment wa;
	/** The length in bytes for the identification of a node. */
	protected int identificationLength;
	/** Maximum number of interdomain possible routes */
	protected int maxInterRoutes;

	/**
	 * The collection of LSP disrupted by a failure, which are eligible for full
	 * re-routing.
	 */
	protected Hashtable<String, LightpathRequest> disruptedLSP;
	/** The collection of LSP successfully restored after a failure. */
	protected Hashtable<String, Connection> restoredLSP;
	/** The actual collection of shortest paths of this network. */
	public static LinkedHashMap<String, Vector<Path>> setPaths;
	/** The collection of ASBRs in the full domain topology **/
	public static Vector<String> asbrs;

	/** The collection of interdomain routing policies from config file */
	public static LinkedHashMap<String, Vector<String>> setPolicy;

	/** The collection of graphs for each domain **/
	public static LinkedHashMap<String, Graph> domainGraphs;

	/** The collection of shortest paths for each domain **/
	public static LinkedHashMap<String, LinkedHashMap<String, Vector<Path>>> domainSetPaths;

	/** The interdomain routing for all topology - like BGP **/
	protected static LinkedHashMap<String, Vector<Path>> interdomainRoutes;

	protected static LinkedHashMap<String, Connection> reroutedLSP;

	/** A boolean flag for verbose printf debug mode **/
	private boolean verbose = false;

	/**
	 * Creates a new DistributedControlPlane object.
	 * 
	 * @param aConfig
	 *            The XML configuration file for this problem.
	 */
	public OBGPControlPlane(Config aConfig, SimulationAccounting aAccounting) {
		
	

		super(aConfig);
		// System.out.println("Carregando plano de controle");
		this.accounting = (Accounting) aAccounting;
		// Get the links of this network
		links = config.getLinks();
		// System.out.println(links.toString());
		// Create the nodes of this network
		nodes = new LinkedHashMap<String, OBGPLabelSwitchRouter>();
		// Create the storage of disrupted connections by failure
		disruptedLSP = new Hashtable<String, LightpathRequest>();
		restoredLSP = new Hashtable<String, Connection>();
		// Get the simulation parameters
		Hashtable<String, Vector<String>> parameters = config
				.getSimulationParameters();
		// see all the parameters
		if (verbose)
			System.out.println(parameters.toString());

		// get all the asbrs. It will be important to identify the node
		asbrs = parameters.get("/Domains/ASBR");

		// policyFrom = parameters.get("/Domains/POLICY/@from");
		setPolicy = new LinkedHashMap<String, Vector<String>>();

		hopLimit = Integer.parseInt(parameters.get("/OPS/Hop/@limit")
				.firstElement());
		// Get details about the RWA algorithm used
		rerouting = ReRouting.valueOf(parameters.get("/RWA/Routing/@rerouting")
				.firstElement());

		// Get the number of rerounting attempts
		reroutingAttempts = Integer.parseInt(parameters.get(
				"/RWA/Routing/@attempts").firstElement());
		// Get the number of interomdina rerouting attempts
		maxInterReroutingAttempts = Integer.parseInt(parameters.get(
				"/RWA/Routing/@interAttempts").firstElement());
		// The max number of rerouting attempts
		maxReroutingAttempts = Integer.parseInt(parameters.get(
				"/RWA/Routing/@maxAttempts").firstElement());
		this.alternative = reroutingAttempts + 1; // Number of k-shortest paths
		wa = WavelengthAssignment.valueOf(parameters.get("/RWA/WA/@type")
				.firstElement());

		maxInterRoutes = Integer.parseInt(parameters.get(
				"/RWA/Routing/@interRoutes").firstElement());

		boolean serRT = Boolean.parseBoolean(parameters.get(
				"/RWA/Serialize/@rt").firstElement());

		// Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get(
				"/Outputs/Transient/@timeSlice").firstElement());
		actualTimeSlice = timeSlice;
		// Gets the time necessary for fault localization.
		faultLocalizationTime = Double.parseDouble(parameters.get(
				"/Failure/Timing/@localization").firstElement());
		identificationLength = Integer.parseInt(parameters.get(
				"/OPS/Hop/@bytes").firstElement());

		LinkedHashMap<String, ExplicitRoutingTable> rTables = null;

		// create the collection of graphs
		domainGraphs = new LinkedHashMap<String, Graph>();
		reroutedLSP = new LinkedHashMap<String, Connection>();

		/** GENERATION OF PER DOMAIN GRAPH */

		setPerDomainPaths(graph, domainGraphs);

		if (serRT) {
			String fileRT = parameters.get("/RWA/Serialize/@file")
					.firstElement();

			rTables = this.read(fileRT);
		} else {

			/** COMMAND BLOCK: GENERATION OF PER-DOMAIN ROUTES */

			domainSetPaths = new LinkedHashMap<String, LinkedHashMap<String, Vector<Path>>>();
			// Populates the hash table domainSetPaths with path inside each
			// domain
			for (String domain : domainGraphs.keySet()) {
				domainSetPaths.put(domain,
						this.getPaths(domainGraphs.get(domain), alternative));
			}
			// Now, each domain has all the best paths from one node to another
			// (inside the same domain)
		}

		// We need to create a routing table of the node inside its own domain.
		for (String domain : domainGraphs.keySet()) {
			// the Graph representing the domain
			Graph gDomain = domainGraphs.get(domain);
			// for each node inside the domain
			for (String node : gDomain.nodes()) {
				// creates the routing table of this node
				ExplicitRoutingTable rtable;

				// if this table comes from a file, so we need to load it
				if (rTables != null) {
					rtable = rTables.get(node);

				} else { // otherwise, we need to create a new one
					rtable = new ExplicitRoutingTable(node, alternative);

					// and the new routing table will be updated from the
					// topology
					rtable.updateFromTopology(gDomain,
							domainSetPaths.get(domain));

					RoutingTableEntry[][] teste = rtable.getRoutingTable();

				}
				// the adjacents nodes from this node
				Vector<String> adjacent = gDomain.adjacentNodes(node);
				// the linkstate hashmap
				LinkedHashMap<String, LinkState> linkStateSet = new LinkedHashMap<String, LinkState>();

				for (String adjId : adjacent) {
					// get the link between node and adjacent
					Link link = links.get(node + "-" + adjId);
					// create a linkState
					LinkState linkState = new LinkState(link);
					// put this on a link state
					linkStateSet.put(adjId.toString(), linkState);
				}

				// if the current node is an ASBR, so, we need to
				// generate all links to its neighbors
				if (asbrs.contains(node)) {
					if (verbose)
						System.out.println("OBGP Info: Este no eh um ASBR");

					Vector<String> adjancent = graph.adjacentNodes(node);

					for (int i = 0; i < adjancent.size(); i++) {
						if (!getDomain(adjancent.get(i))
								.equals(getDomain(node))) {
							Link link = links
									.get(node + "-" + adjancent.get(i));
							LinkState lstate = new LinkState(link);
							linkStateSet.put(adjancent.get(i).toString(),
									lstate);

						}
					}
				}

				// Create the node and put it into the table.
				OBGPLabelSwitchRouter obgpLSR = new OBGPLabelSwitchRouter(node,
						rtable, linkStateSet, gDomain, wa, rerouting,
						maxReroutingAttempts, reroutingAttempts,
						asbrs.contains(node), getDomain(node),
						maxInterReroutingAttempts);

				nodes.put(node, obgpLSR);

				// System.out.println("=== Routing Table ===\n"
				// + obgpLSR.getRoutingTable().toString());
			}
		}

		/*
		 * GUSTAVO [old] // Initialize the state of each node for (String id :
		 * graph.nodes()) { // System.out.println("...: "+id); // Create the
		 * routing table for this node ExplicitRoutingTable ert; if (rTables !=
		 * null) { ert = rTables.get(id); } else { ert = new
		 * ExplicitRoutingTable(id, alternative); ert.updateFromTopology(graph,
		 * setPaths); // System.out.println(ert.toString()); } // Create the
		 * links adjacent to this node. Vector<String> adjacent =
		 * graph.adjacentNodes(id); // System.out.println(adjacent.toString());
		 * LinkedHashMap<String, LinkState> linkStateSet = new
		 * LinkedHashMap<String, LinkState>(); // for each adjacent node do for
		 * (String adjId : adjacent) { Link link = links.get(id + "-" + adjId);
		 * LinkState linkState = new LinkState(link);
		 * linkStateSet.put(adjId.toString(), linkState); } // Create the node
		 * and put it into the table. // AQUI EU CRIO OS NODES
		 * OBGPLabelSwitchRouter node = new OBGPLabelSwitchRouter(id, ert,
		 * linkStateSet, graph, wa, rerouting, maxReroutingAttempts,
		 * reroutingAttempts); nodes.put(id, node); }
		 */

		/** INTER-DOMAIN ROUTING POLICY */

		// generates the inter-domain routing
		// required by the next steps
		// read policy from XML and generates the policy rules
		setPolicy(setPolicy);
		// generateASPathv2(setPolicy);
		// simulates Path dissemation from BGP generating AS-PATH
		LinkedHashMap<String, ArrayList<Vector<String>>> ASPath = generateASPath(setPolicy);
		OBGPRoutingTable interdomainRoutes = new OBGPRoutingTable(ASPath);
		// System.out.println(domainGraphs.toString());
		// Fill the Interdomain Routing Table
		// All interdomain routes does not contain :
		// Example: 1:a-1:g is an intra-domain route
		// and 1-2 is an interdomain route
		// for (String key : setPolicy.keySet()) {
		// if (!key.contains(":")) {
		// interdomainRoutes.putEntry(key, ASPath.get(key));
		// interdomainRoutes.putEntry(key, setPolicy.get(key));
		// }

		// }

		System.out.println("Tabela de Rotas - Interdominio");
		System.out.println(interdomainRoutes.toString());
		// System.out.println("Os links:");
		// System.out.println(config.getLinks().toString());

		System.out.println("JA PASSEI PELO CONSTRUTOR");
	}

	/**
	 * Generates a collection of graphs, one graph for each domain
	 */
	protected void setPerDomainPaths(Graph graph,
			LinkedHashMap<String, Graph> perDomainGraphSet) {

		// for each node from the global graph
		for (String id : graph.nodes()) {
			String domain = getDomain(id);
			if (!perDomainGraphSet.containsKey(domain)) {
				perDomainGraphSet.put(domain, new Graph());
			}
			// Here we get the graph of this domain
			Graph graphDomain = perDomainGraphSet.get(domain);
			try {
				// try do add the node in the graph, if it does not exist yet
				if (!graphDomain.nodes().contains(id)) {
					graphDomain.addNode(id);
				}
				// try do add all the nodes that are in the same domain and
				// have edge with the node (id)
				Vector<Edge> edges = graph.adjacentEdges(id);
				// discover all neighbors
				Vector<String> neighbors = graph.adjacentNodes(id);

				for (int i = 0; i < neighbors.size(); i++) {
					// if the neighbors is in the same domain
					if (getDomain(neighbors.get(i)).equals(domain)) {
						// try do add the neighbor node to domain graph
						if (!graphDomain.nodes().contains(neighbors.get(i))) {
							graphDomain.addNode(neighbors.get(i));
						}
						// now, try do add the edges from node (id) and to the
						// neighbor
						for (int j = 0; j < edges.size(); j++) {
							if (edges.get(j).getSource().equals(id)
									&& edges.get(j).getDestination()
											.equals(neighbors.get(i))) {
								graphDomain.addEdge(id, neighbors.get(i), edges
										.get(j).getValue());

							}
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Process the specified event.
	 * 
	 * @param event
	 *            The event to be processed.
	 * @return The processed event. Null, if nothing else is to be returned to
	 *         the scheduler.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Event process(Event event) {
		// The id of the processing node
		String id;
		// Update the time stamp of the last event processed.
		lastTime = event.getTimeStamp();
		// Do transient accounting, if applicable
		if (lastTime > actualTimeSlice) {
			// Update the actual time slice
			actualTimeSlice = actualTimeSlice + timeSlice;
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
			CrankRSVP rsvpPath = new CrankRSVP(request, hopLimit,
					counterLightpath);
			this.counterLightpath++; // Increment the counter
			// Create a new event for setting up the lightpath
			return new Event(event.getTimeStamp(), Event.Type.PACKET_ARRIVAL,
					rsvpPath);

		case LIGHTPATH_ESTABLISHED: // Lightpath established
			if (verbose)
				System.out
						.println("O LIGHTPATH FOI ESTABELECIDO COM SUCESSO!!! INTER-DOMAIN WORKS!!!!");
			CrankRSVP rsvpConfirm = (CrankRSVP) event.getContent();
			// Gets the connection object.
			Connection connectionEst = (Connection) rsvpConfirm.getObject();
			// Gets the duration of the lightpath
			double duration = connectionEst.getRequest().getDuration();
			// Accounts the successful lightpath establishment
			accounting.addSuccesful(rsvpConfirm);
			// See if it is a successful re-routing of a failed LSP
			if (rsvpConfirm.isRerouting()) {
				reroutedLSP.put(rsvpConfirm.getFlowLabel(), connectionEst);
			}

			if (rsvpConfirm.inRestoration()) {
				restoredLSP.put(rsvpConfirm.getFlowLabel(), connectionEst);
			}
			// System.out.println(event.toString());
			// Return a new event for tearing down the lightpath when
			// appropriate

			return new Event((event.getTimeStamp() + duration),
					Event.Type.LIGHTPATH_TEARDOWN, connectionEst);

		case LIGHTPATH_PROBLEM:

			CrankRSVP rsvpErr = (CrankRSVP) event.getContent();
			LightpathRequest lRequest;
			// Get the error status
			Error error = rsvpErr.getError();
			// Get the label
			int label = Integer.parseInt(rsvpErr.getFlowLabel());
			CrankRSVP rsvpRetry = null; // new Rsvp message
			Error.Code errorCode = error.getErrorCode();
			// Allocation of wavelength contention problem

			if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {

				lRequest = (LightpathRequest) ((Connection) rsvpErr.getObject())
						.getRequest();
				rsvpRetry = new CrankRSVP(lRequest, hopLimit, label);
				// System.out.println("Contention: "+lRequest.toString());
				if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {
					rsvpRetry.setRestoration(); // set the flag for restoration
					// System.out.println(event.toString());
				}
				// LSP failure forward or backward

			} else if (errorCode.equals(Error.Code.LSP_FAILURE)) {

				Connection disrupted = (Connection) rsvpErr.getObject();
				lRequest = (LightpathRequest) disrupted.getRequest();
				// reset the counter of retries
				lRequest.resetTry();
				// Calculates the rest of time of the connection
				double residualDuration = lRequest.getDuration()
						- (event.getInitialTimeStamp() - disrupted
								.getStartTime());
				// System.out.println("residual: "+residualDuration);
				lRequest.setDuration(residualDuration);
				// Create a new path message
				rsvpRetry = new CrankRSVP(lRequest, hopLimit, label);
				// Set the label indicating to tackle the failure
				rsvpRetry.setRestoration();
				// Adds the connection to the list of disrupted LSP
				disruptedLSP.put(rsvpErr.getFlowLabel(), lRequest);
				// System.out.println("Adding LSP failure: "+rsvpErr.getFlowLabel()+" ,"+disrupted.toString());
				// Wavelength continuity constraint violated or no link
				// available, use alternate path

			} else if ((errorCode.equals(Error.Code.RP_LABEL_SET))
					|| (errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE))) {

				// --->>> TRATANDO ESTE TIPO DE ERRO NO PLANO DE CONTROLE <-- //

				lRequest = (LightpathRequest) rsvpErr.getObject();
				lRequest.addTry(); // add a try to the counter of tries
				// System.out.println("Adicionando tentativa");
				// ATENCAO: mudei codificacao do tryAgain para suportar
				// roteamento inter
				if (lRequest.tryAgain()) { // resend the request
					rsvpRetry = new CrankRSVP(lRequest, hopLimit, label);
					// System.out.println("Vou tentar novamente");
					rsvpRetry.setRerouting(true);
					if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {

						rsvpRetry.setRestoration(); // set the flag of
													// restoration
						// System.out.println(event.toString());
					}
				} else {
					// se nao posso tentar novamente, marco como falha na
					// contagem. Trabalhar nisto.
					// Accounts the failed lightpath request
					if (verbose)
						System.out
								.println("Na na nina nao....ja tentou muito!!!");

					accounting.addFailed(rsvpErr);
					// if (disruptedLSP.containsKey(rsvpErr.getFlowLabel()))
					// System.out.println("Failed:"+event.toString());
				}
			} else if (errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)) {
				if (verbose)
					System.out
							.println("Vish maria, excedeu o limite de tentar novamente");
				// Accounts the failed lightpath request
				accounting.addFailed(rsvpErr);
			}
			// Now, return the result.
			if (rsvpRetry != null) {
				// System.out.println("Devolvendo como packet arrival");
				return new Event(event.getTimeStamp(),
						Event.Type.PACKET_ARRIVAL, rsvpRetry);
			} else
				return null;
		case LIGHTPATH_TEARDOWN: // Remove connection

			Connection connectionTear = (Connection) event.getContent();
			String flowLabel = connectionTear.getID();
			if ((disruptedLSP.get(flowLabel) == null)
					|| ((restoredLSP.get(flowLabel) != null) && (restoredLSP
							.get(flowLabel).getPath().equals(connectionTear
							.getPath())))) {
				// Send RSVP PathTear message
				CrankRSVP rsvpTear = new CrankRSVP(connectionTear,
						Packet.Header.RSVP_PATH_TEAR,
						connectionTear.getSource(), connectionTear.getTarget());
				// System.out.println(rsvpTear.toString());
				// System.out.println("Eh hora de finalizar a requisicao " +
				// flowLabel);
				return new Event(event.getTimeStamp(),
						Event.Type.PACKET_ARRIVAL, rsvpTear);

			} else { // Ignore the teardown associated to a failed LSP, since it
						// is already cleaned and rerouted.
				return null;
			}
		case LIGHTPATH_REMOVED: // Confirmation of connection removal
			// System.out.println(((Packet)event.getContent()).getFlowLabel() +

			// " finalizado com sucesso em todos os nodes");
			// System.out.println(event.toString());
			return null;
		case PACKET_ARRIVAL: // Ant
			// Get the packet
			Packet packet = (Packet) event.getContent();
			// Get the node associated to this packet
			id = packet.getNode();
			// Give the packet to the right node
			// System.out.println("Enviando requisicao  para " + id);
			OBGPLabelSwitchRouter procNode = nodes.get(id);
			if (procNode != null) { // Node functioning
				// Process the event
				Event response = procNode.process(event);
				// System.out.println("A resposta foi " + response.getType());
				if (response.getType().equals(Event.Type.IGNORE))
					return null;
				else
					return response;
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
			// detect and remove "orphan" nodes, i.e., disconnected ones.
			Vector<String> aNodes = (Vector<String>) graph.nodes().clone();
			for (String node : aNodes) {
				int degree = graph.adjacencyDegree(node);
				if (degree == 0) {
					try {
						// System.out.println("Removing node: "+node);
						graph.removeNode(node);
						// Remove the node from the list of nodes
						nodes.remove(node);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			// Recalculate the set of paths

			// remove all domain graphs
			domainGraphs.clear();

			// re-creates per-domain graphs
			setPerDomainPaths(graph, domainGraphs);

			// update the domain set path
			for (String domain : domainGraphs.keySet()) {
				domainSetPaths.put(domain,
						this.getPaths(domainGraphs.get(domain), alternative));
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
			// Recalculate the set of paths

			// remove all domain graphs
			domainGraphs.clear();

			// re-creates per-domain graphs
			setPerDomainPaths(graph, domainGraphs);

			// update the domain set path
			for (String domain : domainGraphs.keySet()) {
				domainSetPaths.put(domain,
						this.getPaths(domainGraphs.get(domain), alternative));
			}

			// now, update all graphs for each node
			for (String domain : domainGraphs.keySet()) {
				for (String node : nodes.keySet()) {
					if (domain.equals(getDomain(node))) {
						OBGPLabelSwitchRouter tempNode = nodes.get(node);
						tempNode.updateGraph(domainGraphs.get(domain));
					}

				}
			}

			// setPaths = this.getPaths(graph, alternative);
			// Remove the node from the list of nodes
			nodes.remove(id);
			// Return the response containing the failure of the multiple links
			return new Event(lastTime, Event.Type.MULTIPLE, failuresNode);
		default:
			System.err.println("Unknown event: " + event.toString());
			return null;
		}
	}

	/**
	 * Returns the domain of a node, by syntax analysis of the node ID
	 * 
	 * @param id
	 *            The id of the node. For example: node 1:a, represents domain 1
	 */

	public static String getDomain(String nodeId) {

		return nodeId.split(":")[0];
	}

	/**
	 * Prints the last simulation time.
	 */
	public void updateValues() {
		System.out.println("LastTime: " + lastTime);
		// System.out.println("Rerouted: " + restoredLSP.toString());
		// System.out.println("Crack rerouted success: " +
		// reroutedLSP.toString());
		System.out.println("Number of Crack rerouted success: "
				+ reroutedLSP.size());
	}

	/**
	 * Read a serialized routing table.
	 * 
	 * @param file
	 *            The file to be read.
	 * @return The set of routing tables.
	 */
	@SuppressWarnings("unchecked")
	public LinkedHashMap<String, ExplicitRoutingTable> read(String file) {
		LinkedHashMap<String, ExplicitRoutingTable> result = null;
		try {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			result = (LinkedHashMap<String, ExplicitRoutingTable>) ois
					.readObject();
			ois.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Create the set of shortest paths
	 * 
	 * @param topology
	 *            The topology of the network
	 * @param alternative
	 *            The number of alternative paths
	 * @return The alternatives paths for each pair source-destination of the
	 *         topology.
	 */
	public LinkedHashMap<String, Vector<Path>> getPaths(Graph topology,
			int alternative) {
		LinkedHashMap<String, Vector<Path>> routes = new LinkedHashMap<String, Vector<Path>>();
		YEN yen = new YEN();

		for (String src : topology.nodes()) {
			// System.out.println("Paths para no: " + src);
			for (String tgt : topology.nodes()) {
				if (!src.equals(tgt)) { // Assure different nodes in the pair
					Vector<Path> paths = null;

					try {
						paths = yen.getShortestPaths(src, tgt, topology,
								alternative);

					} catch (Exception e) {
						e.printStackTrace();
					}
					routes.put(src + "-" + tgt, paths);
				}
			}
		}
		// System.out.println(routes.toString());
		return routes;
	}

	/**
	 * Returns the set of shortest paths of the actual topology.
	 * 
	 * @return The set of shortest paths of the actual topology.
	 */
	public static LinkedHashMap<String, Vector<Path>> getPaths() {
		return setPaths;
	}

	public static LinkedHashMap<String, Vector<Path>> getPaths(String domain) {
		return domainSetPaths.get(domain);
	}

	/**
	 * Generates the inter-domain routing policy
	 * 
	 * @param policy
	 *            - an LinkedHashMap to receive the policy
	 */
	protected void setPolicy(LinkedHashMap<String, Vector<String>> policy) {

		// get all the parameters from config file
		Vector<String> policyFrom = parameters.get("/Domains/POLICY/@from");
		Vector<String> policyTo = parameters.get("/Domains/POLICY/@to");
		Vector<String> policyBy = parameters.get("/Domains/POLICY/@by");

		for (int i = 0; i < policyFrom.size(); i++) {
			Vector<String> temp = new Vector<String>();
			// if the policy entry has a comma (,), there`s a multiple possible
			// transit domain
			if (policyBy.get(i).contains(",")) {
				String[] policyHop = policyBy.get(i).split(",");
				for (int p = 0; p < policyHop.length; p++) {
					temp.add(policyHop[p]);
				}
			} else {
				// otherwise, just add the hop in the config file
				// All the policies with only one path, will apply this
				temp.add(policyBy.get(i));
			}
			policy.put(policyFrom.get(i) + "-" + policyTo.get(i), temp);
		}

	}

	/**
	 * This method is an abstraction of BGP message exchange for route
	 * dissemination It will generate the AS-PATH for all domain based on XML
	 * file description
	 * 
	 * @param policy
	 *            - the XML parsed policy
	 */
	protected LinkedHashMap<String, ArrayList<Vector<String>>> generateASPath(
			LinkedHashMap<String, Vector<String>> policy) {

		LinkedHashMap<String, ArrayList<Vector<String>>> ASPath = new LinkedHashMap<String, ArrayList<Vector<String>>>();

		// get all the policies
		for (String key : policy.keySet()) {
			// quero garantir que exista no minimo 1 politica
			if (policy.get(key) != null && policy.get(key).size() > 0) {
				// vou iterar pelos elementos da minha politica
				String ASsource = key.split("-")[0];
				String ASDestination = key.split("-")[1];
				for (int i = 0; i < policy.get(key).size(); i++) {
					String value = policy.get(key).get(i); // pego um valor da
															// politica
					// preciso verificar se ele é uma politica direta, ou nao
					if (value.contains(":")) { // logo eh um link direto
						// entao preciso colocar um dos ASBR's do dominio da
						// origem como rota
						Vector<String> caminho = new Vector<String>();
						Vector<String> neigh = graph.adjacentNodes(value);
						// System.out.println("Estou analisando o valor " +
						// value
						// + " e ele possui os seguintes vizinhos "
						// + neigh.toString());
						int vizinhos = 0;
						Vector<String> vizinhanca;

						for (String neighbour : neigh) {
							if (getDomain(neighbour).equals(ASsource)) {
								vizinhanca = new Vector<String>();
								vizinhanca.add(neighbour);
								vizinhanca.add(value);
								ArrayList<Vector<String>> cam;
								if (ASPath.containsKey(key)
										&& ASPath.get(key) != null) {
									cam = ASPath.get(key);
								} else {
									cam = new ArrayList<Vector<String>>();
								}

								cam.add(vizinhanca);
								if (verbose)
									System.out.println("Adicionando caminho "
											+ cam.toString() + " para " + key);
								ASPath.put(key, cam);

							}
						}

						/*
						 * System.out.println("O vetor de vizinhanca eh " +
						 * vizinhanca.toString());
						 * 
						 * while(vizinhanca.size() > 0) {
						 * caminho.add(vizinhanca.lastElement());
						 * vizinhanca.remove(vizinhanca.lastElement());
						 * 
						 * 
						 * 
						 * 
						 * 
						 * 
						 * 
						 * 
						 * /* for (String neighbour : neigh) { if
						 * (getDomain(neighbour).equals(ASsource)) { // ou seja,
						 * descobri quem Ã© eh o ASBR meu que se // comunica com
						 * o proximo ASBR System.out.println(ASsource +
						 * " Como eh do mesmo dominio, vou adicionar " +
						 * neighbour); caminho.add(neighbour); break; } }
						 * 
						 * 
						 * 
						 * 
						 * 
						 * 
						 * System.out.println("Adicionando " + value);
						 * caminho.add(value); ArrayList<Vector<String>> cam;
						 * if(ASPath.containsKey(key) && ASPath.get(key)!=null)
						 * { cam = ASPath.get(key); }else { cam = new
						 * ArrayList<Vector<String>>(); }
						 * 
						 * cam.add(caminho);
						 * System.out.println("Adicionando caminho " +
						 * cam.toString() + " para " + key) ; ASPath.put(key,
						 * cam); System.out.println("O AsPath eh " +
						 * ASPath.get(key).toString()); }
						 */
					} else { // logo entao representa um numero de AS, preciso
								// iterar para descobrir tudo

						Graph grafoDominioSource = domainGraphs.get(ASsource);
						int viz = 0;
						/*
						 * for(String node: grafoDominioSource.nodes()) {
						 * System.out.println("O dominio " + ASsource +
						 * " possui o node " + node); System.out.println(
						 * "O numero do index deste no no grafo global eh " +
						 * graph.getNodeIndex(node));
						 * 
						 * 
						 * 
						 * }
						 */
						// System.out.println("Descobri que " + ASsource +
						// " possui " + ASPath.get(ASsource+"-"+value)+
						// " como links com " + value);

						Vector<String> caminho = new Vector<String>();
						Vector<String> temp = new Vector<String>();
						temp.add(ASsource);
						String hop = value;

						while (!hop.equals(ASDestination)) {

							if (policy.get(hop + "-" + ASDestination)
									.firstElement().contains(":")) {

								// System.out
								// .println("estou em "
								// + value
								// + " O proximo hop eh "
								// + policy.get(hop + "-"
								// + ASDestination));
								temp.add(hop);
								hop = policy.get(hop + "-" + ASDestination)
										.firstElement().split(":")[0];

								// System.out
								// .println(" :  novo hop gerado antes do fim do while: "
								// + hop);
							} else {

								temp.add(hop);
								String hopantigo = hop;
								hop = policy.get(hop + "-" + ASDestination)
										.firstElement();
								if (temp.contains(hop)) {
									hop = policy.get(
											hopantigo + "-" + ASDestination)
											.lastElement();
								}
								// System.out
								// .println("novo hop gerado antes do fim do while: "
								// + hop);
							}

							// System.out
							// .println("Passei aqui, ja gerei todos os hops = "
							// + temp.toString());

						}

						temp.add(ASDestination);
						// System.out
						// .println("Sai do while!, os hops para o par SD "
						// + key + "  sao: " + temp.toString());
						if (!ASPath.containsKey(key)) {
							ArrayList<Vector<String>> lista = new ArrayList<Vector<String>>();
							lista.add(temp);
							ASPath.put(key, lista);
						} else {
							ASPath.get(key).add(temp);
						}
					}
				}
				// MARCO: possui em ASPath a lista de prefixo de todos os
				// dominios
				// System.out.println("O ASPath obtido foi "+
				// ASPath.toString());
			}
		}
		for (String key : ASPath.keySet()) {

			ArrayList<Vector<String>> linha = ASPath.get(key);

			for (int i = 0; i < linha.size(); i++) {
				Vector<String> elemento = linha.get(i);
				if (elemento.firstElement().contains(":")) {
					// System.out.println(elemento +
					// " -  nao preciso fazer nada");
				} else {
					Vector<String> teste = new Vector<String>();
					for (int j = 0; j < elemento.size() - 1; j++) {
						// System.out.println(ASPath.get(elemento.get(j)+"-"+elemento.get(j+1)));
						int m;

						for (m = 0; m < ASPath
								.get(elemento.get(j) + "-"
										+ elemento.get(j + 1)).get(0).size(); m++) {
							teste.add(ASPath
									.get(elemento.get(j) + "-"
											+ elemento.get(j + 1)).get(0)
									.get(m));
						}
						// teste.addAll(ASPath.get(elemento.get(m)+"-"+key.split("-")[1]).get(0));
					}
					// teste.add(ASPath.get(elemento.get(teste.lastElement()))+"-"+elemento.get()).get(0).get(m))
					// teste.add(ASPath.get(ASPath.get(elemento.get(m)+"-"+elemento.get(j+1)).get(0).get(m)));
					// System.out.println(elemento + " ja trabalhei, ficou = " +
					// teste.toString());
					linha.remove(i);
					linha.add(i, teste);
				}
			}
		}

		

		return ASPath;

	}

	public static LinkedHashMap<String, Graph> getDomainGraphs() {
		return domainGraphs;
	}

	public static int getNumberofDomains() {
		return domainGraphs.size();
	}

	public static String getSourceNode(String domain) {

		return getSourceNode(random, domainGraphs.get(domain));
	}

	public static String getTargetNode(String sourceNode) {
		String domain = getDomain(sourceNode);
		return getTargetNode(sourceNode, domainGraphs.get(domain));
	}

}
