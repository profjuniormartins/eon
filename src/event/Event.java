/*
 * Created on Sep 14, 2005.
 */

package event;

import java.io.Serializable;

/**
* This class represents an event in an event-driven simulation. 
*
* @author Gustavo S. Pavani
* @version 1.0
*
*/
@SuppressWarnings("unchecked")
public class Event implements Comparable, Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** Specifies the type of the event. */
	public enum Type{
		/** Ignore event. */ IGNORE,
		/** Multiple events. */ MULTIPLE,
		/** Terminate simulation. */ TERMINATE,
		/** Serialize the simulation to the disk. */ SERIALIZE,
		/** Packet arriving on a node. */ PACKET_ARRIVAL,
		/** Packet dropped by a node. */ PACKET_DROP,
		/** Packet arrived at destination node. */ PACKET_ROUTED,
		/** Ant killed for some reason. */ ANT_KILLED,
		/** Ant did all its round-trip. */ ANT_ROUTED,
		/** Resource request. */ RESOURCE_REQUEST,
		/** Job running. */ JOB_RUNNING,
		/** Problem with a job. */ JOB_PROBLEM,
		/** Job finished. */ JOB_FINISHED,
		/** Lightpath request. */ LIGHTPATH_REQUEST,
		/** Ligthpath establishment confirmation. */ LIGHTPATH_ESTABLISHED,
		/** Ligthpath establishment problem. */ LIGHTPATH_PROBLEM,
		/** Ligthpath teardown. */ LIGHTPATH_TEARDOWN,
		/** Lightpath removing confirmation. */ LIGHTPATH_REMOVED,
		/** Failure on a link. */ FAILURE_LINK,
		/** Failure on a node. */ FAILURE_NODE,
		/** Check connection constraints. */ CHECK_CONSTRAINTS,
		/** Generic request. */ REQUEST,
		/** Generic tear-down. */ TEARDOWN,
	}
	
	/** The time when the event takes place. */
	double timeStamp=0;
	/** The time when the event is generated for the first time. */
	double initialTimeStamp;
	/** The type of this event. */
	Type type;
	/** The content associated with this event, such as packet arrival or link failure.*/
	Object content;
	
	/**
	 * Creates a new Event object.
	 * 
	 * @param time The time when the event takes place.
	 * @param aType The type of this event.
	 * @param content The content associated with this event.
	 */
	public Event(double time, Type aType, Object aContent) {
		this.timeStamp = time;
		this.initialTimeStamp = time;
		this.type = aType;
		this.content = aContent;		
	}
	
	/**
	 * Compares this Event object to another one and returns a number indicating the natural order
	 * for scheduling these events.
	 * 
	 * @param obj Another Event object.
	 * @return -1, if this event has to be served first;
	 *         +1, if this event has to be served after the compared event;
	 *          0, if both events have to be served at the same time. 
	 */
	public int compareTo(Object obj) {
		if (this.timeStamp - ((Event)obj).getTimeStamp() < 0.0) {
			return -1;
		} else if (this.timeStamp - ((Event)obj).getTimeStamp() > 0.0) {
			return +1;
		} else {
			return 0;
		}
	}
	
	/**
	 * Returns the type of this event.
	 * @return The type of this event.
	 */
	public Type getType() {
		return this.type;
	}
	
	/**
	 * Returns the time stamp of this event, i.e., when this event have to take place. 
	 * @return The time stamp of this event.
	 */
	public double getTimeStamp() {
		return this.timeStamp;
	}	
	
	/**
	 * Returns the time stamp when the event was generated.
	 * @return The time stamp when the event was generated.
	 */
	public double getInitialTimeStamp() {
		return this.initialTimeStamp;
	}
	
	/**
	 * Set the new time stamp for this event.
	 * @param stamp The new time stamp for this event.
	 */
	public void setTimeStamp(double stamp) {
		this.timeStamp = stamp;
	}
	
	/**
	 * Set the type of this event.
	 * @param aType The new type of this event.
	 */
	public void setType(Type aType) {
		this.type = aType;
	}
	
	/**
	 * Returns the content associated with this event.
	 * @return The content associated with this event.
	 */
	public Object getContent() {
		return this.content;
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Type: ");
		buf.append(type);
		buf.append(", time stamp: ");
		buf.append(timeStamp);
		buf.append(", content [");
		buf.append(content);
		buf.append("]");
		return buf.toString();
	}

}
