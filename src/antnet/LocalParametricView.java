/*
 * Created on Oct 13, 2005.
 */
package antnet;

import java.io.Serializable;

/**
 * This class represents the local view of the current traffic situation
 * on the paths that are used to reach the destination from this node.
 * In this sense, it is a local parametric view of the global network traffic.
 * The estimation is done using a exponential model. 
 * <p><b>Note:</b> Based on the work of Jacobson and Karels for TCP retransmission time-outs. 
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class LocalParametricView implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The sample mean traveling distance to reach destination from the actual node. */
	private double average;
	/** The standard deviation traveling distance to reach destination from the actual node. */	
	private double deviation;
	/** The best traveling distance over the window observation. */
	private double best;
	/** Number of samples for the non-sliding window. */
	private long window;
	/** The total number of samples. */
	private long sample;	
	/** The exponential model factor. */
	private double exponential;
	
	
	/**
	 * Creates a new LocalParametricView object.
	 * @param exponentialFactor The exponential factor.
	 * @param reductor The window reductor.
	 */
	public LocalParametricView(double exponentialFactor, double reductor) {
		this.exponential = exponentialFactor;
		window = Math.round(5 * (reductor/exponential));
		this.sample = 0;
	}

	/**
	 * Updates the local view with the specified value.
	 * @param value The value to be added.
	 */
	public void update(double value) {
		if ((sample % window)==0) { //Starting of the non-sliding window
			average = value;
			deviation = 0;
			best = value;
			sample = 0;
		} else { //Otherwise
			if (value < best)
				best = value;
			double difference = (value - average);
			average = average + exponential*difference;
			deviation = deviation + exponential*(Math.abs(difference) - deviation);
		}
		//Increment the sample counter
		sample ++;
	}
	
	/**
	 * Returns the distance from the average in terms of stadard deviation units.
	 * @param value The value to be evaluated.
	 * @return The distance from the average in terms of stadard deviation units.
	 */
	public double evaluate(double value) {
		return (value - average) / deviation;
	}
	
	/**
	 * Returns the best value stored.
	 * @return The best value.
	 */
	public double getBest() {
		return this.best;
	}

	/**
	 * @return Returns the average.
	 */
	public double getAverage() {
		return average;
	}

	/**
	 * @return Returns the deviation.
	 */
	public double getDeviation() {
		return deviation;
	}

	/**
	 * @return Returns the window size.
	 */
	public long getWindow() {
		return window;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Best: ");
		builder.append(this.best);
		builder.append(", average: ");
		builder.append(this.average);
		builder.append(", devitation: ");
		builder.append(this.deviation);
		builder.append(", sample: ");
		builder.append(this.sample);		
		return builder.toString();		
	}
}
