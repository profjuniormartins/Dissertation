package fon.ospf.llrsa;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class LSDB{
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The set of links and his states */
	protected LinkedHashMap<String,boolean[]> flexiLinksStates;

	/**
	 * Creates a new ExplicitRoutingTable object;
	 * @param source The source id of the node associated to this routing table.
	 * @param k The number of k-shortest paths calculated.
	 */
	public LSDB(LinkedHashMap<String,boolean[]> flexiLinksStates) {
		this.flexiLinksStates = flexiLinksStates;
	}

	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String id: flexiLinksStates.keySet()) {
			builder.append(id);
			builder.append(" : ");
			builder.append(Arrays.toString(flexiLinksStates.get(id)));
			builder.append("\n");
		}
		return builder.toString();
	}
	
}
