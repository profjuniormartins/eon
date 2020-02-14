package graph;

/*
 * PathTree.java
 *
 * Created on 12 de Setembro de 2002, 09:28
 */

import java.util.*;

/**
 * The class PathTree represents a pseudo-tree of paths. In fact, it is not a tree
 * because it contain repeated nodes . However, it can be seen in such a way, as 
 * long as the same node is distinguished when belonging to different paths.
 *
 * @author  Gustavo Sousa Pavani
 * @version 1.0
 */

public class PathTree {
    /** Key for the source of the pseudo-tree. */
    private Object source;
    /** Key for the destination of the pseudo-tree. */    
    private Object destination;
    /** Node representing the root of the pseudo-tree. Its key is the source of the pseudo-tree. */
    private Node root;
    /** Node representing the only allowed leaf of the pseudo-tree. Its key is the destination of the pseudo-tree. */
    private Node leaf;
    
    /** Creates new PathTree 
     * @param keySource The key of the source node.
     * @param keyDestination The key of the destination node.
     */
    public PathTree(Object keySource, Object keyDestination) {
        source = keySource;
        destination = keyDestination;
        root = new Node();
        root.setKey(source);
        leaf = new Node();
        leaf.setKey(destination);
    }
    
    /** Insert a path in the pseudo-tree.
     * @param path The path to be inserted.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
	public void insertPath(Path path) throws Exception {
        /* Raises an exception if the source and/or the destination node of the path differs
         * from the source and destination node of the pseudo-tree. */
        if (!path.lastNode().equals(destination))
            throw new Exception("The last node of the path differs from the path tree destination");
        if (!path.firstNode().equals(source))
            throw new Exception("The first node of the path differs from the path tree source");    
        Node node = root;
        Enumeration enumE = path.nodes().elements();
        Object key = enumE.nextElement(); //Ignores the root
        key = enumE.nextElement(); //Take the second element in the path
        Node previous = node;
        node = node.getNext(key); //Verifiy if this second element is already in the path tree
        //Bypass the elements already inserted
        while ((node != null) && enumE.hasMoreElements()) {
            key = enumE.nextElement(); //Take the next element
            previous = node;
            node = node.getNext(key);
        }        
        //Insert the "spur" path in the path tree
        Node newNode = makeSpurPath(key,enumE);
        previous.insertNode(newNode,null);
    }
    
    /** Make a node from the destination node to the first node not found in the pseudo-tree,
     * recursively. 
     * @param key The key of the node to be inserted.
     * @param enum The enumeration of the remaining nodes to be inserted in the pseudo-tree.
     * @return The node containing the first node not found until the destination node.
     */
    @SuppressWarnings("unchecked")
	private Node makeSpurPath(Object key,Enumeration enumE) {
        if (key.equals(destination)) {
            Node endNode = new Node();
            endNode.setKey(key);
            return endNode;
        }
        Node nextNode = makeSpurPath(enumE.nextElement(),enumE);
        Node node = new Node();
        node.setKey(key);
        node.insertNode(nextNode,null);
        return node;
    }
    
    /** Get the keys of the nodes that follows the specified path. If the path 
     * is empty, then the root node is considered, i.e, this method will get the keys 
     * od the nodes that follows the source node of the pseudo-tree.
     * @param path The specified path. 
     * @return An enumeration of the keys of the nodes that follows the path.
     */
    @SuppressWarnings("unchecked")
	public Enumeration nextsToRootPath(Path path) throws Exception {
        if (path.size()!=0)
            if (!path.firstNode().equals(source))
                throw new Exception("The first node of the path differs from the path tree source");
        Enumeration enumE = path.nodes().elements();
        Node node = root;
        Object key;
        //If the path is empty return the keys of the nodes next to the source node.
        if (path.size()!=0)
            key = enumE.nextElement();  //discard the source node key
        while (enumE.hasMoreElements()) {
            key = enumE.nextElement();
            node = node.getNext(key);
        }
        return node.getNextNodesKey().elements();
    }
    
    /** Verify if the specified path is contained in the pseudo-tree of paths.
     * @param path The specified path.
     * @return True, if the path is found; false, otherwise.
     */
    @SuppressWarnings("unchecked")
	public boolean contains(Path path) throws Exception{
        if (!path.lastNode().equals(destination))
            throw new Exception("The last node of the path differs from the path tree destination");
        if (!path.firstNode().equals(source))
            throw new Exception("The first node of the path differs from the path tree source");
        Node node = root;
        Enumeration enumE = path.nodes().elements();
        Object key = enumE.nextElement(); //Ignores the root
        key = enumE.nextElement(); //Take the second element in the path
        node = node.getNext(key); //Verifiy if this second element is already in the path tree
        //Bypass the elements already inserted
        while ((node != null) && enumE.hasMoreElements()) {
            key = enumE.nextElement(); //Take the next element
            node = node.getNext(key);
        }
        //If the previous node contain the destination node, then return true; otherwise, return false
        if (node == null) //to avoid null pointer exception
            return false;
        else return (node.getKey()).equals(destination);
    }
    
    /** Debug function to print all paths stored in the pseudo-tree to the stdout. 
     */
    public void print() {
        String sPath = (String) root.getKey();
        for (Object node: root.getNextNodes()) {
            printTree(sPath,(Node)node);
        }
    }
    
    /** Recursively prints the nodes below the specified node.
     * @param sPath The string containing the previous keys.
     * @param node The node to be recursively printed.
     */
    private void printTree(String sPath, Node node) {
        Object key = node.getKey();
        sPath=sPath+"-"+(String)key;
        if (key.equals(destination)) {
            System.out.println(sPath);
            return; //Finish the recursive printing
        }
        for (Object nodeRec: node.getNextNodes()) {
            printTree(sPath,(Node)nodeRec);
        }
    }
}

