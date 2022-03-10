/**
 * Created on 27/09/2016.
 */
package fon;

import graph.Path;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class Connection {
	/** The request that generated this connection. */
	Request request;
	/** The frequency slots. */
	protected FrequencySlot fs;
    /** Path from source to target station. */
	protected Path path;
    /** The start time of this connection. */
	protected double startTime;

    /** Creates new Connection object.
     * @param aPath Path from source to destination station. 
	 * @param request The request object that generated this connection.
	 * @param aFs Frequency slots allocated to this connection.
     */
    public Connection(Path aPath, Request aRequest, FrequencySlot aFs) {
        path = aPath;
		this.request = aRequest;
		this.fs = aFs;
    }
    
    /**
     * Returns the path of this connection.
     * @return The path of this connection.
     */
    public Path getPath() {
        return this.path;
    }	
    
    /**
     * Returns the number of hops of this connection.
     * @return The number of hops of this connection.
     */
    public int size() {
    	return (this.path.size() - 1);
    }
	
	/**
	 * Returns the associated request to this connection.
	 * @return The associated request to this connection.
	 */
	public Request getRequest() {
		return this.request;
	}
	
	/**
	 * Returns the frequency slots used in this connection.
	 * @return The frequency slots used in this connection.
	 */
	public FrequencySlot getFS() {
		return this.fs;
	}

	/**
	 * Returns the identification of this connection.
	 * @return The identification of this connection.
	 */
	public String getId() {
		return this.request.getId();
	}
	
	/**
	 * Returns the source node of this connection.
	 * @return The source node of this connection.
	 */
	public String getSource() {
		return this.request.getSource();
	}
	
	/**
	 * Returns the destination node of this connection.
	 * @return The destination node of this connection.
	 */
	public String getDestination() {
		return this.request.getDestination();
	}
	
	/**
	 * Returns the time when this connection started.
	 * @return The time when this connection started.
	 */
	public double getStartTime() {
		return startTime;
	}

	/**
	 * Sets ghe time when this connection started.
	 * @param startTime The start time.
	 */
	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.request.toString());
		builder.append(" ");
		builder.append(this.fs.toString());
		return builder.toString();
	}
}
