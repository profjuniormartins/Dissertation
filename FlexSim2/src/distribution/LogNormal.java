/*
 * Created on 2013-04-03 by Pavani. 
 */
package distribution;

import random.MersenneTwister;

/**
 * Generates a Log Normal uniform distribution of random numbers.
 * 
 * @author Pavani
 * @version 1.0
 */
public class LogNormal implements QueueDistribution {
	/** Random number generator. */
	MersenneTwister random;
	/** The mean of the distribution. */
	double mean;
	/** The standard deviation of the distribution. */
	double std_dev;
	/** Flag indicating if the second generated normal random number has already been used. */
	boolean lastUsed;
	/** Last normal random generated. */
	double lastNormal;
	
	/**
	 * Creates a new LogNormal object.
	 * @param mean The mean of the distribution.
	 * @param std_dev The standard deviation of the distribution.
	 * @param seed The random seed.
	 */
	public LogNormal(double mean, double std_dev, long seed) {
		this.mean = mean;
		this.std_dev = std_dev;
		random = new MersenneTwister(seed);
		this.lastUsed = true;
	}
	
	/** 
	 * Returns a random number with log normal distribution.
	 * @see http://en.wikipedia.org/wiki/Log-normal_distribution
	 */
	@Override
	public double getInterarrivalTime() {
		//random variable drawn from the normal distribution with 0 mean and 1 standard deviation
		double normal = this.getBoxMullerTransformation();
		//use the relation between log-normal and normal distributions to generate the log-normal random number
		return Math.exp(mean + std_dev*normal);
	}

	/** 
	 * Returns a random number with log normal distribution.
	 * @see http://en.wikipedia.org/wiki/Log-normal_distribution
	 */
	@Override
	public double getServiceTime() {
		return this.getInterarrivalTime();
	}
	
	/**
	 * Returns a normal distributed random number using the polar form of the Box-Muller transformation, with 0 mean and 1 standard deviation.
	 * @return A normal distributed random number using the polar form of the Box-Muller transformation, with 0 mean and 1 standard deviation.
	 * @see http://www.design.caltech.edu/erik/Misc/Gaussian.html
	 * @see http://en.wikipedia.org/wiki/Box%E2%80%93Muller_transform
	 */
	private double getBoxMullerTransformation() {
		if (!lastUsed) { // use value from previous call.
			lastUsed = true;
			return lastNormal;
		} else {
			double u, v; // random numbers independent and uniformly distributed in the closed interval [-1, +1]
			double s; // s = u^2 + v^2, but has to be in the open interval (0, 1). 
			do {
				u = (2.0 * random.nextDouble()) - 1.0;
				v = (2.0 * random.nextDouble()) - 1.0;
				s = (u * u) + (v * v);
			} while (s >= 1.0 || s==0.0);
			double w = Math.sqrt((-2.0 * Math.log(s)) / s); //the polar transform
			lastNormal = (v * w);
			lastUsed = false;
			return (u * w);
		}
	}
}
