package util;

import java.io.Serializable;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Binary heap implementation of a priority queue, in which items of
 * the same priority are removed in order of insertion.<p>
 * Adapted to used the reversed priority binary heap.
 * 
 * @author Peter Williams and Gustavo Pavani. */

public class StrictBinaryHeap implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
    private BinaryHeap h;         // the heap
    private static long ticks = 0; // the number of insertions so far

    @SuppressWarnings("unchecked")
	private class Item implements Comparable, Serializable {
    	/** Serial version uid. */
    	private static final long serialVersionUID = 1L;
        Comparable item;
        long time;
        Item(Comparable item, long time) {
            this.item = item;
            this.time = time;
        }
        
        @SuppressWarnings("unchecked")
		public int compareTo(Object other) {
            int result = item.compareTo(((Item) other).item);
            if (result == 0) { // items have same original priority
                result = new Long(((Item) other).time - time).intValue();
                // so earlier items now have higher priority
            }
            return result;
        }
        
        /**
         * Return a String representation of this object.
         */
        public String toString() {
        	return item.toString();
        }
    }
  
    /**
     * Constructs the binary heap.
     * @param reversedPriority If it is true, then the heap order property is such that
     * the predecessor has a lower priority than its successors. Otherwise, the heap
     * order property is such that the predecessor has a higher priority than its successors.
     */
    public StrictBinaryHeap(boolean reversedPriority) {
        h = new BinaryHeap(reversedPriority);
    }
  
    /**
     * Tests if the heap is empty.
     * @return true if empty, false otherwise.
     */
    public boolean isEmpty() {
        return h.isEmpty();
    }

    /**
     * Returns the first item of the heap, without removing it.
     * 
     * @return An item of highest priority (or lowest priority if reversedPriority is true).
     * @exception NoSuchElementException if the heap is empty. 
     */     
    @SuppressWarnings("unchecked")
	public Comparable peek() {
        if (size() == 0) {
            throw new NoSuchElementException();
        }
    	return ((Item)h.peek()).item;
    }
    
    /**
     * Returns the current size of the queue.
     * @return the current size of the queue
     */
    public int size() {
        return h.size();
    }
  
    /**
     * Adds and item to the heap.
     * @param item the item to add.
     */
    @SuppressWarnings("unchecked")
	public void add(Comparable item) {
        h.add(new Item(item, ticks++));
    }
  
    /**
     * Removes an item of highest priority item from the heap.<p>
     * 
     * If several items are currently of highest priority, returns them
     * in order of insertion.<p>
     *
     * @return the first item of highest priority that was inserted into the heap.
     * @exception NoSuchElementException if the heap is empty.
     */
    @SuppressWarnings("unchecked")
	public Comparable remove() {
        return ((Item) h.remove()).item;
    }
    
    /**
     * Returns a list representation of this binary heap.
     * No assumption can be made over the ordering of the
     * elements of this list.
     * @return A list representation of this binary heap.00
     */
    @SuppressWarnings("unchecked")
	public List asList() {
    	return h.asList();
    }

}
