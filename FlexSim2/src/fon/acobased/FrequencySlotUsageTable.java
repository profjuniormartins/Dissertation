/*
 * Created on 14/02/2008.
 */
package fon.acobased;

import graph.Graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class represents the local statistics of the wavelength usage,
 * concerning all destinations of an optical node.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class FrequencySlotUsageTable implements Serializable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;	
	/** Mapping between nodes and values. */
	protected HashMap<String,Integer> map;
	/** Wavelength usage values. */
	FrequencySlotUsage[] lambdaTable;
	/** The physical topology. */
	protected Graph graph;
	/** The identification of this node. */
	protected String id;

	/** 
	 * Creates a new WavelengthUsageTable object.
	 * @param aId The id of the node that belongs this model.
	 * @param aGraph The physical topology of the network.
	 * @param window The size of the window.
	 */
	public FrequencySlotUsageTable(String aId, Graph aGraph, int wavelength, int window, boolean sliding) {
		this.id = aId;
		this.graph = aGraph;
		map = new HashMap<String,Integer>();
		lambdaTable = new FrequencySlotUsage[graph.size()];
		ArrayList<String> nodes = graph.nodes();
		for (int index=0; index < graph.size();index++) {
			String nodeId = nodes.get(index); 
			map.put(nodeId.toString(),index);
			if (!nodeId.equals(id)) {
				lambdaTable[index] = new FrequencySlotUsage(wavelength,window,sliding); 
			} else {
				lambdaTable[index] = null;
			}
		}
	}
	
	/**
	 * Gets the wavelength usage associated to the specified destination.
	 * @param destination The id of the destination of the node.
	 * @return The wavelength usage associated to the specified destination.
	 */
	public FrequencySlotUsage get(String destination) {
		int index = map.get(destination);
		return lambdaTable[index];
	}
	
	public FrequencySlotUsage[] getArray() {
		return this.lambdaTable;
	}
	
	/**
	 * Update the local table associated with the specified node.
	 * @param usage The wavelength usage collected by the backward ant.
	 * @param nodeId The specified node.
	 */
	public void update(int[] usage, String nodeId) {
		int index = map.get(nodeId);
		FrequencySlotUsage wu = lambdaTable[index];
		wu.addUsage(usage);
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Id: ");
		builder.append(id);
		builder.append("\n");
		ArrayList<String> nodes = graph.nodes();
		for (int index=0; index < nodes.size(); index ++) {
			String key = nodes.get(index);
			builder.append(key);
			builder.append(" - ");
			FrequencySlotUsage wu = lambdaTable[index];
			builder.append(wu.toString());
			builder.append("\n");
		}
		return builder.toString();
	}
}
