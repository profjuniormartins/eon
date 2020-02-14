/*
 * Path.java
 *
 * Created on 5/Sep/2002.
 * New methods added on 23/May/2003.
 * New methos added on 26/Feb/2005.
 */
package graph;


import java.io.*;
import java.util.*;

/** The Path class represents a directed path or route and its associated cost.
 * It cannot contain repeated nodes, i.e., it must be a loopless path.
 *
 * @author Gustavo Sousa Pavani
 * @version 1.1
 */
@SuppressWarnings("unchecked")
public class Path implements Serializable,Cloneable,Comparable {
    /** Default serial ID for serialization. */
	private static final long serialVersionUID = 1L;
	/** Total cost of this path. */
    private double cost = 0;
    /** Contains the nodes of this path. */
    protected Vector<String> path;
    
    /** Creates new Path */
    public Path() {
        path = new Vector<String>();
    }
        
    /** Adds a node to the end of the path.
     * @param key The key of the node to be added.
     */
    public void addNode(String key) {
        path.add(key);
    }
    
    /** Adds a node to an arbitrary positon of the path.
     * param i The position in the path. The index starts at 0.
     * @param key The key of the node to be added.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public void addNode(int i, String key) throws java.lang.ArrayIndexOutOfBoundsException {
        path.add(i,key);
    }
    
    /** Gives an ordered enumeration of the nodes in the path. The first element
     * is the source node.
     * @return An enumeration of the nodes.
     */
    public Vector<String> nodes() {
        return path;
    }
    
    /** Gets the last node (destination node).
     * @return The last node.
     */
    public String lastNode() {
        return path.lastElement();
    }
    
    /** Gets the first node (source node).
     * @return The first node.
     */
    public String firstNode() {
        return path.firstElement();
    }
    
    /** Gets the storage of the vertices.
     * return The path field.
     */
    private Vector<String> getPath() {
        return path;
    }
    
    /** Gets the node at the specified position.
     * @param i The index of the node.
     * @return The specified node.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public String getNode(int i) {
    	
    	
    	
    	try {
    		 return path.get(i);
    	}catch (Exception e) {
    		System.out.println("Algum erro doido");
    	
    	}
    	
    	return path.get(i);
    	
    }
    
    /* Gets the next node of the specified node.
     * @param key The key of the specified node.
     * @return The next node.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public String getNextNode(String key) throws java.lang.ArrayIndexOutOfBoundsException{
        int position = path.indexOf(key);
        return path.get(position+1);
    }

    /* Gets the next node of the specified node.
     * @param key The key of the specified node.
     * @return The next node.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public String getPreviousNode(String key) throws java.lang.ArrayIndexOutOfBoundsException{
        int position = path.indexOf(key);
        return path.get(position-1);
    }

    /** Gets the index of the specified node.
     *  @param key The key of the specified node.
     * @return The index of the node.
     */
    public int getNodePosition(String key) {
        return path.indexOf(key);
    }
    
    /** Replaces the node at the specified position with the specified node.
     * @param i The position to be inserted.
     * @param key The key of the node.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public void setNode(int i, String key) throws java.lang.ArrayIndexOutOfBoundsException {
        path.set(i,key);
    }
    
    /** Deletes the node at the specified index.
     * @param i The index of the node to be removed.
     * @throws java.lang.ArrayIndexOutOfBoundsException
     */
    public void removeNodeAt(int i) throws java.lang.ArrayIndexOutOfBoundsException {
        path.remove(i);
    }
    
    /** Deletes the node that matches the key.
     * @param key The key of the node to be removed.
     * @return True, if the node has been removed; false, otherwise. 
     */    
    public boolean removeNode(Object key) {
        return path.remove(key);
    }
    
    /**
     * Verifies if a node belongs to this path.
     * @param key The key of the node to be verified.
     * @return True, if the key node is present in the path. False, otherwise.
     */
    public boolean containNode(String key) {
    	for (String keyNode: path) {
    		if (keyNode.equals(key)) {
    			return true;
    		}
    	}
    	return false;
    }
     
    /**
     * Returns the subpath from keyStart (inclusive) to keyStop (exclusive)
     */
    public List subPath(String keyStart, String keyStop) throws java.lang.Exception {
        int start, stop;
        start = path.indexOf(keyStart);
        stop = path.indexOf(keyStop,start);
        //System.out.println("Start "+start+" stop "+stop);
        if (start == -1 || stop == -1) {
            throw new Exception("There is no such sub path!");
        } else {
            return path.subList(start,stop);
        }
    }
    
    /** Returns a clone object of this path object.
     * @return A clone of this object.
     */
    @SuppressWarnings("unchecked")
	public Object clone() {
        Path clonePath = new Path();
        clonePath.setCost(cost);
        clonePath.path = (Vector)path.clone();
        return clonePath;
    }
    
    /** Gets the size of the path (in number of nodes).
     * @return The size of the path (in number of nodes).
     */
    public int size() {
        return path.size();
    }
    
    /** Gets the cost of this path object.
     * @return The cost of this object.
     */
    public double getCost() {
        return cost;
    }
    
    /** Sets the cost of this path object.
     * @param value The value of the cost to be setted.
     */
    public void setCost(double value) {
        cost = value;
    }
    
