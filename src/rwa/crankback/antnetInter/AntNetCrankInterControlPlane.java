/*
 * Created on 15/02/2008.
 * Update on Sep 2012.
 */
package rwa.crankback.antnetInter;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import javax.accessibility.AccessibleStateSet;

import antnet.NeighborAttr;
import antnet.StatisticalParametricModel;
import ops.Accounting;
import ops.Packet;
import random.MersenneTwister;
import rwa.Connection;
import rwa.Error;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.crankback.antnetInter.CrankRSVP;
import rwa.crankback.obgp.OBGPLabelSwitchRouter;
import rwa.crankback.LabelSwitchRouter;
import rwa.crankback.LabelSwitchRouter.ReRouting;
import rwa.crankback.LabelSwitchRouter.WavelengthAssignment;
import util.QuickSort;
import event.Event;
import graph.Edge;
import graph.Graph;
import graph.Path;
import graph.YEN;
import main.Config;
import main.ControlPlane;
import main.Failure;
import main.Link;
import main.RoutingTableEntry;
import main.SimulationAccounting;

/**
 * This class is a control plane for the AntNet framework with crankback
 * capabilities, but for the *interdomain* RWA problem.
 * 
 * @author Andre Filipe M. Batista, Gustavo S. Pavani
 * @version 1.0
 */
public class AntNetCrankInterControlPlane extends ControlPlane {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** Workaround for transporting static fields. */
	StaticTransporter transporter;
	/** The set of links of this network. */
	protected static LinkedHashMap<String, Link> links;
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String, AntNetInterLSR> nodes;
	/** The accounting of the simulation results. */
	Accounting accounting;
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
	/** The delay for resending a path message for restoring LSP. */
	protected double holdoff;
	/** The rate for launching ants during the hold-off timer. */
	protected double restoreAntRate;
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Maximum number of re-routing attempts allowed. */
	protected int maxReroutingAttempts;
	/** Number of re-routing attempts per LSR. */
	protected int reroutingAttempts;
	/** The chosen wavelength assignment algorithm. */
	protected WavelengthAssignment wa;
	/** The length in bytes for the identification of a link or a node. */
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
	/** Random number generator for ants. */
	public static MersenneTwister rngAnt;

	// novas variaveis
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

	protected static LinkedHashMap<String, ArrayList<Vector<String>>> prefixASPath = new LinkedHashMap<String, ArrayList<Vector<String>>>();

	public static HashMap<String, LinkedHashMap<String, LinkState>> antNetLSRLink = new HashMap<String, LinkedHashMap<String, LinkState>>();

	/** The logging generator. */
	// private static Logger logger =
	// Logger.getLogger(AntNetCrankControlPlane.class.getName());

