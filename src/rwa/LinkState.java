/*
 * Created on 06/12/2005.
 */
package rwa;

import java.io.Serializable;

import main.Link;
import graph.Edge;

/**
 * This class represents a link with attributes needed for
 * ant routing.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class LinkState implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The link of the network. */
	protected Link link;
	/** The wavelength mask associated to this link. */
	protected WavelengthMask mask;
	
	/**
	 * Creates a new LinkState object.
	 * @param aLink The link of the network.
	 */
	public LinkState(Link aLink) {
		this.link = aLink;
		this.mask = new WavelengthMask(link.getNumberWavelengths());
	}
	
	
	/**
	 * Gets the wavelength mask associated to this link.
	 * @return The wavelength mask associated to this link.
	 */
	public WavelengthMask getMask() {
		return this.mask;
	}
	
	/**
	 * Returns the edge of the graph representing the network.
	 * @return The edge of the graph representing the network.
	 */
	public Edge getEdge() {
		return this.link.getEdge();
	}
	
	/**
	 * Return the number of wavelengths avaliable in this link.
	 * @return The number of wavelengths avaliable in this link.
	 */
	public int getNumberWavelengths() {
		return this.link.getNumberWavelengths();
	}

	/**
	 * Sets the wavelength mask with the specified mask.
	 * @param aMask The specified mask.
	 */
	public void setMask(WavelengthMask aMask) {
		this.mask = aMask;
	}
	
	/**
	 * Returns the delay due to transmission time.
	 * @return The delay due to transmission time.
	 */
	public double getDelay() {
				
		return this.link.getDelay();
	}
	
	/**
	 * Returns the link properties.
	 * @return The link properties.
	 */
	public Link getLink() {
		return this.link;
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(link.toString());
		builder.append(" mask: ");
		builder.append(mask.toString());
		return builder.toString();
	}
}

