/*
 * Dijkstra.java
 *
 * Created on 5/Dec/2002.
 * Logging API added in Feb. 20, 2003.
 */
package graph;

import java.util.logging.*;
import util.*;

/**
 * A heap implementation of the Dijkstra's Algorithm. For further information,
 * see Ahuja, R. and Magnanti, T. and Orli, J. <i>"Networks flows"</i>  Prentice-Hall, 1993.
 * Section 4.7 Heap Implementations - page 115.
 * Important: One source to all destinations version!
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.1
 */
public class Dijkstra {
    /** The heap used in this implementation.*/
    DijkstraHeap dHeap;
    /** The array of shortest distance for each node. */
    double[] distance;
    /** The array of predecessors for each node. */
    String[] predecessor;
    /** The number of nodes of the graph. */
    int size;
    /** The logging generator. */
    private static Logger logger = Logger.getLogger(Dijkstra.class.getName());

    /** Creates new Dijkstra. */
    public Dijkstra() {
    }
    
    /** Executes the Djikstra's algorithm for the shortest path.
     * @param keySource The key of the source node.
     * @param keyDestination The key of the destination node.
     * @param graph The graph used to calculate the algorithm.
     * @return The shortest path, if it exists; otherwise, returns null. Important: If the source node
     * and the destination node are the same, then null is returned!
     */
    public Path getShortestPath(String keySource, String keyDestination,Graph graph) {
        //Initializes the binary heap.
        dHeap = new DijkstraHeap(true);
        //Gets the number of nodes of the graph
        size = graph.size();
        //Initializes the predecessor list
        predecessor = new String[size];
        //Initializes all distance labels as infinity value.
        distance = new double[size];
        for (int i=0; i < size; i++) {
            distance[i]=Double.POSITIVE_INFINITY;
        }
        //Insert the first node in the heap and update the predecessor's list.
        int position = graph.map.indexOf(keySource);
        distance[position] = 0.0;
        predecessor[position] = keySource;
        dHeap.add(new DijkstraNode(keySource,0.0));
        
        //Iterations
        while (!dHeap.isEmpty()) {
            //Node Selection Operation
            DijkstraNode node = (DijkstraNode) dHeap.remove();
            //for debug purposes
            if (logger.isLoggable(Level.FINER))
                logger.finer("Selecting node: " + node.getKey() +" with label: "+node.getDistanceLabel());  
            //Relax all nodes that are adjacents to this node.
            for (Edge edge : graph.adjacentEdges(node.getKey())) {
                //Gets the value of the edge
                double value = node.getDistanceLabel() + ((Double)edge.getValue()).doubleValue();
                //Gets the index in the Node-Node Adjacence Matrix
                position = graph.map.indexOf(edge.getDestination());
                //for debug purposes
                logger.finest("Adjacent Node: " + edge.getDestination());   
                //Distance Update Operation
                if (distance[position] > value) {
                    //If the node is not already inserted in the heap, inserts it
                    if (distance[position] == Double.POSITIVE_INFINITY) {
                        distance[position] = value;
                        predecessor[position] = node.getKey();
                        logger.finest("Updating inf distance:"+value);    //for debug purposes
                        dHeap.add(new DijkstraNode(edge.getDestination(),value));
                    }
                    //If the node is already inserted in the heap, updates its priority in the key.
                    else {
                        distance[position] = value;
                        predecessor[position] = node.getKey();
                        logger.finest("Updating distance:"+value);   //for debug purposes
                        //Decrease-key
                        dHeap.decreaseKey(edge.getDestination(),value);
                    }
                }
            }
        }
        //Verify if a shortest path has been found; return null if not.
        int positionDestination = graph.map.indexOf(keyDestination);
        if (distance[positionDestination] == Double.POSITIVE_INFINITY) { //Infinite distance means node not reachable.
            return null;
        }
        logger.finer("Distance label: " + distance[positionDestination]);  //for debug purposes
        //Now, it is possible to generate the shortest path using the predecessor list.
        Path path = generateShortestPath(predecessor, keySource, keyDestination, graph);
        path.setCost(distance[positionDestination]);
        return path;
    }
    
