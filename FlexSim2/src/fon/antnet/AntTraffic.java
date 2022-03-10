/*
 * Created on Oct 12, 2005.
 */
package fon.antnet;

import random.MersenneTwister;
import fon.topological.FON;
import ant.Ant;
import distribution.QueueDistribution;
import event.Event;
import event.EventSubscriber;
import event.Event.Type;

/**
* This class represents the generation of ant for each node. 
*rwa.crankback.antnet
* @author Gustavo S. Pavani
* @version 1.0
*
*/
public class AntTraffic implements EventSubscriber {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The distribution for ant generation. */
	protected QueueDistribution distribution;
	/** The maximum number of hops allowed for this packet. */
	protected int hopLimit;
	/** The number of bytes added at each hop. */
	protected int bytesPerHop;
	/** Random number generator. */
	protected MersenneTwister rng;
	/** The ant message id */
	protected int id;
	
	/**
	 * Creates a new AntTraffic object.
	 * @param aHopLimit The maximum number of hops allowed for this packet.  
	 * @param bytesHop The number of bytes added at each hop.
	 * @param seed The random seed.
	 */
	public AntTraffic(int aHopLimit, long seed) {
		this.rng = new MersenneTwister(seed);
		this.hopLimit = aHopLimit;
	}
	
	/**
	 * Get an ant message.
	 */
	public Object getContent() {
		id++;		
		String source = FON.getSourceNode(rng);
		String destination = FON.getDestinationNode(rng,source);
		Ant ant = new Ant(Integer.toString(id),source,destination,hopLimit);
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
