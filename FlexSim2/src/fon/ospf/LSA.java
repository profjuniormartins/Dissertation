/**
 * 
 */
package fon.ospf;

import java.util.ArrayList;
import graph.Path;
import net.Message;

/**
 * @author Pavani
 *
 */
public class LSA extends Message implements Comparable<LSA>{
	/** The number of bytes added to the LSA'S memory at each hop. */
	public static int BYTES_PER_HOP = 8; 
	/** The LSA's types. */
	public enum Type {
		ROUTER; /** Type 1 - Router. */
	}
	/** The LSA Type. */
	protected Type LSA_type;
	/** The status of the links sent by the LSA */
	protected LinkState linkState;
	/** The LSA source node */
	protected String nodeLSA;

	/**
	 * Creates a new LSA object.
	 * @param sourceId The source of the LSA.
	 * @param destinationId The destination of the LSA.
	 * @param aId The LSA identification.
	 */
	public LSA(String aId, String sourceId, String destinationId, String nodeLSA,  LinkState adv) {
		super(aId, Message.Type.LSA, sourceId, destinationId, adv.getLength(), 1);
		this.linkState = adv;
		this.nodeLSA = nodeLSA;
	}
	
	/**
	 * Create a new LSA object for cloning purposes.
	 */
	public LSA() {
	}

	public Type getLSAType() {
		return this.LSA_type;
	}
	
	public LinkState getLinkState() {
		return this.linkState;
	}	
	
	/**
	 * Returns the number of bytes other than the IPv4 header, OSPF Header and LSA header (source
	 * and destination address, type, identifier, wavelength mask), i.e., the number
	 * of labels in the tabu list times the number of bytes of the label.
	 */
	public int getPayloadLength() {
		return path.size()*BYTES_PER_HOP;		
	}
	
	/**
	 * Returns the node LSA
	 */
	public String getNodeLSA() {
		return this.nodeLSA;
	}
	
	/**
	 * Returns a clone object to this message. The content of the LSA (ArrayList<Double>) is cloned, if not null!
	 */
	@SuppressWarnings("unchecked")
	public Object clone() {
		LSA clone = new LSA();
		clone.id = new String(id);
		clone.type = type;
		clone.source = new String(source);
		clone.nodeLSA = nodeLSA;
		clone.destination = new String(destination);
		clone.length = length;
		clone.hopLimit = hopLimit;
		clone.creationTime = creationTime;
		clone.recordRoute = recordRoute;
		clone.linkState = linkState;
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
		StringBuilder buf = new StringBuilder();
		buf.append(id);
		buf.append(" - ");
		buf.append(type.toString());
		buf.append(". Src: ");
		buf.append(source);
		buf.append(", tg: ");
		buf.append(destination);
		buf.append(", proc: ");
		buf.append(procNode);
		buf.append(", hop limit: ");
		buf.append(hopLimit);
		buf.append(", length: ");
		buf.append(length);
		buf.append(", created: ");
		buf.append(creationTime);
		if (recordRoute)
			buf.append(" [RR]");
		buf.append(". Path: ");
		buf.append(path.toString());
		buf.append(", node LSA: ");
		buf.append(nodeLSA);
		buf.append("\n");
		buf.append("linkState: ");		
		buf.append("\n");
		buf.append(linkState);
		if (content != null) {
			buf.append(". Content: ");
			buf.append(content.toString());
		}
		return buf.toString();
	}

	@Override
	public int compareTo(LSA lsa) {
		if (Integer.parseInt(lsa.getNodeLSA()) < Integer.parseInt(this.nodeLSA)) {
			return 1;
		} else if (Integer.parseInt(lsa.getNodeLSA()) > Integer.parseInt(this.nodeLSA)){
			return -1;
		} else {
			return 0;
		}
	}
		

}
