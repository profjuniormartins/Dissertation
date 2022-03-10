/*
 * Created on 2013-05-28 by Pavani. 
 */
package net;

/**
 * This class encapsulates information about statistics.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Counter {
	/** The number of times. */
	protected long counter;
	/** The average quantity. */
	protected double average;
	/** The variance of the average. */
	protected double variance;
	
	/**
	 * Creates a new Counter object.
	 */
	public Counter() {
		//Initialize the variables.
		this.counter = 0L;
		this.average = 0.0;
		this.variance = 0.0;
	}
	
	/**
	 * Increments the counter and updates the moving average value of the quantity.
	 * @param value The value to be added to the average.
	 */
	public void increment(double value) {
		//Increment counter
		counter ++;
		//Save the value of the previous average value for calculating the variance
		double prevAverage = average;
		//Add the value to the moving average
		average = this.movingAverage(value);
		//Now, update the variance
		variance = this.movingVariance(prevAverage);
	}
	
	/**
	 * Adds another Counter object to this object.
	 * @param another The added Counter object.
	 */
	public void add(Counter another) {
		//Get the another values
		long anotherCounter = another.counter;
		double anotherAverage = another.average;
		double anotherVariance = another.variance;
		//Weight the number of counters to make the new values of average and variance
		this.average = (((double)this.average*this.counter) + ((double)anotherCounter*anotherAverage)) / ((double)(this.counter + anotherCounter));
		this.variance = (((double)this.variance*this.counter) + ((double)anotherCounter*anotherVariance)) / ((double)(this.counter + anotherCounter));
		//Update the counter
		this.counter = this.counter + anotherCounter;
	}
	
	/**
	 * Returns the number of times this counter was incremented.
	 * @return The number of times this counter was incremented.
	 */
	public long getCounter() {
		return this.counter;
	}
	
	/**
	 * Returns the average quantity of this counter.
	 * @return The average quantity of this counter.
	 */
	public double getAverage() {
		return this.average;
	}
	
	/**
	 * Returns the unbiased sample variance of the quantity of this counter.
	 * @return The unbiased sample variance of the quantity of this counter.
	 */
	public double getVariance() {
		return (this.variance / (double)(counter - 1));
	}
	
	/**
	 * Returns the unbiased sample standard deviation of the quantity of this counter.
	 * @return The unbiased sample standard deviation of the quantity of this counter.
	 */
	public double getStandardDeviation() {
		return Math.sqrt(this.getVariance());
	}
	
	/**
	 * Returns the current moving average.
	 * @param currValue The current value.
	 * @return The current moving average.
	 */
	protected double movingAverage(double value) {
		return average + (value - average)/((double) counter);
	}
	
	/**
	 * Returns the current moving n variance.
	 * @param oldAverage The previous average value. It has to be calculated before this calculation.
	 * @return The current moving n variance.
	 */
	protected double movingVariance(double oldAverage) {
		return variance + (double)(counter * (counter - 1)) * ((average - oldAverage)*(average - oldAverage)); 
	}

	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Counter: ");
		builder.append(counter);
		builder.append(", average: ");
		builder.append(average);
		builder.append(", variance: ");
		builder.append(variance);
		return builder.toString();
	}
}
