/*
 * Graph.java
 *
 * Created on 4/Dec/2002
 * Modified on 26/Feb/2005 - added adjacent nodes method.
 */

package graph;


/**
 * A implementation of a graph that is optimized for dense graphs. This graph
 * does not support parallel edges and only supports directed edges.
 * Its internal representation is a Node-Node Adjacency Matrix.
 *
 * @author Gustavo Sousa Pavani
 * @version 1.1
 */
import java.util.*;
import java.io.Serializable;

public class Graph implements Cloneable, Serializable {
    /** Default serial ID for serialization. */
	private static final long serialVersionUID = 1L;
	/** Holds the representation of the Node-Node Adjacency Matrix in a 1-dimensional array. */
    protected Vector<Edge> matrix;
    /** The number of nodes of this graph. */
    private int size;
    /** The number of edges of this graph. */
    private int numberOfEdges;
    /** Holds the translation from the key of the node to the integer index of the Node-Node Adjacency Matrix. */
    protected Vector<String> map;
    
    /** Creates new Graph */
    public Graph() {
        matrix = new Vector<Edge>();
        map = new Vector<String>();
        size = 0;
        numberOfEdges = 0;
    }
    
    /**
     * Adds a new node to this graph.
     * @param The key of the node to be inserted.
     * @throws Exception When the specified node already exists.
     */
    public void addNode(String key) throws Exception {
        //Verify if this node has been already added to the graph.
        if (map.contains(key)) {
            throw new Exception("Duplicate node :" + key + " !");
        } else {
            map.add(key); //Put the key in the end of the mapping vector
            //Insert null positions inside the Node-Node Adjacence Matrix (in the form of a 1-dimensional array).
            if (size==0)
                matrix.add(null);
            else {
                for (int i=0; i < size; i++) {
                    matrix.add(i*(size+1) + size,null);
                }
                for (int j=0; j <= size; j++) {
                    matrix.add(null);
                }
            }
            size ++; //Increment the size of the graph
        }
    }
    
    /**
     * Adds an edge to this graph.
     * @param keySource The key from the source node.
     * @param keyTarget The key from the target node.
     * @param value The value (or cost) of this edge.
     * @throws Exception When the specified edge already exists.
     */
    public Edge addEdge(String keySource, String keyTarget, Object value) throws Exception{
        //Get the position to be inserted in the Node-Node Adjacence Matrix.
        int source = map.indexOf(keySource);
        int target = map.indexOf(keyTarget);
        //Verify if a edge has already been inserted.
        Edge edge = null;
        if ((source == -1) || (target == -1)) {
            throw new Exception("Cannot add edge."+ keySource + " and/or " + keyTarget + " does not exist!");        	
        } else if (matrix.get(source*size+target) != null) {
            throw new Exception("Cannot add edge. Edge from " + keySource + " to " + keyTarget + " already exists!");
        } else {
            //Insert the new edge in the matrix.
            edge = new Edge(keySource,keyTarget,value);
            matrix.set(source*size+target,edge);
            //Increment the number of edges.
            numberOfEdges ++;
        }
        return edge;
    }
    
    /**
     * Removes a node of this graph and all its adjacent edges.
     * @param The key of the node to be removed.
     * @throws Exception When the specified node does not exist.
     */
    public void removeNode(String key) throws Exception {
        //Verify if this node exists.
        if (!map.contains(key)) {
            throw new Exception("Cannot remove node. Node :" + key + " does not exist!");
        } else {
            int position = map.indexOf(key);
            //First, remove the row.
            for (int i=0; i < size; i++) {
                matrix.remove(position*size);
            }
            //After, remove the column.
            for (int i=0; i < (size-1); i++) {
                matrix.remove(i*size + position - i);
            }
            size --; //Decrement the size of the graph
            map.remove(key); //Remove the key from the mapping
        }
    }
    
    /**
     * Removes an edge of this graph.
     * @param keySource The key from the source node.
     * @param keyTarget The key from the target node.
     * @throws Exception When the specified edge does not exist.
     */
    public void removeEdge(String keySource, String keyTarget) throws Exception {
        //Get the position to be removed in the Node-Node Adjacence Matrix.
        int source = map.indexOf(keySource);
        int target = map.indexOf(keyTarget);
        //Verify if exists an edge in between these keys.
        if (matrix.get(source*size+target) == null) {
            throw new Exception("Cannot remove edge. Edge from " + keySource + " to " + keyTarget +  "does not exist!");
        } else {
            //Remove the edge in te matrix.
            matrix.set(source*size+target,null);
            //Decrement the number of edges.
            numberOfEdges --;
        }
    }
    
    /**
     * Returns the specified edge of this graph.
     * @param keySource The key from the source node.
     * @param keyTarget The key from the target node.
     * @return The specified edge of this graph.
     * @throws Exception When the specified edge does not exist.
     */ 
    public Edge getEdge(String keySource, String keyTarget) throws Exception {
        //Get the position of the specified edge in the Node-Node Adjacence Matrix.
        int source = map.indexOf(keySource);
        int target = map.indexOf(keyTarget);
        //Verify if exists an edge in between these keys.
        if (matrix.get(source*size+target) == null) {
            throw new Exception("Cannot get edge. Edge from " + keySource + " to " + keyTarget +  "does not exist!");
        }
        return matrix.get(source*size+target);
    }