    /** Concatenates this path object to another path.
     * @param anotherPath The path to be concatenated to this object.
     * @return The concatenated path.
     */
    public Path concatenate(Path anotherPath) {
        cost = cost + anotherPath.getCost();
        for (String value: anotherPath.nodes()) {
            path.add(value);
        }
        return this;
    }
    /**
     * Verifies if two paths are equal. Two paths are defined to be equal if they
     * contain the same elements in the same order and have the same cost.
     * @param aPath The Path to be compared to this Path.
     * @return True if the specified Path is equal to this Path.
     */
    public boolean equals(Path anotherPath) {
        return (path.equals(anotherPath.getPath())) && (anotherPath.getCost() == cost);
    }

    /**
     * Compares the cost of this path to another path. 
     * @param anotherPath The path to be compared to this object. 
     * @return +1 if anotherPath has a lower cost, -1 if anotherPath has a greater
     * cost or 0 if it has the same cost. 
     * @see java.lang.Comparable
     */
    public int compareTo(Object anotherPath) {
        if ((this.cost > ((Path) anotherPath).getCost())) 
            return +1;
        else if ((this.cost < ((Path) anotherPath).getCost()))
            return -1;
        else return 0;
    }
    
    /**
     * Prints the path object and its associated cost for debug purposes in the standard output.
     */
    void print() {
        for (String value: path)
            System.out.print(value + "-");
        System.out.println(" Cost: "+cost);
    }

    /**
     * Verifies if the path contains the specified edge.
     * @return True, if the path contains the specified edge.
     */
    public boolean containEdge(Edge edge) {
    	String keySource = edge.getSource();
    	String keyDestination = edge.getDestination();
        int indexSource = path.indexOf(keySource);
        int indexDestination = path.indexOf(keyDestination);
        return ((indexSource != -1) && (indexDestination != -1) && ((indexDestination - indexSource) == 1)); 
    }
    
    /**
     * Returns the consecutive edge of the specified edge, with null values.
     * @param edge The specified edge.
     * @return The consecutive edge, with null values.
     * @throws Exception When the specified edge is the last one or it is not a valid edge in this path.
     */
    public Edge nextEdge(Edge edge) throws Exception{
    	String keySource = edge.getSource();
    	String keyDestination = edge.getDestination();
        int indexSource = path.indexOf(keySource);
        int indexDestination = path.indexOf(keyDestination);
        if ((indexSource == -1) || (indexDestination == -1) || ((indexDestination - indexSource) != 1)) {
            throw new Exception("This path "+path.toString() +" does not contain the specified edge: "+edge.toString());
        } else if (indexDestination == path.size()) {
            throw new Exception("This is already the last edge: "+edge.toString());            
        }
        return new Edge(path.elementAt(indexSource + 1),path.elementAt(indexDestination + 1),null);
    }
    
    /**
     * Returns true, if it is the last edge. Otherwise, returns false.
     * @param edge The specified edge.
     * @return True, if it is the last edge. Otherwise, returns false.
     * @throws Exception When the specified edge is not a valid edge in this path.
     */
    public boolean lastEdge(Edge edge) throws Exception{
    	String keySource = edge.getSource();
    	String keyDestination = edge.getDestination();
        int indexSource = path.indexOf(keySource);
        int indexDestination = path.indexOf(keyDestination);
        if ((indexSource == -1) || (indexDestination == -1) || ((indexDestination - indexSource) != 1)) {
            throw new Exception("This path "+path.toString() +" does not contain the specified edge: "+edge.toString());
        }
        return (indexDestination == (path.size() - 1));
    }
    
    /**
     * Returns a string representation of the path object.
     * @return A string representation of the path object.
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        for (String value: path) {
            buffer.append(value);
            buffer.append("-");
        }
        if (path.size() > 0)
        	buffer.deleteCharAt(buffer.length()-1); //delete the last "-" 
        return buffer.toString();        
    }
    
    /**
     * Returns the edges of this path, with null values.
     * @return The edges of this path, with null values.
     */
    public Edge[] edges() {
        Edge[] edge = new Edge[path.size() - 1];
        String sNode = path.get(0);
        for (int i=1; i < path.size(); i++) {
        	String dNode = path.get(i);
            edge[i-1] = new Edge(sNode,dNode,null);
            sNode = dNode;
        }
        return edge;
    }
    
    /**
     * Returns the specified edge of this path, with null values.
     * @param position The position of the specified edge within this path.
     * @return The specified edge of this path, with null values.
     * @throws Exception When Position is greater than the number of edges in this path.
     */
    public Edge getEdge(int position) throws Exception{
        if (path.size() <= (position-1)) {
            throw new Exception("Position is greater than the number of edges in this path!");
        }
        return new Edge(path.get(position),path.get(position+1),null);
    }
    
     /** Returns true, if it is the fisrt edge. Otherwise, returns false.
     * @param edge The specified edge.
     * @return True, if it is the first edge. Otherwise, returns false.
     * @throws Exception When the specified edge is not a valid edge in this path.
     */
    public boolean firstEdge(Edge edge) throws Exception{
    	String keySource = edge.getSource();
    	String keyDestination = edge.getDestination();
        int indexSource = path.indexOf(keySource);
        int indexDestination = path.indexOf(keyDestination);
        if ((indexSource == -1) || (indexDestination == -1) || ((indexDestination - indexSource) != 1)) {
            throw new Exception("This path "+path.toString() +" does not contain the specified edge: "+edge.toString());
        }
        return (indexSource == 0);        
    }
    
    /**
     * Returns the last edge of this path.
     * @return The last edge of this path.
     */
    public Edge getLastEdge() {
    	int size = path.size();
    	String sNode = path.get(size - 2);
    	String dNode = path.get(size - 1);
    	return new Edge(sNode,dNode,null);
    }
}
