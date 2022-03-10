/*
 * Created on 2013-03-26 by Pavani. 
 */
package distribution;

import random.MersenneTwister;

/**
 * Class for obtaining a negative exponential distribution.
 * 
 * @author Pavani
 */
public class Exponential implements QueueDistribution {
	/** Random number generator. */
	MersenneTwister random;
	/** The rate parameter. */
	double rate;
	
	/**
	 * Creates a new Negative Exponential distribution. 
	 * @param rate The rate parameter.
	 * @param seed The random seed.
	 */
	public Exponential(double rate, long seed) {
		this.rate = rate;
		random = new MersenneTwister(seed);
	}
	
	/**
	 * Returns a random number with negative exponential distribution.
	 * @return A random number with negative exponential distribution.
	 */
	@Override
	public double getInterarrivalTime() {
		return -(Math.log(1.0-random.nextDouble())/rate);
	}
	
	/**
	 * Returns a random number with negative exponential distribution.
	 * @return A random number with negative exponential distribution.
	 */
	@Override
	public double getServiceTime() {
		return this.getInterarrivalTime();
	}
}
