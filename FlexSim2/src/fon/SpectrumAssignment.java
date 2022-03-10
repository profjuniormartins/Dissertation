/**
 * The Spectrum Assignment algorithms.
 */
package fon;

import java.util.ArrayList;

public class SpectrumAssignment {
	
	/** The strategys for the spectrum assignment. */
	public enum Strategy{
		/** First-fit. */ FIRST_FIT,
		/** Best-fit. */ BEST_FIT,
		/** Segmented First-fit. */ SEGMENTED_FIRST_FIT,
		/** Segmented First-fit. */ SEGMENTED_BEST_FIT,
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
				//System.out.println("centralSlotPosition: "+centralSlotPosition+ " slotsFS: "+slotsFS);
				//Return the Frequency Slot
				return new FrequencySlot(centralSlotPosition, slotsFS);			
			}
		}
		return null;
	} 
	
	/**
	 * Returns the frequency slot accordingly to the segmented first fit strategy.
	 * @param ls The label set.
	 * @param slotsFS The number of required slots.
	 * @param bandwidhts 
	 * @return The frequency slot.
	 */
	public static FrequencySlot segmentedFirstFit(LabelSet ls, int slotsFS, ArrayList<Double> bandwidths) {
		//The number slots in the label set. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.
		int slotsLS = ls.getNumberSlots();
		//The total number slots in the label set 
		int slotsLSTotal = ls.getSlots().length;
		
		//The total number slots required to the Frequency slot
		int slotsFSTotal = slotsFS*2;
		
		//System.out.println("Tamanho do LS: "+ls.getNumberSlots());
		//System.out.println("Tamanho do LS length: "+ls.getSlots().length);
		
		//The size of the grid (divided by the number of bandwidths)
		int gridSize = (slotsLSTotal/bandwidths.size());			
		
		//The position of piece of the grid according to bandwidth size
		int positionGrid = bandwidths.indexOf(slotsFS* FlexiLink.SLOT_WIDTH_GRANULARITY);
		
		//The first slot of the grid piece according to bandwidth size
		int firstSlotLS = gridSize * positionGrid;
		//The last slot of the grid piece according to bandwidth size
		int lastSlotLS = firstSlotLS + gridSize - 1;
		
		//System.out.println("positionGrid: " + positionGrid);
		//System.out.println("first Slots LS: " + firstSlotLS);
		//System.out.println("last Slots LS: " + lastSlotLS);		
		//System.out.println("quantidade de slots necessária: slotsfsTotal "+ slotsFSTotal);
		//System.out.println("vai de: "+ firstSlotLS + " até "+ (lastSlotLS - slotsFSTotal+1));
		//System.out.println("first Slots LS: " + (firstSlotLS - slotsLS) + " last Slots LS: "+ (lastSlotLS - slotsLS));


		//Search the required free slots (from first to last slot of grid piece)
		for (int i = firstSlotLS; i <= (lastSlotLS - slotsFSTotal + 1); i++ ) {
			boolean freeSlots = true; //Flag
			for (int j = i; j < (i + (slotsFSTotal)); j++) {
				if (!ls.slots[j]){ //Busy				
					freeSlots = false;
					break;
				}
			}
			//Return the Frequency Slot if find free slots
			if (freeSlots) {
				//Get the central slot position
				int centralSlotPosition = i - slotsLS + slotsFS;
				//System.out.println("bandwidth: "+ (slotsFS* FlexiLink.SLOT_WIDTH_GRANULARITY)+" - index: ["+positionGrid+"]");
				//System.out.println("centralSlotPosition: "+centralSlotPosition+ " slotsFS: "+slotsFS);
				//Return the Frequency Slot				
				return new FrequencySlot(centralSlotPosition, slotsFS);			
			}
		}
		return null;
	} 
	
	/**
	 * Returns the frequency slot accordingly to the segmented best fit strategy.
	 * @param ls The label set.
	 * @param slotsFS The number of required slots.
	 * @param bandwidhts 
	 * @return The frequency slot.
	 */
	public static FrequencySlot segmentedBestFit(LabelSet ls, int slotsFS, ArrayList<Double> bandwidths) {
		//The number slots in the label set. It assumes the same logic of the FrequencySlot, i.e., -slots <-> +slots.
		int slotsLS = ls.getNumberSlots();
		//The total number slots in the label set 
		int slotsLSTotal = ls.getSlots().length;
		
		//The total number slots required to the Frequency slot
		int slotsFSTotal = slotsFS*2;
		
		//System.out.println("Tamanho do LS: "+ls.getNumberSlots());
		//System.out.println("Tamanho do LS length: "+ls.getSlots().length);
		
		//The size of the grid (divided by the number of bandwidths)
		int gridSize = (slotsLSTotal/bandwidths.size());			
		
		//The position of piece of the grid according to bandwidth size
		int positionGrid = bandwidths.indexOf(slotsFS* FlexiLink.SLOT_WIDTH_GRANULARITY);
		
		//The first slot of the grid piece according to bandwidth size
		int firstSlotLS = gridSize * positionGrid;
		//The last slot of the grid piece according to bandwidth size
		int lastSlotLS = firstSlotLS + gridSize - 1;
		
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
		
		//System.out.println("positionGrid: " + positionGrid);
		//System.out.println("first Slots LS: " + (firstSlotLS - slotsLS) + " last Slots LS: "+ (lastSlotLS - slotsLS));
		//System.out.println("quantidade de slots necessária: slotsfsTotal "+ slotsFSTotal);
		//System.out.println("vai de: "+ firstSlotLS + " até "+ (lastSlotLS - slotsFSTotal+1));
			
		//Search the required free slots in the label set
		for (int i = firstSlotLS; i <= (lastSlotLS - slotsFSTotal + 1); i++ ) {
			freeSlots = true; //Flag
			//Search the required free slots starting from i
			for (int j = i; j < (i + (slotsFSTotal)); j++) {
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
				for (int k = i + (slotsFSTotal) - 1 ; k < slotsLSTotal; k++){					
					//Stop if the slot is not free
					if (!ls.slots[k]){ //Busy
						//Set the last empty slot of the gap
						lastEmpty = k - 1;
						break;
						//Set the slot if is the last label set slot
					}else if (k == slotsLSTotal -1 ) { //Last label set slot
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
			//System.out.println("centralSlotPosition: "+centralSlotPosition+ " slotsFS: "+slotsFS);
			//Return the Frequency Slot
			return new FrequencySlot(centralSlotPosition, slotsFS);			
		} else {		
			return null;
		}
	}
}
