/*
 * Created on 12/06/2006.
 */
package distribution;

import java.io.Serializable;

import random.MersenneTwister;

/**
 * Generates a self-similar traffic, with Poisson arrivals 
 * and bounded-Pareto duration.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */

public class SelfSimilar implements Distribution, Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** Average interarrival rate. */	 
	protected double lambda;
	/** The shape parameter. */
	protected double alpha;
	/** The lower bound of the distribution. */
	protected double k;
	/** The uppper bound of the distribution. */
	protected double p;
	/** Random number generator. */
	public MersenneTwister random;
	
	/**
	 * Creates a new SelfSimilar object.
	 * @param shape The shape parameter.
	 * @param lowerBound The lower bound of the distribution.
	 * @param upperBound The uppper bound of the distribution.
	 */
	public SelfSimilar(double interarrivalRate, double shape, double lowerBound, double upperBound) {
		this.lambda = interarrivalRate;
		this.alpha = shape;
		this.k = lowerBound;
		this.p = upperBound;
		random = new MersenneTwister();	
		//System.out.println("Average packet length:"+ ((this.getMeanServiceTime()*1.25E9)));
	}
	
	/**
	 * Creates a new SelfSimilar object.
	 * @param shape The shape parameter.
	 * @param lowerBound The lower bound of the distribution.
	 * @param upperBound The uppper bound of the distribution.
	 * @param seed The pseudo-random generator seed.
	 */
	public SelfSimilar(double interarrivalRate,double shape, double lowerBound, double upperBound, long seed) {
		this.lambda = interarrivalRate;
		this.alpha = shape;
		this.k = lowerBound;
		this.p = upperBound;
		random = new MersenneTwister(seed);		
	}
		
	/**
	 * Returns the service (duration) time of the next request.
	 * @return The service (duration) time of the next request.
	 */
	public double getServiceTime() {
		return k / Math.pow((1.0 + random.nextDouble()*(Math.pow(k/p,alpha) - 1.0)),1.0/alpha);
	}

	/**
	 * Returns the interarrival time between this and the next request.
	 * @return The interarrival time between this and the next request.
	 */
	public double getInterarrivalTime() {
		return -(Math.log(1.0-random.nextDouble())/lambda);			
	}

	/**
	 * Gets the mean service time.
	 */
	public double getMeanServiceTime() {
		return (Math.pow(k,alpha) / (1.0 - Math.pow(k/p,alpha)))*(alpha / (alpha - 1.0)) * ((1.0/Math.pow(k,alpha-1.0)) - (1.0/Math.pow(p,alpha-1.0)));
		
	}

	/**
	 * Gets the mean inter-arrival time.
	 */
	public double getMeanInterarrivalTime() {
		return 1.0 / lambda;
	}

}
