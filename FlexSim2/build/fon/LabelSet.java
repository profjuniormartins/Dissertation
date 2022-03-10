package fon;

import java.util.Arrays;

/**
 * A set of flexi-grid labels, where each entry represents and encodes a frequency slot unit.
 * 
 * IMPORTANT: It differs from RFC 7698, where the label set specifies the available nominal
   central frequencies that meet the slot width requirements of the LSP.
 * 
 * @author Gustavo Pavani
 *
 */
public class LabelSet {
	/** The total number of slots of this link. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.  */
	protected int numberSlots;
	/** The status of the frequency slots in this label set. True means available slot. */
	protected boolean[] slots;

		
	/**
	 * Creates a new LabelSet object.
	 * @param aSlots The number of slots of this flexi-grid label set.
	 */
	public LabelSet(int aSlots) {
		//Create the label set
		this.numberSlots = aSlots;
		this.slots = new boolean[2*numberSlots]; //double to assume the logic -slots <-> +slots
		Arrays.fill(slots, true); //True means available
	}
	
	/**
	 * Creates a new LabelSet object.
	 * @param aSlots The frequency slots.
	 */
	public LabelSet(boolean[] aSlots) {
		//Get the length
		this.numberSlots = aSlots.length / 2;
		//Create a new array
		this.slots = new boolean[2*numberSlots];
		//Copy the array
		this.slots = Arrays.copyOf(aSlots,aSlots.length);

		
	}
	
	/**
	 * Update the label set with the specified mask. It assumes that the mask has the same size as the slot.
	 * @param mask The mask.
	 */
	public void update(boolean[] mask) {
		//System.out.println("Slots: \n"+slotsToString(slots));		
		//System.out.println("Mask: \n"+slotsToString(mask));
		//System.out.println("masklenght: "+mask.length);
		for(int i=0; i < mask.length; i++) {
			slots[i] = mask[i] && slots[i]; //Set false if mask or slots has a false (i.e. busy) slot.
		}
	}
		
	/**
	 * Clone a LabelSet object.
	 * This is needed by the history table of the crankback mechanism.
	 */
	public Object clone() {
		//Create a new LabelSet from the slots		
		LabelSet clone = new LabelSet(this.slots);
		//Return the cloned object
		return clone;
	}
	
	/**
	 * Return the status of the frequency slots in this label set. True means available slot.
	 */
	public boolean[] getSlots() {
		return this.slots;
	}
	
	/**
	 * Return the number slots in this label set.
	 */
	public int getNumberSlots() {
		return this.numberSlots;
	}		
	
	/** 
	 * Gets the total number of free bandwidth.
	 * @return The total number of free bandwidth.
	 */
	public double freeBandwidth() {
		double bandwidth = 0;		
		for (int i=0; i<slots.length; i++) {
			if (slots[i])
				bandwidth = bandwidth + FlexiLink.NOMINAL_CENTRAL_FREQUENCY_GRANULARITY;
		}
		return bandwidth;		
	}
	
	/** 
	 * Gets the total number of free slots.
	 * @return The total number of free slots.
	 */
	public double freeSlots() {
		double freeSlots = (this.freeBandwidth()/FlexiLink.SLOT_WIDTH_GRANULARITY);
		return freeSlots;		
	}
				
	/**
	 * Returns a String representation of this object.
	 * Based on RFC 7698, Section 3.2.1., page 7.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		builder.append("Number slots: ");
		builder.append(this.numberSlots);
		builder.append(" - \n");
		builder.append(slotsToString(this.getSlots()));
		//builder.append(Arrays.toString(this.getSlots()));	
		return builder.toString();
	}

	
	/** 
	 * Returns a String to show the status of the frequency slots in the label set.
	 * @return string to show the status of the frequency slots in the label set.
	 */
	public static String slotsToString(boolean[] array) {
		int numberSlots = (array.length/2);
		StringBuilder builder = new StringBuilder();		
		for (int i = numberSlots*-1; i <= numberSlots; i++) {		
				builder.append(String.format("%3s", i)); 		
		}	
		builder.append("\n  ");		
		for (int i = 0; i <= (array.length -1); i++) {		
				builder.append(i==0 ? String.format("%3s", "+--+") : String.format("%3s", "--+"));
		}
		builder.append("\n  ");		
		for (int i = 0; i <= (array.length -1); i++) {		
				builder.append((array[i]) ? (String.format("%3s", "")) : (String.format("%3s", "---")));
		}
		builder.append("\n");		
		return builder.toString();
	}
	
	

	
}
