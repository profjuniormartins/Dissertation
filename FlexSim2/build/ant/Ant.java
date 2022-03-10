/*
 * Created on 2013-09-23 by Pavani. 
 */
package ant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import fon.FlexiLink;
import graph.Path;
import net.Message;

/**
 * This class represents a Ant message in ACO routing.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class Ant extends Message {
	/** The number of bytes added to the ant's memory at each hop. */
	public static int BYTES_PER_HOP = 8; 
	/** The number of occurrences for each lambda in the visited links. */
	protected int[] collector;
	/** True, if the ant has entered a loop. False, otherwise. */
	protected boolean loopFlag = false;
	private int bytesPerHop;

	
	/**
	 * Creates a new Ant object. Used when ants and data are separated messages.
	 * @param aId The ant identification.
	 * @param sourceId The source of the ant.
	 * @param destinationId The destination of the ant.
	 * @param aLimit The maximum number of hops allowed.
	 */
	public Ant(String aId, String sourceId, String destinationId, int aLimit) {
		super(aId, Message.Type.ANT_FORWARD, sourceId, destinationId, Message.HEADER_LENGTH, aLimit);
	}

	/**
	 * Creates a new Ant object. Used when the ants are encapsulated in data messages.
	 * @param aId The ant identification.
	 * @param sourceId The source of the ant.
	 * @param targetId The destination of the ant.
	 * @param aLimit The maximum number of hops allowed.
	 * @param aLength The size of the message length, in bytes.
	 */
	public Ant(String aId, String sourceId, String targetId, int aLimit, int aLength) {
		super(aId, Message.Type.ANT_FORWARD, sourceId, targetId, aLength, aLimit);
	}

	/**
	 * Creates a new Ant object. Used for creating stand-alone backward ants.
	 * @param aId The ant identification.
	 * @param sourceId The source of the ant.
	 * @param targetId The destination of the ant.
	 * @param aLimit The maximum number of hops allowed.
	 */
	public Ant(String aId, String sourceId, String targetId, Path aPath) {
		super(aId, Message.Type.ANT_BACKWARD, sourceId, targetId, (Message.HEADER_LENGTH + aPath.size() * Message.ID_HEADER_LENGTH), 
				aPath.size() + 1);
		//Set the path
		this.path = aPath;
		//Disable the record route flag
		this.setRecordRoute(false);
	}
	
	/**
	 * Create a new Ant object for cloning purposes.
	 */
	protected Ant() {
	}

	
	/**
	 * Set the actual processing node of this packet and add it to the path, if the recordRoute flag is enable.
	 * @param procId The actual processing node id of this packet.
	 */
	public void setNode(String procId) {
		//Call super class
		super.setNode(procId);
		//Add the length of the identifier
		if (recordRoute)
			this.setLength(this.getLength() + Message.ID_HEADER_LENGTH);
	}
	
	/**
	 * Turns the forward ant into a backward one.
	 */
	public void toBackward() {
		//Set it as a backward ant
		this.setType(Message.Type.ANT_BACKWARD);
		//Disable the record route flag
		this.setRecordRoute(false);		
	}

	/**
	 * Returns true, if the specified node has already been visited.
	 * @param nodeId The specified node.
	 * @return True, if the specified node has already been visited.
	 * False, otherwise.
	 */
	public boolean isTabu(String nodeId) {
		//For all nodes in the path
		for (String node : path.nodes()) {
			if (node.equals(nodeId))
				return true;
		}
		return false; //Not found in the tabu list (nodes already visited).
	}
	
	/**
	 * Returns the number of hops necessary to reach the second node
	 * from a first node.
	 * @param first The first node.
	 * @param second The second node.
	 * @return The number of hops necessary to reach the second node
	 * from a first node.
	 */
	public int getSubPathLength(String first, String second) {
		int index1 = path.getNodePosition(first);
		int index2 = path.getNodePosition(second);
		if (index2 - index1 > 0)
			return (index2 - index1);
		else 
			return (index1 - index2);
	}
	

	/**
	 * Returns a list containing the nodes between the specified node
	 * (exclusive) and the last node (inclusive). Last node is the target node for backward ant and the source node, otherwise. 
	 * @param nodeId The specified node.
	 * @return A list containing the nodes between the specified node
	 * (exclusive) and the last node (inclusive). 
	 */
	public List<String> getSubPath(String nodeId) {
		int index = path.getNodePosition(nodeId);
		//Accordingly to the ant type do
		if (type.equals(Message.Type.ANT_FORWARD)) {
			//Assumes the source node is the last one
			return path.nodes().subList(0, index);
		} else if (type.equals(Message.Type.ANT_BACKWARD)) {
			//Assumes the target node is the last one
			return path.nodes().subList(index+1,path.size());
		}
		return null;
	}
	
	/**
	 * Returns a list containing the nodes between the processing node
	 * (exclusive) and the target node (inclusive). 
	 * @return
	 */
	public List<String> getSubPath() {
		int index = path.getNodePosition(this.procNode);
		return path.nodes().subList(index+1,path.size());
	}


	/**
	 * Returns a list containing the nodes between the first node
	 * (inclusive) and the second node (inclusive). 
	 * @return 
	 */
	public List<String> getInclusiveSubPath(String first, String second) {
		int index1 = path.getNodePosition(first);
		int index2 = path.getNodePosition(second);
		if (index2>index1)
			return path.nodes().subList(index1,index2+1);
		else
			return path.nodes().subList(index2,index1+1);
	}

	/**
	 * Gets the backward node of the ant. To be used only for the backward ant.
	 * @return The backward node of the ant. Null, if the processing node
	 * is the source node.
	 */
	public String getBackwardNode() {
		int counter = 0;
		for (String node : path.nodes()) {
			if (node.equals(procNode) && !procNode.equals(source)) {
				return path.getNode(counter - 1);
			}
			counter ++; //Increment the counter
		}
		return null; // In case of an error!
	}

	/**
	 * Gets the forward node of the ant. To be used only for the backward ant.
	 * @return The forward node of the ant. Null, if the processing node
	 * is the target node.
	 */
	public String getForwardNode() {
		int counter = 0;
		for (String node : path.nodes()) {
			if (node.equals(procNode) && !procNode.equals(source)) {
				return path.getNode(counter + 1);
			}
			counter ++; //Increment the counter
		}
		return null; // In case of an error!
	}
	
	/**
	 * Gets the identification of the last node visited. 
	 * @return The identification of the last node visited. Null, if the processing node
	 * is the source node.
	 */
	public String getLastVisitedNode() {
		if (path.size() > 1)
			return path.getNode(path.size() - 2);
		return null;
	}
	
	/**
	 * Gets the state of the loop flag. True, if the ant has entered a loop. False, otherwise.
	 * @return True, if the ant has entered a loop. False, otherwise.
	 */
	public boolean getLoopFlag() {
		return this.loopFlag;
	}
	
	/**
	 * Sets the loop flag of the ant.
	 */
	public void setLoopFlag() {
		this.loopFlag = true;
	}
	
	/**
	 * Remove the nodes visited till the specified node (inclusive).
	 * @param id The specified node.
	 * @return The number of nodes removed.
	 */
	public int destroyLoop(String id) {
		int size = path.size();
		int index = path.getNodePosition(id);
		if (index != -1) { //Loop detected
			for (int i=1; i <= (size-index); i++) { //starts from the last till the specified node
				path.removeNodeAt(size - i);
			}
		}
		return (size - index);
	}	
	
	/**
	 * Returns the number of bytes per hop of each node identification.
	 * @return The number of bytes per hop of each node identification.
	 */
	public int getBytesHop() {
		return this.bytesPerHop;
	}
	
	
		/**
	 * Returns the number of bytes other than the IPv4 header and Ant header (source
	 * and destination address, type, identifier, wavelength mask), i.e., the number
	 * of labels in the tabu list times the number of bytes of the label.
	 */
	public int getPayloadLength() {
		return path.size()*BYTES_PER_HOP;		
	}
	
	/**
	 * Returns the collector statistics of the wavelength usage gathered in its trip.
	 * @return The collector statistics of the wavelength usage gathered in its trip.
	 */
	public int[] getCollector() {
		return this.collector;
	}
	
	
	/**
	 * Adds the wavelength link mask to the ant's memory.
	 * @param mask The wavelength mask of the link.
	 */
	public void addMask(FlexiLink mask) {
		//Create the mask, if it is not available
		//System.out.println("slots: "+Arrays.toString(mask.getSlots()));
		//System.out.println("slots: "+mask.size());
		if (collector == null) {
			this.collector = new int[mask.size()];
			Arrays.fill(collector,0);
		}		
		boolean slots[]= mask.getSlots();
		//System.out.println("variavel slots: "+Arrays.toString(slots));
		//Gets the length of the collector
		int len = collector.length;
		//System.out.println("len: "+len);
			for (int i=0; i<slots.length; i++) {
				//System.out.println("i: "+i);
				if (!slots[i]) {
					//System.out.println("livre? "+slots[i]);
					collector[i] = collector[i] + 1; //Increment the counter
					//System.out.println("collector array: "+Arrays.toString(collector));
				}
			}		
	}
	
	/**
	 * Returns a clone object to this message. The content of the ant (ArrayList<Double>) is cloned, if not null!
	 */
	@SuppressWarnings("unchecked")
	public Object clone() {
		Ant clone = new Ant();
		clone.id = new String(id);
		clone.type = type;
		clone.source = new String(source);
		clone.destination = new String(destination);
		clone.length = length;
		clone.hopLimit = hopLimit;
		clone.creationTime = creationTime;
		clone.recordRoute = recordRoute;
		if (content != null) {
			clone.content = new ArrayList<Double>((ArrayList<Double>)content);
		}
		clone.path = (Path) path.clone();		
		return clone;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString());
		return builder.toString();
	}

}
