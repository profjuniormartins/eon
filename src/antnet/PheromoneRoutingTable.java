/*
 * Created on Sep 23, 2005.
 */
package antnet;

import graph.Graph;
import main.LocalRoutingTable;
import main.RoutingTableEntry;
import ops.Packet;
import rwa.antnet.AntNetControlPlane;

import java.util.*;


/**
 * A pheromone routing table to be used with Ant Colony Optimization Algorithms.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class PheromoneRoutingTable extends LocalRoutingTable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** Maximum allowed reinforcement, to prevent pheromone values going to 100% .*/
	public static final double MAX_REINFORCEMENT = 0.9;
	/** The confidence level. */
	protected double confidence;
	/** The z-factor derived from the confidence coefficient level. */
	protected static double zFactor;
	/** The constant that weights the best value in the reinforcement equation. */
	protected double c1;
	/** The constant that weights the confidence interval in the reinforcement equation. */
	protected double c2;
	/** The amplifier of the squash function. */
	protected double amplifier;
	/** It weights the relative importance of the heuristic correction 
	 * with respect to the pheromone values stored in the routing table.*/
	protected static double alpha;
	
	/**
	 * Creates a new PheromoneRoutingTable object.
	 * @param source The id of the node associated with this routing table.
	 * @param aConfidence The confidence level.
	 * @param firstWeight The constant that weights the best value in the reinforcement equation.
	 * @param secondWeight The constant that weights the confidence interval in the reinforcement equation.
	 * @param aAmplifier The amplifier of the squash function.
	 * @param alpha It weights the relative importance of the heuristic correction 
	 * with respect to the pheromone values stored in the routing table.
	 */
	public PheromoneRoutingTable(String source, double aConfidence, double firstWeight, double secondWeight, double aAmplifier, double aAlpha) {
		super(source);
		this.confidence = aConfidence;
		zFactor = 1.0 / (Math.sqrt(1.0 - confidence));
		this.c1 = firstWeight;
		this.c2 = secondWeight;
		this.amplifier = aAmplifier;
		this.alpha = aAlpha;
	}		
	
	/**
	 * Creates a new PheromoneRoutingTable. For sub-classing purposes only!
	 * @param source The id of the node associated with this routing table.
	 */
	protected PheromoneRoutingTable(String source) {
		super(source);
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
		super.updateFromTopology(graph);
		//for each destination of the routing table do
		for (Object destinationId : destinationMap.keySet()) {
			int index = destinationMap.get(destinationId);
			RoutingTableEntry rTable = destination[index];
			//Find the neighbors that must be updated
			Vector<String> update = new Vector<String>();  
			//The sum of the probabilities of all neighbors.
			double sum = 0;
			//for each neighbor link of the destination do			
			for (String neighId : rTable.neighborhood()) {
				if (rTable.getEntry(neighId) == null) { 
					update.add(neighId); //add the id of the neighbor to the vector
				} else { //valid neighbor object
					sum = sum + ((NeighborAttr)rTable.getEntry(neighId)).getPheromoneLevel();
				}
			}
			//Initialize or update the routing table
			int size = update.size(); //number of neighbors to be updated. 
			if (size == rTable.size()) { //Initialization of the routing table
				if (update.contains(destinationId)) {  //Inteligent initialization
					//for each new neighbor link of the destination do			
					for (Object neighId : update) { 
						//Create a new Neighbor object
						NeighborAttr neigh = new NeighborAttr(neighId);
						if (destinationId.equals(neighId)) { //Neighbor == destination
							double level = (1.0/((double)size)) + 1.5 * (((double)size - 1.0) / ((double)size*size));
							neigh.setPheromoneLevel(level);
							
						} else { //Rest of neighbors
							if (size == 1) {
								neigh.setPheromoneLevel(0.0);
							} else {
								double level = (1.0/((double)size)) - 1.5 * (1.0 / ((double)size*size));
							
								neigh.setPheromoneLevel(level)
								;
							}
						}
						//Update the neighbor link information
						rTable.putEntry(neighId,neigh);
					}
				} else { //Conventional initialization
					//for each new neighbor link of the destination do			
					for (String neighId : update) {
						//Create a new Neighbor object
						NeighborAttr neigh = new NeighborAttr(neighId);
						//Normalize the feromone level for each neighbor of this node					
						neigh.setPheromoneLevel(1.0/((double)size));
						//Update the neighbor link information
						rTable.putEntry(neighId,neigh);
					}
				}
			} else if(size > 0) { //Update of just few new (added) neighbors. 
				//for each new neighbor link of the destination do			
				for (String neighId : update) {
					//Create a new Neighbor oject
					NeighborAttr neigh = new NeighborAttr(neighId);
					//Set the pheromone level for each new neighbor of this node to zero.					
					neigh.setPheromoneLevel(0.0);
					//Update the neighbor link information
					rTable.putEntry(neighId,neigh);					
				}				
			} else { //Check for removed link or nodes (use of the Baran & Sosa algorithm)
				//for each neighbor link of the destination do			
				for (String neighId : rTable.neighborhood()) {
					NeighborAttr neigh = (NeighborAttr) rTable.getEntry(neighId);
					//Get the actual pheromone level
					double pLevel = neigh.getPheromoneLevel();
					//Distribute the remaing level among the other neighbors, i.e., the total sum od
					//the pheromone levels is now equal to one.
					neigh.setPheromoneLevel(pLevel/sum);
				}				
			}
		}		
	}

	/**
	 * Gets the next hop of the specified packet, relying only on pheromone values.
	 * @param packet The packet to be routed.
	 * @return The next hop of the specified packet, relying only on routing values. 
	 */
	@SuppressWarnings("unchecked")
	public String nextHop(Packet packet) {
		//Get the target node
		String target = packet.getTarget();
		//Get the appropriate destination routing table
		int index = destinationMap.get(target);
		RoutingTableEntry neighborhood = destination[index];
//		System.out.println("Packet: "+packet);
//		System.out.println("Neighborhood: "+neighborhood);
//		try {
		return AntNetControlPlane.select(neighborhood,packet);
//		} catch(Exception e){System.out.println("*** "+packet.toString() +" \n"+neighborhood.toString());return null;}
	}
	

	
	/**
	 * Returns the adaptive reinforcement given by the backward ant in respect
	 * to the local parametric model of the node.
	 * @param ant The backward ant;
	 * @param view The local parametric view associated with the target of the backward ant.
	 * @param size The size of the neighborhood.
	 * @param destId The identification of the destination node
	 * @return The adaptive reinforcement value.
	 */
	protected double getReinforcement(AntPacket ant, LocalParametricView view, int size, String destId) {
		//The reinforcement given by the specified ant.
		double reinforcement;
		String procId = ant.getNode();
		double subPath = (double) ant.getSubPathLength(procId,destId); 
//		System.out.println("SubPath: "+subPath);
//		System.out.println("View: "+view.toString());
		double firstTerm = (view.getBest() / subPath);
		double upperInterval = view.getAverage() + zFactor * (view.getDeviation() / Math.sqrt((double)view.getWindow()));
//		System.out.println("upperInterval: "+upperInterval);
		double denominator = ((upperInterval - view.getBest()) + (subPath - view.getBest()));
		double secondTerm = (upperInterval - view.getBest()) / denominator;
//		System.out.println("SecondTerm: "+secondTerm);
		if (denominator == 0.0) { //Singularity problems!
			reinforcement = firstTerm;
//			System.out.println(".....");
		} else { //Normal case
			reinforcement = this.c1 * firstTerm + this.c2 * secondTerm;
		}
		//Now uses the squash function to compress the lower scale
		reinforcement = squash(reinforcement,size);
		//Verifies if the reinforcement is above the maximum level
//		System.out.println("Pheromone reinforcement: "+reinforcement);
		if (reinforcement > MAX_REINFORCEMENT) {
			return MAX_REINFORCEMENT;
		} else {
			return reinforcement;
		}
	}
	
	/**
	 * Squash function.
	 * @param value The value to be squashed.
	 * @param neighborhoodSize The number of neighbors 
	 * @return The squashed value.
	 */
	protected double squash(double value, int neighborhoodSize) {
		return 1.0 / (1.0 + Math.exp(amplifier / (value * neighborhoodSize))); 
	}
}

