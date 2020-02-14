/*
 * Created on Sep 14, 2005.
 */
package event;

import java.io.Serializable;

import distribution.*;

/**
 * This class is responsible for generating the events following a specified 
 * probability distribution.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class EventGenerator implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The instant where the event generation must start. */
	public double startTime;
	/** The probabilistic distribution of this event generator. */
	public Distribution distribution;
	/** The subscriber associated to this event generator. */
	public EventSubscriber subscriber;
	/** The name of the subscriber, which is used to identify this generator. */
	private String subscriberName;
	
	/**
	 * Creates a new EventGenerator object.
	 * @param probDistribution The probabilistic distribution associated to this generator.
	 */
	public EventGenerator(Distribution probDistribution) {
		this.distribution = probDistribution;
		this.startTime = 0;
	}
	
	/**
	 * Creates a new EventGenerator object.
	 * @param probDistribution The probabilistic distribution associated to this generator.
	 * @param start The start time of this distribution.
	 */
	public EventGenerator(Distribution probDistribution, double start) {
		this.distribution = probDistribution;
		this.startTime = start;
	}
	
	/**
	 * Returns the time when this event generator must start.
	 * @return The time when this event generator must start.
	 */
	public double getStartTime() {
		return this.startTime;
	}
	
	/**
	 * Subscribes a event listener to this event generator and 
	 * associate the generator's distribution to this subscriber.
	 * @param aSubscriber The subscriber of this event generator.
	 */
	public void subscribe(EventSubscriber aSubscriber) {
		this.subscriber = aSubscriber;
		this.subscriberName=subscriber.getClass().getName();
		subscriber.setDistribution(distribution);
	}
	
	/**
	 * Gets the name of the subscriber to this generator.
	 * @return The name of the subscriber to this generator.
	 */
	public String getSubscriberName() {
		return this.subscriberName;
	}
	/**
	 * Creates a new event following the associated distribution.
	 * @return Creates a new event.
	 * @throws Exception When there is no subscriber associated with this generator.
	 */
	public Event create(double timeStamp) throws Exception {
		if (subscriber == null) {
			throw new Exception("No subscriber associated to this generator with "+distribution.getClass().getCanonicalName());
		}
		Event event = new Event(timeStamp, subscriber.getType(), subscriber.getContent());
		return event;
	}
	
	/**
	 * Returns the interarival time between two sucessive events.
	 * @return The interarival time between two sucessive events.
	 */
	public double getNextEventTime() {
		return distribution.getInterarrivalTime();
	}
}
