/**
 * Created on 26/09/2016.
 */
package fon;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;

import fon.topological.FlexiNode;
import net.Link;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FlexiLink implements Serializable {
	/** Default serial version id. */
	private static final long serialVersionUID = 1L;
	/** ITU-T "anchor frequency" for transmission (193.1 THz for C-band). */
	public static final double ANCHOR_FREQUENCY = 193.1;
	/** Nominal Central Frequency Granularity (6.25 GHz). */
	public static final double NOMINAL_CENTRAL_FREQUENCY_GRANULARITY = 6.25;
	/** Slot Width Granularity - SWG (12.5 GHz). */
	public static final double SLOT_WIDTH_GRANULARITY = 12.5;
	/** The anchor frequency (THz). */
	protected double anchorFrequency;
	/** The nominal central frequency granularity (GHz). */
	protected double frequencyGranularity;
	/** The slot width granularity (GHz). */
	protected double slotGranularity;
	/** The associated physical link. */
	protected Link link;
	/** The total number of slots of this link. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.  */
	protected int numberSlots;
	/** The status of the frequency slots in this link. True means available slot. */
	protected boolean[] slots;
	/** The connections that uses this link. */
	protected LinkedHashMap<String,FrequencySlot> connections;
	/** The number of bytes offered to this link. */
	protected long byteCounter;
	
	
	/**
	 * Creates a new FlexiLink object.
	 * @param aLink The physical link.
	 * @param aSlots The number of slots of this flexi link.
	 */
	public FlexiLink(Link aLink, int aSlots) {
		this.anchorFrequency = FlexiLink.ANCHOR_FREQUENCY;
		this.frequencyGranularity = FlexiLink.NOMINAL_CENTRAL_FREQUENCY_GRANULARITY;
		this.slotGranularity = FlexiLink.SLOT_WIDTH_GRANULARITY;
		this.link = aLink;
		this.numberSlots = aSlots;
		this.slots = new boolean[2*numberSlots]; //double to assume the logic -slots <-> +slots
		Arrays.fill(slots, true); //True means available
		connections = new LinkedHashMap<String,FrequencySlot>();
		this.byteCounter = 0;

	}	
	
	/**
	 * Returns the physical link associated to this flexi link.
	 * @return The physical link associated to this flexi link.
	 */
	public Link getLink() {
		return this.link;
	}
	
	/**
	 * Returns the anchor frequency, in THz.
	 * @return The anchor frequency, in THz.
	 */
	public double getAnchorFrequency() {
		return this.anchorFrequency;
	}
	
	/**
	 * Returns the nominal central frequency granularity, in GHz.
	 * @return The nominal central frequency granularity, in GHz.
	 */
	public double getFrequencyGranularity() {
		return this.frequencyGranularity;
	}
	
	/**
	 * Returns the slot width granularity, in GHz.
	 * @return The slot width granularity, in GHz.
	 */
	public double getSlotGranularity() {
		return this.slotGranularity;
	}
	
	/**
	 * Returns the total number of slots. 
	 * @return The total number of slots.
	 */
	public int getNumberSlots() {
		return this.numberSlots;
	}
	
	/**
	 * Returns the delay due to transmission time.
	 * @return The delay due to transmission time.
	 */
	public double getDelay() {
		return this.link.getDelay();
	}
	
	
	
	/**
	 * Returns the status of the frequency slots in this link. 
	 * @return The status of the frequency slots in this link.
	 */
	public boolean[] getSlots() {
		return this.slots;
	}
	
	/**
	 * Returns True, if the specified frequency range is available at this link. False, otherwise.
	 * @param fs A frequency slot object.
	 * @return True, if the specified frequency range is available at this link. False, otherwise.
	 */
	public boolean isAvailable(FrequencySlot fs) {
		//return slots[fs.getCentralSlot()];
		//Get the n (central slot) and the m (number slots) parameters
		int n = fs.getCentralSlot();
		int m = fs.getNumberSlots();
		//Get the first position of the frequency slot in the slots array
		int firstSlotPosition = (n + this.numberSlots - m);
		//Verify if the slots are available
		for (int i = firstSlotPosition; i < (firstSlotPosition + m * 2); i++) {
			if (!this.slots[i]){
				return false;				
			}
		}
		return true;		
	}
	
	/**
	 * Adds a frequency slot to this flexi link. 
	 * The method isAvailable() must be invoked first to the verify the availability.
	 * @param id The connection identifier.
	 * @param fs The frequency slot to be added.
	 */
	public void addFrequencySlot(String id, FrequencySlot fs) {
		//Add the connection to the list
		this.connections.put(id,fs);
		//Get the n (central slot) and the m (number slots) parameters
		int n = fs.getCentralSlot();
		int m = fs.getNumberSlots();
		//Get the first position of the frequency slot in the slots array
		int firstSlotPosition = (n + this.numberSlots - m);
		//Insert the frequency slot at the flexi link slots 
		for (int i = firstSlotPosition; i < (firstSlotPosition + m * 2); i++) {
			this.slots[i] = false;
		}
	} 
	
	/**
	 * Removes the indicated frequency slot.
	 * @param id The connection identifier.
	 * @param True, if there was a frequency slot specified by id to be removed. False, otherwise.
	 */
	public boolean removeFrequencySlot(String id) {
		//Remove the connection from the frequency slot table
		FrequencySlot fs = this.connections.remove(id);
		if (fs != null) {
			//Get the n (central slot) and the m (number slots) parameters
			int n = fs.getCentralSlot();
			int m = fs.getNumberSlots();
			//Get the first position of the frequency slot in the slots array
			int firstSlotPosition = (n + this.numberSlots - m);
			//Invoke the method isAvailable to verify the availability
			//Insert the frequency slot at the slots.
			for (int i = firstSlotPosition; i < (firstSlotPosition + m * 2); i++) {
				this.slots[i] = true;
			}	
		} //Already removed connection
		return false;
	}
	
	/**
	 * Gets the frequency slot mask associated to this link.
	 * @return The frequency slot mask associated to this link.
	 */
	public boolean[] getMask() {
		return this.slots;
	}
	
	/** 
	 * Gets the total number of free slots.
	 * @return The total number of free slots.
	 */
	public int freeSlots() {
		int count = 0;
		for (int i=0; i<slots.length; i++) {
			if (slots[i])
				count++;
		}
		return count;		
	}
	
	/** 
	 * Gets the size of the biggest number of contiguous free slots.
	 * @return The size of the biggest contiguous free slots
	 */
	public int biggestContiguousSlots() {
		//System.out.println("---------------- Início do biggestContiguousSlots ------------");
		int count = 0;
		int biggest = 0;
		for (int i=0; i<slots.length; i++) {
			if (slots[i]) {
				count++;
				//System.out.println("slot livre - contador: "+count);
			}
			else {
				if (count > biggest){
					//System.out.println("count: "+count+" - Biggest: "+biggest);					
					biggest = count;
					//System.out.println("Biggest = count: "+biggest);
				}
				//System.out.println("Antes de zerar contador: "+count);
				count = 0;
				//System.out.println("Contador zerado: "+count);
			}
			if ((i == (slots.length-1)) && (count> biggest)) {
				//System.out.println("Último slot (count: "+count+")");
				biggest = count;								
			}
		}
		//System.out.println("-- Saiu do contador --");

		//System.out.println("Retorna: "+ biggest/2);
		return biggest;		
	}
	
	
	
	/**
	 * Returns the size, i.e., the number of positions of the mask.
	 * @return The size, i.e., the number of positions of the mask.
	 */
	public int size() {
		return slots.length;
	}
		
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Link: ");
		builder.append(link.toString());
		builder.append(" "+this.numberSlots);
		builder.append(" slots with width of ");
		builder.append(this.slotGranularity);
		builder.append(" GHz. \n");
		//Get the mask
		builder.append("{");
		builder.append((LabelSet.slotsToString(slots)).toString());
		builder.append("} \n");
		for (String id: connections.keySet()) {
			builder.append(id);
			builder.append(" - ");
			builder.append(connections.get(id));
			builder.append("\n");
		}
		return builder.toString();
	}
	
}
