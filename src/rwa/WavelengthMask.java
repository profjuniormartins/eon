/*
 * Created on 26/02/2005
 *
 */
package rwa;

import java.io.Serializable;
import java.util.*;

/**
 * Wavelength mask. The wavelengths with true values are available.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0 
 */
public class WavelengthMask implements Serializable, Cloneable {
	static final long serialVersionUID = 1L;
	/** The wavelength mask array. */
	boolean[] mask;
	/** The wavelengths in the network. */
	int wavelengths;
	
	/**
	 * Creates a new WavelengthMask object 
	 * @param w The size of the mask.
	 */
	public WavelengthMask(int w) {
		this.wavelengths = w;
		mask = new boolean[w];
		Arrays.fill(mask,true);
	}
	
	/** For cloning purposes. */
	protected WavelengthMask() {
	}
	
	/**
	 * Set as used (false) the wavelength at the specified position.
	 * @param pos The specified position of the wavelength.
	 */
	public void setWavelength(int pos) {
		mask[pos]=false;		
	}
	
	
	/**
	 * Set as unused (true) the wavelength at the specified position.
	 * @param pos The specified position of the wavelength.
	 */	
	public void clearWavelength(int pos) {
		mask[pos]=true;
	}
	
	/**
	 * Test if the wavelength at the specified position is available (not allocated).
	 * Return, true if the wavelength is available. False, otherwise.
	 * @param pos The specified position of the wavelength.
	 * @return True if the wavelength is available. False, otherwise.
	 */
	public boolean testWavelength(int pos) {
		return mask[pos];
	}
	
	/**
	 * Returns the free wavelength ratio, i.e., (available wavelengths) / (total wavelengths);
	 * @return The free wavelength ratio.
	 */
	public double freeWavelengthRatio() {
		return ((double)this.freeWavelengths()) / ((double)(mask.length));
	}
	
	/** 
	 * Gets the total number of free wavelengths.
	 * @return The total number of free wavelengths.
	 */
	public int freeWavelengths() {
		int count = 0;
		for (int i=0; i<mask.length; i++) {
			if (mask[i])
				count++;
		}
		return count;		
	}
	
	/**
	 * Gets the list of free wavelengths.
	 * @return The list of free wavelengths.
	 */
	public Vector<Integer> listFreeWavelengths() {
		Vector<Integer> available = new Vector<Integer>();
		for (int i=0; i<mask.length; i++) {
			if (mask[i])
				available.add(i);
		}
		return available;
	}
	
	/**
	 * Updates the current wavelength mask with another one using the AND operator.
	 * @param wMask 
	 */
	public void updateMask(WavelengthMask wMask) {
		for (int i=0; i < mask.length; i++) {
			this.mask[i] = this.mask[i] & wMask.mask[i];
		}		
	}
	
	/**
	 * Returns the size, i.e., the number of positions of the mask.
	 * @return The size, i.e., the number of positions of the mask.
	 */
	public int size() {
		return mask.length;
	}
	
	/**
	 * Returns a string representation of this object.
	 * @return A string representation of this object. Available wavelengths are marked as true.
	 */
	public String toString() {
		return Arrays.toString(mask);
	}
	
	/**
	 * Returns a clone object.
	 * @return A clone object.
	 */
	public Object clone() {
		WavelengthMask newMask = new WavelengthMask();
		newMask.wavelengths = this.wavelengths;		
		newMask.mask = new boolean[wavelengths];
		System.arraycopy(this.mask,0,newMask.mask,0,wavelengths);
		return newMask;
	}
}
