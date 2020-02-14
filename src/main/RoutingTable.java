/*
 * Created on 03/02/2006.
 */
package main;

import graph.Graph;
import ops.Packet;

/**
 *
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public interface RoutingTable {
	/**
	 * Gets the source node of this routing table.
	 * @return The source node of this routing table.
	 */
	public String getId();
	
	/**
	 * Get the next hop of the specified packet.
	 * @param packet The packet to be routed.
	 * @return The next hop of the specified packet.
	 */
	public abstract String nextHop(Packet packet);

	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology. 
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	@SuppressWarnings("unchecked")
	public void updateFromTopology(Graph graph);

}
