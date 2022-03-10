/*
 * Created on 2013-03-26 by Pavani. 
 */
package distribution;

import random.MersenneTwister;

/**
 * Class for obtaining a power-law distribution, where P(x) = x^n for x in [x0, x1].
 * 
 * @see http://mathworld.wolfram.com/RandomNumber.html
 * @author Pavani
 * @version 1.0
 */
public class PowerLaw implements QueueDistribution {
	/** Random number generator. */
	MersenneTwister random;
	/** The distribution power. */
	double power;
	/** The minimum value of x. */
	double x0;
	/** The maximum value of x. */
	double x1;

	/**
	 * Creates a new power-law distribution from an uniform distribution, where P(x) = x^n for x in [x0, x1].
	 * @param x0 The minimum value of x.
	 * @param x1 The maximum value of x.
	 * @param n The distribution power.
	 * @param seed The random seed.
	 */
	public PowerLaw(double x0, double x1, double n, long seed) {
		this.x0 = x0;
		this.x1 = x1;
		this.power = n;
		random = new MersenneTwister(seed);
	}
	
	/**
	 * Returns a random number from the interval [x0,x1] from the power-law distribution.
	 * @return A random number from the interval [x0,x1] from the power-law distribution.
	 */
	@Override
	public double getInterarrivalTime() {
		//return [(x1^(n+1) - x0^(n+1))*y + x0^(n+1)]^(1/(n+1))
		double y = random.nextDouble();
		return Math.pow((Math.pow(x1,(power+1))-Math.pow(x0,(power+1)))*y + Math.pow(x0,(power+1)), (1/(power+1)));
	}

	/**
	 * Returns a random number from the interval [x0,x1] from the power-law distribution.
	 * @return A random number from the interval [x0,x1] from the power-law distribution.
	 */
	@Override
	public double getServiceTime() {
		return this.getInterarrivalTime();
	}
}
