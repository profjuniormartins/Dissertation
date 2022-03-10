/*
 * Created on Sep 14, 2005.
 * Added length of the link on Jan 26, 2006.
 */
package net;

import java.io.Serializable;

import graph.*;
/**
 * Represents the link information as seen by the network.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Link implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** Constant of the speed of the light in the fiber in km/s. */
	public static final double SPEED_OF_LIGHT = 2E5;
	/** The edge in the network graph. */
	protected Edge edge;
	/** The number of bytes offered to this link. */
	protected long byteCounter;
	/** The number of messages exchanged in a link. */
	protected long msgCounter;
	/** The length (in km) of the link. */
	protected double length = 0;
	/** The delay due to transmission time. */
	protected double delay = 0;
	
	/**
	 * Creates a new Link object.
	 * @param aEdge The edge in the network graph.
	 * @param aLength The length (in km) of the link.
	 */
	public Link(Edge aEdge, double aLength) {
		this.edge = aEdge;
		this.byteCounter = 0;
		this.msgCounter = 0;
		this.length = aLength;
		this.delay = aLength / Link.SPEED_OF_LIGHT;
	}
	
	public Link(Edge aEdge) {
		this(aEdge,0.0);
	}

	/**
	 * Returns the edge of the graph representing the network.
	 * @return The edge of the graph representing the network.
	 */
	public Edge getEdge() {
		return this.edge;
	}
	
	/**
	 * Returns the length of this link in km.
	 * @return The length of this link in km.
	 */
	public double getLength() {
		return this.length;
	}
	
	/**
	 * Returns the delay due to transmission time in seconds.
	 * @return The delay due to transmission time in seconds.
	 */
	public double getDelay() {
		return this.delay;
	}
	
	/**
	 * Adds the specified number of bytes to the accumulated counter.
	 */
	public void setCounter(int bytes) {
		//Increment the byte counter
		byteCounter = byteCounter + bytes;
		//Increment the message counter
		msgCounter++;
	}
	
	/**
	 * Returns the number of bytes traversed by this link.
	 * @return The number of bytes traversed by this link.
	 */
	public long getByteCounter() {
		return this.byteCounter;
	}
	
	/**
	 * Returns the number of messages traversed by this link.
	 * @return The number of messages traversed by this link.
	 */
	public long getMessageCounter() {
		return this.msgCounter;
	}
	
	
	/** Reset the number of bytes traversed by this link. */
	public void resetCounter() {
		this.byteCounter = 0;
		this.msgCounter = 0;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Edge: ");
		buf.append(edge.toString());
		buf.append(", length: ");
		buf.append(this.length);
		buf.append(", bytes: ");
		buf.append(this.byteCounter);
		buf.append(", #msgs: ");
		buf.append(this.msgCounter);
		return buf.toString();
	}
}
