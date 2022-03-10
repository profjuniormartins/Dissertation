/**
 * 
 */
package fon.ospf;

import distribution.QueueDistribution;
import event.Event.Type;
import event.Event;
import event.EventSubscriber;

/**
 * @author Pavani
 *
 */
public class OSPFRefreshTraffic implements EventSubscriber {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The distribution for ant generation. */
	protected QueueDistribution distribution;
	/** The LSA message id. */
	protected Long id;
	
	public OSPFRefreshTraffic() {
		id = new Long(0);
	}

	/** Get the id of the OSPF update message.
	 */
	@Override
	public Object getContent() {
		//Increment the counter
		id++;
		//Return the id
		return id;
	}

	/**
	 * Returns the type to for the event generator.
	 */
	public Type getType() {
		return Event.Type.OSPF_UPDATE;
	}

	/**
	 * Set the traffic distribution. 
	 */
	public void setDistribution(QueueDistribution distrib) {
		this.distribution = distrib;		
	}


}
