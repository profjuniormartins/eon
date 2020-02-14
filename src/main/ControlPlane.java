/*
 * Created on Sep 16, 2005.
 * Updated on 2012
 */
package main;

import event.Event;
import graph.*;
import random.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

/**
 * This class represents a generic framework of a network control plane.
 *
 * @author Gustavo S. Pavani, Andre Filipe de Moraes Batista
 * @version 1.0
 *
 */
public abstract class ControlPlane implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** Workaround for transporting static fields. */
	StaticTransporter transporter;
	/** The XML configuration file. */
	protected transient Config config;
	/** The physical topology of the network. */
	protected static Graph graph;

	
	
	/** The random generator. */
	protected static MersenneTwister random;
	/** The simulation parameters. */
	protected Hashtable<String,Vector<String>> parameters;
	/** The load of the traffic matrix. */
	public enum Load { //Not implemented.
		/** Non-uniform traffic matrix. */ NON_UNIFORM,
		/** Uniform traffic matrix and balanced.*/ UNIFORM_BALANCED,
		/** Uniform traffic matrix but unbalanced.*/ UNIFORM_UNBALANCED
	} 
	
	/**
	 * Creates a new ControlPlane object.
	 * @param aConfig The configuration XML file instance.
	 */
	public ControlPlane(Config aConfig) {
		this.updateConfig(aConfig);
		graph = config.getGraph(); //gets the graph
		Vector<String> seed = parameters.get("/Main/ControlPlane/@seed");
		if (seed != null) 
			random = new MersenneTwister(Long.parseLong(seed.firstElement()));
		else 
			random = new MersenneTwister();
	}
	
	/**
	 * Updates the configuration of this control plane.
	 * @param aConfig The new configuration XML file instance.
	 */
	public void updateConfig(Config aConfig) {
		this.config = aConfig;
		this.parameters = config.getSimulationParameters();
	}
	
	/**
	 * Process the specified event. 
	 * @param event The event to be processed.
	 * @return A event to be re-scheduled or accounted.
	 */
	public abstract Event process(Event event);
		
	/**
	 * Toss a random source node.
	 * @return A random source node.
	 */	
	public static String getSourceNode() {
		return getSourceNode(random);
	}

	/**
	 * Toss a random source node.
	 * @param rng Random number generator.
	 * @return A random source node.
	 */	
	public static String getSourceNode(MersenneTwister rng) {
		int size = graph.size();
		double[] probabilityDistribution = new double[size];
		//Normalize the weights
		for (int i=0; i < size; i++) {
			probabilityDistribution[i] = 1.0 / ((double)size);
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return graph.getNode(n);		
	}
	
	
	
	/**
	 * GENERATES A SOURCE NODE INSIDE A UNIQUE AS
	 * Toss a random source node.
	 * @param rng Random number generator.
	 * @param grafo The graph from the domain
	 * @return A random source node.
	 */	
	public static String getSourceNode(MersenneTwister rng, Graph grafo) {
		int size = grafo.size();
		double[] probabilityDistribution = new double[size];
		//Normalize the weights
		for (int i=0; i < size;   i++) {
			probabilityDistribution[i] = 1.0 / ((double)size);
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return grafo.getNode(n);		
	}
	
	/**
	 * Toss a random target node, different from the source node.
	 * @param rng Random number generator.
	 * @param sourceNode The source node.
	 * @return A random target node.
	 */	
	public static String getTargetNode(MersenneTwister rng, String sourceNode) {
		int size = graph.size();
		double[] probabilityDistribution = new double[size];
		int index = graph.getNodeIndex(sourceNode);
		//Normalize the weights
		for (int i=0; i < size; i++) {
			if (i != index) {
				probabilityDistribution[i] = 1.0 / ((double)(size-1)); 
			} else {
				probabilityDistribution[i] = 0.0;
			}
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return graph.getNode(n);		
		
	}
	
	
	/**
	 * GENERATES A TARGET NODE INSIDE A UNIQUE AS
	 * Toss a random target node, different from the source node.
	 * @param rng Random number generator.
	 * @param sourceNode The source node.
	 * @return A random target node.
	 */	
	public static String getTargetNode(MersenneTwister rng, String sourceNode, Graph grafo) {
		int size = grafo.size();
		double[] probabilityDistribution = new double[size];
		int index = grafo.getNodeIndex(sourceNode);
		//Normalize the weights
		for (int i=0; i < size; i++) {
			if (i != index) {
				probabilityDistribution[i] = 1.0 / ((double)(size-1)); 
			} else {
				probabilityDistribution[i] = 0.0;
			}
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return grafo.getNode(n);		
		
	}


	/**
	 * Toss a random target node, different from the source node.
	 * @param sourceNode The source node.
	 * @return A random target node.
	 */	
	public static String getTargetNode(String sourceNode) {
		return getTargetNode(random,sourceNode);
	}
	
	/**
	 * Toss a random target node, different from the source node.
	 * @param sourceNode The source node.
	 * @return A random target node.
	 */	
	public static String getTargetNode(String sourceNode, Graph graph) {
		return getTargetNode(random,sourceNode,graph);
	}
	
	/**
	 * Returns a random double number that belongs to the [0;1] interval. 
	 * @return A random double number that belongs to the [0;1] interval.
	 */
/*	public static double getRandomDouble() {
		return random.nextDouble();
	}
*/	
	
	/**
	 * Returns True, if the two points are connected by a link. False, otherwise.
	 * @param sourceId The source node of the link.
	 * @param targetId The target node of the link.
	 * @return True, if the two points are connected by a link. False, otherwise.
	 */
	public static boolean hasConnectivity(String sourceId, String targetId) {
		return graph.hasEdge(sourceId,targetId);
	}

	/**
	 * Writes this class to a serialized object.
	 * @param s
	 * @throws IOException
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {
	    transporter = new StaticTransporter(graph,random);
	    s.defaultWriteObject();
	}
	
	/**
	 * Reads the class from the serialization.
	 * @param s
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException  {
	    s.defaultReadObject();
	    // customized deserialization code
	    ControlPlane.graph = transporter.graph;
	    ControlPlane.random = transporter.random;
	}

}

/**
 * Wrapper-class for transporting static variables.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
class StaticTransporter implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	Graph graph;
	MersenneTwister random;
	
	public StaticTransporter(Graph g, MersenneTwister r) {
		this.graph = g;
		this.random = r;
	}
}