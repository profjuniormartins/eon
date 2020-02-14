/*
 * Created on 14/02/2008.
 */
package rwa.crankback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import graph.Path;
import ops.Packet;
import rwa.Connection;
import rwa.Error;
import rwa.LightpathRequest;
import rwa.WavelengthMask;

/**
 * The RSVP message for signaling the establishment and the removing
 * of lightpaths. Use of crankback signaling as in RFC 4920.
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
public class CrankRSVP extends Packet {
	/** Serial UID for serialization. */
	private static final long serialVersionUID = 1L;
	/** The main "object" related to the RSVP message. */
	public Object object; 
	/** The Label Set object of the message. */
	protected LabelSet labelSet;
	/** The error specification object. */
	protected Error error=null;
	/** Indicates whether to record or no the route traversed. */
	protected boolean recordRoute = true;
	/** Explicit routing object for traffic engineering purposes. */
	protected Path explicitRoute;
	/** Indicates if the message is associated with a full LSP restoration. */
	protected boolean restoration = false;
	/** Indicates the number of effective hops in establishing a connection. */
	protected int effectiveHops = 0;
	public ArrayList<String> ignoreASBR = new ArrayList<String>();
	//public  String ignoreASBR = "";
	public int selectedRoute = -1;
	public HashMap<String , Vector<String>> selectedDomain = new HashMap<String, Vector<String>>();
	protected boolean rerouting = false;
	/**
	 * Creates a new CrankRSVP object.
	 * 
	 * @param request The specified request for lightpath.
	 * @param limit The maximum number of hops allowed.
	 * @param label A label for identifying the flow (lightpath).
	 */
	public CrankRSVP(LightpathRequest request, int limit, long label) {
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
	public CrankRSVP(Connection connection, Header type, String keySource, String keyTarget) {
		super(type,keySource,keyTarget,Packet.Priority.HIGH,0,(connection.size() + 1));
		this.setFlowLabel(connection.getID());
		this.object = connection;
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
	 * Gets the backward node of the RSVP message. To be used only for the reservation message.
	 * @return The backward node of the RSVP message. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		try {
			return path.getPreviousNode(procNode);
		} catch (Exception e) {return null; /* In case of an error!*/}
	}
	
	/**
	 * Gets the forward node of the RSVP message. To be used only for the backward message.
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
	 * Returns the error associated with the message.
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
	 * Returns the Label Set of this message.
	 * @return The Label Set of this message.
	 */
	public LabelSet getLabelSet() {
		return this.labelSet;
	}
	
	/**
	 * Sets the Label Set of this message.
	 * @param set The Label Set object.
	 */
	public void setLabelSet(LabelSet set) {
		this.labelSet = set;
	}
	
	/**
	 * Updates the Label Set for this message.
	 * @param aMask The link mask.
	 */
	public void updateMask(WavelengthMask aMask) {
		this.labelSet.inclusive(aMask);
	}
	
	/**
	 * Remove from the route the last visited node.
	 * @return The id of the last visited node. Null, if there is no node left.
	 */
	public String removeLastVisited() {
		int len = path.size();
		if (len > 0) {
			String last = path.getNode(len - 1);
			path.removeNodeAt(len - 1);
			return last;
		} else {
			return null;
		}
	}
	
	/**
	 * Set the status of a failed LSP looking for full restoration.
	 */
	public void setRestoration() {
		this.restoration = true;
	}
	
	/**
	 * Return the status whether this message is looking for full LSP
	 * restoration after a failure.
	 * @return True, if this message indicates a full LSP restoration.
	 * False, otherwise.
	 */
	public boolean inRestoration() {
		return this.restoration;
	}
	
	/**
	 * Increment the counter of effective hops. 
	 */
	public void addEffectiveHop() {
		this.effectiveHops ++;
	}
	
	/**
	 * Returns the number of effective hops of this RSVP message.
	 * @return The number of effective hops of this RSVP message.
	 */
	public int getEffectiveHops() {
		return this.effectiveHops;
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
		if (this.labelSet != null) {
			builder.append(", label set: ");
			builder.append(labelSet.toString());
		}			
		builder.append(", effective hops: ");
		builder.append(this.effectiveHops);
		if (this.recordRoute)
			builder.append(", [RECORD_ROUTE]");
		return superString.concat(builder.toString());
	}
	
	public void putSelectedRoute(int selected) {
		this.selectedRoute = selected;
	}
	
	public int getSelectedRoute() {
		return selectedRoute;
	}
	public void resetSelectedRoute() {
		this.selectedRoute = -1;
	}
	
	public boolean hasSelectedRoute() {
		return selectedRoute != -1;
	}
	
	public void addIgnoreASBR(String nodeId) {
		this.ignoreASBR.add(nodeId);
	}
	
	public void removeIgnoreASBR(String nodeId) {
		this.ignoreASBR.remove(nodeId);
	}
	
	public boolean isRerouting() {
		return rerouting;
	}

	public void setRerouting(boolean rerouting) {
		this.rerouting = rerouting;
	}
	
	
	
 
}
