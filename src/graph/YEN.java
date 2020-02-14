package graph;

/*
 * YEN.java
 *
 * Created on 5 de Setembro de 2002, 10:37
 * Modified on 2 de Dezembro de 2002 - Removed the drasys package (www.opsresearch.com)
 * Logging API added in Feb. 20, 2003.
 */

import java.util.*;
import java.util.logging.*;
import util.*;

/**
 * The YEN class implements the Yen's algorithm for finding the K shortest simple
 * paths (those that are without loops). This implementation is applicable only to
 * directed networks. The Yen's algorithm uses a path deviation approach and can
 * be found at: J.Y. Yen, <i>"Finding the K Shortest Loopless Paths in a Network"</i>,
 * Management Science Vol. 17, pp.712-716, 1971.
 * @author  Gustavo Sousa Pavani
 * @version 1.2
 */
public class YEN {
    /** Stores the candidate set for shortest paths */
    private BinaryHeap heap;
    /** Stores the K shortest paths of this graph */
    private Vector<Path> results;
    /** The logging generator. */
    private static Logger logger = Logger.getLogger(YEN.class.getName());
    
    
    /** Creates new YEN */
    public YEN() {
        heap = new BinaryHeap(true);
    }
    
    /**Removes all the nodes from source (inclusive) until the deviation node (exclusive)
     * contained in the root path. This is to ensure that no cycles are found int the
     * new path.
     * @param keySource The key of the source node.
     * @param deviationNode The deviation node.
     * @param shortestPath The shortest path used on the calculation of the root path.
     * @param graph A clone of the graph.
     * @return The root path (without the deviation node!). When the deviation node is the source node,
     * then a empty path is returned.
     */
    @SuppressWarnings("unchecked")
	private Path restriction1(String keySource, String deviationNode, Path shortestPath, Graph graph) {
        //Create the root path
        Path root = new Path();
        //Debug purposes
        logger.finest("Root path: ");
        try {
            //Get  a list of all the nodes from source (inclusive) until
            //the deviation node (exclusive)
            List list = shortestPath.subPath(keySource,deviationNode);
            Iterator iterator = list.iterator();
            //If the list is empty, the restriction 1 is not necessary. This happens
            //when the deviation node is the source node. Then, a empty root path is returned.
            if (iterator.hasNext()) {
                //Create the root path and calculate its costs
                String current=null, previous=null;
                double rootCost=0;
                while (iterator.hasNext()) {
                    current = (String)iterator.next();
                    //Debug purposes
                    logger.finest(current+"-");
                    root.addNode(current);
                    if (previous!=null) {
                        Edge edge = graph.getEdge(previous,current);
                        rootCost = rootCost + (Double)edge.getValue();
                    }
                    previous = current;
                }
                //Debug purposes
                logger.finer(deviationNode.toString());
                //Calculate the cost for the last edge, because the deviation node is not
                //included in the root path for reasons of implementation.
                Edge edge = graph.getEdge(current,deviationNode);
                rootCost = rootCost + (Double)edge.getValue();
                root.setCost(rootCost);
                //Remove all the nodes in the root path, but the deviation node.
                for (String node: root.nodes()) {
                    //Debug purposes
                    logger.finest("Removing node: "+node);
                    graph.removeNode(node);
                }
            }
        } catch (Exception e) {
			e.printStackTrace();
            logger.severe(e.toString());
        }
        return root;
    }
    
    /** Removes links from the deviation node on any edge used by previously found
     * k-shortest paths with the same root path, i.e., links that follows the deviation
     * node and belongs to pseudo-tree of k-shortest paths.
     * @param keyDestination The key of the destination node.
     * @param deviationNode The deviation node
     * @param root The root path obtained in the Restriction 1.
     * @param graph A clone of the graph.
     * @param tree The pseudo-tree of k-shortest found paths.
     * @return The spur path, if it exists; null, otherwise.
     */
    @SuppressWarnings("unchecked")
	private Path restriction2(String keyDestination, String deviationNode, Path root, Graph graph, PathTree tree) {
        //Remove the links of restriction 2 from the graph.
        try {
            //This part is necessary because the root path does not contain
            // the deviation node for implementation reasons
            Path rootAndDeviation = (Path) root.clone();
            rootAndDeviation.addNode(deviationNode);
            for (Enumeration enumTree = tree.nextsToRootPath(rootAndDeviation);enumTree.hasMoreElements();) {
                try { //When a edge has already been removed
                	String node = (String)enumTree.nextElement();
                    //Debug purposes
                    logger.finest("Removing edge: "+deviationNode+"-"+node);
                    graph.removeEdge(deviationNode,node);
                } catch (Exception eEdge) {/*do nothing*/}
            }
        } catch(Exception e) {e.printStackTrace();}
        //The spur path
        Path spur = null; //To return null if there is no shortest path
        try { //For the exception that occurs when there is not a shortest path
            //Calculate the spur path in the modified graph
            spur = getShortestPath(deviationNode,keyDestination,graph);
            //Debug purposes
            logger.finest("Spur Path: " + spur.toString());
        } catch (Exception eSpur) {/*System.out.println(); */ /*Do nothing*/}
        return spur;
    }
    
