/*
 * Created on 21/02/2008.
 */
package rwa.crankback.antnet;

import main.ControlPlane;

/**
 * This class represents the generation of ant for each node.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class AntTraffic extends antnet.AntTraffic {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new AntTraffic object.
	 * @param aHopLimit The maximum number of hops allowed for this packet.  
	 * @param bytesHop The number of bytes added at each hop.
	 * @param seed The random seed.
	 */
	public AntTraffic(int aHopLimit, int bytesHop, long seed) {
		super(aHopLimit,bytesHop,seed);
	}
	
	/**
	 * Get an ant packet.
	 */
	public Object getContent() {
		String source = ControlPlane.getSourceNode(rng);
		String target = ControlPlane.getTargetNode(rng,source);
		Ant ant = new Ant(source,target,hopLimit,bytesPerHop);
		return ant;
	}

}
