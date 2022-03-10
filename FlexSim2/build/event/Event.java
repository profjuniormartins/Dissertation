/*
 * Created on 2013-03-06 by Pavani. 
 * 
 */

package event;

/**
* This class represents an event in an event-driven simulation. 
*
* @author Gustavo S. Pavani
* @version 1.1
*
*/
public class Event implements Comparable<Event>{
	/** Specifies the type of the event to be processed by the simulator. */
	public enum Type {
		/** Ignore event. */ IGNORE,
		/** Multiple events. */ MULTIPLE,
		/** Terminate simulation. */ TERMINATE,
		/** Message arriving on a node. */ MESSAGE_ARRIVAL,
		/** Connection request. */ CONNECTION_REQUEST,
		/** Connection established. */ CONNECTION_ESTABLISHED,
		/** Connection problem at setup. */ CONNECTION_PROBLEM,
		/** Connection tear-down requested. */ CONNECTION_TEARDOWN,
		/** Connection finished (removed). */ CONNECTION_FINISHED,
		/** Ant killed for some reason. */ ANT_KILLED,
		/** Ant did all its round-trip. */ ANT_ROUTED,
		/** Ant did all its round-trip, being delivered at the source node. */ ANT_DELIVERED,
		/** Node failure. */ FAILURE_NODE,
		/** Link failure. */ FAILURE_LINK,
		/** Node added to the topology. */ TOPOLOGY_NODE,
		/** Link added to the topology. */ TOPOLOGY_LINK,	
		/** Update the LSDB. */ OSPF_UPDATE;
	}	
	/** The time when the event takes place. */
	double timeStamp=0;
	/** The time when the event is generated for the first time. */
	double initialTimeStamp;
	/** The type of this event. */
	Type type;
	/** The content associated with this event, such as message arrival or link failure.*/
	Object content;
	
	/**
	 * Creates a new Event object.
	 * 
	 * @param time The time when the event takes place.
	 * @param aType The type of this event.
	 * @param content The content associated with this event.
	 */
	public Event(double time, Type aType, Object aContent) {
		this.timeStamp = time;
		this.initialTimeStamp = time;
		this.type = aType;
		this.content = aContent;		
	}
	
	public Event(double time, double initialTime, Type aType, Object aContent) {
		this.timeStamp = time;
		this.initialTimeStamp = initialTime;
		this.type = aType;
		this.content = aContent;		
	}

	/**
	 * Compares this Event object to another one and returns a number indicating the natural order
	 * for scheduling these events.
	 * 
	 * @param obj Another Event object.
	 * @return -1, if this event has to be served first;
	 *         +1, if this event has to be served after the compared event;
	 *          0, if both events have to be served at the same time. 
	 */
	public int compareTo(Event obj) {
		if (this.timeStamp - obj.getTimeStamp() < 0.0) {
			return -1;
		} else if (this.timeStamp - obj.getTimeStamp() > 0.0) {
			return +1;
		} else {
			return 0;
		}
	}
	
	/**
	 * Returns the type of this event.
	 * @return The type of this event.
	 */
	public Type getType() {
		return this.type;
	}
	
	/**
	 * Returns the time stamp of this event, i.e., when this event have to take place. 
	 * @return The time stamp of this event.
	 */
	public double getTimeStamp() {
		return this.timeStamp;
	}	
	
	/**
	 * Returns the time stamp when the event was generated.
	 * @return The time stamp when the event was generated.
	 */
	public double getInitialTimeStamp() {
		return this.initialTimeStamp;
	}
	
	/**
	 * Set the new time stamp for this event.
	 * @param stamp The new time stamp for this event.
	 */
	public void setTimeStamp(double stamp) {
		this.timeStamp = stamp;
	}
	
	/**
	 * Set the type of this event.
	 * @param aType The new type of this event.
	 */
	public void setType(Type aType) {
		this.type = aType;
	}
	
	/**
	 * Returns the content associated with this event.
	 * @return The content associated with this event.
	 */
	public Object getContent() {
		return this.content;
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Type: ");
		buf.append(type);
		buf.append(", time stamp: ");
		buf.append(timeStamp);
		buf.append(", initial time stamp: ");
		buf.append(initialTimeStamp);
		buf.append(", content [");
		if (content != null)
			buf.append(content.toString());
		buf.append("]");
		return buf.toString();
	}

}
