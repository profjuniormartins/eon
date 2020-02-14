/*
 * Created on Sep 14, 2005.
 */
package antnet;

import java.util.List;

import graph.Edge;
import ops.Packet;

/**
 * Creates a ant packet for optical packet switching.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class AntPacket extends Packet{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The number of bytes added at each hop. */
	private int bytesPerHop;
	
	/**
	 * Create a new AntPacket object. The initial one is of the type ANT_FORWARD.
	 * 
	 * @param sourceId The source identification of this node.
	 * @param targetId The target identification of this node.
	 * @param hopLimit The maximum number of hops allowed for this ant.
	 * @param bytesHop The number of bytes for each node identification.
	 */
	public AntPacket(String sourceId, String targetId, int hopLimit, int bytesHop) {
		super(Packet.Header.ANT_FORWARD, sourceId, targetId,Packet.Priority.HIGH,bytesHop, hopLimit);
		this.bytesPerHop = bytesHop;
	}

	/**
	 * Set the actual processing node of this packet and
	 * add the length of the id of the node to its payload.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		if (nextHeader.equals(Packet.Header.ANT_FORWARD)) {
			super.setNode(procId);
			//Add the extra information about the node
			this.setPayloadLength(this.getPayloadLength() + bytesPerHop);
		} else if (nextHeader.equals(Packet.Header.ANT_BACKWARD)) {
			//Just set the processing node.
			this.procNode = procId;
		}
	}

	/**
	 * Returns true, if the specified node has already been visited.
	 * @param id The specified node.
	 * @return True, if the specified node has already been visited.
	 * False, otherwise.
	 */
	public boolean isTabu(String id) {
		for (String node : path.nodes()) {
			if (node.equals(id))
				return true;
		}
		return false; //Not found in the tabu list (nodes already visited).
	}
	
	/**
	 * Gets the identification of the last node visited. 
	 * @return The identification of the last node visited.
	 */
	public Object getLastVisited() {
		Edge last = path.getLastEdge();
		return last.getSource();
	}
	
	/**
	 * Remove the nodes visited till the specified node (inclusive).
	 * @param id The specified node.
	 * @return The number of nodes removed.
	 */
	public int destroyLoop(String id) {
		int size = path.size();
		int index = path.getNodePosition(id);
		if (index != -1) { //Loop detected
			for (int i=1; i <= (size-index); i++) { //starts from the last till the specified node
				path.removeNodeAt(size - i);
			}
		}
		return (size - index);
	}	
	
	/** Gets the number of hops necessary to reach the target node from the 
	 * specified node. 
	 * @param id The specified node.
	 * @return The number of hops necessary to reach the target node from the 
	 * specified node.
	 */
	public int getSubPathLength(String id) {
		int size = path.size();
		int index = path.getNodePosition(id);
		return (size - index - 1);
	}
	
	/**
	 * Returns a list containing the nodes between the processing node
	 * (exclusive) and the target node (inclusive). 
	 * @return
	 */
	public List<String> getSubPath() {
		int index = path.getNodePosition(this.procNode);
		return path.nodes().subList(index+1,path.size());
	}

	/**
	 * Returns a list containing the nodes between the processing node
	 * (inclusive) and the target node (inclusive). 
	 * @return
	 */
	public List<String> getInclusiveSubPath() {
		int index = path.getNodePosition(this.procNode);
		return path.nodes().subList(index,path.size());
	}

	/**
	 * Returns a list containing the nodes between the processing node
	 * (inclusive) and the target node (inclusive). 
	 * @return
	 */
	public List<String> getInclusiveSubPath(String first, String second) {
		int index1 = path.getNodePosition(first);
		int index2 = path.getNodePosition(second);
		return path.nodes().subList(index1,index2+1);
	}

	
	/**
	 * Returns the number of hops necessary to reach the second node
	 * from a first node.
	 * @param first The first node.
	 * @param second The second node.
	 * @return The number of hops necessary to reach the second node
	 * from a first node.
	 */
	public int getSubPathLength(String first, String second) {
		int index1 = path.getNodePosition(first);
		int index2 = path.getNodePosition(second);
		return (index2 - index1);
	}
	
	/**
	 * Returns the number of hops of the loop.
	 * @param id The node that closes the loop.
	 * @return The number of hops of the loop. If there is no loop, returns 0.
	 */
	public int getLoopSize(String id) {
		int size = path.size();
		int index = path.getNodePosition(id);
		if (index == -1) { //There is no loop!
			return 0;
		} else { //Loop found
			return (size - index);
		}
	}
	/**
	 * Turns the forward ant into a backward one.
	 */
	public void toBackward() {
		//Set it as a backward ant
		this.setNextHeader(Packet.Header.ANT_BACKWARD);
	}
	
	/**
	 * Gets the backward node of the ant. To be used only for the backward ant.
	 * @return The backward node of the ant. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		int counter = 0;
		for (String node : path.nodes()) {
			if (node.equals(procNode) && !procNode.equals(source)) {
				return path.getNode(counter - 1);
			}
			counter ++; //Increment the counter
		}
		return null; // In case of an error!
	}
	
	/**
	 * Gets the forward node of the ant. To be used only for the backward ant.
	 * @return The forward node of the ant. Null, if the processing node
	 * is the target node.
	 */
	public String getForwardNode() {
		int counter = 0;
		for (String node : path.nodes()) {
			if (node.equals(procNode) && !procNode.equals(target)) {
				return path.getNode(counter + 1);
			}
			counter ++; //Increment the counter
		}
		return null; // In case of an error!
	}
	
	/**
	 * Returns the number of bytes per hop of each node identification.
	 * @return The number of bytes per hop of each node identification.
	 */
	public int getBytesHop() {
		return this.bytesPerHop;
	}
}