    /** Generates recursively the shortest path using the predecessor list of Dijkstra's algorithm.
     * @param predecesorList The predecessor list obtained from the Dijkstra's algorithm.
     * @param keySource The key of the source node.
     * @param keyDestination The key of the destination node.
     * @param graph The graph used to calculate the algorithm.
     * @return The shortest path, if it exists; otherwise, returns null. Important: If the source node
     * and the destination node are the same, then null is returned!
     */
    private Path generateShortestPath(String[] predecessorList, String keySource, String keyDestination,Graph graph) {
        //Gets the index of the destination node.
        int positionDestination = graph.map.indexOf(keyDestination);
        //Base case - the source node has been reached.
        if (positionDestination == graph.map.indexOf(keySource)) {
            return null;
        } else {
            Path path = generateShortestPath(predecessorList, keySource, predecessorList[positionDestination], graph);
            //Avoids a path without edge, i. e., the case where the source and destination node are the same.
            if (path == null) {
                path = new Path();
                path.addNode(keySource);
            }
            //Adds node to the end of the path and updates its costs.
            path.addNode(keyDestination);
            return path;
        }
    }
    
}

/** Inner class for abstracting a node in the Dijkstra's Algorithm.
 */
@SuppressWarnings("unchecked")
class DijkstraNode implements Comparable {
    /** The key of the node. */
	String key;
    /** The distance label of this node. */
    double distanceLabel;
    
    /** Creates new DijkstraNode. */
    DijkstraNode(String key, double distance) {
        this.key = key;
        distanceLabel = distance;
    }
    
    /**
     * Returns the distance label of this node object.
     * @return The distance label of this node object.
     */
    double getDistanceLabel() {
        return distanceLabel;
    }
    
    /**
     * Sets the distance label of this node object.
     */
    void setDistanceLabel(double distance) {
        distanceLabel = distance;
    }
    
    /**
     * Returns the key of this node object.
     * @return The key of this node object.
     */
    String getKey() {
        return key;
    }
    
    /**
     * Compares the distance of this node to another node.
     * @param node The node to be compared to this object.
     * @return +1 if the specified node has a lower distance, -1 if it has a greater
     * distance or 0 if it has the same distance.
     * @see java.lang.Comparable
     */
    public int compareTo(Object node) {
        if (this.distanceLabel > ((DijkstraNode)node).getDistanceLabel()) {
            return +1;
        } else if (this.distanceLabel < ((DijkstraNode)node).getDistanceLabel()) {
            return -1;
        } else return 0;
    }
    
    /**
     * Verifies if the specified node has the same key and same priority.
     * @param node The node to be compared.
     * @return True, if the specified node has the same key and same priority; false, otherwise.
     */
    public boolean equals(Object node) {
        return (((DijkstraNode)node).getKey()).equals(key) && (((DijkstraNode)node).getDistanceLabel() == distanceLabel);
    }
}

/**
 * Inner class for defining a Binary Heap to be used in this implementation of the
 * Dijkstra's Algorithm. It adds the functionality of decreasing the priority of a
 * given node, maintaining all the original behavior of the parent class.
 */
class DijkstraHeap extends BinaryHeap {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
   /** The logging generator. */
    private static Logger logger = Logger.getLogger(DijkstraHeap.class.getName());
    
    /** Creates new DijkstraHeap. */
    public DijkstraHeap(boolean reversedPriority) {
        super(reversedPriority);
    }
    
    /**
     * Decrease the key of the specified node to the specified value.
     * @param key The key of the node to be decreased
     * @param value The new value of the node. It must be less the original one.
     */
    @SuppressWarnings("unchecked")
	public void decreaseKey(String key, double value) {
        //Find the position the of the node in the heap.
        int position = 0;
        DijkstraNode node = null;
        while ((position < super.size()) && (node == null)) {
            if (key.equals( ((DijkstraNode)super.heap[position]).getKey())) {
                node = (DijkstraNode) super.heap[position];
            } else {
                //Increments the position counter
                position ++;
            }
        }
        //for debug purposes
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Updating the heap key: "+((DijkstraNode)super.heap[position]).getKey().toString() +"key: "+key);  
            logger.finest("Position of the key: "+position); 
            logger.finest("Old value:"+((DijkstraNode)super.heap[position]).getDistanceLabel()); 
        }
        //Change the value of the specified node.
        node.setDistanceLabel(value);
        //for debug purposes
        if (logger.isLoggable(Level.FINEST)) 
            logger.finest("New value:"+((DijkstraNode)super.heap[position]).getDistanceLabel()); 
        //Then, do the Siftup operation.
        int parent, child = position; //index of the node specified by the argument key.
        if (reversedPriority) {
            while (child > 0 && super.heap[parent = (child - 1) / 2].compareTo(node) > 0) {
                super.heap[child] = super.heap[parent];
                child = parent;
            }
        } else {
            while (child > 0 && super.heap[parent = (child - 1) / 2].compareTo(node) < 0) {
                super.heap[child] = super.heap[parent];
                child = parent;
            }
        }
        super.heap[child]=node;
    }
}