    /**
     * Verify if it has an edge between two nodes.
     * @param keySource The key from the source node.
     * @param keyTarget The key from the target node.
     * @return True, if there is an edge between the two specified nodes. False, otherwise.
     */
    public boolean hasEdge(String keySource, String keyTarget) {
        //Get the position of the specified edge in the Node-Node Adjacence Matrix.
        int source = map.indexOf(keySource);
        int target = map.indexOf(keyTarget);
        if (source == -1 || target == -1) { //if one or both nodes do not exist.
        	return false;
        }
        //Verify if exists an edge in between these keys.
        return (matrix.get(source*size+target) != null);
    }
    /**
     * Gets the node key at position n.
     * @param n The position of the node.
     * @return The node key at position n.
     */
    public String getNode(int n) {
    	return map.get(n);   	
    }
    
    /**
     * Gets the node index. 
     * @param key The key of the specified node.
     * @return The node index.
     */
    public int getNodeIndex(String key) {
    	return map.indexOf(key);    	
    }
    
    /**
     * Prints the node-node adjacence matrix for debug purposes in the standard output.
     * "*" means no value associated with the corresponding edge.
     */
    public void print() {
        //Prints the row keys.
        for (Object value: map) {
            System.out.print("  " + value.toString() + " ");
        }
        System.out.println();
        for (int i=0; i < size; i++) {
            //Prints the key of the node (Column keys).
            System.out.print(map.get(i).toString());
            //Then prints the edge values.
            for (int j=0; j < size; j++) {
                Edge edge = matrix.get(i*size + j);
                if (edge == null)
                    System.out.print(" * ");
                else
                    System.out.print(" "+ edge.getValue().toString() +" ");
            }
            System.out.println();
        }
    }
    
    /**
     * Returns the number of nodes of this graph.
     * @return The number of nodes of this graph.
     */
    public int size() {
        return size;
    }
    
    /**
     * Returns the number of edges of this graph.
     * @return The number of edges of this graph.
     */
    public int numberOfEdges() {
        return numberOfEdges;
    }
    
    /**
     * Returns a clone of this graph.
     * @return A clone of this graph.
     */
    @SuppressWarnings("unchecked")
	public Object clone() {
        Graph cloneGraph = new Graph();
        cloneGraph.matrix = (Vector) matrix.clone();
        cloneGraph.map = (Vector) map.clone();
        cloneGraph.size = size;
        cloneGraph.numberOfEdges = numberOfEdges;
        return cloneGraph;
    }
    
    /**
     * Verifies if the other graph has the same topology of this object.
     */
    public boolean equals(Object other) {
    	Graph graph = (Graph) other;
    	//Fast comparison
    	if ((graph.size() != this.size) || (graph.numberOfEdges() != this.numberOfEdges)) {
    		return false;
    	}
    	//Compare this object to the other one
    	for(String src: map) {
    		for (String tgt: map) {
    			if (!src.equals(tgt)) {
    				if (this.hasEdge(src,tgt) && !graph.hasEdge(src,tgt)) {
    					return false;
    				}    			
    			}
    		}
    	}
    	//Compare the other one to this object
    	for(String src: graph.nodes()) {
    		for (String tgt: graph.nodes()) {
    			if (!src.equals(tgt)) {
    				if(graph.hasEdge(src,tgt) && !this.hasEdge(src,tgt)) {
    					return false;
    				}
    			}    			
    		}
    	}
    	//No differences found!
    	return true;
    }
    
    /**
     * Returns the edges of this graph.
     * @return An array of the edges of this graph.
     */
    public Edge[] edges() {
        Edge[] edges = new Edge[numberOfEdges];
        int counter=0;
        for (int i=0; i < size*size; i++) {
            Edge edge = matrix.get(i);
            if ( edge != null) {  //If the edge exists, then it is different from null.
                edges[counter] = edge;
                counter ++; //Increments the counter
            }
        }
        return edges;
    }
    
    /**
     * Returns an enumeration of the key of the nodes of this graph.
     * @return An enumeration of the key of the nodes of this graph.
     */
    public Vector<String> nodes() {
        return map;
    }
    
    /**
     * Returns an enumeration of the adjacent edges of the specified node.
     * @param key The key of the specified node.
     * @return An enumeration of the adjacent edges of the specified node.
     */
    public Vector<Edge> adjacentEdges(String key) {
        Vector<Edge> adjacent = new Vector<Edge>();
        int position = map.indexOf(key);
        for (int i=0; i < size; i++) {
            Edge edge = matrix.get(position*size + i);
            if (edge != null)
                adjacent.add(edge); //Add the adjacent edge.
        }
        return adjacent;
    }
    
    /**
     * Returns an enumeration of the adjacent (neighbors) nodes of the specified node.
     * @param key The key of the specified node.
     * @return An enumeration of the adjacent (neighbors) nodes of the specified node.
     */
    public Vector<String> adjacentNodes(String key) {
    	Vector<String> adjacent = new Vector<String>();
        int position = map.indexOf(key);
        for (int i=0; i < size; i++) {
        	Edge edge = matrix.get(position*size + i);
        	if (edge != null)
        		adjacent.add(edge.getDestination());
        }
    	return adjacent;
    }
    
    /**
     * Returns the adjacency degree of the specified node, i.e., the number of neighbor nodes.
     * @param key The key of the specified node.
     * @return The adjacency degree of the specified node, i.e., the number of neighbor nodes.
     */
    public int adjacencyDegree(String key) {
    	int degree = 0;
        int position = map.indexOf(key);
        for (int i=0; i < size; i++) {
        	Edge edge = matrix.get(position*size + i);
        	//If has edge, increment the counter of neighbors
        	if (edge != null)
        		degree ++;
        }
        return degree;
    }
}
