package antnet;

import java.io.Serializable;

/**
 * Wrapper class for hiding neighbor's attributes.
 * 
*/
public class NeighborAttr implements Serializable, Comparable<NeighborAttr>{
	static final long serialVersionUID = 1L;
	/** The id of the neighbor in the network. */
	private Object id;
	/** The pheromone level of the link between the node and the neighbor. */ 
	private double pheromone;
	/** Default value for pheromone. */
	private final static double DEFAULT_LEVEL = 0;  
	
	/**
	 * Creates a new NeighborAttr object.
	 * @param aId The id of the neighbor in the network
	 */
	public NeighborAttr(Object aId) {
		id = aId;
		//Sets the default pheromone level.
		pheromone = DEFAULT_LEVEL;
	}
		
	/**
	 * Gets the id of the neighbor.
	 * @return The id of the neighbor.
	 */
	public Object getId() {
		return id;
	}
		
	/**
	 * Gets the pheromone level of the link between the node and the neighbor.
	 * @return The pheromone level of the link between the node and the neighbor.
	 */
	public double getPheromoneLevel() {
		return pheromone;
	}

	/**
	 * Sets the pheromone level of the link between the node and the neighbor.
	 * @param level The new level of pheromone.
	 */
	public void setPheromoneLevel(double level) {
		pheromone = level;
	}
		
	/**
	 * Returns a string representation of the neighbor column.
	 * @return A string representation of the neighbor column.
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("Id: ");
		buffer.append(id);
		buffer.append(" ,level: ");
		buffer.append(pheromone);
		return buffer.toString();
	}

	/**
	 * Returns a negative integer, zero, or a positive integer as this object has
	 *  less than, equal to, or greater level of pheromone than the specified object.
	 */
	@Override
	public int compareTo(NeighborAttr attr) {
		if (this.pheromone < attr.getPheromoneLevel()) 
			return -1;
		else if (this.pheromone > attr.getPheromoneLevel())
			return 1;
		else //equal
			return 0;
	}
}
