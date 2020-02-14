/*
 * Created on Sep 20, 2005.
 */
package event;

import java.io.Serializable;

import distribution.*;

/**
 * If a class is requested to generate an event by the Scheduler,
 * it must implement this interface.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public interface EventSubscriber extends Serializable {
	/**
	 * Returns the content associated with the event requested by the scheduler.
	 */
	public Object getContent(); 
	
	/** 
	 * Returns the type associated with the event requested by the scheduler.
	 */
	public Event.Type getType();

	/**
	 * Sets the distribution associated with this subscriber.
	 * 
	 */
	public void setDistribution (Distribution distrib);
}
