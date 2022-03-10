/**
 * Created on 26/09/2016.
 */
package fon;

import net.Message;
import net.Error;
import fon.Connection;

/**
 * This class represents a RSVP-TE signaling message.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class RSVP extends Message {
	/** The total length, in bytes, of a RSVP Path message. */
	public static final int RSVP_PATH_LENGHT = 32;	
	/** The label set carried by this message. */
	protected LabelSet labelSet;
	/** The error specification object. */
	protected Error error=null;
	/** Indicates if the message is associated with a full LSP re-routing. */
	protected boolean reRouting = false;
	/** Indicates the number of effective hops in establishing a connection. */
	protected int effectiveHops = 0;
	
	
	/**
	 * Creates a new RSVP object.
	 * @param aReq The request.
	 * @param aLimit The hop limit.
	 * @param aSlots The number of implemented slots in the network.
	 */
	public RSVP(Request aReq, int aLimit, int aSlots) {
		super(aReq.getId(), Type.RSVP_PATH, aReq.getSource(), aReq.getDestination(), RSVP_PATH_LENGHT, aLimit);
		//Set the request as the content of the message
		this.setContent(aReq);	
		//Create the LabelSet object
		labelSet = new LabelSet(aSlots);
	}
	
	/**
	 * Creates a RSVP message with the source given by the keySource value and 
	 * the destination node given by keyDestination value.
	 * @param connection The information about the connection.
	 * @param type The type of the RSVP-TE message
	 * @param keySource The identification of the source node.
	 * @param keyDestination The identification of the destination node.
	 */
	public RSVP(Connection connection, Type type, String keySource, String keyDestination) {
		super(connection.getId(),type,keySource,keyDestination,0,(connection.size() + 1));
		//Set the connection as the content of the message
		this.setContent(connection);		
	}
	
	public void setId(String newId) {
		this.id = newId;
	}
	
	/**
	 * Return the label set of this message.
	 * @return The frequency slots that fulfill the request, if available. Otherwise, return null.
	 */
	public LabelSet getLabelSet() {
		return this.labelSet;
	}

	/**
	 * Returns the error associated with the message.
	 * @return The error associated with the message.
	 */
	public Error getError() {
		return error;
	}

	/**
	 * Set the error object.
	 * @param aError The error to set.
	 */
	public void setError(Error aError) {
		this.error = aError;
	}
	
	/**
	 * Set the status of a failed Connection looking for full re-routing.
	 */
	public void setReRouting() {
		this.reRouting = true;
	}
	
	/**
	 * Return the status wheter this message is looking for full Connection
	 * re-routing after a failure.
	 * @return True, if this message indicates a full Connection re-routing.
	 * False, otherwise.
	 */
	public boolean isReRouting() {
		return this.reRouting;
	}
	
	/**
	 * Gets the backward node of the rsvp message. To be used only for the reservation message.
	 * @return The backward node of the rsvp message. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		try {
			return path.getPreviousNode(procNode);
		} catch (Exception e) {return null; /* In case of an error!*/}
	}
	
	/**
	 * Set a new value for the source and destination of
	 * this message.
	 * @param newSource The new source identification of this message.
	 * @param newDestination The new destination identification of this message.
	 */
	public void setSDPair(String newSource, String newDestination) {
		this.source = newSource;
		this.destination = newDestination;
	}
	
	/**
	 * Gets the forward node of the rsvp message. To be used only for the backward message.
	 * @return The forward node of the ant. Null, if the processing node
	 * is the destination node.
	 */
	public String getForwardNode() {
		try {
			return path.getNextNode(procNode);
		} catch (Exception e) {return null; /* In case of an error!*/}		
	}
	
	/**
	 * Remove from the route the last visited node.
	 * @return The id of the last visited node. Null, if there is no node left.
	 */
	public String removeLastVisited() {
		int len = path.size();
		if (len > 0) {
			String last = path.getNode(len - 1);
			path.removeNodeAt(len - 1);
			return last;
		} else {
			return null;
		}
	}
	
	/**
	 * Sets the Label Set of this message.
	 * @param set The Label Set object.
	 */
	public void setLabelSet(LabelSet set) {
		this.labelSet = set;
	}
	
	/**
	 * Increment the counter of effective hops. 
	 */
	public void addEffectiveHop() {
		this.effectiveHops ++;
	}	
	
	/**
	 * Returns the number of effective hops of this RSVP message.
	 * @return The number of effective hops of this RSVP message.
	 */
	public int getEffectiveHops() {
		return this.effectiveHops;
	}
	
	public void setEffectiveHops(int eff) {
		this.effectiveHops = eff;
	}	
	
	/**
	 * Updates the Label Set for this message.
	 * @param aMask The link mask.
	 */
	public void updateMask(boolean[] aMask) {
		this.labelSet.update(aMask);
	}
	
	/**
	 * Returns a String representation of this object. 
	 */
	public String toString() {
		String superString = super.toString();
		StringBuilder builder = new StringBuilder();
		
		builder.append(". ");
		if (this.error != null) {
			builder.append(error.toString());
			builder.append(". ");
		}
		
		builder.append("Content: ");
		builder.append(content.toString());		
		if (this.labelSet != null) {
			builder.append(", label set: ");
			builder.append(labelSet.toString());
		}		
		
		builder.append(", effective hops: ");
		builder.append(this.effectiveHops);
		if (this.recordRoute)
			builder.append(", [RECORD_ROUTE]");
		if (this.reRouting) 
			builder.append(", [RE-ROUTING]");				
		
		return superString.concat(builder.toString());
	}
}
