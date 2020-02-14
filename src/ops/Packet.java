/*
 * Created on Sep 14, 2005.
 */
package ops;

import java.io.Serializable;
import graph.Path;
import graph.Edge;

/**
 * <p> This class defines a packet inside a network. The packet header has a fixed
 * size, which is not accounted in its payload length. The packet can
 * carry any other type of packet, like TCP, UDP, ICMP, etc, and its contents
 * is indicated by the Next Header field.  
 * <p>
 * <b>Note:</b> The fields were based on the standard IPv6 headers.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Packet implements Serializable,Cloneable {
	/** Type of priority of a packet. */
	public enum Priority {
		/** High priority traffic. */HIGH,
		/** Best-effort traffic.*/ NORMAL
	}	
	/** The type of the packet as indicate in the next header field of IPv6. */
	public enum Header {
		/** Normal data packet. */ DATA, 
		/** Link failure. */ LINK_FAILURE,
		/** Node failure. */ NODE_FAILURE,
		/** Forward ant. */ ANT_FORWARD,
		/** Backward ant. */ ANT_BACKWARD,
		/** RSVP Path message. */ RSVP_PATH,
		/** RSVP Resv message. */ RSVP_RESV,
		/** RSVP PathErr message. */ RSVP_PATH_ERR,
		/** RSVP ResvErr message. */ RSVP_RESV_ERR,
		/** RSVP PathTear message. */ RSVP_PATH_TEAR,
		/** RSVP ResvTear message. */ RSVP_RESV_TEAR,
		/** Job request. */ JOB_REQUEST,
		/** Job setup. */ JOB_SETUP,
		/** Job finished - cleanup resources. */ JOB_CLEANUP,
		/** Advertising in a publish and subscribe scheme. */ PUBLISH,
	}	
	
	/** The fixed length of the header of this packet. */
	public static final int HEADER_LENGTH = 40;
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** Source node identification/address. */
	protected String source;
	/** Target node identification/address.*/
	protected String target;
	/** Identifies all packets belonging to a specific flow, i.e., the same LSP. */
	protected String flowLabel;
	/** Defines the priority of the packet. */
	protected Priority trafficClass;
	/** Indicates the next extension header to examine, i.e., 
	 * the type of the encapsulated packet, such as TCP, UDP, ICMP, etc... */
	protected Header nextHeader; 
	/** Payload length (in bytes). Not linked to the actual payload (Object) size. */
	protected int payloadLength;
	/** Maximum number of hops allowed. */
	protected int hopLimit;
	/** A reference to the data payload. */
	protected Payload payload;
	/* Not in IPv6 standard. Needed to facilitate the simulation */
	/** The actual processing node. */
	protected String procNode;
	/** The path followed by the packet. */
	protected Path path;
	/** The wavelength used by this packet. */
	protected int wavelength;
	
	/**
	 * Creates a new Packet object.
	 * @param header The header indicating the type of the encapsulated packet.
	 * @param sourceId The source identification of this node.
	 * @param targetId The target identification of this node.
	 * @param priority The class of traffic of this packet.
	 * @param length The payload length of this packet. The header has a fixed length, which is not accounted in this field.
	 * @param limit The maximum number of hops allowed.
	 */
	public Packet(Header header, String sourceId, String targetId, Priority priority, int length, int limit) {
		this.nextHeader = header;
		this.source = sourceId;
		this.target = targetId;
		this.trafficClass = priority;
		this.payloadLength = length;
		this.hopLimit = limit;
		this.procNode = sourceId;
		path = new Path();
		path.addNode(source);
	}
	
	/**
	 * Creates a new Packet object.
	 * @param header The header indicating the type of the encapsulated packet.
	 * @param sourceId The source identification of this node.
	 * @param targetId The target identification of this node.
	 * @param priority The class of traffic of this packet.
	 * @param length The payload length of this packet. The packet has a fixed length, which is not accounted in this field.
	 * @param limit The maximum number of hops allowed.
	 */
	public Packet(Header header, String sourceId, String targetId, Priority priority, int length, int limit, int wavelength) {
		this(header,sourceId,targetId,priority, length, limit);
		this.wavelength = wavelength;
	}
	
	/**
	 * Creates a new Packet object, for cloning purposes.
	 */
	public Packet() {
	}
	
	/**
	 * Retransmit the packet to another destination as if 
	 * the retransmitter is the new sender.
	 * @param sourceId The new source identification of this packet.
	 * @param targetId The new target identification of this packet.
	 */
	public void retransmit(String sourceId, String targetId) {
		this.source = sourceId;
		this.target = targetId;
	}

	/**
	 * Returns the eventual label identifying the associated flow.
	 * @return Returns the eventual label identifying the associated flow.
	 */
	public String getFlowLabel() {
		return this.flowLabel;
	}

	/**
	 * Set the label identifying the associated flow.
	 * @param flowLabel A new label to set.
	 */
	public void setFlowLabel(String fLabel) {
		this.flowLabel = fLabel;
	}

	/**
	 * Returns a reference to the payload object.
	 * @return Returns a reference to the payload object.
	 */
	public Object getPayload() {
		return this.payload;
	}

	/**
	 * Set a payload to this packet.
	 * @param payload The payload to set.
	 */
	public void setPayload(Payload aPayload) {
		this.payload = aPayload;
	}

	/**
	 * Returns the payload length. It does not include the header length.
	 * @return Returns the payload length.
	 */
	public int getPayloadLength() {
		return this.payloadLength;
	}

	/**
	 * Set the payload length. It does not include the header length.
	 * @param aPayloadLength The payload length to set.
	 */
	public void setPayloadLength(int aPayloadLength) {
		this.payloadLength = aPayloadLength;
	}

	/**
	 * Returns the maximum number of hops allowed for this packet/
	 * @return Returns the hop limit.
	 */
	public int getHopLimit() {
		return this.hopLimit;
	}

	/**
	 * Decrements the number of allowed for this packet. In each visited node, this function
	 * must be called to decrement this number.
	 */
	public void decrementHopLimit() {
		hopLimit = hopLimit - 1;
	}

	/**
	 * Returns the nextHeader field (the type of the encapsulated packet) of this packet.
	 * @return The nextHeader field (the type of the encapsulated packet) of this packet.
	 */
	public Header getNextHeader() {
		return this.nextHeader;
	}

	/**
	 * Sets the nextHeader field (the type of the encapsulated packet) of this packet.
	 * @param header The new value for the header.
	 */ 
	public void setNextHeader(Header header) {
		this.nextHeader = header;
	}
	
	/**
	 * Returns the source node of this packet.
	 * @return The source node of this packet.
	 */
	public String getSource() {
		return this.source;
	}

	/**
	 * Returns the target node of this packet.
	 * @return The target node of this packet.
	 */
	public String getTarget() {
		return this.target;
	}

	/**
	 * Returns the actual processing node of this packet.
	 * @return The actual processing node of this packet.
	 */
	public String getNode() {
		return this.procNode;
	}

	/**
	 * Set the actual processing node of this packet.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		this.procNode = procId;
		this.path.addNode(procId);
	}

	/**
	 * Returns the traffic class of this packet.
	 * @return Returns the traffic class of this packet.
	 */
	public Priority getTrafficClass() {
		return this.trafficClass;
	}
	
	/**
	 * Gets the wavelength used by this packet.
	 * @return Returns the wavelength.
	 */
	public int getWavelength() {
		return wavelength;
	}

	/**
	 * Sets the wavelength used by this packet.
	 * @param wavelength The wavelength to set.
	 */
	public void setWavelength(int wavelength) {
		this.wavelength = wavelength;
	}

	/**
	 * Return the length, in number of hops, traversed by this packet. 
	 * @return The length of the path traversed by this packet.
	 */
	public int getPathLength() {
		if (path.size() == 0)
			return 0;
		else 
			return (path.size() - 1);
	}
	
	/**
	 * Returns the path followed by the packet.
	 * @return The path followed by the packet.
	 */
	public Path getPath() {
		return this.path;
	}
	
	/**
	 * Returns the last link (to be) traversed by this packet.
	 * This link has a Edge, where the last node is the actual processing node.
	 * @return The last link (to be) traversed by this packet.
	 */
	public Edge getLastLink() {
		return path.getLastEdge();
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Header: ");
		buf.append(nextHeader);
		buf.append(", priority: ");
		buf.append(trafficClass);
		buf.append(", source: ");
		buf.append(source);
		buf.append(", target: ");
		buf.append(target);
		buf.append(", proc: ");
		buf.append(procNode);
		buf.append(", pl_length: ");
		buf.append(payloadLength);		
		buf.append(", label: ");
		buf.append(flowLabel);
		buf.append(", hopLimit: ");
		buf.append(hopLimit);
		buf.append(", path: ");
		buf.append(path.toString());
		buf.append(", w: ");
		buf.append(wavelength);
		return buf.toString();
	}
	
	/**
	 * Clone the object.
	 */
	public Object clone() {
		Packet cloned = new Packet();
		cloned.source = new String(this.source);
		cloned.target = new String(this.target);
		cloned.procNode = new String(this.procNode);
		if (flowLabel != null)
			cloned.flowLabel = new String(this.flowLabel);
		cloned.path = (Path)this.path.clone();
		cloned.hopLimit = this.hopLimit;
		cloned.nextHeader = this.nextHeader;
		cloned.payload = (Payload)this.payload.clone();
		cloned.payloadLength = this.payloadLength;
		cloned.trafficClass = this.trafficClass;
		cloned.wavelength = this.wavelength;
		return cloned;
	}
}
