/*
 * Created on 15/02/2008.
 */
package rwa.crankback;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Vector;

import ops.Accounting;
import ops.Packet;
import rwa.Connection;
import rwa.Error;
import rwa.ExplicitRoutingTable;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.crankback.LabelSwitchRouter.ReRouting;
import rwa.crankback.LabelSwitchRouter.WavelengthAssignment;
import event.Event;
import graph.Edge;
import graph.Graph;
import graph.Path;
import graph.YEN;
import main.Config;
import main.ControlPlane;
import main.Failure;
import main.Link;
import main.SimulationAccounting;

/**
 * A Distributed Control Plane for Routing and Wavelength Assignment,
 * with crankback routing capabilities.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class CrankControlPlane extends ControlPlane {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String,LabelSwitchRouter> nodes;
	/** The set of links of this network. */
	private LinkedHashMap<String,Link> links;
	/** The maximum hop limit for a packet. */
	protected int hopLimit;
	/** The counter for identifying a request. */
	protected long counterLightpath=0L;
	/** The accounting of the simulation results. */
	Accounting accounting;
	/** The number of alternative paths of the network. */
	int alternative;
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
	/** The collection of LSP disrupted by a failure, which
	 * are eligible for full re-routing. */
	protected Hashtable<String,LightpathRequest> disruptedLSP;
	/** The collection of LSP successfully restored after a failure. */
	protected Hashtable<String,Connection> restoredLSP;
	/** The actual collection of shortest paths of this network. */
	public static LinkedHashMap<String,Vector<Path>> setPaths;

	/**
	 * Creates a new DistributedControlPlane object.
 	 * @param aConfig The XML configuration file for this problem.
     */
	public CrankControlPlane(Config aConfig, SimulationAccounting aAccounting) {
		super(aConfig);
		
		this.accounting = (Accounting) aAccounting;
		//Get the links of this network
		links = config.getLinks();
		//System.out.println(links.toString());
		//Create the nodes of this network
		nodes = new LinkedHashMap<String,LabelSwitchRouter>();
		//Create the storage of disrupted connections by failure
		disruptedLSP = new Hashtable<String,LightpathRequest>();
		restoredLSP = new Hashtable<String,Connection>();
		//Get the simulation parameters
		Hashtable<String,Vector<String>> parameters = config.getSimulationParameters();
		//see all the parameters
		//System.out.println(parameters.toString());
		hopLimit = Integer.parseInt(parameters.get("/OPS/Hop/@limit").firstElement());
		//Get details about the RWA algorithm used
		rerouting = ReRouting.valueOf(parameters.get("/RWA/Routing/@rerouting").firstElement());
		reroutingAttempts = Integer.parseInt(parameters.get("/RWA/Routing/@attempts").firstElement());
		maxReroutingAttempts = Integer.parseInt(parameters.get("/RWA/Routing/@maxAttempts").firstElement());
		this.alternative = reroutingAttempts + 1; //Number of k-shortest paths
		wa = WavelengthAssignment.valueOf(parameters.get("/RWA/WA/@type").firstElement());
		boolean serRT = Boolean.parseBoolean(parameters.get("/RWA/Serialize/@rt").firstElement());
		LinkedHashMap<String,ExplicitRoutingTable> rTables = null; 
		if (serRT) {
			String fileRT = parameters.get("/RWA/Serialize/@file").firstElement();
			rTables = this.read(fileRT);
		} else { //Calculate the set of paths of the network
	//		System.out.println("Calculando caminhos");
			//TEREI QUE MUDAR ISTO, POIS AQUI ENTRA A IMPLEMENTACAO DO OBGP PARA CALCULAR OS CAMINHOs
			//PARA A REDE. Vou ter que saber calcular para interno (ai funciona isto mesmo), e para externo
			setPaths = this.getPaths(graph,alternative);
		}
		//Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get("/Outputs/Transient/@timeSlice").firstElement());
		actualTimeSlice = timeSlice;
		// Gets the time necessary for fault localization.
		faultLocalizationTime = Double.parseDouble(parameters.get("/Failure/Timing/@localization").firstElement());
		identificationLength = Integer.parseInt(parameters.get("/OPS/Hop/@bytes").firstElement());
		
		//Initialize the state of each node
		for (String id: graph.nodes()) {
		//	System.out.println("...: "+id);		
			//Create the routing table for this node
			ExplicitRoutingTable ert;
			if (rTables != null) {
				ert = rTables.get(id);
			} else {
				ert = new ExplicitRoutingTable(id,alternative);
				ert.updateFromTopology(graph,setPaths);
				//System.out.println(ert.toString());
			}
			//Create the links adjacent to this node.
			Vector<String> adjacent = graph.adjacentNodes(id);
			//System.out.println(adjacent.toString());
			LinkedHashMap<String,LinkState> linkStateSet = new LinkedHashMap<String,LinkState>();	
			//for each adjacent node do
			for (String adjId:adjacent) {
				Link link = links.get(id+"-"+adjId);
				LinkState linkState = new LinkState(link);
				linkStateSet.put(adjId.toString(),linkState);
			}
			//Create the node and put it into the table.
			//AQUI EU CRIO OS NODES
			
			
			LabelSwitchRouter node = new LabelSwitchRouter(id,ert,linkStateSet,graph,wa,rerouting,maxReroutingAttempts,reroutingAttempts);
			nodes.put(id,node);
		}
		System.out.println("JA PASSEI PELO CONSTRUTOR");
	}
	
	/**
	 * Process the specified event.
	 * @param event The event to be processed.
	 * @return The processed event. Null, if nothing else is 
	 * to be returned to the scheduler.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Event process(Event event) {
		//The id of the processing node
		String id;
		//Update the time stamp of the last event processed.		
		lastTime = event.getTimeStamp(); 
		//Do transient accounting, if applicable
		if (lastTime > actualTimeSlice) {
			//Update the actual time slice
			actualTimeSlice = actualTimeSlice + timeSlice;
			//Updates the transient accounting, if applicable
			try {
				Method updateInstantaneous = accounting.getClass().getMethod("setInstantaneousValues",links.getClass());
				updateInstantaneous.invoke(accounting,links);
			}catch (Exception e) {} //do nothing - method not implemented					
		}
		//For each event type
		switch (event.getType()) {
			case LIGHTPATH_REQUEST: //Lightpath request
				LightpathRequest request = (LightpathRequest) event.getContent();
				//Send RSVP Path message
				CrankRSVP rsvpPath = new CrankRSVP(request,hopLimit,counterLightpath);
				this.counterLightpath ++; //Increment the counter
				//Create a new event for setting up the lightpath
				return new Event(event.getTimeStamp(),Event.Type.PACKET_ARRIVAL,rsvpPath);
			case LIGHTPATH_ESTABLISHED: //Lightpath established
				CrankRSVP rsvpConfirm = (CrankRSVP) event.getContent();
				//Gets the connection object.
				Connection connectionEst = (Connection) rsvpConfirm.getObject();
				//Gets the duration of the lightpath
				double duration = connectionEst.getRequest().getDuration();
				//Accounts the successful lightpath establishment
				accounting.addSuccesful(rsvpConfirm);
				//See if it is a successful re-routing of a failed LSP
				if (rsvpConfirm.inRestoration()) {
					restoredLSP.put(rsvpConfirm.getFlowLabel(),connectionEst);
				}
				//System.out.println(event.toString());
				//Return a new event for tearing down the lightpath when appropriate
				return new Event((event.getTimeStamp() + duration),Event.Type.LIGHTPATH_TEARDOWN,connectionEst);
			case LIGHTPATH_PROBLEM:
				CrankRSVP rsvpErr = (CrankRSVP) event.getContent();
				LightpathRequest lRequest;
				//Get the error status
				Error error = rsvpErr.getError();
				//Get the label
				int label = Integer.parseInt(rsvpErr.getFlowLabel());
				CrankRSVP rsvpRetry = null; //new Rsvp message
				Error.Code errorCode = error.getErrorCode(); 
				//Allocation of wavelength contention problem
				if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {
					lRequest = (LightpathRequest) ((Connection)rsvpErr.getObject()).getRequest();
					rsvpRetry = new CrankRSVP(lRequest,hopLimit,label);
					//System.out.println("Contention: "+lRequest.toString());
					if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {
						rsvpRetry.setRestoration(); //set the flag for restoration
						//System.out.println(event.toString());
					}
					//LSP failure forward or backward 	
				} else if(errorCode.equals(Error.Code.LSP_FAILURE)) {
					Connection disrupted = (Connection)rsvpErr.getObject();
					lRequest = (LightpathRequest) disrupted.getRequest();
					//reset the counter of retries
					lRequest.resetTry(); 
					//Calculates the rest of time of the connection
					double residualDuration = lRequest.getDuration() - (event.getInitialTimeStamp() - disrupted.getStartTime());
					//System.out.println("residual: "+residualDuration);
					lRequest.setDuration(residualDuration);
					//Create a new path message
					rsvpRetry = new CrankRSVP(lRequest,hopLimit,label);
					//Set the label indicating to tackle the failure
					rsvpRetry.setRestoration();
					//Adds the connection to the list of disrupted LSP 
					disruptedLSP.put(rsvpErr.getFlowLabel(),lRequest);
					//System.out.println("Adding LSP failure: "+rsvpErr.getFlowLabel()+" ,"+disrupted.toString());
				//Wavelength continuity constraint violated or no link available, use alternate path	
				} else if((errorCode.equals(Error.Code.RP_LABEL_SET)) || (errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE))) {
					lRequest = (LightpathRequest) rsvpErr.getObject();
					lRequest.addTry(); //add a try to the counter of tries					
					if (lRequest.tryAgain()) { //resend the request
						rsvpRetry = new CrankRSVP(lRequest,hopLimit,label);
						if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) {
							rsvpRetry.setRestoration(); //set the flag of restoration
							//System.out.println(event.toString());
						}
					} else { 
						//Accounts the failed lightpath request
						accounting.addFailed(rsvpErr);
						//if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) 
							//System.out.println("Failed:"+event.toString());
					}					
				} else if(errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)){
					//Accounts the failed lightpath request
					accounting.addFailed(rsvpErr);					
				}
				//Now, return the result.
				if (rsvpRetry != null)
					return new Event(event.getTimeStamp(),Event.Type.PACKET_ARRIVAL,rsvpRetry);
				else 
					return null;
			case LIGHTPATH_TEARDOWN: //Remove connection
				Connection connectionTear = (Connection) event.getContent();
				String flowLabel = connectionTear.getID();
				if ((disruptedLSP.get(flowLabel) == null) || ((restoredLSP.get(flowLabel) != null) && (restoredLSP.get(flowLabel).getPath().equals(connectionTear.getPath()) ) )) {
					//Send RSVP PathTear message
					CrankRSVP rsvpTear = new CrankRSVP(connectionTear,Packet.Header.RSVP_PATH_TEAR,connectionTear.getSource(),connectionTear.getTarget());
					//System.out.println(rsvpTear.toString());
					return new Event(event.getTimeStamp(),Event.Type.PACKET_ARRIVAL,rsvpTear);
				} else { //Ignore the teardown associated to a failed LSP, since it is already cleaned and rerouted.
					return null;
				}
			case LIGHTPATH_REMOVED: //Confirmation of connection removal
				//System.out.println(event.toString());
				return null;
			case PACKET_ARRIVAL: // Ant
				//Get the packet
				Packet packet = (Packet) event.getContent();
				//Get the node associated to this packet
				id = packet.getNode();
				//Give the packet to the right node
				LabelSwitchRouter procNode = nodes.get(id);
				if (procNode != null) { //Node functioning
					//Process the event
					Event response = procNode.process(event);
					if (response.getType().equals(Event.Type.IGNORE))  
						return null;
					else 
						return response;
				} else { //Failed node
					accounting.addFailed(packet);					
					return null;					
				}
			case FAILURE_LINK:  //For link failure
				//Get the edge associated with the failure
				String sEdge = (String) event.getContent();
				Edge edge = links.get(sEdge).getEdge();
				//Remove the failure edge from the graph
				try { //Do it only if it is not a node failure
					if (nodes.containsKey(edge.getSource()) && nodes.containsKey(edge.getDestination())) {
						graph.removeEdge(edge.getSource(),edge.getDestination());
					}
				} catch(Exception e) {e.printStackTrace();}	
				//detect and remove "orphan" nodes, i.e., disconnected ones.
				Vector<String> aNodes = (Vector<String>) graph.nodes().clone();
				for (String node : aNodes) {
					int degree = graph.adjacencyDegree(node);
					if (degree == 0) {
						try {
							//System.out.println("Removing node: "+node);
							graph.removeNode(node);
							//Remove the node from the list of nodes 
							nodes.remove(node);
						} catch (Exception e) {e.printStackTrace();}
					}
				}
				//Recalculate the set of paths
				setPaths = this.getPaths(graph,alternative);
				//Notifies the end nodes of the failure after the localization time
				double timeNotification = lastTime + this.faultLocalizationTime;
				int lengthFailure = 2 * this.identificationLength;
				//Creates the packets of notification
				Packet failureTo = new Packet(Packet.Header.LINK_FAILURE,edge.getDestination(),edge.getDestination(),Packet.Priority.HIGH,lengthFailure,graph.size());
				Packet failureFrom = new Packet(Packet.Header.LINK_FAILURE,edge.getSource(),edge.getSource(),Packet.Priority.HIGH,lengthFailure,graph.size());
				//Adds the edge to the packets.
				Failure failureLinkAdv = new Failure(edge); 
				failureTo.setPayload(failureLinkAdv); 
				failureFrom.setPayload(failureLinkAdv);
				//Add to the vector of events
				Vector<Event> failuresLink = new Vector<Event>();
				failuresLink.add(new Event(timeNotification,Event.Type.PACKET_ARRIVAL,failureFrom));
				failuresLink.add(new Event(timeNotification,Event.Type.PACKET_ARRIVAL,failureTo));				
				return new Event(timeNotification,Event.Type.MULTIPLE,failuresLink);
			case FAILURE_NODE: //For node failure
				//Get the node associated with the failure
				id = (String)event.getContent();
				Vector<Event> failuresNode = new Vector<Event>();
				//Gets the neighbors of this graph
				Vector<String> neighbors = graph.adjacentNodes(id);
				for (String neighId:neighbors) {
					//Add the edge "from" the removed node
					failuresNode.add(new Event(lastTime,Event.Type.FAILURE_LINK,new String(id+"-"+neighId)));
					//Add the edge "to" the removed node
					failuresNode.add(new Event(lastTime,Event.Type.FAILURE_LINK,new String(neighId+"-"+id)));
				}
				//Remove the failure node from the graph
				try {					
					graph.removeNode(id);
				} catch(Exception e) {e.printStackTrace();}				
				//Recalculate the set of paths
				setPaths = this.getPaths(graph,alternative);
				//Remove the node from the list of nodes 
				nodes.remove(id);
				//Return the response containing the failure of the multiple links
				return new Event(lastTime,Event.Type.MULTIPLE,failuresNode);
			default: System.err.println("Unknown event: "+event.toString());
				return null;
		}
	}
	
	/**
	 * Prints the last simulation time.
	 */
	public void updateValues() {
		System.out.println("LastTime: "+lastTime);
		System.out.println("Rerouted: "+restoredLSP.toString());
	}
	
	/**
	 * Read a serialized routing table.
	 * @param file The file to be read.
	 * @return The set of routing tables.
	 */
	@SuppressWarnings("unchecked")
	public LinkedHashMap<String,ExplicitRoutingTable> read(String file) {
		LinkedHashMap<String,ExplicitRoutingTable> result = null;
		try {
	        FileInputStream fis = new FileInputStream(file);
	        ObjectInputStream ois = new ObjectInputStream(fis);
	        result = (LinkedHashMap<String,ExplicitRoutingTable>)ois.readObject();
	        ois.close();
		} catch (Exception e) {e.printStackTrace();}
		return result;
	}

	/**
	 * Create the set of shortest paths
	 * @param topology The topology of the network
	 * @param alternative The number of alternative paths
	 * @return The alternatives paths for each pair source-destination of the topology.
	 */
	public LinkedHashMap<String,Vector<Path>> getPaths(Graph topology, int alternative) {
		LinkedHashMap<String,Vector<Path>> routes = new LinkedHashMap<String,Vector<Path>>();
		YEN yen = new YEN();
		for(String src: topology.nodes()) {
		//	System.out.println("Paths para n�: " + src);
			for (String tgt: topology.nodes()) {
				if (!src.equals(tgt)) { //Assure different nodes in the pair
					Vector<Path> paths = null;;
					try { 
						paths = yen.getShortestPaths(src,tgt,topology,alternative);
					} catch (Exception e) {e.printStackTrace();}
					routes.put(src+"-"+tgt,paths);
				}
			}
		}
		//System.out.println(routes.toString());
		return routes;
	}
	
	/**
	 * Returns the set of shortest paths of the actual topology.
	 * @return The set of shortest paths of the actual topology.
	 */
	public static LinkedHashMap<String,Vector<Path>> getPaths() {
		return setPaths;
	}

}
