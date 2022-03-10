/*
 * Created on 01/05/2020.
 */

package fon.acobased;

import random.MersenneTwister;
import fon.topological.FON;
import distribution.QueueDistribution;
import event.Event;
import event.EventSubscriber;
import event.Event.Type;

/**
* This class represents the generation of ant for each node ACOBasedAnt
*
*/
public class ACOBasedTraffic implements EventSubscriber {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The distribution for ant generation. */
	protected QueueDistribution distribution;
	/** The maximum number of hops allowed for this packet. */
	protected int ttl;
	/** Random number generator. */
	protected MersenneTwister rng;
	/** The ant message id */
	protected int id;
	
	/**
	 * Creates a new AntTraffic object.
	 * @param aTTL The maximum number of hops allowed for this packet.  
	 * @param seed The random seed.
	 */
	public ACOBasedTraffic(int aTTL, long seed) {
		this.rng = new MersenneTwister(seed);
		this.ttl = aTTL;
	}
	
	/**
	 * Get an ant message.
	 */
	public Object getContent() {
		id++;		
		String source = FON.getSourceNode(rng);
		String destination = FON.getDestinationNode(rng,source);
		ACOBasedAnt ant = new ACOBasedAnt(Integer.toString(id),source,destination,ttl);
		return ant;
	}

	/**
	 * Returns the type to for the event generator.
	 */
	public Type getType() {
		return Event.Type.MESSAGE_ARRIVAL;
	}

	/**
	 * Set the traffic distribution. 
	 */
	public void setDistribution(QueueDistribution distrib) {
		this.distribution = distrib;		
	}

}
