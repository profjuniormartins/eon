/*
 * Created on 14/02/2008.
 */
package rwa.crankback.obgp;

import java.io.Serializable;
import java.util.Vector;

import rwa.WavelengthMask;

/**
 * This class represents a Label Set (Inclusive) object.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class LabelSet implements Serializable {
	/** Serial UID for serialization. */
	private static final long serialVersionUID = 1L;

	/** The set of available wavelengths. */
	protected Vector<Integer> set;
	
	/**
	 * For cloning purposes.
	 */
	protected LabelSet() {
	}
	
	/**
	 * Creates a new LabelSet object. This constructor is suited for First-Fit operations.
	 * @param wavelength The number of available wavelengths.
	 */
	public LabelSet(int wavelength) {
		set = new Vector<Integer>();
		for(int i=0; i< wavelength; i++)
			set.add(i);
	}
	
	/**
	 * Creates a new LabelSet object. This constructor is suited for LU and MU operations.
	 * @param wavelength The number of available wavelengths.
	 */
	public LabelSet(int[] labels) {
		set = new Vector<Integer>();
		for(int i=0; i< labels.length; i++)
			set.add(labels[i]);
	}
	
	/**
	 * Add a label to the end of the Label Set object.
	 * @param label The label to be added.
	 */
	public void addLabel(int label) {
		set.add(label);
	}
	
	/**
	 * Remove the specified label of the Label Set object.
	 * @param label The label to be removed.
	 */
	public void removeLabel(int label) {
		int index = set.indexOf(label);
		set.remove(index);
	}
	
	/**
	 * Remove the unavailable wavelengths from the Label Set object.
	 * @param mask The wavelength mask.
	 */
	public void inclusive(WavelengthMask mask) {
		Vector<Integer> remove = new Vector<Integer>(); 
		for(int label:set) { //for all labels in the label set
			if (!mask.testWavelength(label)) { //label not available
				remove.add(label);
			}
		}
		//System.out.println(remove.toString());
		for(int rem:remove) { //remove the labels not available
			this.removeLabel(rem);
		}
	}
	
	/**
	 * Returns a Vector of the labels of this Label Set object.
	 * @return A Vector of the labels of this Label Set object.
	 */
	public Vector<Integer> getLabels() {
		return set;
	}
	
	/**
	 * Returns the first label of this Label Set object.
	 * @return The first label of this Label Set object.
	 */
	public int getFirstLabel() {
		return set.firstElement();
	}
	
	/**
	 * Returns the number of labels in the Label Set object.
	 * @return The number of labels in the Label Set object.
	 */
	public int size() {
		return set.size();
	}
	
	/**
	 * Returns a String representation of this object.
	 * @return A String representation of this object.
	 */
	public String toString() {
		return set.toString();
	}

	/**
	 * Returns a cloned object.
	 */
	@SuppressWarnings("unchecked")
	public Object clone() {
		LabelSet clone = new LabelSet();
		clone.set = (Vector<Integer>)this.set.clone();
		return clone;
	}
}
