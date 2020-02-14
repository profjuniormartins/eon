/*
 * Created on December 10, 2005.
 */
package rwa;

import java.io.Serializable;
import java.util.*;

import ops.Packet;
import event.Event;
import graph.Edge;
import graph.Graph;
import main.Failure;
import main.RoutingTable;
import main.Failure.Location;

/**
 * This class represents an optical node with cross-connection 
 * capabilities in a wavelength-routed optical network. 
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class OpticalNode implements Serializable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;	
	/** Delta introduced to avoid race conditions between
	 *  flooding and failure notification. */
	public static final double DELTA_TIME = 1E-7; 
	/** The id of this node in the network. */
	protected String id;
	/** The routing table of this node. */
	protected RoutingTable routingTable;
	/** The link attributes of this node. */
	protected LinkedHashMap<String,LinkState> links;
	/** The unique IDs of the failures already processed. */
	protected Vector<Integer> failureID;
	/** The table of active connections in that node. */
	protected Hashtable<String,Connection> activeConnections;
	/** The physical topology of the network. */
	protected Graph graph;

	/** 
	 * Creates a new OpticalNode object.
	 * @param identification The identification of this node in the network.
	 * @param aRoutingTable The routing table associated with this node.
	 * @param aLinks The set of link states that belongs to this node.
	 * @param aGraph The grpah representing the network.
	 */
	public OpticalNode(String identification, RoutingTable aRoutingTable, LinkedHashMap<String,LinkState> aLinks, Graph aGraph) {
		this.id = identification;
		this.routingTable = aRoutingTable;
		this.links = aLinks;
		this.graph = aGraph;
		this.activeConnections = new Hashtable<String,Connection>();
		this.failureID = new Vector<Integer>();
	}

	/**
	 * Gets the identification of this node.
	 * @return The identification of this node.
	 */
	public String getId() {
		return this.id;
	}
	
	/**
	 * Gets the routing table associated to this node.
	 * @return The routing table associated to this node.
	 */
	public RoutingTable getRoutingTable() {
		return this.routingTable;
	}

	/**
	 * Gets the wavelength mask of the specified neighbor.
	 * @param neighId The id of the specified neighbor.
	 * @return The wavelength mask of the specified neighbor.
	 */
	public WavelengthMask getMask(String neighId) {
		return this.links.get(neighId).getMask();
	}
	
	/**
	 * Gets the link associated with the specified neighbor.
	 * @param neighId The id of the specified neighbor.
	 * @return The link associated with the specified neighbor.
	 */	
	public LinkState getLink(String neighId) {
		return this.links.get(neighId);
	}

	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Id: ");
		buf.append(this.id);
		buf.append("\n Routing Table:\n");
		buf.append(this.routingTable);
		return buf.toString();
	}

	/**
	 * Process the event.
	 * @param event The event to be processed.
	 * @return The processed event.
	 */
	public Event process(Event event) {
		//Get the packet associated to this event
		Packet packet = (Packet)event.getContent();
		//Inspects its header and decides what to do with it.
	    Packet.Header header = packet.getNextHeader();
	    Event response=null; //The response to the processing 
	    String nextHop; //The next hop in the path
		RSVP rsvp; //RSVP message
		String target; //Target node
		Error error; //The associated error specification
	    switch (header) {
	    case LINK_FAILURE: //Packet associated with control plane (separated channel)
	    	//Identify the failure
	    	Failure failure = (Failure) packet.getPayload();
	    	//Flooding of the failure
	    	if (!failureID.contains(failure.getID())) { //First time
	    		//Mock local update of the topology
	    		((ExplicitRoutingTable)routingTable).updateFromTopology(graph,DistributedControlPlane.getPaths());
	    		//Broadcast the failure to the neighbors
	    		String previousHop = packet.getSource();
	    		Vector<String> neighbors = graph.adjacentNodes(id);
	    		Vector<Event> broadcast = new Vector<Event>(); 
	    		//Adds the flooding information
	    		for(String neighId:neighbors) {
	    			if (!neighId.equals(previousHop)) { //not visited
	    				//Retransmit the packet
	    				Packet clonedFrom = (Packet)packet.clone();
	    				clonedFrom.retransmit(id,neighId);
	    				clonedFrom.setNode(neighId);
	    				//Set new time stamp
	    				double transmissionTime = this.links.get(neighId).getDelay();
	    				double newTimeStamp = event.getTimeStamp() + transmissionTime;
	    				//Add to the list of broadcast
	    				broadcast.add(new Event(newTimeStamp,Event.Type.PACKET_ARRIVAL,clonedFrom));
	    			}
	    		}
	    		//Add the failure to the list of processed ones.
	    		failureID.add(failure.getID());
	    		/* Remove the link states affected by the failure. */
	    		if (((Edge)failure.getInformation()).getSource().equals(id)) {
	    			links.remove(((Edge)failure.getInformation()).getDestination());
	    		}
	    		/* Treatment the failure by the neighbor nodes */
	    		//Now, if it is the closest node upstream to the failure
	    		if(id.equals(((Edge)failure.getInformation()).getSource())) {
	    			//Adds the notification of broken LSP to the event
	    			for(String activeID:activeConnections.keySet()) {
	    				//Gets the connection
	    				Connection active = activeConnections.get(activeID);
	    				//Locate the failure
	    				Location location = failure.locate(id,active.getPath());
	    				if(!location.equals(Failure.Location.NOT_APPLICABLE)) {
	    					//Create the PathErr packet
	    					RSVP pathErr = new RSVP(active,Packet.Header.RSVP_PATH_ERR,id,active.getSource());
	    					//Create the error with path remove flag
	    					error = new Error(Error.Code.LSP_FAILURE,true);
	    					pathErr.setError(error);
	    					pathErr.setFlowLabel(activeID);
	    					//System.out.println(pathErr.toString());
	    					double transmissionTime=0;
	    					//Not the source node to treat the failure
	    					if(!id.equals(active.getPath().firstNode())) {
	    						nextHop = active.getPath().getPreviousNode(id);
	    						pathErr.setNode(nextHop);
	    						//Set new time stamp
	    						transmissionTime = this.links.get(nextHop).getDelay();	
	    					}
	    					double newTimeStamp = event.getTimeStamp() + transmissionTime + DELTA_TIME;
	    					broadcast.add(new Event(newTimeStamp,Event.Type.PACKET_ARRIVAL,pathErr));
	    				}
	    			}
	    			//Now, if it is the closest node downstream to the failure
	    		} else if(id.equals(((Edge)failure.getInformation()).getDestination())) {
	    			//Adds the notification of broken LSP to the event
	    			for(String activeID:activeConnections.keySet()) {
	    				//Gets the connection
	    				Connection active = activeConnections.get(activeID);
	    				//Locate the failure
	    				Location location = failure.locate(id,active.getPath());
	    				if(!location.equals(Failure.Location.NOT_APPLICABLE)) {
	    					//Create the PathErr packet
	    					RSVP pathTear = new RSVP(active,Packet.Header.RSVP_PATH_TEAR,id,active.getTarget());
	    					//Create the error
	    					error = new Error(Error.Code.LSP_FAILURE);
	    					pathTear.setError(error);
	    					pathTear.setFlowLabel(activeID);
	    					//System.out.println(pathTear.toString());
	    					//Not the target node to treat the failure
	    					double transmissionTime = 0;
	    					//Not the last node to tackle the failure
	    					if(!id.equals(active.getPath().lastNode())) {
	    						nextHop = active.getPath().getNextNode(id);
	    						pathTear.setNode(nextHop);
	    						transmissionTime = this.links.get(nextHop).getDelay();	
	    					}
    						//Set new time stamp
	    					double newTimeStamp = event.getTimeStamp() + transmissionTime + DELTA_TIME;
	    					//Add to the list of broadcast
	    					broadcast.add(new Event(newTimeStamp,Event.Type.PACKET_ARRIVAL,pathTear));
	    				}
	    			}
	    		}	    		
	    		//System.out.println(broadcast.toString());
	    		//Return the multiple packets associated with the failure
	    		response = new Event(event.getTimeStamp(),Event.Type.MULTIPLE,broadcast);
	    	} else { //Already processed the failure.
	    		event.setType(Event.Type.IGNORE);
	    		response = event;
	    	}
	    	break;
	    	case RSVP_PATH: //path reservation
	    		//System.out.println(event.toString());
	    		//System.out.println(this.routingTable.toString());
	    		rsvp = (RSVP) packet;
				//Get the target node
				target = rsvp.getTarget();
				//Get the lightpath request
			    LightpathRequest request = (LightpathRequest) rsvp.getObject();
	    		WavelengthMask mask = rsvp.getMask(); 
	    		if ((mask != null) && (mask.freeWavelengths() == 0)) { //There is no free wavelengths to allocate
	    			//wavelength continuity constraint violated
	    			rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); //change header to problem
	    			//Reset the SD pair to the new values
	    			rsvp.setSDPair(id,request.getSource());
	    			//Set the error
	    			error = new Error(Error.Code.RP_LABEL_SET);
	    			rsvp.setError(error);
		    		//Do not record the route anymore
		    		rsvp.resetRecordRoute();
	    			//Get the next hop
	    			nextHop = rsvp.getBackwardNode();
	    		} else if (target.equals(id)) { //Rsvp reached the target node
	    			//change header to reservation
		    		rsvp.setNextHeader(Packet.Header.RSVP_RESV); 
	    			//Reset the SD pair to the new values
	    			rsvp.setSDPair(target,request.getSource());		    		
		    		//Choose a free wavelength
		    		int pos = 0; //position of the first free wavelength
		    		while (!mask.testWavelength(pos)) { //First-fit strategy
		    			pos++; //Increment the counter
		    		}
		    		//Create a connection and add it as a object.
		    		Connection connectionEst = new Connection(rsvp.getPath(),pos,rsvp.getFlowLabel(),request);
		    		//Set the start time of the connection, which starts after arriving at the source node.
		    		//Because of that, it uses the round trip time as the time to start it
		    		connectionEst.setStartTime(event.getTimeStamp() + (event.getTimeStamp() - event.getInitialTimeStamp()));
		    		//Set the object to the message
		    		rsvp.setObject(connectionEst);
		    		//Add the connection to the table of active connections. 
		    		activeConnections.put(rsvp.getFlowLabel(),connectionEst);
		    		//System.out.println(activeConnections.toString());
		    		//Do not record the route anymore
		    		rsvp.resetRecordRoute();
	    			//Get the next hop
	    			nextHop = rsvp.getBackwardNode();
		    	} else { //Intermediate node
		    		//Get the next hop
		  //  		nextHop = null;
		  //  		try {
		    			nextHop = ((ExplicitRoutingTable)routingTable).nextHop(rsvp,request.getTry());
		  //  		} catch (Exception e) {System.err.println(rsvp.toString());}
		    		if (nextHop == null || (packet.getHopLimit() == 0) || !(DistributedControlPlane.hasConnectivity(id,nextHop))) { //dead-end 
//		    			request.addTry(); //add to the counter of tries
		    			rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); //change header to problem
		    			//Reset the SD pair to the new values
		    			rsvp.setSDPair(id,request.getSource());
		    			//Create the error information
		    			error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
		    			rsvp.setError(error);
			    		//Do not record the route anymore
			    		rsvp.resetRecordRoute();
			    		if(!id.equals(request.getSource())) {
			    			//Get the next hop (backward)
			    			nextHop = rsvp.getBackwardNode();
			    		} else { //Failure after the first link of the node
			    			response = event;
			    			break;
			    		}
		    		} else {
		    			//Updates the mask
		    			rsvp.updateMask(links.get(nextHop).getMask());
		    			//Decrement the number of hops
		    			rsvp.decrementHopLimit();
		    		}
		    	}
    			//Set the next node
    			rsvp.setNode(nextHop);
	    		//Set the new time of the event due to transmission time
    			//System.out.println(event.toString());
    			event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
 		    	//Return the response
	    		response = event;
	    		break;
	    	case RSVP_PATH_TEAR: //Forward direction
	    		rsvp = (RSVP) packet;
				//Remove this connection from the list of active connections
    			activeConnections.remove(rsvp.getFlowLabel());
				//Get the target node
				target = rsvp.getTarget();
		    	//Verify if the rsvp message has reached the target node. 
		    	if (target.equals(id)) { //Rsvp reached the target node
		    		event.setType(Event.Type.LIGHTPATH_REMOVED);
		    	} else {
		    		Connection teared = (Connection)rsvp.getObject(); 
		    		nextHop = teared.getPath().getNextNode(id);
	    			LinkState link = links.get(nextHop);
	    			if (link != null) { //Maybe the state was removed due to failure
	    				//Get the associated mask
	    				WavelengthMask linkMaskTear = link.getMask();
	    				//Clear the wavelength
	    				linkMaskTear.clearWavelength(teared.getWavelength());
	    			} else {
	    				System.out.print(event.toString());
	    			}
			    	//Set the next hop in the packet
			    	rsvp.setNode(nextHop);
		    		//Set the new time of the event due to transmission time
		    		event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
		    	}
				//Return the response
		    	response = event;		    	
	    		break;
	    	case RSVP_PATH_ERR: //Backward direction
	    		rsvp = (RSVP) packet;
	    		//System.out.println(rsvp.toString());
	    		//Get the error 
	    		error = rsvp.getError();
				//Remove this connection from the list of active connections
	    		//if the remove flag is enabled
	    		if (error.getRemoveFlag()) {
	    			activeConnections.remove(rsvp.getFlowLabel());
	    			Connection removed_perr = (Connection)rsvp.getObject();
	    			String forwardHop = removed_perr.getPath().getNextNode(id);
	    			LinkState link = links.get(forwardHop);
	    			if (link != null) { //Maybe the state was removed due to failure
			    		//Get the associated mask
	    				WavelengthMask linkMaskRem = link.getMask();
	    				//Clear the wavelength
	    				linkMaskRem.clearWavelength(removed_perr.getWavelength());
	    			}
	    		}
				//Gets the target node
				target = rsvp.getTarget();
		    	//Verify if the rsvp message has reached the target node. 
		    	if (target.equals(id)) { //RSVP reached the target node
		    		event.setType(Event.Type.LIGHTPATH_PROBLEM);
		    	} else { //intermediate nodes
		    		if (error.getErrorCode().equals(Error.Code.LSP_FAILURE)) {
		    			//System.out.println(rsvp.toString());
		    			nextHop = ((Connection)rsvp.getObject()).getPath().getPreviousNode(id);
		    		} else {
		    			nextHop = rsvp.getBackwardNode();
		    		}
			    	//Set the next hop in the packet
			    	rsvp.setNode(nextHop);
		    		//Set the new time of the event due to transmission time
		    		event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
		    	}
		    	response = event;
	    		break;
	    	case RSVP_RESV: //Backward direction
	    		rsvp = (RSVP) packet;
	    		//System.out.println(rsvp.toString());
	    		/* Update the the wavelength mask of this node. */
	    		Connection connection = (Connection) rsvp.getObject();
	    		//Get the forward node
	    		String fwdId = rsvp.getForwardNode();
	    		//Get the associated mask
	    		WavelengthMask linkMask = links.get(fwdId).getMask();
	    		//See the status of the wavelength
	    		if (linkMask.testWavelength(connection.getWavelength())) {
		    		//System.out.println("Adding conn: "+rsvp.getFlowLabel()+" with: "+connection.toString()+" to intermediate node: "+id);
		    		activeConnections.put(rsvp.getFlowLabel(),connection);
		    		//Set the wavelength
		    		linkMask.setWavelength(connection.getWavelength());
		    		//Verify if the ant reached the source node.
		    		target = rsvp.getTarget();
			    	if (target.equals(id)) {
			    		event.setType(Event.Type.LIGHTPATH_ESTABLISHED);
			    	} else { //Intermediate node
			    		//See the next hop
						nextHop = rsvp.getBackwardNode();
						//In case of failure
						if (!(DistributedControlPlane.hasConnectivity(id,nextHop))) {
			    			//Send a resvErr msg to the sender
			    			rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
			    			rsvp.setSDPair(id,connection.getTarget());
			    			//Add the error
							error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
			    			rsvp.setError(error);
				    		//Change the next hop
							nextHop = rsvp.getForwardNode();
						}
				    	//Set the next hop in the packet
				    	rsvp.setNode(nextHop);
			    		//Set the new time of the event due to transmission time
			    		event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
			    	}	    			    			
	    		} else { //Contention problem!
	    			//Send a resvErr msg to the sender
	    			rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
	    			rsvp.setSDPair(id,connection.getTarget());
	    			//Set the error
	    			error = new Error(Error.Code.ADMISSION_CONTROL_FAILURE);
	    			rsvp.setError(error);
		    		//See the next hop
					nextHop = rsvp.getForwardNode();
			    	//Set the next hop in the packet
			    	rsvp.setNode(nextHop);
		    		//Set the new time of the event due to transmission time
		    		event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
	    		}
	    		response = event;
	    		break;
	    	case RSVP_RESV_TEAR: //Backward direction (Not used!)
	    		rsvp = (RSVP) packet;
	    		/* Update the the wavelength mask of this node. */
	    		//Get the backward node
	    		String forwardId = rsvp.getForwardNode();
	    		//Get the associated mask
	    		WavelengthMask linkMaskTear = links.get(forwardId).getMask();
	    		//Clear the wavelength
	    		int clear = ((Connection)rsvp.getObject()).getWavelength();
	    		linkMaskTear.clearWavelength(clear);
	    		//Verify if the ant reached the source node.
		    	if (rsvp.getSource().equals(id)) {
		    		event.setType(Event.Type.LIGHTPATH_REMOVED);
		    	} else {
		    		//See the next hop
					nextHop = rsvp.getBackwardNode();
		    		rsvp.setNode(nextHop);
		    	}	    		
	    		response = event;
	    		break;
	    	case RSVP_RESV_ERR: //Forward direction
	    		rsvp = (RSVP) packet;
	    		//Remove the connection from the table of connections
	    		activeConnections.remove(rsvp.getFlowLabel());
	    		Connection removed_rerr = (Connection) rsvp.getObject();
	    		//Now, send a PathErr to the ingress node, with "Admission Control Failure".
	    		target = rsvp.getTarget(); 
	    		if (target.equals(id)) {
	    			//Convert to pathErr msg
	    			rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR);
	    			//System.out.println("Creating: "+rsvp.toString());
	    			//Reset the SD pair to the new values
	    			rsvp.setSDPair(id,removed_rerr.getSource());
	    		} else {
		    		nextHop = rsvp.getForwardNode();
		    		//Get the associated mask
		    		WavelengthMask linkMaskErr = links.get(nextHop).getMask();
		    		//Clear the wavelength
		    		linkMaskErr.clearWavelength(removed_rerr.getWavelength());
			    	//Set the next hop in the packet
			    	rsvp.setNode(nextHop);
		    		//Set the new time of the event due to transmission time
		    		event.setTimeStamp(event.getTimeStamp() + links.get(nextHop).getDelay());
	    		}
	    		response = event;
	    		break;
	    }
		return response;
	}
	
	
	
}
