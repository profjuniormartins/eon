/*
 * Created on 06/12/2005.
 */
package rwa;

import rwa.crankback.obgp.OBGPControlPlane;
import main.ControlPlane;
import distribution.Distribution;
import event.Event;
import event.EventSubscriber;
import event.Event.Type;

/**
 * This class represents the generation of lightpath requests 
 * for each node of the network.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class InterLightpathTraffic implements EventSubscriber {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The distribution for generating the ligthpaths. */
	Distribution distribution;
    /** The maximum number of tries for establishing a lightpath. */
	int maxTries;
	
	/**
	 * Creates a new LightpathTraffic object.
	 * @param aMaxTries The maximum number of tries.
	 */
	public InterLightpathTraffic(int aMaxTries) {
		this.maxTries = aMaxTries;
	}
	
	/**
	 * Returns a lightpath request object.
	 */
	public Object getContent() {
		String source = ControlPlane.getSourceNode();
		String target = ControlPlane.getTargetNode(source);
		//mudanca rapida para garantir que o target nao seja no mesmo dominio
		
		while(getDomain(target).equals(getDomain(source))) {
			target = ControlPlane.getTargetNode(source);
		}
		
		//System.out.println("Source "   + source + " \t Target "  + target);
		
		double duration = distribution.getServiceTime();
		return new LightpathRequest(source,target, duration, maxTries);
	}

	/**
	 * Returns a lightpath request event arrival.
	 */
	public Type getType() {
		return Event.Type.LIGHTPATH_REQUEST;
	}

	
	public String getDomain(String node) {
		return node.split(":")[0];
	}
	
	/**
	 * Sets the appropriate distribution to this object.
	 * @param distrib The appropriate distribution.
	 */
	public void setDistribution(Distribution distrib) {
		this.distribution = distrib;		
	}

}
