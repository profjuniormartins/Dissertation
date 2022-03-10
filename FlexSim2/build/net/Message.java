/*
 * Created on 2013-03-06 by Pavani. 
 */
package net;

import graph.Path;

/**
 * A message to be exchanged between nodes.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Message {
	/** The identification of the message. */
	protected String id;
	/** The possible types of the message. */
	public enum Type {
		/** RSVP Path message. */ RSVP_PATH("PATH"),
		/** RSVP Resv message. */ RSVP_RESV("RESV"),
		/** RSVP PathErr message. */ RSVP_PATH_ERR("PATH_ERR"),
		/** RSVP ResvErr message. */ RSVP_RESV_ERR("RESV_ERR"),
		/** RSVP PathTear message. */ RSVP_PATH_TEAR("PATH_TEAR"),
		/** RSVP ResvTear message. */ RSVP_RESV_TEAR("RESV_TEAR"),
		/** RSVP Notify message. */ RSVP_NOTIFY("NOTIFY"),
		/** Ant forward message. */ ANT_FORWARD("ANT_FWD"),
		/** Ant backward message. */ ANT_BACKWARD("ANT_BACK"),
		/** Link failure message. */ FAILURE_LINK("FAILURE_LINK"),
		/** Node failure message. */ FAILURE_NODE("FAILURE_NODE"),
		/** LSA message  */ LSA("LSA");		
		private String label;
		private Type(String aLabel) {
			label = aLabel;
		}
		public String getLabel() {
			return this.label;
		}
	}
	/** The message type. */
	protected Type type;
	/** The source node of the message. */
	protected String source;
	/** The destination node of the message. */
	protected String destination;
	/** The processing node. */
	protected String procNode;
	/** The length (in bytes) of this message. */
	protected int length;
	/** Maximum number of hops allowed - decremented at each hop. */
	protected int hopLimit;
	/** The time when the message was created. */
	protected double creationTime;
	/** Record route flag. */
	protected boolean recordRoute;
	/** The path followed by the message. */
	protected Path path;
	/** The content of this message. */
	protected Object content;
	/** The size of the header of the message, in bytes. */
	public static int HEADER_LENGTH;
	/** The size of an identifier (address) in the header, in bytes. */
	public static int ID_HEADER_LENGTH;
	/** Identifies all packets belonging to a specific flow, i.e., the same LSP. */
	protected String flowLabel;
	/** Payload length (in bytes). Not linked to the actual payload (Object) size. */
	protected int payloadLength;
		
	/** 
	 * Creates a new Message object. 
	 * 
	 * @param aId The identification of the message.
	 * @param aType The type of the message.
	 * @param sourceId The source identification of this message.
	 * @param destinationId The destination identification of this message.
	 * @param aLength The length of this message.
	 * @param aLimit The maximum number of hops allowed.
	 */
	public Message(String aId, Type aType, String sourceId, String destinationId, int aLength, int aLimit) {
		this.id = aId;
		this.type = aType;
		this.source = sourceId;
		this.destination = destinationId;
		this.length = aLength;
		this.hopLimit = aLimit;
		this.recordRoute = true;
		this.content = null;
		//Create a new path
		path = new Path();
		//Set the source node as the processing node
		this.setNode(source);
	}
	
	/**
	 * Create a new Message object for cloning purposes.
	 */
	protected Message() {
		
	}
	
	
	/**
	 * Retransmit the packet to another destination as if 
	 * the retransmitter is the new sender.
	 * @param sourceId The new source identification of this packet.
	 * @param targetId The new destination identification of this packet.
	 */
	public void retransmit(String sourceId, String destinationId) {
		this.source = sourceId;
		this.destination = destinationId;
	}
	
	
	/**
	 * Returns the type of this message.
	 * @return The type of this message.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Sets a new type for this message.
	 * @param type The new type for this message.
	 */
	public void setType(Type type) {
		this.type = type;
	}

	/**
	 * Returns the source node of this message.
	 * @return The source node of this message.
	 */
	public String getSource() {
		return source;
	}

	/**
	 * Sets a new source node for this message.
	 * @param source The new source node for this message.
	 */
	public void setSource(String source) {
		this.source = source;
	}

	/**
	 * Returns the destination node of this message.
	 * @return The destination node of this message.
	 */
	public String getDestination() {
		return this.destination;
	}

	/**
	 * Sets a new destination node for this message.
	 * @param destination The new destination node for this message.
	 */
	public void setDestination(String destination) {
		this.destination = destination;
	}

	/**
	 * Returns the identification of this message.
	 * @return The identification of this message.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the processing node for this message.
	 * @return The processing node for this message.
	 */
	public String getProcNode() {
		return procNode;
	}

	/**
	 * Set the actual processing node of this packet and add it to the path, if the recordRoute flag is enable.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		this.procNode = procId;
		if (recordRoute) { //if true, then add to the path
			this.path.addNode(procId);
		}
	}
	
	/**
	 * Returns the length of this message, in bytes.
	 * @return The length of this message, in bytes.
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Sets a new length (in bytes) for this message.
	 * @param length The new length (in bytes) for this message.
	 */
	public void setLength(int length) {
		this.length = length;
	}
	
	/**
	 * Returns the time that this message was created.
	 * @return The time that this message was created.
	 */
	public double getCreationTime() {
		return creationTime;
	}

	/**
	 * Sets a new creation time for this message.
	 * @param aCreationTime The new creation time to be set.
	 */
	public void setCreationTime(double aCreationTime) {
		this.creationTime = aCreationTime;
	}
	
	
	/**
	 * Returns true, if the recording of route is enabled. False, otherwise.
	 * @return True, if the recording of route is enabled. False, otherwise.
	 */
	public boolean isRecordRoute() {
		return recordRoute;
	}

	/**
	 * Sets the flag for route recording.
	 * @param enableRecordRoute True, to enable the recording of route. False, otherwise.
	 */
	public void setRecordRoute(boolean enableRecordRoute) {
		this.recordRoute = enableRecordRoute;
	}

	/**
	 * Returns the number of hops left for this message.
	 * @return The number of hops left for this message.
	 */
	public int getHopLimit() {
		return hopLimit;
	}

	/**
	 * Decrements the number of allowed for this packet. In each visited node, this function
	 * must be called to decrement this number.
	 */
	public void decrementHopLimit() {
		hopLimit = hopLimit - 1;
	}
	
	/**
	 * Returns the path followed by this message.
	 * @return The path followed by this message.
	 */
	public Path getPath() {
		return this.path;
	}
	
	/**
	 * Return the length, in number of hops, traversed by this packet. 
	 * @return The length of the path traversed by this packet.
	 */
	public int getPathLength() {
		if (path.size() == 0)
			return 0;
		else 
			return (path.size() - 1);
	}
	
	/**
	 * Returns the content of this message.
	 * @return The content of this message.
	 */
	public Object getContent() {
		return content;
	}

	/**
	 * Sets the content of this message.
	 * @param content The new content of this message.
	 */
	public void setContent(Object content) {
		this.content = content;
	}

	/**
	 * Returns true, if it is a newly generated message. False, otherwise.
	 * @return True, if it is a newly generated message. False, otherwise.
	 */
	public boolean isNew() {
		return (this.getPathLength() == 0 && this.getSource().equals(this.getProcNode()));
	}
	
	/**
	 * Set the payload length. It does not include the header length.
	 * @param aPayloadLength The payload length to set.
	 */
	public void setPayloadLength(int aPayloadLength) {
		this.payloadLength = aPayloadLength;
	}
	
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(id);
		buf.append(" - ");
		buf.append(type.toString());
		buf.append(". Src: ");
		buf.append(source);
		buf.append(", tg: ");
		buf.append(destination);
		buf.append(", proc: ");
		buf.append(procNode);
		buf.append(", hop limit: ");
		buf.append(hopLimit);
		buf.append(", length: ");
		buf.append(length);
		buf.append(", created: ");
		buf.append(creationTime);
		if (recordRoute)
			buf.append(" [RR]");
		buf.append(". Path: ");
		buf.append(path.toString());
		if (content != null) {
			buf.append(". Content: ");
			buf.append(content.toString());
		}
		return buf.toString();
	}
	
	/**
	 * Returns a clone object to this message. The content of the message is not cloned!
	 */
	public Object clone() {
		Message clone = new Message();
		clone.id = new String(id);
		clone.type = type;
		clone.source = new String(source);
		clone.destination = new String(destination);
		clone.length = length;
		clone.hopLimit = hopLimit;
		clone.creationTime = creationTime;
		clone.recordRoute = recordRoute;
		clone.content = content;
		clone.path = (Path) path.clone();		
		return clone;
	}

}
