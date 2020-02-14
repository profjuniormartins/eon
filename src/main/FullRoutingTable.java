/*
 * Created on 03/02/2006.
 */
package main;

import graph.Graph;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * This class contains a routing table from each source node
 * to all destinations in the network.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public abstract class FullRoutingTable implements RoutingTable, Serializable{
	/** Default serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The source node of this routing table. */
	protected String id; 
	/** The mapping between nodes and values. */
	protected LinkedHashMap<String,Integer> nodeMap;
	/** The routing table entry from each source node to
	 *  all destinations in the network. */
	protected RoutingTableEntry[][] table;
	/** Local topology graph. */
	protected Graph localGraph;

	/**
	 * Creates a new FullRoutingTable object.
	 * @param source The id of the the node, which this table belongs to.
	 */
	public FullRoutingTable(String source) {
		this.id = source;
		this.nodeMap = new LinkedHashMap<String,Integer>();
		this.table = null;
		this.localGraph = null;
	}

	/**
	 * Returns an entry containing the neighborhood of this node, 
	 * given a source and a destination node.
	 * @param source The id of the source node.
	 * @param target The id of the destination node. 
	 * @return An object containing the neighborhood of this node, 
	 * given a destination node.
	 */
	public RoutingTableEntry neighbors(String source, String target) {
		int indexSource = nodeMap.get(source);
		int indexTarget = nodeMap.get(target);
		return table[indexSource][indexTarget];		
	}
	
	/**
	 * Returns the routing table from each source node to all destinations in the network.
	 * @return The routing table from each source node to all destinations in the network.  
	 */
	public RoutingTableEntry[][] getRoutingTable() {
		return this.table;
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
		//Clone the graph and maintain a copy
		this.localGraph = (Graph)graph.clone();
		//Create space for the new destination
		RoutingTableEntry[][] newTable = new RoutingTableEntry[graph.size()][graph.size()];
		//Create a new mapping
		LinkedHashMap<String,Integer> newNodeMap = new LinkedHashMap<String,Integer>();
		int counter= 0; //Index counter
		//For each node in the graph
		for(String nodeId:graph.nodes()) {
			//Put it into the new mapping
			newNodeMap.put(nodeId,counter);
			counter ++; //Increment counter
		}
		//for each source-destination pair in the graph do
		for (String source: graph.nodes()) {
			for (String target: graph.nodes()) {
				if (!source.equals(target)) {
					//The index of the mapping
					int indexSource = newNodeMap.get(source);
					int indexTarget = newNodeMap.get(target);
					String[] neighborIds = new String[0];
					neighborIds = graph.adjacentNodes(id).toArray(neighborIds); //The ids of the neighbors
					//Verify if it is a brand new node or if it needs updating
					if ((nodeMap.get(source) == null) ||(nodeMap.get(target) == null)) { //Brand new 
						newTable[indexSource][indexTarget] = new RoutingTableEntry(neighborIds);
					} else { //the node already exists in the routing table, so maybe its needs to update a neighbor
						newTable[indexSource][indexTarget] = table[nodeMap.get(source)][nodeMap.get(target)];
						//for each adjacent node, add a node if a new one is found.
						for (String neighId: neighborIds) {
							if (!newTable[indexSource][indexTarget].contains(neighId.toString())) { //adds a new one
								newTable[indexSource][indexTarget].addEntry(neighId);
							}
						}
						//Now check if a neighbor link was removed
						Iterator<String> iterator = table[nodeMap.get(source)][nodeMap.get(target)].neighborhood().iterator(); 
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
//								System.out.println(destination[destinationMap.get(key)].toString());
							}
						}
					} 
				}
			}
		}
		//Updates the variables
		this.table = newTable;
		this.nodeMap = newNodeMap;
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
		for (String source: nodeMap.keySet()) {
			for(String target: nodeMap.keySet()) {
				if (!source.equals(target)) {
					buffer.append(source);
					buffer.append("-");
					buffer.append(target);
					buffer.append(": \n");
					int indexSource = nodeMap.get(source);
					int indexTarget = nodeMap.get(target);
					buffer.append(table[indexSource][indexTarget].toString());
					buffer.append("\n");					
				}
			}
		}		
		return buffer.toString();
	}

}
