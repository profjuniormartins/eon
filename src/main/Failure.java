/*
 * Created on January 26, 2006.
 */
package main;

import graph.Edge;
import graph.Path;
import java.io.Serializable;
import ops.Payload;

/**
 * This class represents a failure that has to be flooded or notified
 * to the rest of the network.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class Failure implements Payload, Serializable {
	/** Default UID version for serialization */
	private static final long serialVersionUID = 1L;
	/** The possible types of failure. */
	public enum Type {
		/** Node Failure. */ NODE,
		/** Link Failure. */ LINK,
	}
	/** The location of the failure to the specified node. */
	public enum Location {
		/** Upstream node. */ UPSTREAM,
		/** Upstream node closest to the failure. */ NEIGHBOR_UPSTREAM,
		/** Downstream node. */ DOWNSTREAM,
		/** Downstream node closest to the failure. */ NEIGHBOR_DOWNSTREAM,
		/** Not applicable, i.e., no associated failure
		 *  found in the path. */ NOT_APPLICABLE,
	}
	/** The type of this failure. */
	Type type;
	/** The information about the failure. */
	Object information;
	/** Unique identifier of the failure. Calculated using the
	 *  hash code of the string representation of the information
	 *  object. */
	int uniqueID;
	
	/**
	 * Creates a new Failure object.
	 * @param edge The edge of the graph that failed.
	 */
	public Failure(Edge edge) {
		information = edge;
		type = Type.LINK;
		uniqueID = edge.toString().hashCode();
	}

	/**
	 * Creates a new Failure object.
	 * @param nodeId The identification of the node that failed.
	 */
	public Failure(String nodeId) {
		information = nodeId;
		type = Type.NODE;
		uniqueID = nodeId.hashCode();
	}
	
	/**
	 * Creates a new Failure object, for cloning purposes.
	 */
	protected Failure() {
	}
	
	/**
	 * Returns the type of the failure.
	 * @return The type of the failure.
	 */
	public Type getType() {
		return this.type;
	}
	
	/**
	 * Returns the information of the failure.
	 * @return The information of the failure.
	 */
	public Object getInformation() {
		return this.information;
	}
	
	/**
	 * Returns the unique ID of this failure.
	 * @return The unique ID of this failure.
	 */
	public int getID() {
		return uniqueID;
	}
	
	/**
	 * Locates the failure specified by the key of the node and
	 * the path of a connection.
	 * @param key The key of the node where the connection passes by.
	 * @param path The path of the connection.
	 * @return The location of the failure.
	 */
	public Location locate(String key, Path path) {
		//The location of the failure
		Location location = null;
		//Gets the positon of the key in the path of the connection
		int indexKey = path.getNodePosition(key);
		if (type.equals(Failure.Type.NODE)) { //Node failure
			//Gets the node failed
			String nodeFailure = (String) information;
			//Gets the index of the node failed
			int indexNode = path.getNodePosition(nodeFailure);
			//Verify the position of the failure
			if (indexNode == -1) { //Failure not in the path!
				location = Location.NOT_APPLICABLE;
			} else if (indexKey < indexNode) { //Upstream
				if (indexNode - indexKey == 1) 
					location = Location.NEIGHBOR_UPSTREAM;
				else
					location = Location.UPSTREAM;
			} else if (indexKey > indexNode) { //Downstream
				if (indexKey - indexNode == 1)
					location = Location.NEIGHBOR_DOWNSTREAM;
				else
					location = Location.DOWNSTREAM;
			}
		} else if (type.equals(Failure.Type.LINK)) { //Link failure
			//Gets the last node upstream to the failure 
			String sourceFailure = ((Edge) information).getSource();
			//Gets the first node downstream to the failure 
			String targetFailure = ((Edge) information).getDestination();
			//Gets the positions of the failure 
			int indexSource = path.getNodePosition(sourceFailure);
			int indexTarget = path.getNodePosition(targetFailure);
			//Verify the position of the failure
			if ((indexSource == -1) || (indexTarget == -1) || ((indexTarget - indexSource) != 1)) {
				//Failure not in the path!
				location = Location.NOT_APPLICABLE;
			} else if (indexKey <= indexSource) { //Upstream
				if (indexKey == indexSource) 
					location = Location.NEIGHBOR_UPSTREAM;
				else
					location = Location.UPSTREAM;
			} else if (indexKey >= indexTarget) { //Downstream
				if (indexKey == indexTarget)
					location = Location.NEIGHBOR_DOWNSTREAM;
				else
					location = Location.DOWNSTREAM;
			}
		}
		//Returns the location of the failure.
		return location; 
	}
	
	/**
	 * Returns a cloned version of this object.
	 */
	public Object clone() {
		Failure cloned = new Failure();
		cloned.uniqueID = this.uniqueID;
		cloned.type = this.type;
		if (type.equals(Failure.Type.LINK)) {		
			cloned.information = ((Edge)this.information).clone();
		} else if (type.equals(Failure.Type.NODE)) {
			cloned.information = new String((String)this.information);
		}
		return cloned;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ID: ");
		builder.append(this.uniqueID);
		builder.append(" type: ");
		builder.append(type.toString());
		builder.append(" , information: ");
		builder.append(information.toString());
		return builder.toString();
	}
}
