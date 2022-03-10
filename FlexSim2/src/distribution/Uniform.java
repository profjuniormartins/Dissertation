/*
 * Created on 2013-04-10 by Pavani. 
 */
package distribution;

import random.MersenneTwister;

/**
 * Generates a random uniform distribution for x in the range [x0,x1] and for y in the range [y0,y1].
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Uniform implements QueueDistribution {
	/** Random number generator. */
	MersenneTwister random;
	/** The minimum value of x. */
	double x0;
	/** The maximum value of x. */
	double x1;
	/** The minimum value of y. */
	double y0;
	/** The maximum value of y. */
	double y1;

	
	/**
	 * Creates a new Uniform object.
	 * @param x0 The minimum value of x.
	 * @param x1 The maximum value of x.
	 * @param y0 The minimum value of y.
	 * @param y1 The maximum value of y.
	 * @param seed The random seed.
	 */
	public Uniform(double x0, double x1, double y0, double y1, long seed) {
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
		random = new MersenneTwister(seed);		
	}

	/**
	 * Returns a random number in the range [x0,x1]. The density is not well preserved if (x1-x0)>>1.
	 */
	@Override
	public double getServiceTime() {		
		return (random.nextDouble()*(x1-x0)) + x0;
	}
	
	/**
	 * Returns a random number in the range [y0,y1]. The density is not well preserved if (y1-y0)>>1.
	 */
	@Override
	public double getInterarrivalTime() {
		return (random.nextDouble()*(y1-y0)) + y0;
	}

}
