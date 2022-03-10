/*
 * Created on 01/05/2020.
 */
package fon.acobased;

import java.util.ArrayList;
import java.util.Arrays;


import fon.FlexiLink;
import graph.Path;
import net.Message;

/**
 * This class represents a Ant message in ACO Based Algorithm routing.
 */

public class ACOBasedAnt extends Message {
	/** The number of bytes added to the ant's memory at each hop. */
	public static int BYTES_PER_HOP = 8; 
	/** TTL */
	private int ttl;
	/** Path length (number of hops) */
	private int pathLength = 0;
	/** The spectrum fragmentation of the last link visited. 
	 * The bit value 1 means a free slot while the bit value 0 corresponds to an occupied slot.*/
	protected int[] spectrumFragmentation;
	/**
	 * Creates a new Ant object. Used when ants and data are separated messages.
	 * @param aId The ant identification.
	 * @param sourceId The source of the ant.
	 * @param destinationId The destination of the ant.
	 * @param aTTL The maximum number of hops allowed.
	 */
	public ACOBasedAnt(String aId, String sourceId, String destinationId, int aTTL) {
		super(aId, Message.Type.ANT_FORWARD, sourceId, destinationId, Message.HEADER_LENGTH);
		this.ttl = aTTL;
	}

	/**
	 * Creates a new Ant object. Used when the ants are encapsulated in data messages.
	 * @param aId The ant identification.
	 * @param sourceId The source of the ant.
	 * @param destinationId The destination of the ant.
	 * @param aTTL The maximum number of hops allowed.
	 * @param aLength The size of the message length, in bytes.
	 */
	public ACOBasedAnt(String aId, String sourceId, String destinationId, int aTTL, int aLength) {
		super(aId, Message.Type.ANT_FORWARD, sourceId, destinationId, aLength);
		this.ttl = aTTL;
	}

	
	/**
	 * Create a new Ant object for cloning purposes.
	 */
	protected ACOBasedAnt() {
	}

	/**
	 * Returns the number of hops left for this message.
	 * @return The number of hops left for this message.
	 */
	public int getTTL() {
		return this.ttl;
	}

	/**
	 * Decrements the number of allowed for this packet. In each visited node, this function
	 * must be called to decrement this number.
	 */
	public void decrementTTL() {
		this.ttl = this.ttl - 1;
	}
	
	
	/**
	 * Returns the number of hops of the ant.
	 * @return The number of hops of the ant.
	 */
	public int getPathLength() {
		return this.pathLength;
	}

	/**
	 * Increases the number of hops of the ant. In each visited node, this function
	 * must be called to increases this number.
	 */
	public void increasePathLength() {
		this.pathLength = this.pathLength + 1;
	}
	
	
		
	/**
	 * Set the actual processing node of this packet and add it to the path, if the recordRoute flag is enable.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		//Call super class
		super.setNode(procId);
		//Add the length of the identifier
		if (recordRoute)
			this.setLength(this.getLength() + Message.ID_HEADER_LENGTH);
	}

	/**
	 * Gets the backward node of the ant. To be used only for the backward ant.
	 * @return The backward node of the ant. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		int counter = 0;
		for (String node : path.nodes()) {
			if (node.equals(procNode) && !procNode.equals(source)) {
				return path.getNode(counter - 1);
			}
			counter ++; //Increment the counter
		}
		return null; // In case of an error!
	}

	
	/**
	 * Returns the number of slots in the spectrum
	 * @return The number of slots in the spectrum
	 */
	public int getSpectrumLength() {
		return Arrays.toString(this.spectrumFragmentation).length();		
	}	

	
	/**
	 * Adds the usage of the spectrum link mask to the ant's memory.
	 * @param mask The spectrum usage mask of the link.
	 */
	public void addSpectrumUsage(FlexiLink mask) {
		//Create the mask, if it is not available
		//System.out.println("slots: "+Arrays.toString(mask.getSlots()));
		//System.out.println("slots: "+mask.size());
		if (spectrumFragmentation == null) {
			this.spectrumFragmentation = new int[mask.size()];
			Arrays.fill(spectrumFragmentation,1);
		}		
		boolean slots[]= mask.getSlots();
		//System.out.println("variavel slots: "+Arrays.toString(slots));
		//System.out.println("len: "+len);
			for (int i=0; i<slots.length; i++) {
				//System.out.println("i: "+i);
				if (!slots[i]) {
					spectrumFragmentation[i] = 0; //Add the number 0 to slot occupied
					//System.out.println("spectrum array: "+Arrays.toString(spectrumFragmentation));
				}
			}		
	}
	
	
	/**
	 * Returns a clone object to this message. The content of the ant (ArrayList<Double>) is cloned, if not null!
	 */
	@SuppressWarnings("unchecked")
	public Object clone() {
		ACOBasedAnt clone = new ACOBasedAnt();
		clone.id = new String(id);
		clone.type = type;
		clone.source = new String(source);
		clone.destination = new String(destination);
		clone.length = length;
		clone.ttl = ttl;
		clone.creationTime = creationTime;
		clone.recordRoute = recordRoute;
		if (content != null) {
			clone.content = new ArrayList<Double>((ArrayList<Double>)content);
		}
		clone.path = (Path) path.clone();		
		return clone;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString());
		builder.append(", TTL: ");
		builder.append(ttl);
		builder.append(", Source: ");
		builder.append(source);
		builder.append(", Destination: ");
		builder.append(destination);
		builder.append(", Path Length: ");
		builder.append(pathLength);
		builder.append(" - usage spectrum:" + Arrays.toString(this.spectrumFragmentation));		
		return builder.toString();
	}

}
