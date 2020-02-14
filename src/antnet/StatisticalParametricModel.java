/*
 * Created on Nov 3, 2005.
 */
package antnet;

import graph.Graph;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;


/**
 * It is a vector of (N - 1) data structures, containing
 * statistics concerning all destinations of an optical node.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class StatisticalParametricModel implements Serializable{
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;	
	/** Mapping between nodes and values. */
	protected HashMap<String,Integer> map;
	/** Statistical parametric model values. */
	protected LocalParametricView[] parametricModel;
	/** The physical topology. */
	protected Graph graph;
	/** The identification of this node. */
	protected String id;
	
	/**
	 * Creates a new StatisticalParametricModel object
	 * @param aId The id of the node that belongs this model.
	 * @param aGraph The physical topology of the network.
	 * @param exponentialFactor The exponential factor.
	 * @param reductor The window reduction.
	 */
	public StatisticalParametricModel(String aId, Graph aGraph, double exponentialFactor, double reductor) {
		this.id = aId;
		this.graph = aGraph;
		map = new HashMap<String,Integer>();
		parametricModel = new LocalParametricView[graph.size()];
		Vector<String> nodes = graph.nodes();
		for (int index=0; index < graph.size();index++) {
			String nodeId = nodes.get(index); 
			map.put(nodeId.toString(),index);
			if (!nodeId.equals(id)) {
				parametricModel[index]= new LocalParametricView(exponentialFactor,reductor);
			} else {
				parametricModel[index] = null;
			}
		}
	}
	
	/**
	 * Gets the local parametric view associated to the specified destination.
	 * @param destination The id of the destination of the node.
	 * @return The local parametric view associated to the specified destination.
	 */
	public LocalParametricView get(String destination) {
		int index = map.get(destination);
		return parametricModel[index];
	}
	
	/**
	 * Update the local model associated with the specified node.
	 * @param metric The value of the metric.
	 * @param nodeId The specified node.
	 */
	public void update(double metric, String nodeId) {
		int index = map.get(nodeId);
		LocalParametricView view = parametricModel[index];
		view.update(metric);
	}
	
	/**
	 * Update the local model associated with the destination of this
	 * backward ant.
	 * @param ant The backward ant.
	 */
/*	public void update(AntPacket ant) {
		String target = ant.getTarget();
		int index = map.get(target);
		LocalParametricView view = parametricModel[index];
		double numberHops = (double) ant.getSubPathLength(id);
		view.update(numberHops);
	}
*/	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Id: ");
		builder.append(id);
		builder.append("\n");
		Vector<String> nodes = graph.nodes();
		for (int index=0; index < nodes.size(); index ++) {
			String key = nodes.get(index);
			builder.append(key);
			builder.append(" - ");
			LocalParametricView view = parametricModel[index];
			builder.append(view.toString());
			builder.append("\n");
		}
		return builder.toString();
	}
	
}
