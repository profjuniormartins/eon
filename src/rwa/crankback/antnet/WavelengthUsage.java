/*
 * Created on 14/02/2008.
 */
package rwa.crankback.antnet;

import java.io.Serializable;
import java.util.Arrays;

/**
 * This class represents the collection of wavelength usage, given a destination.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class WavelengthUsage implements Serializable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;	
	/** True, if a sliding window is used. False, otherwise. */
	private boolean sliding;
	/** The table for storing the wavelength usage historic. */
	long[][] table;
	/** The size of the window. */
	protected int window;
	/** The number of wavelengths. */
	protected int wavelength;
	/** The index of the sample inside the window. */
	protected int sample = 0;
	
	/**
	 * Creates a new WavelengthUsage object.
	 * @param wavelength The number of wavelengths.
	 * @param window The window size.
	 * @param aSliding The type; sliding or non-sliding.
	 */
	public WavelengthUsage(int aWavelength, int aWindow, boolean aSliding) {
		this.wavelength = aWavelength;
		this.window = aWindow;
		this.sliding = aSliding;
		if (sliding) { //sliding
			table = new long[window][wavelength];
		} else { //non-sliding
			table = new long[1][wavelength];
		}
	}
	
	/**
	 * Adds the specified wavelength usage collected to the history table.
	 * @param usage The wavelength usage.
	 */
	public void addUsage(int[] usage) {
		if (sliding) {
			for(int i=0; i < wavelength; i++) {
				table[sample][i] = (long)usage[i];
			}
		} else { //Non-sliding
			if (sample == 0) { //Values equal to the usage
				for(int i=0; i < wavelength; i++) {
					table[0][i] = (long) usage[i];
				}
			} else { //Add the usage to the counters
				for(int i=0; i < wavelength; i++) {
					table[0][i] = table[0][i] + (long)usage[i];
				}				
			}
		}
		//Increment the sample
		sample = (sample + 1) % window;
	}
	
	/**
	 * Gets the wavelength usage history in the table.
	 * @return The wavelength usage history in the table.
	 */
	public long[] getUsage() {
		//Allocate space
		long[] usage = new long[wavelength];
		Arrays.fill(usage,0L);
		if (sliding) { //sliding
			for(int i=0; i < wavelength; i++) {
				for(int j=0; j < window; j++) {
					usage[i] = usage[i] + table[j][i];
				}
			}
		} else { //Non-sliding
			for(int i=0; i < wavelength; i++) {
				usage[i] = table[0][i];
			}			
		}
		return usage;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (sliding) {
			for(int i=0; i < window; i++) {
				builder.append("[");
				for(int j=0; j < wavelength; j++) {
					builder.append(" ");
					builder.append(table[i][j]);					
					builder.append(" ");
				}
				builder.append("]");
				builder.append("\n");				
			}
		} else {
			builder.append("[");
			for (int i=0; i < wavelength; i++) {
				builder.append(" ");
				builder.append(table[0][i]);
				builder.append(" ");
			}
			builder.append("]");
		}
		return builder.toString();		
	}
}
