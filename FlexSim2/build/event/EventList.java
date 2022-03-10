/*
 * Created on 2013-03-06 by Pavani. 
 */
package event;

import java.util.PriorityQueue;

/**
 * A list for storing the events to be served. For speeding-up the operations of
 * removing and adding events to this list, this implementation uses a priority queue (heap)
 * internally. If multiple elements are tied for least value, the head is one of those elements -- ties are broken arbitrarily.
 *
 * @author Gustavo S. Pavani
 * @version 1.1
 *
 */
public class EventList {
	/** The queue for storing all events waiting for processing. */
	PriorityQueue<Event> queue;
	
	/**
	 * Creates a new EventList object.
	 */
	public EventList() {
		queue = new PriorityQueue<Event>();
	}
	
	/**
	 * Returns the next event of the list, without removing it from the heap. 
	 * @return The next event of the list.
	 */
	public Event getNextEvent() {
		return queue.peek();
	}
	
	/**
	 * Returns the next event of the list, by removing it from the heap. 
	 * @return The next event of the list.
	 */
	public Event pollNextEvent() {
		return queue.remove();
	}
	
	/**
	 * Stores a new Event object in this list.
	 * @param event The specified event to be stored.
	 */
	public void addEvent(Event event) {
		queue.add(event);
	}
	
	/**
	 * Return the number of events stored in this list.
	 * @return The number of events stored in this list.
	 */
	public int size(){
		return queue.size();
	}
	
	/**
	 * Dump the contents of this list to the standard output using the natural order of the heap.
	 * Use for debug purposes only.
	 */
	public void dump() {
		while (queue.size() > 0) {
			System.out.println(queue.remove().toString());
		}		
	}
	
	/**
	 * Returns a String representation of this object.
	 * No ordering can be assumed in this String!
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		Object[] array = queue.toArray();
		for (Object element:array) {
			buf.append(element.toString());
			buf.append("\n");
		}
		return buf.toString();
	}
	
}
