/*
 * Created on Nov 14, 2005.
 */
package distribution;

/**
 * This class represents a constant distribution.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Constant implements Distribution, java.io.Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The constant service rate. */
	double serviceRate;
	/** The constant inter-arrival rate. */
	double arrivalRate;
	
	/**
	 * Creates a new Constant object.
	 * @param aServiceRate The constant service rate.
	 * @param aArrivalRate The constant inter-arrival rate.
	 */
	public Constant(double aServiceRate,double aArrivalRate){
		this.serviceRate = aServiceRate;
		this.arrivalRate = aArrivalRate;
	}
	
	/**
	 * Returns the service (duration) time of the next request.
	 * @return The service (duration) time of the next request.
	 */
	public double getServiceTime() {
		return 1.0 / serviceRate;
	}

	/**
	 * Returns the interarrival time between this and the next request.
	 * @return The interarrival time between this and the next request.
	 */	
	public double getInterarrivalTime() {
		return 1.0 / arrivalRate;
	}
	
	public double getMeanServiceTime() {
		return this.getServiceTime();
	}
	
	public double getMeanInterarrivalTime() {
		return this.getInterarrivalTime();
	}


}
