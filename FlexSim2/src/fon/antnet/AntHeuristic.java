/**
 * The Spectrum Assignment algorithms.
 */
package fon.antnet;

import fon.FlexiLink;

public class AntHeuristic {
	
	/** The heuristics for the Ant algorithm. */
	public enum Heuristic{
		/** Free slots. */ FREE_SLOTS,
		/** Biggest Free Contiguous Slots */ BIGGEST_FREE_CONTIGUOUS_SLOTS,
	}

	/**
	 * Returns the number of free slots in the flexilink.
	 * @param flexiLink 
	 * @return The numbers of slots.
	 */
	public static double freeSlots(FlexiLink flexiLink) {
		return flexiLink.freeSlots();
	}
	
	/**
	 * Returns the number of slots in biggest free contiguous slots.
	 * @param flexiLink 
	 * @return The number of slots.
	 */
	public static double biggestFreeContiguousSlots(FlexiLink flexiLink) {
		return flexiLink.biggestContiguousSlots();
	}
		
	}
	
	
	
	
