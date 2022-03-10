/*
 * Created on 03/02/2006.
 */
package net;

import graph.Graph;

/**
 *
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public interface RoutingTable {
	
	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology. 
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	public void updateFromTopology(Graph graph);

}
