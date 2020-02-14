/*
 * Created on 21/02/2008.
 */
package rwa.crankback.antnetInter;

import main.ControlPlane;

/**
 * This class represents the generation of ant for each node.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class InterAntTraffic extends antnet.AntTraffic {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new InterAntTraffic object.
	 * @param aHopLimit The maximum number of hops allowed for this packet.  
	 * @param bytesHop The number of bytes added at each hop.
	 * @param seed The random seed.
	 */
	public InterAntTraffic(int aHopLimit, int bytesHop, long seed) {
		super(aHopLimit,bytesHop,seed);
	}
	
	/**
	 * Get an ant packet.
	 */
	public Object getContent() {
		  
		String source = AntNetCrankInterControlPlane.getInterSourceNode(rng); 
	//	System.out.println("Gerado source = " + source);
	
		String target = AntNetCrankInterControlPlane.getInterTargetNode(rng,source);
	//	System.out.println("Gerado target = " + target);
	//	System.out.println("Gerada Ant de " + source + " para " + target);		
		Ant ant = new Ant(source,target,hopLimit,bytesPerHop);
		return ant;
	}
	
	
	
	
	public String getDomain(String node) {
		return node.split(":")[0];
	}
	
	

}
