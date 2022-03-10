/*
 * Created on 2013-03-06 by Pavani. 
 */
package net;

import event.Event;

/**
 * A node in the network.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public abstract class Node {
	/** The identification of this node. */
	protected String id;
	/** The list of types for this node. */
	public enum Type {
		/** Fixed alternate routing (topological) at the source node. */ FIXED_ALTERNATE;
	}
	/** The type of this node. */
	protected Type type;
	
	/**
	 * Creates a Node object.
	 * 
	 * @param aId The identification of the node.
	 * @param aType The type for this node.
	 * @param aStorage The storage for this node.
	 * @param aRadio The radio for this node.
	 */
	public Node(String aId, Type aType) {
		this.id = aId;
		this.type = aType;
	}
		
	/**
	 * Returns the identification of this node.
	 * @return The identification of this node.
	 */
	public String getId() {
		return this.id;
	}
	
	/**
	 * Returns the type of this node.
	 * @return The type of this node.
	 */
	public Type getType() {
		return this.type;
	}
	
	
	/**
	 * Returns a String representation of a node object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(id);
		buf.append(" - Type: ");
		buf.append(type.toString());
		return buf.toString();
	}
	

	/**
	 * Process the specified event.
	 * @param event The event to be processed.
	 * @return The response for the event processed. Null, if no response is returned.
	 */
	public abstract Event process(Event event);	
	

}

