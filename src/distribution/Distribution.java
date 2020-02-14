/*
 * Created on 24/March/2004.
 *
 */
package distribution;

/**
 * Defines the methods that all distributions must have.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0 
 */
public interface Distribution {
	
	/**
	 * Returns the service (duration) time of the next request.
	 * @return The service (duration) time of the next request.
	 */
	public double getServiceTime();

	/**
	 * Returns the interarrival time between this and the next request.
	 * @return The interarrival time between this and the next request.
	 */	
	public double getInterarrivalTime();

	public double getMeanServiceTime();
	
	public double getMeanInterarrivalTime();
}
