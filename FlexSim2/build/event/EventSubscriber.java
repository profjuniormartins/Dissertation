/*
 * Created on 2013-03-06 by Pavani. 
 */
package event;

import distribution.QueueDistribution;;

/**
 * If a class is requested to generate an event by the Scheduler,
 * it must implement this interface.
 *
 * @author Gustavo S. Pavani
 * @version 1.1
 *
 */
public interface EventSubscriber extends java.io.Serializable {

	/**
	 * Returns the content associated with the event requested by the scheduler.
	 */
	public Object getContent(); 
	
	/** 
	 * Returns the type associated with the event requested by the scheduler.
	 */
	public Event.Type getType();

	/**
	 * Sets the distribution associated with this subscriber.
	 * @param distrib The distribution of this subscriber.
	 */
	public void setDistribution (QueueDistribution distrib);
} 
