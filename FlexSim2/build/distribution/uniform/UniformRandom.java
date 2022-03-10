/*
 * Created on 2013-04-17 by Pavani. 
 */
package distribution.uniform;

import random.MersenneTwister;

/**
 * Generates a random uniform distribution for x in the range [x0,x1].
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class UniformRandom implements UniformDistribution<Double> {
	/** Random number generator. */
	MersenneTwister random;
	/** The minimum value of x. */
	double x0;
	/** The maximum value of x. */
	double x1;
	
	/**
	 * Creates a new UniformRandom object.
	 * @param x0 The minimum value of x.
	 * @param x1 The maximum value of x.
	 * @param y0 The minimum value of y.
	 * @param y1 The maximum value of y.
	 * @param seed The random seed.
	 */
	public UniformRandom(double x0, double x1, long seed) {
		this.x0 = x0;
		this.x1 = x1;
		random = new MersenneTwister(seed);		
	}
	
	/**
	 * Returns a random number in the range [x0,x1]. The density is not well preserved if (x1-x0)>>1.
	 */
	@Override
	public Double getNumber() {
		return (random.nextDouble()*(x1-x0)) + x0;
	}

}
