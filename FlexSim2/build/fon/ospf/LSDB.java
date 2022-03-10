package fon.ospf;

import fon.topological.ExplicitRoutingTable;

public class LSDB extends ExplicitRoutingTable {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new ExplicitRoutingTable object;
	 * @param source The source id of the node associated to this routing table.
	 * @param k The number of k-shortest paths calculated.
	 */
	public LSDB(String source, int k) {
		super(source,k);
	}
	
}
