/*
 * Created on 2013-03-06 by Pavani. 
 */
package event;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.ArrayList;

/**
 * Schedules the events and manages the event generation.
 *
 * @author Gustavo S. Pavani
 * @version 1.1
 *
 */
public class Scheduler {
	/** The event list associated with the scheduler. */
	public EventList eventList;
	/** The event generator priority list. */
	public PriorityQueue<Listener> generatorList;
	/** Statistics for generation of each subscriber/generator. */
	public HashMap<String,Long> statistics;
	/** Table of listeners. */
	public HashMap<String,ArrayList<Listener>> listenerTable;
	/** The time of the last event. */
	protected double lastTimeStamp;
	
	/**
	 * Creates a new Scheduler object.
	 */
	public Scheduler() {
		//Creates the new classes of list
		eventList = new EventList();
		generatorList = new PriorityQueue<Listener>();
		statistics = new HashMap<String,Long>();
		listenerTable = new HashMap<String,ArrayList<Listener>>();
	}
		
	/**
	 * Add a event generator to this scheduler.
	 * @param generator An event generator.
	 */
	public void addGenerator(EventGenerator generator) {
		//Create new listener object
		Listener listener = new Listener(generator);
		//Add it to the generator list
		generatorList.add(listener);
		//Get the subscriber name
		String subscriberName = generator.getSubscriberName();
		//Add the subscriber name to the statistics counting
		statistics.put(subscriberName,new Long(0));
		//Add the listener to the table
		ArrayList<Listener> vec = listenerTable.get(subscriberName);
		if (vec != null) { //there is already a listener with the same subscriber name
			vec.add(listener);
		} else { //Create the vector and add the listener to it
			vec = new ArrayList<Listener>();
			vec.add(listener);
			listenerTable.put(subscriberName,vec);
		}
	}
	
	/**
	 * Removes all generators related to the specified subscriber name.
	 * 
	 * @param subscriberName The name of the subscriber to be removed.
	 */
	public void removeGenerator(String subscriberName) {
		ArrayList<Listener> vec = listenerTable.get(subscriberName);
		for (Listener listener : vec) {
			generatorList.remove(listener);
		}
	}
	
	/**
	 * Gives back a processed event to the scheduler.
	 * @param event A processed event to be returned to the scheduler.
	 */
	public void insertEvent(Event event) {
		eventList.addEvent(event);
	}
	
	/**
	 * Get the next event, selecting it from the queue of events or a event generator.
	 * @return The next event.
	 * @throws Exception When no event generator is associated to this scheduler.
	 */
	public Event step() throws Exception {
		double timeGen = Double.MAX_VALUE, timeQueue = Double.MAX_VALUE; //time of the next event in the generator list and queue of events
		Event event=null; //the event to be returned
		//Get the time of the next event in the list of generators
		if (generatorList.size() > 0)
			timeGen = generatorList.peek().getTime();
		//else
		//	throw new Exception("There is no event generator associated to the scheduler!");
		//Get the time of the next event in the list of queued events
		if (eventList.size() > 0) 
			timeQueue = eventList.getNextEvent().getTimeStamp();
		//Now compare these times and decides what kind of event to use.
		if ((timeGen <= timeQueue) && (timeGen != Double.MAX_VALUE)) { //generate event
			//Get the first element of the list
			Listener listener = generatorList.remove();
			//Get the generator
			EventGenerator generator = listener.getGenerator();
			//Increment the counter
			String subscriberName = generator.getSubscriberName();
			Long counter = statistics.get(subscriberName);
			long newCounter = counter.longValue() + 1;
			statistics.put(subscriberName,new Long(newCounter));
			//Generate the event
			try {
				event = generator.create(timeGen);
			} catch (Exception e) {e.printStackTrace();}
			//Get the interarrival time
			double iat = generator.getNextEventTime();
			if ((iat != Double.POSITIVE_INFINITY)) {
				//Update the time of the next event and reinsert it in the list of generators
				listener.setTime(timeGen+iat);
				generatorList.add(listener);
			} 
			//Update the time stamp of the last event
			lastTimeStamp = timeGen;
		} else if (timeQueue != Double.MAX_VALUE){ //get the first element of the list (priority queue).
			event = eventList.pollNextEvent();
			//Update the time stamp of the last event
			lastTimeStamp = timeQueue;
		} else { //Terminate - no more events in the queue or event generators
			event = new Event(lastTimeStamp, Event.Type.TERMINATE, "Queue and generator list empty!");
		}
		return event;
	}
	
	/**
	 * Returns the number of times each event generator is executed.
	 * The key is the name of the subscriber to the event generation
	 * and the value is a Long containing the number of times that 
	 * the associated generator is executed.
	 */
	public HashMap<String,Long> getStatistics() {
		return statistics;
	}
	
	/**
	 * Returns the number of times that the specified subscriber
	 * is executed, i.e., the generator creates a new event.
	 * @param subscriberName The name of the subscriber.
	 * @return The number of times that the specified subscriber
	 * is executed.
	 */
	public long getCounter(String subscriberName) {
		return statistics.get(subscriberName).longValue();
	}
	
	/**
	 * Returns a String representation of this object.
	 * No ordering can be assumed for the event list.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Event list:");
		buf.append(eventList.toString());
		buf.append("\n Statistics summary:\n");
		buf.append(statistics.toString());
		return buf.toString();
	}
	
	/**
	 * Encapsulates the information relative to each event generator.
	 * 
	 * @author Gustavo S. Pavani
	 * @version 1.1
	 */
	class Listener implements Comparable<Listener>{
		/** The time of the next event. */
		double nextEventTime;
		/** The event generator. */
		EventGenerator generator;
		
		/**
		 * Creates a new Listener object with nextEventTime equal to the generator start time.
		 * @param aEventGenerator The specified event generator.
		 */
		Listener(EventGenerator aEventGenerator) {
			this.generator = aEventGenerator;
			nextEventTime = generator.getStartTime();
		}
		
		/**
		 * Compares this Listener object to another one and returns a number indicating the natural order
		 * for scheduling these events.
		 * 
		 * @param obj Another Listener object.
		 * @return -1, if this event has to be served first;
		 *         +1, if this event has to be served after the compared event;
		 *          0, if both events have to be served at the same time. 
		 */
		public int compareTo(Listener obj) {
			if (this.nextEventTime - obj.getTime() < 0.0) {
				return -1;
			} else if (this.nextEventTime - obj.getTime() > 0.0) {
				return +1;
			} else {
				return 0;
			}
		}
		
		/**
		 * Returns the time of the next event.
		 * @return The time of the next event.
		 */
		public double getTime() {
			return this.nextEventTime;
		}

		/**
		 * Set the time of the next event.
		 * @param time The new time of the next event.
		 */
		public void setTime(double time) {
			this.nextEventTime = time;
		}

		/**
		 * Gets the event generator associated to this listener object.
		 * @return The event generator associated to this listener object.
		 */
		public EventGenerator getGenerator() {
			return this.generator;
		}

	}

}
