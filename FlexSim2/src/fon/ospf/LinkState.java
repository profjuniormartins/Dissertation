/**
 * 
 */
package fon.ospf;

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
		//The initial length
		int lenght = 16;
		//The bitmap count
		int bitmapLength = 0;
		for (String id: neighborsLinksStates.keySet()) {			
			bitmapLength += (neighborsLinksStates.get(id).length/2);						
		}	
		bitmapLength = bitmapLength/8;
		return lenght + bitmapLength;
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
}
