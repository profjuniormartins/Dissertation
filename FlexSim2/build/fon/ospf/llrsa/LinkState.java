/**
 * 
 */
package fon.ospf.llrsa;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * The informations gathered by a node about all its neighbors.
 * 
 * @author Pavani
 *
 */
public class LinkState {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The set of adjacent flexi links and his states of this node. */
	protected LinkedHashMap<String,boolean[]> neighborsLinksStates;

	/**
	 * Creates a new LinkState object.
	 * @param sourceId The source of the LSA.
	 * @param destinationId The destination of the LSA.
	 * @param aId The LSA identification.
	 */
	public LinkState (LinkedHashMap<String,boolean[]> neighborsLinksStates) {
		this.neighborsLinksStates = neighborsLinksStates;
	}	

	/**
	 * Returns the length of the LSA links states, in bytes.
	 * @return the length of the LSA links states, in bytes
	 */
	public int getLength() {
		//The initial length of each link state, in bytes
		int initialLenght = 16;
		//The total initial length (initial lenght * number of link states)
		int totalInitialLenght = initialLenght * neighborsLinksStates.size();
		//The bitmap count
		int bitmapLength = 0;
		//System.out.println(neighborsLinksStates);
		for (String id: neighborsLinksStates.keySet()) {
			//Slots Number of each neighbor link state
			int slotsNumber = neighborsLinksStates.get(id).length/2;
			//The bitmap length, in bytes
			bitmapLength += bitmapSize(slotsNumber);						
		}	
		int length = totalInitialLenght + bitmapLength;
		return length;
	}
	
	
	public LinkedHashMap<String,boolean[]> getNeighborsLinksStates(){
		return this.neighborsLinksStates;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String id: neighborsLinksStates.keySet()) {
			builder.append(id);
			builder.append(" - ");
			builder.append(Arrays.toString(neighborsLinksStates.get(id)));
			builder.append("\n");
		}
		return builder.toString();
	}
	
	/**
	 * Calculate the bitmap size, in bytes
	 * @param number of slots
	 * @return the bitmap size
	 */
	public int bitmapSize(int numberSlots) {
		//Initialize the bitmapSize		
		int bitmapSize;
		//Calculate the bitmapSize (must be multiple of 32 bits)
		if (numberSlots % 32 == 0)
			bitmapSize = numberSlots;
		else {
			//Padding bits (to complete 32 bits)
			int paddingBits = 32 - (numberSlots % 32);
			bitmapSize = numberSlots + paddingBits;
		}		
		//Return in bytes
		return bitmapSize / 8;
	}
	
}

