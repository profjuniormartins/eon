/*
 * Created on 15/02/2008.
 */
package rwa.crankback.antnetInter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;

import main.RoutingTableEntry;
import antnet.AntPacket;
import antnet.LocalParametricView;
import antnet.NeighborAttr;
import antnet.PheromoneRoutingTable;
import antnet.StatisticalParametricModel;
import ops.Packet;
import rwa.LinkState;
import rwa.crankback.antnetInter.CrankRSVP;

/**
 * Routing table using AntNet characteristics.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class AntNetCrankRoutingTable extends PheromoneRoutingTable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The power factor to enhance the difference in the heuristics correction. */ 
	protected double powerFactor;
	/** Indicates if the routing of the RSVP messages are deterministic or stochastic. True means deterministic. */
	protected boolean deterministic;
	
	/**
	 * Creates a new AntNetCrankRoutingTable object.
	 * @param source The id of the node associated with this routing table.
	 * @param aConfidence The confidence level.
	 * @param firstWeight The constant that weights the best value in the reinforcement equation.
	 * @param secondWeight The constant that weights the confidence interval in the reinforcement equation.
	 * @param aAmplifier The amplifier of the squash function.
	 * @param aAlpha It weights the relative importance of the heuristic correction
	 * @param factor Enhances the difference of available wavelengths in the heuristic calculation
	 * @param aDeterministic Indicates if the routing of the RSVP messages are deterministic or stochastic.
	 */
	public AntNetCrankRoutingTable(String source, double aConfidence, double firstWeight, double secondWeight, double aAmplifier, double aAlpha, double factor, boolean aDeterministic) {
		super(source, aConfidence, firstWeight, secondWeight, aAmplifier, aAlpha);
		this.powerFactor = factor;
		this.deterministic = aDeterministic;
	}
	
	/**
	 * Gets the next hop, using local heuristics variables, i.e., the status of the 
	 * free wavelengths.
	 * @param ant The ant to be processed.
	 * @param links The state of the neighbor links.
	 * @return The next hop, using local heuristics variables.
	 */
	@SuppressWarnings("unchecked")
	public String nextHop(Packet ant, LinkedHashMap<String,LinkState> links) {
		//Get the target node
		String target = ant.getTarget();
		//Get the appropriate destination routing table
		Integer index = destinationMap.get(target);
		if (index == null) //removed node from the topology
			return null;
		RoutingTableEntry neighborhood = destination[index];
		return AntNetCrankInterControlPlane.select(neighborhood,links,(Ant)ant,alpha,powerFactor);
	}

	/**
	 * Gets the next hop for the packet.
	 * @param packet The specified packet.
	 */
	public String nextHop(Packet packet, Vector<String> history) {
		//Get the target node
		String target = packet.getTarget();
		//Get the appropriate destination routing table
		Integer index = destinationMap.get(target);
		if (index == null) { //removed node from the topology
			//System.out.println(packet.toString());
			return null;
		}
		RoutingTableEntry neighborhood = destination[index];
		//Gives the appropriate processing to the RSVP packet
		if (deterministic)
			return AntNetCrankInterControlPlane.select(neighborhood,(CrankRSVP)packet,history);
		else 
			return AntNetCrankInterControlPlane.select(neighborhood,packet);
	}

	/**
	 * Updates the pheromone routing table using the informations contained
	 * in the backward ant and in the local parametric model. 
	 * @param ant The backward ant responsible for updating the node.
	 * @param view The local parametric view.
	 */
	@SuppressWarnings("unchecked")
	public void update(AntPacket ant, StatisticalParametricModel model) {
		//Gets the ant target
		String target = ant.getTarget();
		//Gets the processing node 
		String procId = ant.getNode();
		//Get the list of possible destination nodes to sub-path updating
		List<String> subPath = ant.getSubPath();
		//For each node in the subpath
		for (String nodeId:subPath) {
			//Get the appropriate view
			LocalParametricView view = model.get(nodeId);
			//Verify if it is good to update the sub-path
			double upperCondidenceInterval = view.getAverage() + zFactor * (view.getDeviation() / Math.sqrt((double)view.getWindow()));
			//Get the traveling value of the subpath
			double pathValue = (double)ant.getSubPathLength(procId,nodeId);
			//If it is a good sub-path or it is the "true" target node do
			if ((pathValue < upperCondidenceInterval) || target.equals(nodeId)) {
				//Updates the local view
				model.update(pathValue,nodeId);
				view = model.get(nodeId);
				//Gets the appropriate routing table 
				int index = destinationMap.get(nodeId);
				RoutingTableEntry rTable = destination[index];
				int neighborhoodSize = rTable.size();
				//Gets the reinforcement value.
				double reinforcement = this.getReinforcement(ant,view,neighborhoodSize,nodeId);
				//Gets the node who is the one chosen as forward node
				String forwardId = ant.getForwardNode();
				//For each neighbor do		
				for (String neighId : rTable.neighborhood()) {
					//Gets the old pheromone level
					NeighborAttr attr = (NeighborAttr)rTable.getEntry(neighId);
					double oldLevel = attr.getPheromoneLevel();
					double newLevel; //New pheromone level
					if (neighId.equals(forwardId)) { //Positive reinforcement
						newLevel = oldLevel + (reinforcement * (1.0 - oldLevel));
					} else { //Negative reinforcement
						newLevel = oldLevel - (reinforcement * oldLevel);
					}
					//Set the new level and update the pheromone routing table
					attr.setPheromoneLevel(newLevel);
					rTable.putEntry(neighId,attr);
				}
			}
		}		
	}
	
}
