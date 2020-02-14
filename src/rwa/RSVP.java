/*
 * Created on Dec 6, 2005.
 */
package rwa;

import graph.Path;
import ops.Packet;

/**
 * The RSVP message for signaling the establishment and the removing
 * of lightpaths. 
 * 
 * The label for the LSP is carried using the flow label section of
 * the packet header.
 *
 * Backward reservation using Label Set object, only.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class RSVP extends Packet{
	/** Serial UID for serialization. */
	private static final long serialVersionUID = 1L;
	/** The main "object" related to the RSVP message. */
	public Object object; 
	/** The wavelength mask of this packet. Equivalent to the Label Set inclusive. */
	protected WavelengthMask mask;
	/** The error specification object. */
	protected Error error=null;
	/** Indicates whether to record or no the route traversed. */
	protected boolean recordRoute = true;
	/** Indicates if the message is associated with a full LSP re-routing. */
	protected boolean reRouting = false;
	/** Explicit routing object for traffic engineering purposes. */
	protected Path explicitRoute;
	
	/**
	 * Creates a new RSVP object for establishing a lightpath.
	 * @param request The specified request for lightpath.
	 * @param limit The maximum number of hops allowed.
	 * @param label A label for identifying the flow (lightpath).
	 */
	public RSVP(LightpathRequest request, int limit, long label) {
		super(Packet.Header.RSVP_PATH,request.getSource(),request.getTarget(),Packet.Priority.HIGH,0,limit);
		this.setFlowLabel(Long.toString(label));
		object = request;
	}
	
	/**
	 * Creates a RSVP message with the source given by the keySource value and 
	 * the destination node given by keyTarget value.
	 * @param connection The information about the connection.
	 * @param type The type of the RSVP-TE message
	 * @param keySource The identification of the source node.
	 * @param keyTarget The identification of the target node.
	 */
	public RSVP(Connection connection, Header type, String keySource, String keyTarget) {
		super(type,keySource,keyTarget,Packet.Priority.HIGH,0,(connection.size() + 1));
		this.setFlowLabel(connection.getID());
		object = connection;		
	}
	
	/**
	 * Returns the object associated with the RSVP message.
	 * @return The object associated with the RSVP message.
	 */
	public Object getObject() {
		return this.object;
	}
	
	/**
	 * Sets a new object associated with the RSVP message.
	 * @param aObject The new object.
	 */
	public void setObject(Object aObject) {
		this.object = aObject;
	}
	
	
	/**
	 * Updates the Label Set for this message.
	 * @param aMask The link mask.
	 */
	public void updateMask(WavelengthMask aMask) {
		if (mask == null) { //Not initialized
			mask = (WavelengthMask) aMask.clone();
		} else {
			mask.updateMask(aMask);
		}
	}
	
	/**
	 * Gets the Label Set of this message.
	 * @return The Label Set of this message.
	 */
	public WavelengthMask getMask() {
		return this.mask;
	}
	
	/**
	 * Set the actual processing node of this packet.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		if (recordRoute) {
			super.setNode(procId); //Record route
		} else {
			//Just set the processing node.
			this.procNode = procId;
		}
	}
	
	/**
	 * Gets the backward node of the rsvp message. To be used only for the reservation message.
	 * @return The backward node of the rsvp message. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		try {
			return path.getPreviousNode(procNode);
		} catch (Exception e) {return null; /* In case of an error!*/}
	}
	
	/**
	 * Gets the forward node of the rsvp message. To be used only for the backward message.
	 * @return The forward node of the ant. Null, if the processing node
	 * is the target node.
	 */
	public String getForwardNode() {
		try {
			return path.getNextNode(procNode);
		} catch (Exception e) {return null; /* In case of an error!*/}		
	}

	/**
	 * Resets (clear) the record route flag.
	 */
	public void resetRecordRoute() {
		this.recordRoute = false;
	}
	
	/**
	 * Sets (activate) the record route flag.
	 */
	public void setRecordRoute() {
		this.recordRoute = true;
	}
	
	/**
	 * Retutns the error associated with the message.
	 * @return The error associated with the message.
	 */
	public Error getError() {
		return error;
	}

	/**
	 * Set the error object.
	 * @param aError The error to set.
	 */
	public void setError(Error aError) {
		this.error = aError;
	}
	
	/**
	 * Set the explicit route object.
	 * @param route The explicit route to be followed.
	 */
	public void setExplicitRoute(Path route) {
		this.explicitRoute = route;
	}
	
	/**
	 * Get the explicit route object. 
	 * @return The explicit route object.
	 */
	public Path getExplicitRoute() {
		return this.explicitRoute;
	}
	
	/**
	 * Set a new value for the source and destination of
	 * this message.
	 * @param newSource The new source identification of this packet.
	 * @param newTarget The new target identification of this packet.
	 */
	public void setSDPair(String newSource, String newTarget) {
		this.source = newSource;
		this.target = newTarget;
	}
	
	/**
	 * Set the status of a failed LSP looking for full re-routing.
	 */
	public void setReRouting() {
		this.reRouting = true;
	}
	
	/**
	 * Return the status wheter this message is looking for full LSP
	 * re-routing after a failure.
	 * @return True, if this message indicates a full LSP re-routing.
	 * False, otherwise.
	 */
	public boolean isReRouting() {
		return this.reRouting;
	}
	
	/**
	 * Returns true, if the next hop has already been visited, i.e.,
	 * if a loop will be formed.
	 * @param id The id of the next hop.
	 * @return True, if the next hop has already been visited. False, otherwise.
	 */
	public boolean detectLoop(String id) {
		return (path.getNodePosition(id) != -1);		
	}
	
	/**
	 * Returns a String representation of this object. 
	 */
	public String toString(){
		String superString = super.toString();
		StringBuilder builder = new StringBuilder();
		//Add the object to the string
		builder.append(". ");
		if (this.error != null) {
			builder.append(error.toString());
			builder.append(". ");
		}
		builder.append("Object: ");
		builder.append(object.toString());
		if (this.recordRoute)
			builder.append(", [RECORD_ROUTE]");
		if (this.reRouting) 
			builder.append(", [RE-ROUTING]");
		//Add the masks to the string
		if (this.mask != null) {
			builder.append(", label set: ");
			builder.append(mask.toString());
		}
		return superString.concat(builder.toString());
	}

}
