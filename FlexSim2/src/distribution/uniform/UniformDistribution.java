/*
 * Created on 2013-04-17 by Pavani. 
 */
package distribution.uniform;

/**
 * Represents a uniform distribution of randomly generated numbers.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public interface UniformDistribution<T extends Number> {
	
	/**
	 * Returns a uniformly distributed random number.
	 * @return A uniformly distributed random number.
	 */
	public T getNumber();
	
}
