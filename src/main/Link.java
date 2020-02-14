/*
 * Created on Sep 14, 2005.
 * Added length of the link on Jan 26, 2006.
 */
package main;

import java.io.Serializable;

import graph.*;
/**
 * Represents the link information as seen by the network.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Link implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** Constant of the speed of the light in the fiber in km/s. */
	public static final double SPEED_OF_LIGHT = 2E5;
	/** The edge in the network graph. */
	protected Edge edge;
	/** The number of supported wavelengths in this link. */
	protected int numberWavelengths;
	/** The data rate supported by this link. */
	protected double dataRate;
	/** The number of bytes offered to this link. */
	protected long byteCounter;
	/** The length (in km) of the link. */
	protected double length = 0;
	/** The delay due to transmission time. */
	protected double delay = 0;
	
	/**
	 * Creates a new Link object.
	 * @param aEdge The edge in the network graph.
	 * @param w The maximum number of supported wavelengths in this link.
	 * @param aDataRate The data rate supported by this link. 
	 */
	public Link(Edge aEdge, int w, double aDataRate) {
		this.edge = aEdge;
		this.numberWavelengths = w;
		this.dataRate = aDataRate;
		this.byteCounter = 0;
	}

	/**
	 * Creates a new Link object.
	 * @param aEdge The edge in the network graph.
	 * @param w The maximum number of supported wavelengths in this link.
	 * @param aDataRate The data rate supported by this link. 
	 * @param aLength The length (in km) of the link.
	 */
	public Link(Edge aEdge, int w, double aDataRate, double aLength) {
		this(aEdge,w,aDataRate);
		this.length = aLength;
		this.delay = aLength / Link.SPEED_OF_LIGHT;
	}
	
	/**
	 * Returns the edge of the graph representing the network.
	 * @return The edge of the graph representing the network.
	 */
	public Edge getEdge() {
		return this.edge;
	}
	
	/**
	 * Return the number of wavelengths available in this link.
	 * @return The number of wavelengths available in this link.
	 */
	public int getNumberWavelengths() {
		return this.numberWavelengths;
	}
	
	/**
	 * Returns the data rate supported by this link.
	 * @return The data rate supported by this link.
	 */
	public double getDataRate() {
		return this.dataRate;
	}
	
	/**
	 * Returns the length of this link in km.
	 * @return The length of this link in km.
	 */
	public double getLength() {
		return this.length;
	}
	
	/**
	 * Returns the delay due to transmission time in seconds.
	 * @return The delay due to transmission time in seconds.
	 */
	public double getDelay() {
		return this.delay;
	}
	
	/**
	 * Adds the specified number of bytes to the accumulated counter.
	 */
	public void setCounter(int bytes) {
		byteCounter = byteCounter + bytes;
	}
	
	/**
	 * Returns the number of bytes traversed by this link.
	 * @return The number of bytes traversed by this link.
	 */
	public long getCounter() {
		return this.byteCounter;
	}
	
	public void resetCounter() {
		this.byteCounter = 0;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Edge: ");
		buf.append(edge.toString());
		buf.append(", number of wavelengths: ");
		buf.append(this.numberWavelengths);
		buf.append(", length: ");
		buf.append(this.length);
		buf.append(", data rate: ");
		buf.append(this.dataRate);
		buf.append(", bytes: ");
		buf.append(this.byteCounter);
		return buf.toString();
	}
}
