/**
 * Created on 27/09/2016.
 */
package fon;

import net.Network;
import distribution.QueueDistribution;
import event.Event;
import event.Event.Type;
import event.EventSubscriber;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FixedRequestTraffic implements EventSubscriber{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The distribution for generating the requests. */
	QueueDistribution distribution;	
    /** The maximum number of retries for establishing a connection. */
	int retries;
	/** The requested bandwidth (fixed). */
	double bandwidth;
	/** Counter of requests. */
	long counter;
	
	/**
	 * Creates a new FixedRequestTraffic object.
	 * @param aMaxTries
	 * @param aBandwidth
	 */
	public FixedRequestTraffic(int aMaxTries, double aBandwidth) {
		this(aMaxTries);
		this.bandwidth = aBandwidth;
	}
	
	/**
	 * Creates a new FixedRequestTraffic object.
	 * @param aMaxTries
	 */
	protected FixedRequestTraffic(int aMaxTries) {
		this.retries = aMaxTries;		
		this.counter = 0L;
	}
		
	@Override
	public Object getContent() {
		//Get source and destination nodes
		String source = Network.getSourceNode();
		String destination = Network.getDestinationNode(source);
		//Get the service time (duration)
		double duration = distribution.getServiceTime();
		//Generate the request
		Request req = new Request((new Long(counter)).toString(), source, destination, duration, bandwidth, retries);		
		//Increment counter
		counter++;
		//Return request
		return req;
	}

	@Override
	public Type getType() {
		return Event.Type.CONNECTION_REQUEST;
	}

	@Override
	public void setDistribution(QueueDistribution distrib) {
		this.distribution = distrib;
	}

}
