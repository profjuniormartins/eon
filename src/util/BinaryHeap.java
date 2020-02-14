/*
 * BinaryHeap.java
 */
package util;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Arrays;
import java.util.List;

/**
 * Binary heap implementation of a priority queue.
 *
 * @author Peter Williams and Gustavo Pavani.
 * @version 1.2.
 */
public class BinaryHeap implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
    /** The heap. */
    @SuppressWarnings("unchecked")
	protected Comparable[] heap;
    /** The number of items in the heap. */
    private int size;
    /** Flag that indicates the invertion of the binary heap's priority. */
    protected boolean reversedPriority;
    
    /**
     * Constructs the binary heap.
     * @param reversedPriority If it is true, then the heap order property is such that
     * the predecessor has a lower priority than its successors. Otherwise, the heap
     * order property is such that the predecessor has a higher priority than its successors.
     */
    public BinaryHeap(boolean reversedPriority) {
        heap = new Comparable[1];
        size = 0;
        this.reversedPriority = reversedPriority;
    }
    
    /**
     * Tests if the heap is empty.
     * @return true if empty, false otherwise.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the first item of the heap, without removing it.
     * 
     * @return An item of highest priority (or lowest priority if reversedPriority is true).
     * @exception NoSuchElementException if the heap is empty. 
     */     
    @SuppressWarnings("unchecked")
	public Comparable peek() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
    	return heap[0];
    }
    
    /**
     * Returns the current size of the queue.
     * @return the current size of the queue.
     */
    public int size() {
        return size;
    }
    
    /**
     * Remove all elements of this Binary Heap.
     */
    public void removeAllElements() {
        heap = new Comparable[1];
        size = 0;
    }

    /**
     * Adds and item to the heap.
     * @param item the item to add.
     */
    @SuppressWarnings("unchecked")
	public void add(Comparable item) {
        // grow the heap if necessary
        if (size == heap.length) {
            Comparable[] newHeap = new Comparable[2 * heap.length];
            System.arraycopy(heap, 0, newHeap, 0, heap.length);
            heap = newHeap;
        }
        //Siftup operation.
        // find where to insert while rearranging the heap if necessary
        int parent, child = size++; // the next available slot in the heap
        if (reversedPriority) {
            while (child > 0 && heap[parent = (child - 1) / 2].compareTo(item) > 0) {
                heap[child] = heap[parent];
                child = parent;
            }
        } else {
            while (child > 0 && heap[parent = (child - 1) / 2].compareTo(item) < 0) {
                heap[child] = heap[parent];
                child = parent;
            }
        }
        heap[child] = item;
    }
    
    /**
     * Removes an item of highest priority (or lowest priority if reversedPriority
     * is true) from the heap.
     * @return An item of highest priority (or lowest priority if reversedPriority is true).
     * @exception NoSuchElementException if the heap is empty.
     */
    @SuppressWarnings("unchecked")
	public Comparable remove() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        Comparable result = heap[0];   // to be returned
        Comparable item = heap[--size]; // to be reinserted
        int child, parent = 0;
        while ((child = (2 * parent) + 1) < size) {
            // if there are two children, compare them
            if (reversedPriority) {
                if (child + 1 < size && heap[child].compareTo(heap[child + 1]) > 0) {
                    ++child;
                }
            } else {
                if (child + 1 < size && heap[child].compareTo(heap[child + 1]) < 0) {
                    ++child;
                }
            }
            // compare item with the larger
            if (reversedPriority) {
                if (item.compareTo(heap[child]) > 0) {
                    heap[parent] = heap[child];
                    parent = child;
                } else {
                    break;
                }
            } else {
                if (item.compareTo(heap[child]) < 0) {
                    heap[parent] = heap[child];
                    parent = child;
                } else {
                    break;
                }
            }
        }
        heap[parent] = item;
        return result;
    }

    /**
     * Returns a list representation of this binary heap.
     * No assumption can be made over the ordering of the
     * elements of this list.
     * @return A list representation of this binary heap.00
     */
    @SuppressWarnings("unchecked")
	public List asList() {
    	return Arrays.asList(heap);
    }
    
}
