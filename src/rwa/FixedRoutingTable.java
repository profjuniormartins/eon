/*
 * Created on Dec 12, 2005.
 */
package rwa;

import java.util.Arrays;
import java.util.Vector;

import graph.Graph;
import graph.Path;
import graph.YEN;
import main.LocalRoutingTable;
import main.RoutingTableEntry;
import ops.Packet;

/**
 * Creates a routing table with fixed paths.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class FixedRoutingTable extends LocalRoutingTable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The number of k-shortest paths (alternatives of paths) calculated. */
	int alternative;
	
	/**
	 * Creates a new FixedRoutingTable object.
	 * @param source The source id of the node associated to this routing table.
	 * @param k The number of k-shortest paths calculated.
	 */
	public FixedRoutingTable(String source, int k) {
		super(source);
		this.alternative = k;
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
		super.updateFromTopology(graph);
		YEN yen = new YEN();
		//for each destination of the routing table do
		for (String destinationId : destinationMap.keySet()) {
			int index = destinationMap.get(destinationId);
			RoutingTableEntry rTable = destination[index];
			//for each neighbor link of the destination do			
			for (String neighId : rTable.neighborhood()) {
				//Get the shortest paths between id and the destination
				Vector<Path> paths = null;
				try {
					paths = yen.getShortestPaths(id,destinationId,graph,alternative);
					System.out.println(paths.toString());
				} catch (Exception e) {e.printStackTrace();}
				//Index for generating the paths
				int counter = 0;
				Option option = new Option(alternative);
				//For each path do
				for (Path path : paths) {
					if ((path != null) && path.containNode(neighId)) {
						//Neighbor is present in the path
						option.set(counter); //set as true this position
					} 
					counter ++; //Increment counter
				}
				//Now put the entry
				rTable.putEntry(neighId,option);
			}
		}		
	}
	
	@SuppressWarnings("unchecked")
	/**
	 * Gets the next hop of the packet of the specified packet.
	 * @param packet The packet to be routed.
	 * @param k The shortest-path chosen. The first path starts with 0 index.
	 * @return The next hop of the packet of the specified packet. Null,
	 * if nothing appropriate is found 
	 */
	public String nextHop(Packet packet, int k) {
		//Get the target node
		Object target = packet.getTarget();
		//Get the appropriate destination routing table
		int index = destinationMap.get(target);
		RoutingTableEntry neighborhood = destination[index];
		//Return the node that is marked as true in the table
		for (String neighId : neighborhood.neighborhood()) {
			Option option = (Option)neighborhood.getEntry(neighId);
			if (option.get(k))
				return neighId;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	/**
	 * Gets the next hop of the packet of the specified packet.
	 * @param packet The packet to be routed.
	 * @param k The shortest-path chosen.
	 * @return The next hop of the packet of the specified packet. Null,
	 * if nothing appropriate is found 
	 */
	public String nextHop(Packet packet) {
		return this.nextHop(packet,0);
	}

}

/**
 * Wrapper-class to hide the attributes of chosing between
 * the alternatives routes.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
class Option {
	/** The vector of boolean values representing the paths. */
	boolean[] options;
	
	/**
	 * Creates a new Option object.
	 * @param k The number of shortest-paths.
	 */
	Option(int k) {
		options = new boolean[k];
		Arrays.fill(options,false); //fill with false values		
	}
	
	/**
	 * Sets the specified position, i.e., the shortest path.
	 * @param position The specified position.
	 */
	void set(int position) {
		options[position] = true;
	}
	
	/**
	 * Gets the value of the specified position, i.e., if that id
	 * is set to true.
	 * @param position 
	 * @return
	 */
	boolean get(int position) {
		return options[position];
	}
}
