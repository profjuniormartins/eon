/*
 * Created on 03/12/2005.
 */
package main;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * This class represents an entry in a routing table.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class RoutingTableEntry implements Serializable {
	/** Default serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The mapping between neighbors and entries. */
	protected LinkedHashMap<String,Integer> neighborMap;
	/** The entry routing table of all neighbors. */
	protected Object[] neighborhood;

	/**
	 * Creates a new RoutingTableEntry value.
	 * @param neighbors The id of the neighbors of this entry.
	 */
	public RoutingTableEntry(Object[] neighbors) {
		//Create space for the neighbor objects.
		neighborhood = new Object[neighbors.length];
		//Create the mapping
		neighborMap = new LinkedHashMap<String,Integer>();
		int counter = 0; //Index counter.
		//For each neighbor
		for (Object neighId:neighbors) {
			neighborMap.put(neighId.toString(),counter);
			counter ++; //Updates the counter.
		}
	}
	
	/**
	 * For cloning purposes only!
	 *
	 */
	protected RoutingTableEntry() {
	}


	/**
	 * Returns the entry value with the specified key identification.
	 * @param key The id of the specified neighbor.
	 * @return The entry with the specified key identification.
	 */
	public Object getEntry(Object key) {
		return neighborhood[neighborMap.get(key.toString())];
	}
	
	/**
	 * Updates the entry specified by the node identification key.
	 * @param key The neighbor identification.
	 * @param entry The value in the entry.
	 */
	public void putEntry(Object key, Object entry) {
		//Get the index position and uptates the entry
		neighborhood[neighborMap.get(key.toString())] = entry;
	}
	
	/**
	 * Returns the set of neighbors in this entry.
	 * @return The set of neighbors in this entry.
	 */
	public Set<String> neighborhood() {
		return neighborMap.keySet();
	}
	
	/**
	 * Returns true, if it contains an entry value with the specified key.
	 * @param key The identification of the neighbor.
	 * @return True, if it contains an entry value with the specified key.
	 * False, otherwise.
	 */
	public boolean contains(Object key) {
		return (neighborMap.get(key) != null);
	}
	
	/**
	 * Adds a new entry. 
	 * @param key The identification of a new neighbor.
	 */
	public void addEntry(Object key) {
		//Adds the new entry to the mapping
		neighborMap.put(key.toString(),neighborMap.size());
		//Creates a new array with more space.
		Object[] newNeighborhood = new Object[neighborMap.size() + 1];
		//Synchronizes the old and the new array
		System.arraycopy(neighborhood,0,newNeighborhood,0,neighborMap.size());
		//Updates neighborhood
		neighborhood = newNeighborhood;
	}
	
	
	/**
	 * Removes the specified entry. This implementation just removes the
	 * reference to the removed entry. 
	 * @param key The identification of the neighbor.
	 */
	public void removeEntry(Object key) {
		//Remove the reference in the map.
		neighborMap.remove(key.toString());	
		
		 
	}
	
	/**
	 * Returns the total number of entries of this object.
	 * @return The total number of entries of this object.
	 */
	public int size() {
		return neighborMap.size();
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for(String neighId: neighborMap.keySet()) {
			buffer.append(neighId);
			buffer.append(" - ");
			buffer.append(neighborhood[neighborMap.get(neighId)].toString());
			buffer.append("\n");
		}
		return buffer.toString();
	}
	
	/**
	 * Return a clone version of this object.
	 */
	@SuppressWarnings("unchecked")
	public Object clone() {
		RoutingTableEntry entryClone = new RoutingTableEntry();
		System.arraycopy(this.neighborhood,0,entryClone.neighborhood,0,this.neighborhood.length);
		entryClone.neighborMap = (LinkedHashMap) this.neighborMap.clone();
		return entryClone;
	}
}
