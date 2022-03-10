/*
 * Created on 24/March/2004.
 *
 */
package distribution;

/**
 * Defines the methods that all queue distributions must have.
 * It is composed by a arrival and a servicing process. 
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0 
 */
public interface QueueDistribution {
	
	/**
	 * Returns the service (duration) time of the next request.
	 * @return The service (duration) time of the next request.
	 */
	public double getServiceTime();

	/**
	 * Returns the interarrival time between this and the next request.
	 * @return The interarrival time between this and the next request.
	 */	
	public double getInterarrivalTime();

}
