/*
 * Created on 03/02/2006.
 */
package rwa;

import graph.Graph;
import graph.Path;
import graph.YEN;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Vector;

import ops.Packet;
import main.FullRoutingTable;
import main.RoutingTableEntry;

/**
 * Routing table with full-knowledge of the network's topology.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class ExplicitRoutingTable extends FullRoutingTable implements Serializable {
	/** Default serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The number of k-shortest paths (alternatives of paths) calculated. */
	int alternative;

	/**
	 * Creates a new ExplicitRoutingTable object;
	 * @param source The source id of the node associated to this routing table.
	 * @param k The number of k-shortest paths calculated.
	 */
	public ExplicitRoutingTable(String source, int k) {
		super(source);
		this.alternative = k;
	}

	/**
	 * Gets the next hop of the packet of the specified packet.
	 * @param packet The packet to be routed.
	 * @return The next hop of the packet of the specified packet. Null,
	 * if nothing appropriate is found. 
	 */	
	public String nextHop(Packet packet) {
		return nextHop(packet,0);
	}

	/**
	 * Gets the next hop of the packet of the specified packet.
	 * @param packet The packet to be routed.
	 * @param k The shortest-path chosen. The first path starts with 0 index.
	 * @return The next hop of the packet of the specified packet. Null,
	 * if nothing appropriate is found 
	 */
	@SuppressWarnings("unchecked")
	public String nextHop(Packet packet, int k) {
		//Get the source and target nodes.
		String source = packet.getSource();
		String target = packet.getTarget();
		//Gets its internal mapping
		int indexSource = nodeMap.get(source);
		Integer indexTarget = nodeMap.get(target);
		if (indexTarget == null) //removed node from the topology
			return null;
		//Get the entry in the routing table
		RoutingTableEntry neighborhood = table[indexSource][indexTarget];
		//Return the node that is marked as true in the table
		
		for (String neighId : neighborhood.neighborhood()) {
	//		System.out.println("Vizinho = " + neighId);
	//		System.out.println("K = "  + k);
			boolean[] option = (boolean[])neighborhood.getEntry(neighId);
		//	System.out.println("links disponiveis com vizinho " + option[k]);
			if (option[k]) {
	//			System.out.println("Vou retornar " + neighId);
				return neighId;
			}
				
		}
		return null;
	}
	
	

	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology.
	 * It has a Boolean(true) object if the neighbor belongs to the shortest path or
	 * Boolean(false), otherwise.
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	@SuppressWarnings("unchecked")
	public void updateFromTopology(Graph graph) {
		if ((localGraph != null) && localGraph.equals(graph))
			return; //nothing to do
		//System.out.println("Updating table at node:"+this.id);
		super.updateFromTopology(graph);
		YEN yen = new YEN();
		//for each source-destination pair in the graph do
		for (String source: nodeMap.keySet()) {
			for (String target: nodeMap.keySet()) {
				if (!source.equals(target)) {
					//The index of the mapping
					int indexSource = nodeMap.get(source);
					int indexTarget = nodeMap.get(target);
					//Get the routing table
					RoutingTableEntry rTable = table[indexSource][indexTarget];
					//Get the shortest paths between id and the destination
					Vector<Path> paths = null;
					try {
						paths = yen.getShortestPaths(source,target,graph,alternative);
						//System.out.println("Id: "+id+" "+paths.toString());
					} catch (Exception e) {e.printStackTrace();}
					//for each neighbor link of the destination do			
					for (String neighId : rTable.neighborhood()) {
						//Index for generating the paths
						int counter = 0;
						boolean[] option = new boolean[alternative];
						Arrays.fill(option,false); //fill with false values
						//For each path do
						for (Path path : paths) {
							if ((path != null) && path.containNode(neighId) && path.containNode(id) && (path.getNodePosition(neighId) > path.getNodePosition(id))) {
								//Neighbor is present in the path
								try {
									if (neighId.equals(path.getNextNode(id)))
										option[counter] = true; //set as true this position
								} catch (Exception e) {e.printStackTrace();}
							} 
							counter ++; //Increment counter
						}
						//System.out.println(neighId +" - "+option.toString());
						//Now put the entry
						rTable.putEntry(neighId,option);
					}
				}
			}
		}
	}
	
	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology.
	 * It has a Boolean(true) object if the neighbor belongs to the shortest path or
	 * Boolean(false), otherwise.
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	@SuppressWarnings("unchecked")
	public void updateFromTopology(Graph graph, LinkedHashMap<String,Vector<Path>> setPaths) {
		if ((localGraph != null) && localGraph.equals(graph))
			return; //nothing to do
		//System.out.println("Updating table at node:"+this.id);
		super.updateFromTopology(graph);
		//for each source-destination pair in the graph do
		for (String source: nodeMap.keySet()) {
			for (String target: nodeMap.keySet()) {
				if (!source.equals(target)) {
					//The index of the mapping
					int indexSource = nodeMap.get(source);
					int indexTarget = nodeMap.get(target);
					//Get the routing table
					RoutingTableEntry rTable = table[indexSource][indexTarget];
					//for each neighbor link of the destination do			
					for (String neighId : rTable.neighborhood()) {
						//Get the shortest paths between id and the destination
						Vector<Path> paths = setPaths.get(source+"-"+target);
						//Index for generating the paths
						int counter = 0;
						boolean[] option = new boolean[alternative];
						Arrays.fill(option,false); //fill with false values
						//For each path do
						for (Path path : paths) {
							if ((path != null) && path.containNode(neighId) && path.containNode(id) && (path.getNodePosition(neighId) > path.getNodePosition(id))) {
								//Neighbor is present in the path
								try {
									if (neighId.equals(path.getNextNode(id)))
										option[counter] = true; //set as true this position
								} catch (Exception e) {e.printStackTrace();}
							} 
							counter ++; //Increment counter
						}
						//System.out.println(neighId +" - "+option.toString());
						//Now put the entry
						rTable.putEntry(neighId,option);
					}
				}
			}
		}
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
			//Gets its internal mapping
			int indexSource = nodeMap.get(source);
			for(String target: nodeMap.keySet()) {
				Integer indexTarget = nodeMap.get(target);
				//Get the entry in the routing table
				if (!source.equals(target)) {
					RoutingTableEntry neighborhood = table[indexSource][indexTarget];
					buffer.append(source);
					buffer.append("-");
					buffer.append(target);
					buffer.append(": \n");
					for(int k=0; k < alternative; k++) {
						//Return the node that is marked as true in the table
						for (String neighId : neighborhood.neighborhood()) {
							boolean[] option = (boolean[])neighborhood.getEntry(neighId);
							if (option[k]) {
								buffer.append(k+" - "+neighId);
								buffer.append("\n");
							}
						}
					}
				}
			}
		}		
		return buffer.toString();
	}

}
