/*
 * Edge.java
 *
 * Created on 4/Dec/2002.
 */
package graph;

import java.util.Vector;
import java.io.Serializable;

/**
 * Wrapper-class for defining a directed edge on a graph.
 *
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Edge implements Cloneable, Serializable {    
    /** Default serial ID for serialization. */
	private static final long serialVersionUID = 1L;
	/** The source key of this edge. */
    private String keySource;
    /** The destination key of this edge. */
    private String keyDestination;
    /** The value or distance label of this edge. */
    private Object value;
    
    /** Creates new Edge */
    public Edge() {
    }

    /** Creates new Edge. 
     * @param source The source key of this edge. 
     * @param destination The destination key of this edge. 
     * @param value The value or distance label of this edge. 
     */
    public Edge(String source, String destination, Object value) {
        keySource = source;
        keyDestination = destination;
        this.value = value;
    }
    
    /** Sets the source of the Edge object.
     * @param source The source key of the edge.
     */
    public void setSource(String source){
        keySource = source;        
    }

    /** Sets the destination of the Edge object.
     * @param destination The destination key of the edge.
     */
    public void setDestination(String destination){
        keyDestination = destination;        
    }

    /** Sets the associated value (or cost) of the Edge object.
     * @param value The associated value of the edge.
     */
    public void setValue(Object value){
        this.value = value;
    }

    /** Gets the source of the Edge object.
     * @return The source key of the edge.
     */
    public String getSource(){
        return keySource;        
    }
    
    /** Gets the destination of the Edge object.
     * @return The destination key of the edge.
     */        
    public String getDestination(){
        return keyDestination;        
    }
    
    /** Gets the associated value (or cost) of the Edge object.
     * @return The associated value of the edge.
     */
    public Object getValue(){
        return value;        
    }

    /** Returns a clone object of this edge.
     * @return A clone of this edge.
     */
    @SuppressWarnings("unchecked")
	public Object clone() {
        //Workaround to allow the cloning of this object.
        Vector<Edge> temp = new Vector<Edge>();
        temp.add(this);
        return ((Vector<Edge>)temp.clone()).firstElement();
    }

    /**
     * Indicates whether some other edge object is "equal to" this one. 
     * Equal means same source and same destination node.
     * @param aEdge The edge object to be compared.
     * @return True if this object is the same as the aEdje argument. False, otherwise.
     */
    public boolean equals(Object aEdge) {
        Edge edge = (Edge) aEdge;
        String source = (edge.getSource()).toString();
        String target = edge.getDestination().toString();
        return (keySource.toString().equals(source) && keyDestination.toString().equals(target));
    }
    
    /**
     * Prints the edge object and its associated cost for debug purposes in the standard output.
     */
    void print() {
        System.out.println(keySource+"-"+keyDestination+" cost: "+value.toString());
    }
    /**
     * Returns a string representation of the edge object.
     * @return A string representation of the edge object.
     */
    public String toString() {
    		return new String(keySource+"-"+keyDestination);
    }
}