    /** Calculates the K-shortest paths of a graph using Yen's algorithm.
     * @param keySource The key of the source node.
     * @param keyDestination The key of the destination node.
     * @param graph The graph used to calculate the algorithm.
     * @param K The number of desired shortest paths.
     * @throws Exception When K is less than 1.
     * @return A Vector of K-shortest paths of the graph. Important: There can be less
     *          shortest paths than specified in K!
     */
    @SuppressWarnings("unchecked")
	public Vector<Path> getShortestPaths(String keySource, String keyDestination, Graph graph, int K) throws Exception {
        if (K < 1) throw new Exception("Yen's Algorithm must have K >= 1!");
        //Boolean flag indicating that the candidate set (heap) is empty and the last
        // found k-shortest path has already been tested, i.e, all k-shortest paths
        // has already been found.
        boolean finishYen = false;
        //Pseudo-tree of the k shortest paths
        PathTree tree = new PathTree(keySource,keyDestination);
        //Pseudo-tree of the candidates of shortest path
        PathTree candidateTree = new PathTree(keySource,keyDestination);
        //Clean the K shortest paths and the heap candidates.
        results  = new Vector<Path>();
        heap.removeAllElements();
        try {
            //Calculate the shortest path
            Path shortestPath = getShortestPath(keySource,keyDestination,graph);
            //Insert it in the results vector and the pseudo-tree of paths
            results.add(shortestPath);
            tree.insertPath(shortestPath);
            int counter = 1; //counter for the number of shortest paths found
            while (!finishYen && (counter < K)) {
                //Debug purposes
                logger.fine("Calculating the "+(counter+1)+"-shortest path");
                //Pick the the last k-shortest path calculated
                shortestPath = (Path) results.lastElement();
                //Calculate the candidate set using each deviation node. The enumeration starts with
                // the first vertex, i.e., the source node.
                Enumeration enumPath = shortestPath.nodes().elements();
                String deviationNode;
                do  {
                    //Get the deviation node.
                    deviationNode = (String)enumPath.nextElement();
                    //Debug purposes
                    logger.finer("Deviation Node: " + deviationNode);
                    //Get a clone copy of the original graph.
                    Graph modGraph = (Graph) graph.clone();
                    //Apply restrictions 1 and 2 and obtain the root and spur paths.
                    Path root = restriction1(keySource, deviationNode, shortestPath, modGraph);
                    Path spur = restriction2(keyDestination, deviationNode, root, modGraph, tree);
                    //Concatenate the root and the spur paths and insert the result
                    // into the heap, if the spur path exists and if the result has
                    // not already inserted into the heap
                    if (spur != null) {
                        root.concatenate(spur);
                        //Verify if the heap already contains this candidate path
                        if (!candidateTree.contains(root)) {
                            //Debug purposes
                            logger.finer("Heap: "+root.toString());
                            heap.add(root);
                            //Insert the path in the already inserted candidate path tree
                            candidateTree.insertPath(root);
                        }
                    }
                } while (enumPath.hasMoreElements() && !(shortestPath.getNextNode(deviationNode)).equals(keyDestination));
                //Get the best path in the candidate set and put it in the results vector
                if (heap.size() != 0) {
                    Path tempPath;
                    tempPath = (Path) heap.remove();
                    results.add(tempPath);
                    tree.insertPath(tempPath);
                } else {
                    finishYen = true; //Time to finish the Yen's algorithm, i.e., empty heap and last found shortest path already tested.
                    //There is no more shortest paths in this graph.
                }
                //Increment the counter of shortest paths.
                counter = counter + 1;
            }
        } catch (Exception e) {e.printStackTrace();}
        return results;
    }
    
    /** Executes the Dijkstra's algorithm for the shortest path.
     * @param keySource The key of the source node.
     * @param keyDestination The key of the destination node.
     * @param graph The graph used to calculate the algorithm.
     * @return The shortest path, if it exists; othewise, return an empty path
     */
    public Path getShortestPath(String keySource, String keyDestination, Graph graph) {
        Dijkstra dijkstra = new Dijkstra();
        return dijkstra.getShortestPath(keySource, keyDestination, graph);
    }
}
