/*
 * QuickSort.java
 *
 * Created on 21 de Fevereiro de 2003, 11:15
 */

package util;

import java.util.*;

/**
 * Quicksort with 3-way partioning (Bentley-McIlroy).
 * It is optimized for equal keys, has little overhead and has no need for a separate radix sort.
 * Please refer to <b>Algorithms in Java, Parts 1-4 (Fundamental Algorithms, Data Structures, Sorting, Searching)</b> 
 * <i>Robert Sedgewick</i>, Addison-Wesley 2002. See 
 * <a href="http://www.cs.princeton.edu/~rs/"> Robert Sedgewick Home Page</a>
 *
 * @author Gustavo Sousa Pavani.
 * @version 1.0
 */
public class QuickSort {
    /** If true, sorts in ascending (smaller first) way. */
    private static boolean ascending; 
        
    /**
     * Sorts the specified vector using the QuickSort method.
     * @param vector The vector to be sorted. The elements of this vector MUST implement the java.lang.Comparable
     * interface. If not, then java.lang.ClassCastException is thrown. @see java.lang.Comparable
     * @param aAscending If it is true, then the vector is sorted in a ascending way (i.e. smaller elements first);
     * otherwise, the vector is sorted in a descending way.
     */
    public static <T extends Comparable<? super T>> void sort(Vector<T> vector, boolean aAscending) {
        ascending = aAscending;
        quicksort(vector, 0, vector.size() -1);
    }
    
    /**
     * The quicksort with 3-way partioning.
     * @param a The vector to be sorted.
     * @param l The index of the first element (inclusive) to be sorted.
     * @param r The index of the last element (inclusive) to be sorted.
     */
    static <T extends Comparable<? super T>> void quicksort(Vector<T> a, int l, int r) {
        if (r <= l) return;
        T v =  a.elementAt(r);
        int i = l-1, j = r, p = l-1, q = r, k;
        for (;;) {
            while (less(a.elementAt(++i), v)) ;
            while (less(v, a.elementAt(--j))) if (j == l) break;
            if (i >= j) break;
            exch(a, i, j);
            if (equal(a.elementAt(i), v)) { p++; exch(a, p, i); }
            if (equal(v, a.elementAt(j))) { q--; exch(a, q, j); }
        }
        exch(a, i, r); j = i-1; i = i+1;
        for (k = l  ; k <= p; k++,j--) exch(a, k, j);
        for (k = r-1; k >= q; k--,i++) exch(a, k, i);
        quicksort(a, l, j);
        quicksort(a, i, r);
    }
    
    /**
     * Swaps two elements in a vector.
     * @param a The specified vector.
     * @param i The position of the first element in this vector.
     * @param j The position of the second element in this vector.
     */
    private static <T extends Comparable<? super T>> void exch(Vector<T> a, int i, int j) {
        T obj1 = a.get(i);
        T obj2 = a.get(j);
        a.setElementAt(obj1,j);
        a.setElementAt(obj2,i);
    }
    
    /**
     * Verifies if a element is less than another one, depending if the sort order is ascending or no.
     * @param item1 The first element.
     * @param item2 The second element.
     * @return True, if the first element is less than the second one and ascending is true; false, otherwise.
     * True, if the first element is more than the second one and ascending is false; false, otherwise.
     */
    private static <T extends Comparable<? super T>> boolean less(T item1, T item2) {
        if (ascending)
            return (item1.compareTo(item2) < 0);
        else
            return (item1.compareTo(item2) > 0);
    }
    
    /**
     * Verifies if the first element is equal to the second one.
     * @param item1 The first element.
     * @param item2 The second element.
     * @return True, if item1 is equal to item2. False, otherwise.
     */
    private static <T extends Comparable<? super T>> boolean equal(T item1, T item2) {
        return (item1.compareTo(item2) == 0);
    }
}
