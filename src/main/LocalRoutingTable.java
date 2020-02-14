/*
 * Created on Sep 14, 2005.
 */
package main;

import java.io.Serializable;
import java.util.*;
import graph.*;

/**
 * This class contains a routing table from the node
 * to all destinations in the network. It does not account
 * for the origin of the packet.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public abstract class LocalRoutingTable implements RoutingTable, Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The source node of this routing table. */
	protected String id; 
	/** The mapping between nodes and values. */
	protected LinkedHashMap<String,Integer> destinationMap;
	/** The routing table entry of all destinations in the network. */
	protected RoutingTableEntry[] destination;
	/** Local topology graph. */
	protected Graph localGraph;
	
	/**
	 * Creates a new LocalRoutingTable object.
	 * @param source The id of the the node, which this table belongs to.
	 */
	public LocalRoutingTable(String source) {
		this.id = source;
		this.destinationMap = new LinkedHashMap<String,Integer>();
		this.destination = null;
		this.localGraph = null;
	}
	
	/**
	 * Returns an entry containing the neighborhood of this node, given a destination node.
	 * @param target The id of the destination node. 
	 * @return An object containing the neighborhood of this node, given a destination node.
	 */
	public RoutingTableEntry neighbors(String target) {
		int index = destinationMap.get(target);
		return destination[index];		
	}
	
	/**
	 * Returns the routing table of all destinations in the network.
	 * @return The routing table of all destinations in the network.  
	 */
	public RoutingTableEntry[] getRoutingTable() {
		return this.destination;
	}
	
	/**
	 * Gets the source node of this routing table.
	 * @return The source node of this routing table.
	 */
	public String getId() {
		return this.id;
	}	
	
	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology. 
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	@SuppressWarnings("unchecked")
	public void updateFromTopology(Graph graph) {
		if ((localGraph != null) && localGraph.equals(graph))
			return; //nothing to do
		//Clone the graph and maintain a local copy
		this.localGraph = (Graph)graph.clone();
		//Create space for the new destination
		RoutingTableEntry[] newDestination = new RoutingTableEntry[graph.size() - 1];
		//Create a new mapping
		LinkedHashMap<String,Integer> newDestinationMap = new LinkedHashMap<String,Integer>();
		int counter= 0; //Index counter
		//For each node in the graph
		for(String nodeId:graph.nodes()) {
			if (!nodeId.equals(id)) {
				//Put it into the new mapping
				newDestinationMap.put(nodeId,counter);
				counter ++; //Increment counter
			}
		}
		//for each node in the graph do
		for (String key: graph.nodes()) {
			if (!key.equals(id)) {
				int index = newDestinationMap.get(key); //The index in the routing table
				String[] neighborIds = new String[0];
				neighborIds = graph.adjacentNodes(id).toArray(neighborIds); //The ids of the neighbors
				//Verify if it is a brand new node or if it needs updating
				if (destinationMap.get(key) == null) { //Brand new 
					newDestination[index] = new RoutingTableEntry(neighborIds);
				} else { //the node already exists in the routing table, so maybe its needs to update a neighbor
					newDestination[index] = destination[destinationMap.get(key)];
					//for each adjacent node, add a node if a new one is found.
					for (String neighId: neighborIds) {
						if (!newDestination[index].contains(neighId.toString())) { //adds a new one
							newDestination[index].addEntry(neighId);
						}
					}
					//Now check if a neighbor link was removed
					Iterator<String> iterator = destination[destinationMap.get(key)].neighborhood().iterator(); 
					while(iterator.hasNext()) {
						String neighId = iterator.next();
						boolean remove = true; //remove flag
						for(String nId:neighborIds) {
							if (nId.equals(neighId)) {
								remove = false; //found, no need to remove it
								break;
							}
						}
						if (remove) { //neighbor not found in the actual topology
							iterator.remove();
//							System.out.println(destination[destinationMap.get(key)].toString());
						}
					}
				}
			}
		}
		//Updates the variables
		this.destination = newDestination;
		this.destinationMap = newDestinationMap;
	}
		

	/**
	 * Returns a string representation of the routing table.
	 * @return A string representation of the routing table.
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("Node id: ");
		buffer.append(id);
		buffer.append("\n");
		buffer.append("Routing table: \n");
		for (Object key: destinationMap.keySet()) {
			int index = destinationMap.get(key);
			buffer.append("Destination: ");
			buffer.append(key.toString());
			buffer.append("\n");
			buffer.append(destination[index].toString());
			buffer.append("\n");			
		}		
		return buffer.toString();
	}

}