/** Inner class for abstracting a node of the pseudo-tree of paths. 
 */
class Node {
    /** The key of the node.*/
    Object key;
    /** A Vector that contains the Node objects that follows this node.*/
    Vector<Node> next;
    /** A Vector that contains the edge value from this node to the next nodes. */
    Vector<Object> value;
    
    /** Creates a new node. */
    Node() {
        next = new Vector<Node>();
        value = new Vector<Object>();
    }
    
    /** Set the key of this node object. 
     * @param aKey the key to be setted.
     */
    void setKey(Object aKey) {
        key = aKey;
    }
    
    /** Get the key of this node.
     * @return The key of this node.
     */
    Object getKey() {
        return key;
    }
    
    /** Insert a node into this node.
     * @param node The node to be inserted.
     * @param value The value of the directed edge from this node to the inserted node.
     */
    void insertNode(Node node, Object aValue) {
        next.add(node);
        value.add(aValue);
    }
    
    /** Remove the specified node.
     * @param node The node to be removed.
     */
    void removeNode(Node node) {
        int position = next.indexOf(node);
        next.removeElementAt(position);
        value.removeElementAt(position);
    }
    
    /** Get the node that follows this node and matches the specified key.
     * @param aKey The key of the desired node.
     * @return The node that matches the specified key. If there is not a node 
     * that matches the key, then null is returned.
     */
    Node getNext(Object aKey) {
        for (Node node:next) {
            if (node.getKey().equals(aKey)) {
                return node;
            }
        }
        return null;
    }
    
    /** Returns a Enumeration of the nodes that follows this node object.
     * @return An enumeration of the nodes that follows this node.
     */
    @SuppressWarnings("unchecked")
	Vector getNextNodes() {
        return next;
    }
    
    /** Return the keys of the nodes that follows this node.
     * @return An enumeration of the keys of the nodes that follows this node.
     */
    @SuppressWarnings("unchecked")
	Vector getNextNodesKey() {
        Vector<Object> keys = new Vector<Object>();
        for (Node node: next) {
            keys.add(node.getKey());
        }
        return keys;
    }
    
    /** Get the value of the edge from this node to the specified node.
     * @param node The specified node.
     * @return The value of the edge.
     */
    Object getValue(Node node) {
        return value.get(next.indexOf(node));
    }
}