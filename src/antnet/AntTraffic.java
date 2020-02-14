/*
 * Created on Oct 12, 2005.
 */
package antnet;

import random.MersenneTwister;
import main.ControlPlane;
import distribution.Distribution;
import event.Event;
import event.EventSubscriber;
import event.Event.Type;

/**
* This class represents the generation of ant for each node. 
*
* @author Gustavo S. Pavani
* @version 1.0
*
*/
public class AntTraffic implements EventSubscriber {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The distribution for ant generation. */
	protected Distribution distribution;
	/** The maximum number of hops allowed for this packet. */
	protected int hopLimit;
	/** The number of bytes added at each hop. */
	protected int bytesPerHop;
	/** Random number generator. */
	protected MersenneTwister rng;
	
	/**
	 * Creates a new AntTraffic object.
	 * @param aHopLimit The maximum number of hops allowed for this packet.  
	 * @param bytesHop The number of bytes added at each hop.
	 * @param seed The random seed.
	 */
	public AntTraffic(int aHopLimit, int bytesHop, long seed) {
		this.rng = new MersenneTwister(seed);
		this.hopLimit = aHopLimit;
		this.bytesPerHop = bytesHop;
	}
	
	/**
	 * Get an ant packet.
	 */
	public Object getContent() {
		String source = ControlPlane.getSourceNode(rng);
		String target = ControlPlane.getTargetNode(rng,source);
		AntPacket ant = new AntPacket(source,target,hopLimit,bytesPerHop);
		return ant;
	}

	/**
	 * Returns the type to for the event generator.
	 */
	public Type getType() {
		return Event.Type.PACKET_ARRIVAL;
	}

	/**
	 * Set the traffic distribution. 
	 */
	public void setDistribution(Distribution distrib) {
		this.distribution = distrib;		
	}

}