	/**
	 * Creates a new AntNetControlPlane object.
	 * 
	 * @param aConfig
	 *            The XML configuration file.
	 * @param aAccounting
	 *            The accounting for the simulation results.
	 */
	public AntNetCrankInterControlPlane(Config aConfig,
			SimulationAccounting aAccounting) {
		super(aConfig);
		this.accounting = (Accounting) aAccounting;
		// Get the simulation parameters
		Hashtable<String, Vector<String>> parameters = config
				.getSimulationParameters();
		// Get the links of this network
		links = config.getLinks();
		// Create the nodes of this network
		nodes = new LinkedHashMap<String, AntNetInterLSR>();
		// Create the storage of disrupted connections by failure
		disruptedLSP = new Hashtable<String, LightpathRequest>();
		restoredLSP = new Hashtable<String, Connection>();
		// Get the simulation parameters
		hopLimit = Integer.parseInt(parameters.get("/OPS/Hop/@limit")
				.firstElement());
		holdoff = Double.parseDouble(parameters.get("/Ant/Holdoff/@timer")
				.firstElement());
		restoreAntRate = Double.parseDouble(parameters.get(
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
		// The power factor to enhance the difference in the heuristics
		// correction.
		double powerFactor = Double.parseDouble(parameters.get(
				"/Ant/Routing/@power").firstElement());
		// Gets the correction (alpha) parameter for routing forward ants.
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
		// Get details about the RWA algorithm used
		boolean deterministic = Boolean.parseBoolean(parameters.get(
				"/RWA/Routing/@deterministic").firstElement());
		rerouting = ReRouting.valueOf(parameters.get("/RWA/Routing/@rerouting")
				.firstElement());
		reroutingAttempts = Integer.parseInt(parameters.get(
				"/RWA/Routing/@attempts").firstElement());
		maxReroutingAttempts = Integer.parseInt(parameters.get(
				"/RWA/Routing/@maxAttempts").firstElement());
		wa = WavelengthAssignment.valueOf(parameters.get("/RWA/WA/@type")
				.firstElement());
		int wavelength = Integer.parseInt(parameters.get("/RWA/WA/@wavelength")
				.firstElement());
		int waWindow = (int) Double.parseDouble(parameters.get(
				"/RWA/WA/@window").firstElement());
		boolean waSliding = Boolean.parseBoolean(parameters.get(
				"/RWA/WA/@sliding").firstElement());

		maxInterRoutes = Integer.parseInt(parameters.get(
				"/RWA/Routing/@interRoutes").firstElement());

		// get all the asbrs. It will be important to identify the node
		asbrs = parameters.get("/Domains/ASBR");

		// policyFrom = parameters.get("/Domains/POLICY/@from");
		setPolicy = new LinkedHashMap<String, Vector<String>>();

		// create the collection of graphs
		domainGraphs = new LinkedHashMap<String, Graph>();
		reroutedLSP = new LinkedHashMap<String, Connection>();


		AntNetCrankInterRoutingTable interdomainRoutes;

		setPerDomainPaths(graph, domainGraphs);
		
		
		if (verbose) {
			System.out.println("Domains:");
			for (String graphs : domainGraphs.keySet()) {
				System.out.println(graphs + " "
						+ domainGraphs.get(graphs).nodes());
			}
		}
		for (String grafo : domainGraphs.keySet()) {
			// System.out.println(domainGraphs.toString());
			// Initialize the state of each node
			for (String id : domainGraphs.get(grafo).nodes()) {
				// Create the pheromone routing table for this node
				if (verbose)
					System.out.println("Gerando routing table para " + id);
				AntNetCrankRoutingTable art = new AntNetCrankRoutingTable(id,
						confidenceLevel, firstWeight, secondWeight, amplifier,
						correction, powerFactor, deterministic);
				art.updateFromTopology(domainGraphs.get(grafo));
				if (verbose)
					System.out.println("A routing table gerada foi "
							+ art.toString());
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
				//	System.out.println("Inserido link " + id + "-" + adjId);

				}
				
				antNetLSRLink.put(id, antLinks);

				// System.out.println("TODOS OS LINK STATE: " + antLinks);
				// Create the wavelength usage table.
				WavelengthUsageTable usageTable = new WavelengthUsageTable(id,
						domainGraphs.get(grafo), wavelength, waWindow, waSliding);
				// Create the node and put it into the table.
				AntNetInterLSR colony = new AntNetInterLSR(id, art, antLinks,
						domainGraphs.get(grafo), wa, rerouting, maxReroutingAttempts,
						reroutingAttempts, model, usageTable);
				nodes.put(id, colony);
				if (verbose)
					System.out.println("--> Criado node " + id);
			}
		} // fim da criacao da tabela de roteamento para cada node, em cada
			// grafo
			// aqui vai o codigo para criar a tabela de roteamento interdominio

		// preciso ver como fazer a tabela para roteamento inter

		// generates the inter-domain routing
		// required by the next steps
		// read policy from XML and generates the policy rules
		setPolicy(setPolicy);

		// simulates Path dissemation from BGP generating AS-PATH

		LinkedHashMap<String, ArrayList<Vector<String>>> ASPath = generateASPath(setPolicy);
		// if (verbose)
		// System.out.println("Gerado ASPath routing table");

		// creates an interdomain routing table
		// ATENCAO: o numero 9 esta hardcoded para esta simulacao, representa o
		// numero de dominios
		// TODO: colocar isto no XML.

		// instancia o conjunto de tabelas de roteamento interdominio por
		// ferormonio
		interdomainRoutes = new AntNetCrankInterRoutingTable("",
				confidenceLevel, firstWeight, secondWeight, amplifier,
				correction, powerFactor, deterministic, ASPath, 9);

		// instancia o conjunto de tabelas do modelo parametrico para o
		// roteamento interdominio

		InterStatisticalParametricModel interStatisticalModel = new InterStatisticalParametricModel(
				exponentialFactor, reductor);

		//
		// if (verbose) { System.out.println("Tabela de Rotas - Interdominio");
		System.out.println(interdomainRoutes.toString());
		// }
		//

		/**
		 * INICIALIZACAO DE TABELA DE ROTEAMENTO INTER POR FERORMONIO E TABELA
		 * DO MODELO PARAMETRICO
		 */
		//
		// percorrendo todos os ASs
		for (int i = 0; i < asbrs.size(); i++) {
			String AS = getDomain(asbrs.get(i));

			// cria uma tabela de roteamento interdominio por ferormonio para
			// este AS
			if (!interdomainRoutes.hasAS(AS)) {
				// System.out.println("Adicionando " + AS
				// + " na tabela de roteamento inter");
				interdomainRoutes.putASEntry(AS);
				// System.out.println(interdomainRoutes.testeRoute.get(AS)
				// .toString());
			}
			// cria o modelo estatistico parametrico
			// 9 é o numero de dominos que existe na simulacao
			// TODO: colocar no XML o atributo que informa o numero de ASs, ou
			// criar um metodo que lendo o XML gera este numero.
			interStatisticalModel.create(AS, 9);

		}

		// Gera os niveis de ferormonio iniciais nas tabelas de roteamento por
		// ferormonio para cada dominio
		// usando uma inicializacao inteligente
		interdomainRoutes.generateInitialPheromoneLevel();
		

		// imprime estas rotas
		System.out.println(interdomainRoutes.printPherormones());
		System.out.println(interStatisticalModel.toString());

		// interdomainRoutes.updateInterPheromonefromTopology(ASPath);

		// updateInterPheromonefromTopology(ASPath, interdomainRoutes);
		// System.out.println("Os links:");
		// System.out.println(config.getLinks().toString());

		// System.out.println(interdomainRoutes.printPherormones());
		// System.out.println(links.get("1:a-1:b"));
		System.out.println("JA PASSEI PELO CONSTRUTOR");

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
		// Update the time stamp of the last event to be processed processed.
		lastTime = event.getTimeStamp();
		// Do transient accounting, if applicable
		if (lastTime > actualTimeSlice) {
			// Update the actual time slice
			actualTimeSlice = actualTimeSlice + timeSlice;
		//	System.out.print(".");
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
			CrankRSVP rsvpConfirm = (CrankRSVP) event.getContent();
			// Gets the connection object.
			Connection connectionEst = (Connection) rsvpConfirm.getObject();
			// Gets the duration of the lightpath
			double duration = connectionEst.getRequest().getDuration();
			// Accounts the successful lightpath establishment
		//	System.out.println("Adicionando para metrica de OK! " + rsvpConfirm.getFlowLabel());
			accounting.addSuccesful(rsvpConfirm);
			// See if it is a successful re-routing of a failed LSP
			if (rsvpConfirm.inRestoration()) {
				restoredLSP.put(rsvpConfirm.getFlowLabel(), connectionEst);
			}
			
			//See if it is a successful re-routing of a failed LSP
			if (rsvpConfirm.isRerouting()) {
				reroutedLSP.put(rsvpConfirm.getFlowLabel(), connectionEst);
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
				lRequest = (LightpathRequest) rsvpErr.getObject();
				lRequest.addTry(); // add a try to the counter of tries
				// Resend the request using holdoff-timer - Photonics Network
				// Communications 2008 (Restoration)
				
				if (lRequest.tryAgain()
						&& disruptedLSP.containsKey(rsvpErr.getFlowLabel())
						&& this.rerouting.equals(ReRouting.END_TO_END)) { // resend
																			// the
																			// request
					rsvpRetry = new CrankRSVP(lRequest, hopLimit, label);
					if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {
						rsvpRetry.setRestoration(); // set the flag of
													// restoration
						// System.out.println(event.toString());
						Vector<Event> multiple = new Vector<Event>();
						// add the hold-off timer for resending the message.
						multiple.add(new Event(holdoff + event.getTimeStamp(),
								Event.Type.PACKET_ARRIVAL, rsvpRetry));
						// System.out.println(event.toString());
						int times = (int) (holdoff * restoreAntRate);
						double delay = 0.0; // Delay between two consecutive
											// ants
						int bytesHop = Integer.parseInt(parameters.get(
								"/OPS/Hop/@bytes").firstElement());
						for (int i = 0; i < times; i++) {
							Ant ant = new Ant(lRequest.getSource(),
									lRequest.getTarget(), hopLimit, bytesHop);
							multiple.add(new Event(
									delay + event.getTimeStamp(),
									Event.Type.PACKET_ARRIVAL, ant));
							delay = delay + (1.0 / restoreAntRate);
						}
						// Return the multiple events to the simulator
						return new Event(lastTime, Event.Type.MULTIPLE,
								multiple);
					}
				} else {
					// Accounts the failed lightpath request
					accounting.addFailed(rsvpErr);
					// if (disruptedLSP.containsKey(rsvpErr.getFlowLabel()))
					// System.out.println("Failed:"+event.toString());
				}
			} else if (errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)) {
				// Accounts the failed lightpath request
				// try {
				accounting.addFailed(rsvpErr);
				// } catch(Exception e){System.err.println(event.toString());}
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
					|| ((restoredLSP.get(flowLabel) != null) && (restoredLSP
							.get(flowLabel).getPath().equals(connectionTear
							.getPath())))) {
				// Send RSVP PathTear message
				CrankRSVP rsvpTear = new CrankRSVP(connectionTear,
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
			// System.out.println("Pacote: " + packet.toString());
			// Get the node associated to this packet
			id = packet.getNode();
			// Give the packet to the right node
			LabelSwitchRouter procNode = nodes.get(id);
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
			double timeNotification = 0;
			Vector<Event> failuresLink = null;
			
			
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
			
			//LINK INTRA-DOMINIO
			if(getDomain(edge.getSource()).equals(getDomain(edge.getDestination()))) {
				// Remove the failure edge from the graph
						
				// remove all domain graphs
				domainGraphs.clear();
				
				// re-creates per-domain graphs
				setPerDomainPaths(graph, domainGraphs);
				
				//atualiza o grafo de cada no
				// now, update all graphs for each node
				for (String domain : domainGraphs.keySet()) {
					for (String node : nodes.keySet()) {
						if (domain.equals(getDomain(node))) {
							AntNetInterLSR tempNode = nodes.get(node);
							tempNode.updateGraph(domainGraphs.get(domain));
						}

					}
				}
		
			} else {
				
				//LINK INTERDOMINIO
				this.antNetLSRLink.get(edge.getSource()).remove(edge.getDestination());
				
				//atualiza o nivel de feromonio apos uma falha simples de enlace
				AntNetCrankInterRoutingTable.updateAfterLinkFailure(edge.getSource(), edge.getDestination());
				
												
			}
			
			// Notifies the end nodes of the failure after the localization time
			timeNotification = lastTime + this.faultLocalizationTime;
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
			failuresLink = new Vector<Event>();
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
			

			//atualiza o grafo de todos os nos da rede
			
			for (String domain : domainGraphs.keySet()) {
				for (String node : nodes.keySet()) {
					if (domain.equals(getDomain(node))) {
						AntNetInterLSR tempNode = nodes.get(node);
						tempNode.updateGraph(domainGraphs.get(domain));
					}

				}
			}
			
			
			// remove all domain graphs
			domainGraphs.clear();

			// re-creates per-domain graphs
			setPerDomainPaths(graph, domainGraphs);
		

			// update the domain set path
	//		for (String domain : domainGraphs.keySet()) {
	//			domainSetPaths.put(domain,
	//					this.getPaths(domainGraphs.get(domain), reroutingAttempts));
	//		}

			// now, update all graphs for each node
			for (String domain : domainGraphs.keySet()) {
				for (String node : nodes.keySet()) {
					if (domain.equals(getDomain(node))) {
						AntNetInterLSR tempNode = nodes.get(node);
						tempNode.updateGraph(domainGraphs.get(domain));
					}

				}
			}

			
			
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
		System.out.println("Disrupted: "
				+ this.disruptedLSP.keySet().toString());
		System.out.println("Total disrupted: " + disruptedLSP.size());
		System.out.println("Rerouted: " + reroutedLSP.size());
	}

	/**
	 * Returns the next hop with a decreasing order of pheromone level.
	 * 
	 * @param neighborhood
	 *            The routing table of a given destination.
	 * @param rsvp
	 *            The RSVP message.
	 * @param visited
	 *            The ids of the already visited nodes.
	 * @return The next hop with a decreasing order of pheromone level.
	 */
	public static String select(RoutingTableEntry neighborhood, CrankRSVP rsvp,
			Vector<String> visited) {
		// For each neighbor do
		Vector<NeighborAttr> neighs = new Vector<NeighborAttr>();
		for (String neighId : neighborhood.neighborhood()) {
			// Avoids the RSVP message to come back or visit another time.
			if (!rsvp.getPath().containNode(neighId)
					&& !visited.contains(neighId))
				neighs.add((NeighborAttr) neighborhood.getEntry(neighId));
		}
		// Sort the values of pheromone level
		if (neighs.size() > 0) {
			QuickSort.sort(neighs, false);
			return (String) neighs.get(0).getId();
		} else { // No neighbors available or not visited
			return null;
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
	 * @param alpha
	 *            Trade-off between shortest-path and heuristic correction
	 *            (congestion).
	 * @return The id of the next hop.
	 */
	public static String select(RoutingTableEntry neighborhood,
			LinkedHashMap<String, LinkState> links, Ant ant, double alpha,
			double powerFactor) {
		// Gets the total number of free points between the neighbors.
		double totalFreeWavelengths = 0.0; // Total number of free wavelengths
		double totalPheromoneLevel = 0.0;
		// Gets the neighbors that are not in the tabu list.
		Vector<String> availableNeighbors = new Vector<String>();
		for (String neighId : neighborhood.neighborhood()) {
			if (!ant.isTabu(neighId)) { // not in tabu list
				availableNeighbors.add(neighId);
				// Get the total number of free wavelengths
				double free = (double) links.get(neighId).getMask()
						.freeWavelengths();
				totalFreeWavelengths = totalFreeWavelengths
						+ Math.pow(free, powerFactor);
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
			// Set the loop flag
			ant.setLoopFlag();
			// Set the payload length
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
				// Now calculate the probability
				double freeLambdas = links.get(neighId).getMask()
						.freeWavelengths();
				//
				probabilityDistribution[count] = ((neigh.getPheromoneLevel() / totalPheromoneLevel) + alpha
						* (Math.pow(freeLambdas, powerFactor) / totalFreeWavelengths))
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
						// Vector<String> caminho = new Vector<String>();
						Vector<String> neigh = graph.adjacentNodes(value);
						// System.out.println("Estou analisando o valor " +
						// value
						// + " e ele possui os seguintes vizinhos "
						// + neigh.toString());
						// int vizinhos = 0;
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
								if (cam.size() < maxInterRoutes) {
									cam.add(vizinhanca);
								}

								if (verbose)
									System.out.println("Adicionando caminho "
											+ cam.toString() + " para " + key);

								ASPath.put(key, cam);

							}

						}

					} else { // logo entao representa um numero de AS, preciso
								// iterar para descobrir tudo

						// Graph grafoDominioSource =
						// domainGraphs.get(ASsource);
						// int viz = 0;
						// Vector<String> caminho = new Vector<String>();
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

								Vector<String> possibileHops = policy.get(hop
										+ "-" + ASDestination);
								for (int j = 0; j < possibileHops.size(); j++) {
									String possibleHop = possibileHops.get(j);
									// System.out.println(getDomain(possibleHop));
									if (!getDomain(possibleHop)
											.equals(ASsource)) {
										hop = possibleHop;
										break;
									}
								}

								// hop = policy.get(hop + "-" + ASDestination)
								// .firstElement();
								if (getDomain(hop).equals(ASDestination)) {
									hop = policy.get(hop + "-" + ASDestination)
											.firstElement();
								}
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

			}

		}

		/**
		 * INVARIANTE RELEVANTE: Neste ponto do codigo, prefixASPAth possui as
		 * possiveis rotas interdominio. Os dominios que possuem ligacao fisica
		 * possuem rotas que da forma: [1:b,2:f] isto eh, com hops, e com a
		 * identificacao do nó real.
		 * 
		 * Os dominios que nao possuem ligacao fisica (ou um caminho alternativo
		 * que passe por um dominio de transito), apresentam a seguinte
		 * estrutura: [2,1,5,6,3]
		 */

		// prefixASPath = (LinkedHashMap<String, ArrayList<Vector<String>>>)
		// ASPath
		// .clone();
		// System.out.println("AS so com prefixos " + prefixASPath.toString());
		for (String key : ASPath.keySet()) {
			ArrayList<String> t;

			ArrayList<Vector<String>> linha = ASPath.get(key);
			for (int i = 0; i < linha.size(); i++) {
				Vector<String> elemento = linha.get(i);
				if (elemento.firstElement().contains(":")) {
					// System.out.println(elemento +
					// " -  nao preciso fazer nada");
					// ja eh uma rota strict
				} else {
					// rota loose
					Vector<String> teste = new Vector<String>();
					// vejo quantos possiveis caminhos tenho do AS Source para o
					// primeiro AS de Transito:
					int possibleRoutesNumber = 0;
					ArrayList<Vector<String>> fromSourceandFirstTransitPossibleRoutes = ASPath
							.get(elemento.firstElement() + "-"
									+ elemento.elementAt(1));
					for (int z = 0; z < fromSourceandFirstTransitPossibleRoutes
							.size(); z++) {
						if (fromSourceandFirstTransitPossibleRoutes.get(z)
								.firstElement().contains(":")
								&& getDomain(
										fromSourceandFirstTransitPossibleRoutes
												.get(z).elementAt(1)).equals(
										getDomain(elemento.elementAt(1)))) {
							possibleRoutesNumber++;
						}
					}
					// System.out
					// .println("EXISTEM " + possibleRoutesNumber
					// + " ROTAS FISICAS  ENTRE "
					// + elemento.firstElement() + " e "
					// + elemento.get(1));
					// remove loose
					linha.remove(i);
					int contador = 0;
					while (contador < possibleRoutesNumber
							&& linha.size() < maxInterRoutes) {

						teste = new Vector<String>();

						for (int a = 0; a < elemento.size() - 1; a++) {
							Vector<String> intermediateRoute = new Vector<String>();
							if (a == 0) {
								intermediateRoute = ASPath.get(
										elemento.get(a) + "-"
												+ elemento.get(a + 1)).get(
										contador);
							} else {
								intermediateRoute = ASPath.get(
										elemento.get(a) + "-"
												+ elemento.get(a + 1)).get(0);
							}

						//	System.out.println(intermediateRoute.toString());

							for (int b = 0; b < ASPath
									.get(elemento.get(a) + "-"
											+ elemento.get(a + 1)).get(0)
									.size(); b++) {
								if (a == 0) {
									teste.add(ASPath
											.get(elemento.get(a) + "-"
													+ elemento.get(a + 1))
											.get(contador).get(b));
								} else {
									teste.add(ASPath
											.get(elemento.get(a) + "-"
													+ elemento.get(a + 1))
											.get(0).get(b));
								}

							}

						}
						// if (contador == 0)
						/***
						 * ROTINA PARA ELIMINAR LOOPS DE POSSIVEIS CONVERGENCIA
						 */
						// antes de adicionar eu preciso garantir que nao tem
						// loop

						for (int d = 0; d < teste.size(); d++) {
							String elem = teste.get(d);
							// System.out.println("teste original: " +
							// teste.toString());
							teste.remove(elem);
							if (teste.contains(elem)) {
								int position = teste.indexOf(elem);
								// System.out.println(d + " - JA EXISTE " + elem
								// + " na posicao " + position);
								// se ja existe, apago tudo da posicao d ate
								// aquela posicao
								// System.out.println("Teste sem o elemento mas com a repeticao "
								// + teste.toString());
								while (position > d) {
									teste.remove(position);
									position--;
								}
								// System.out.println("Teste sem o elemento mas sem a repeticao: "
								// + teste.toString());
							}
							teste.add(d, elem);
							// System.out.println("novo teste: " +
							// teste.toString());

						}
						linha.add(i, teste); // insiro exatamente no local
						// else {
						// linha.add(teste); // insiro no final
						// }
						contador++;

					} // fim do while

					
				}
			}
		}

		// neste ponto a tabela de rotas esta completa
		// System.out.println("rotas completas");

		// System.out.println(ASPath.toString());

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

	public static  String getTargetNode(String sourceNode) {
		String domain = getDomain(sourceNode);
		return getTargetNode(sourceNode, domainGraphs.get(domain));
	}

	public static String getInterSourceNode(MersenneTwister random) {

		String sourceNode = "";
		do {
			int dominio = random.nextInt(9) + 1;
			sourceNode = getSourceNode(random,
					domainGraphs.get(String.valueOf(dominio)));
		} while (!asbrs.contains(sourceNode));
		return sourceNode;

	}

	public static String getInterTargetNode(MersenneTwister random,
			String source) {
		String targetNode = "";

		String dominio = "";

		do {
			int dom = random.nextInt(9) + 1;
			dominio = String.valueOf(dom);
		} while (dominio.equals(getDomain(source)));
		// System.out.println("Dominio escolhido = " + dominio);

		do {
			// truque, chama o getSource, mas da no mesmo.
			targetNode = getSourceNode(random, domainGraphs.get(dominio));
		} while (!asbrs.contains(targetNode));

		return targetNode;

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
		// customized de-serialization code
		AntNetCrankInterControlPlane.graph = transporter.graph;
		AntNetCrankInterControlPlane.random = transporter.random;
		AntNetCrankInterControlPlane.rngAnt = transporter.randomAnt;
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
	/** Serial version UID. */
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
