/*
 * Created on Sep 14, 2005.
 */
package event;

import util.*;

import java.io.Serializable;
import java.util.List;

/**
 * A list for storing the events to be served. For speeding-up the operations of
 * removing and adding events to this list, this implementation uses a priority queue (heap)
 * internally, respecting the order of insertion if two elements have
 * the same priority.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class EventList implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The queue for storing all events waiting for processing. */
	StrictBinaryHeap queue;
	
	/**
	 * Creates a new EventList object.
	 *
	 */
	public EventList() {
		queue = new StrictBinaryHeap(true);
	}
	
	/**
	 * Returns the next event of the list, without removing it from the heap. 
	 * @return The next event of the list.
	 */
	public Event getNextEvent() {
		return (Event)queue.peek();
	}
	
	/**
	 * Returns the next event of the list, by removing it from the heap. 
	 * @return The next event of the list.
	 */
	public Event pollNextEvent() {
		return (Event)queue.remove();
	}
	
	/**
	 * Stores a new Event object in this list.
	 * @param event The specified event to be stored.
	 */
	public void addEvent(Event event) {
		queue.add(event);
	}
	
	/**
	 * Return the number of events stored in this list.
	 * @return The number of events stored in this list.
	 */
	public int size(){
		return queue.size();
	}
	
	/**
	 * Dump the contents of this list to the standard output.
	 * Use for debug purposes only.
	 */
	public void dump() {
		while (queue.size() > 0) {
			System.out.println(queue.remove().toString());
		}		
	}
	
	/**
	 * Returns a String representation of this object.
	 * No ordering can be assumed in this String!
	 */
	@SuppressWarnings("unchecked")
	public String toString() {
		StringBuilder buf = new StringBuilder();
		List list = queue.asList();
		for (Object element:list) {
			buf.append(element.toString());
			buf.append("\n");
		}
		return buf.toString();
	}
	
}
