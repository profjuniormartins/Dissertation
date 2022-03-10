/*
 * Created on 2013-04-10 by Pavani. 
 */
package distribution;

/**
 * Dummy distribution, in which is possible to set the service time and interarrival time with arbitrary values.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Dummy implements QueueDistribution {
	/** The service time. */
	double serviceTime;
	/** The interarrival time. */
	double interArrivalTime;
	
	/**
	 * Creates a new Dummy object.
	 */
	public Dummy() {
		this.serviceTime = 0.0;
		this.interArrivalTime = 0.0;
	}
	
	/**
	 * Sets the service time with a new time.
	 * @param time The new service time.
	 */
	public void setServiceTime(double time) {
		this.serviceTime = time;
	}

	/**
	 * Sets the interarrival time with a new time.
	 * @param time The new interarrival time.
	 */
	public void setInterarrivalTime(double time) {
		this.interArrivalTime = time;
	}
	
	@Override
	public double getServiceTime() {
		return this.serviceTime;
	}

	@Override
	public double getInterarrivalTime() {
		return this.interArrivalTime;
	}

}
