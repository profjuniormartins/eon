/*
 * Created on 22/March/2004.
 *
 */
package distribution;

import java.io.Serializable;

import random.*;

/**
 * Generates a Poissonian distribution with interarrival times exponentially 
 * apart.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0 
 */
public class Poissonian implements Distribution, Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** Average service rate. */
	public double mu;
	/** Average interarrival rate. */	 
	public double lambda;
	/** Random number generator. */
	public MersenneTwister random;
	
	/**
	 * Gets a new Poissonian traffic source with default random seed.
	 * @param serviceRate The average service rate.
	 * @param interarrivalRate The average interarrival rate.
	 */
	public Poissonian(double serviceRate, double interarrivalRate) {
		mu = serviceRate;
		lambda = interarrivalRate;
		random = new MersenneTwister();
	}

	/**
	 * Gets a new Poissonian traffic source with specified random seed.
	 * @param serviceRate The average service rate.
	 * @param interarrivalRate The average interarrival rate.
	 * @param seed The pseudo-random generator seed.
	 */
	public Poissonian(double serviceRate, double interarrivalRate, long seed) {
		mu = serviceRate;
		lambda = interarrivalRate;
		random = new MersenneTwister(seed);
	}
	
	/**
	 * Returns the service (duration) time of the next request.
	 * @return The service (duration) time of the next request.
	 */
	public double getServiceTime() {
		return -(Math.log(1.0-random.nextDouble())/mu);
	}
	
	/**
	 * Returns the interarrival time between this and the next request.
	 * @return The interarrival time between this and the next request.
	 */
	public double getInterarrivalTime() {
		return -(Math.log(1.0-random.nextDouble())/lambda);	
	}
	
	public double getMeanServiceTime() {
		return 1.0 / mu;
	}
	
	public double getMeanInterarrivalTime() {
		return 1.0 / lambda;
	}
	
	/**
	 * @return The interarrival rate.
	 */
	public double getLambda() {
		return lambda;
	}

	/**
	 * @return The service rate.
	 */
	public double getMu() {
		return mu;
	}

	/**
	 * Sets the interarrival rate.
	 * @param d The new interarrival rate.
	 */
	public void setLambda(double d) {
		lambda = d;
	}
	
	/**
	 * Sets the service rate.
	 * @param d The new service rate.
	 */
	public void setMu(double d) {
		mu = d;
	}

}
