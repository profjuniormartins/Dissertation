/**
 * The Spectrum Assignment algorithms.
 */
package fon;

public class SpectrumAssignment {
	
	/** The strategys for the spectrum assignment. */
	public enum Strategy{
		/** First-fit. */ FIRST_FIT,
		/** Best-fit. */ BEST_FIT,
	}

	/**
	 * Returns the frequency slot accordingly to the best fit strategy.
	 * @param ls The label set.
 	 * @param slotsFS The number of required slots.
	 * @return The frequency slot.
	 */
	public static FrequencySlot bestFit(LabelSet ls, int slotsFS) {
		//The number slots in the label set. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.
		int slotsLS = ls.getNumberSlots();
		//Gap slots
		int gap;
		//First slot of the best gap free
		int bestI = 0;
		//Best gap free
		int bestGap = Integer.MAX_VALUE; //The gap 
		//Last empty gap slot 
		int lastEmpty = 0;
		//Flag free slots
		boolean freeSlots = true;
		//Flag to return the FS
		boolean returnFS = false;		
		
		//Search the required free slots in the label set
		for (int i = 0; i < (2*(slotsLS - slotsFS) + 1); i++ ) {
			freeSlots = true; //Flag
			//Search the required free slots starting from i
			for (int j = i; j < (i + (2*slotsFS)); j++) {
				if (!ls.slots[j]){ //Busy
					freeSlots = false;
					break;
				}
			}
			//If found the required free slots, calculate the free slots of the gap
			if (freeSlots) {
				//Flag to return FS
				returnFS = true;
				//Measure the gap				
				for (int k = i + (2*slotsFS) - 1 ; k < 2*slotsLS; k++){					
					//Stop if the slot is not free
					if (!ls.slots[k]){ //Busy
						//Set the last empty slot of the gap
						lastEmpty = k - 1;
						break;
					//Set the slot if is the last label set slot
					}else if (k == 2*slotsLS -1 ) { //Last label set slot
					lastEmpty = k;			
					}
				}			
				//Measure the number of slots in the gap
				gap = lastEmpty-i+1;
				//Update the i, if it is the smallest gap
				if (gap < bestGap) {
					bestI = i;
					bestGap = gap;
				}	
				//Set the next i
				i = lastEmpty;
			}
		}			
		//Return the frequency slot, if there is
		if (returnFS) {
			//Get the central slot position
			int centralSlotPosition = bestI - slotsLS + slotsFS;
			//Return the Frequency Slot
			return new FrequencySlot(centralSlotPosition, slotsFS);			
		} else {		
			return null;
		}
	}
	
	/**
	 * Returns the frequency slot accordingly to the first fit strategy.
	 * @param ls The label set.
	 * @param slotsFS The number of required slots.
	 * @return The frequency slot.
	 */
	public static FrequencySlot firstFit(LabelSet ls, int slotsFS) {
		//The number slots in the label set. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.
		int slotsLS = ls.getNumberSlots();
				
		//Search the required free slots
		for (int i = 0; i < (2*(slotsLS - slotsFS) + 1); i++ ) {
			boolean freeSlots = true; //Flag
			for (int j = i; j < (i + (2*slotsFS)); j++) {
				if (!ls.slots[j]){ //Busy
					freeSlots = false;
					break;
				}
			}
			//Return the Frequency Slot if find free slots
			if (freeSlots) {
				//Get the central slot position
				int centralSlotPosition = i - slotsLS + slotsFS;
				//Return the Frequency Slot
				return new FrequencySlot(centralSlotPosition, slotsFS);			
			}
		}
		return null;
	} 
}
