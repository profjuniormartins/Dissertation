/*
 * Created on 2013-03-06 by Pavani. 
 */
package event;

/**
 * Identifies the class related to the content of an event.
 * 
 * @author Pavani
 *
 */
public interface Content {
	
	/**
	 * Information about the class of the content. 
	 */
	public enum Type {
		/** New request to be processed. */ REQUEST,
		/** Message to be processed. */ MESSAGE,
		/** Simulator event. */ SIMULATOR,
	}
	
	/**
	 * Returns the type of this content.
	 * @return The type of this content.
	 */
	public Type getType();
	
	/**
	 * Sets the type of the event.
	 * @param t The specified event type.
	 */
	public void setType(Type t);
}
