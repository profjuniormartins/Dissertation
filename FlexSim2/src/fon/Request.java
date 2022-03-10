/**
 * Created on 27/09/2016.
 */
package fon;


/**
 * A request in a flexi-grid network.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class Request {
	/** The id of this request. */
	protected String id;
	/** The source of this request. */
	protected String source;
	/** The destination of this request. */
	protected String destination; 
	/** The duration of this request, in seconds. */
	protected double duration;
	/** The bandwidth of this request, in GHz. */
	protected double bandwidth;
	/** The number of alternative tries allowed to establish the request.*/
	protected int tries;
	/** The current try. */
	protected int currentTry;
	/** Flag to indicate if this request is in restoration. */
	boolean inRestoration = false;

	
	public Request(String aId, String aSource, String aDestination, double aDuration, double aBandwidth) {
		this(aId, aSource, aDestination, aDuration, aBandwidth, 1);
	}
	
	public Request(String aId, String aSource, String aDestination, double aDuration, double aBandwidth, int aTries) {
		this.id = aId;
		this.source = aSource;
		this.destination = aDestination;
		this.duration = aDuration;
		this.bandwidth = aBandwidth;
		this.currentTry = 0;
		this.tries = aTries;
	}
	
	public void setRestoration() {
		this.inRestoration = true;
	}
	
	public boolean inRestoration() {
		return this.inRestoration;
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getSource() {
		return this.source;
	}
	
	public String getDestination() {
		return this.destination;
	}
	
	public double getDuration() {
		return this.duration;
	}
	
	public void setSource(String s) {
		this.source = s;
	}
	
	public void setDestination(String d) {
		this.destination = d;
	}
	
	/**
	 * Set the new residual duration of this request.
	 * @param aDuration The new duration.
	 */
	public void setDuration(double aDuration) {
		this.duration = aDuration;
	}
	
	public double getBandwidth() {
		return this.bandwidth;
	}
	
	public int getCurrentTry() {
		return this.currentTry;
	}
	
	/**
	 * Verify if a new connection establishment should be tried. 
	 * 
	 * @return True, if a new connection establishment should be tried.
	 */
	public boolean tryAgain() {
		return (tries >= currentTry);
	}
	
	
	/**
	 * Add a tentative of connection establishment.
	 */
	public void addTry() {
		currentTry = currentTry + 1;	
		//System.out.println("addTry: "+currentTry);
	}
	
	/** 
	 * Resets the number of tentatives.
	 */
	public void resetTry() {
		this.currentTry = 0;
	}
	
	/**
	 * Returns the current try.
	 * @return
	 */
	public int getTry() {
		return this.currentTry;
	}
	
	public void setTry(int value) {
		this.currentTry = value;
	}
	
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Id: ");
		builder.append(id);
		builder.append(", src: ");
		builder.append(source);
		builder.append(", dest: ");
		builder.append(destination);
		builder.append(", duration: ");
		builder.append(duration);
		builder.append("s, bandwidth: ");
		builder.append(bandwidth);
		builder.append(" GHz, try ");
		builder.append(currentTry);
		builder.append(" of ");
		builder.append(tries);
		if (inRestoration)
			builder.append(" [RESTORATION]");
		builder.append(".");
		return builder.toString();
	}
}
