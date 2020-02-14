/*
 * Created on 14/02/2008.
 */
package rwa.crankback.antnet;

import java.util.Arrays;

import rwa.WavelengthMask;
import antnet.AntPacket;

/**
 * Ant packet with indication of loop and with a feature of collecting the wavelength usage
 * during its trip from source to target node.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class Ant extends AntPacket {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** True, if the ant has entered a loop. False, otherwise. */
	protected boolean loopFlag = false;
	/** The number of occurrences for each lambda in the visited links. */
	protected int[] collector;
	
	/**
	 * Create a new AntPacket object. The initial one is of the type ANT_FORWARD.
	 * 
	 * @param sourceId The source identification of this node.
	 * @param targetId The target identification of this node.
	 * @param hopLimit The maximum number of hops allowed for this ant.
	 * @param bytesHop The number of bytes for each node identification.
	 */
	public Ant(String sourceId, String targetId, int hopLimit, int bytesHop) {
		super(sourceId,targetId,hopLimit,bytesHop);
	}

	/**
	 * Gets the state of the loop flag. True, if the ant has entered a loop. False, otherwise.
	 * @return True, if the ant has entered a loop. False, otherwise.
	 */
	public boolean getLoopFlag() {
		return this.loopFlag;
	}
	
	/**
	 * Sets the loop flag of the ant.
	 */
	public void setLoopFlag() {
		this.loopFlag = true;
	}
	
	/**
	 * Adds the wavelength link mask to the ant's memory.
	 * @param mask The wavelength mask of the link.
	 */
	public void addMask(WavelengthMask mask) {
		//Create the mask, if it is not available
		if (collector == null) {
			this.collector = new int[mask.size()];
			Arrays.fill(collector,0);
		}
		//Gets the length of the collector
		int len = collector.length;
		//for each position, probe the wavelength mask
		for(int i=0; i < len; i++) {
			if (!mask.testWavelength(i)) { //occupied lambda
				collector[i] = collector[i] + 1; //Increment the counter
			}
		}
	}
	
	/**
	 * Returns the collector statistics of the wavelength usage gathered in its trip.
	 * @return The collector statistics of the wavelength usage gathered in its trip.
	 */
	public int[] getCollector() {
		return this.collector;
	}
	
}
