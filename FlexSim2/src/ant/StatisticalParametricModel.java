/*
 * Created on 2013-09-25 by Pavani. 
 */
package ant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import graph.Graph;

/**
 * It is a table that maintains estimates to other nodes in the network.
 * 
 * @see Di Caro G. A. "Ant Colony Optimization and its application to adaptive routing in telecommunication networks",
 * PhD thesis in Applied Sciences, Polytechnic School, Universite Libre de Bruxelles, Brussels, Belgium, 2004.
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public class StatisticalParametricModel {
	/** The table containing for each destination the local parametric view. */
	HashMap<String, LocalParametricView> model;
	/** The identification of the node that this table belongs to. */
	String nodeId;
	/** The exponential factor. */
	double exponentialFactor;
	/** The window reduction factor. */
	double reductionFactor;
	/** The z-factor derived from the confidence coefficient level. */
	protected double zFactor;
	/** The physical topology. */
	protected Graph graph;
	/** Statistical parametric model values. */
	protected LocalParametricView[] parametricModel;
	/**
	 * Creates a new StatisticalParametricModel object.
	 * @param aNodeId The node identification.
	 * @param aExponentialFactor The exponential factor.
	 * @param aReductionFactor The window reduction.
	 * @param aConfidence The confidence coefficient level for determining statistically good solutions.
	 */
	public StatisticalParametricModel(String aNodeId, double aExponentialFactor, double aReductionFactor, double aConfidence) {
		model = new HashMap<String, LocalParametricView>();
		nodeId = aNodeId;
		exponentialFactor = aExponentialFactor;
		reductionFactor = aReductionFactor;
		zFactor = 1.0 / (Math.sqrt(1.0 - aConfidence));
	}
	
	/**
	 * Creates a new StatisticalParametricModel object
	 * @param aId The id of the node that belongs this model.
	 * @param aGraph The physical topology of the network.
	 * @param exponentialFactor The exponential factor.
	 * @param reductor The window reduction.
	 */
	public StatisticalParametricModel(String aId, Graph aGraph, double exponentialFactor, double reductor) {
		this.nodeId = aId;
		this.graph = aGraph;
		this.exponentialFactor = exponentialFactor;
		this.reductionFactor = reductor;
		model = new HashMap<String, LocalParametricView>();
		parametricModel = new LocalParametricView[graph.size()];
		ArrayList<String> nodes = graph.nodes();
		for (int index=0; index < graph.size();index++) {
			String id = nodes.get(index); 
			model.put(id.toString(),parametricModel[index]);
			if (!id.equals(nodeId)) {
				parametricModel[index]= new LocalParametricView(exponentialFactor,reductor);
			} else {
				parametricModel[index] = null;
			}
		}
	}
	

	/**
	 * Adds a new entry for the specified destination.
	 * @param dest The destination identification.
	 */
	public void addDestination(String dest) {
		//Create a new entry
		LocalParametricView view = new LocalParametricView(exponentialFactor, reductionFactor);
		//Add it to the table
		model.put(dest, view);
	}
	
	/**
	 * Removes the entry relative to the specified destination.
	 * @param dest The destination identification.
	 */
	public void removeDestination(String dest) {
		model.remove(dest);
	}
	
	/**
	 * Returns the set of destinations of this model.
	 * @return The set of destinations of this model.
	 */
	public Set<String> getDestinations() {
		return model.keySet();
	}
	
	/**
	 * Returns the local parametric view associated to the specified destination.
	 * @param dest The destination identification.
	 * @return The local parametric view associated to the specified destination.
	 */
	public LocalParametricView get(String dest) {
		//Get the local view
		LocalParametricView view = model.get(dest);
		//if null, then initialize it
		if (view == null) {
			this.addDestination(dest);
			view = model.get(dest);
		}
		return view;
	}
	
	/** Update the local model associated with the specified node.
	 * @param metric The value of the metric.
	 * @param dest The specified node destination.
	 */
	public void update(double metric, String dest) {
		//Get the entry
		LocalParametricView view = this.get(dest);
		//Update it
		view.update(metric);
	}
	
	/**
	 * Calculates the approximate upper confidence interval derived from Tchebycheff inequality.
	 * @param dest The destination node.
	 * @return
	 */
	public double getUpperConfidenceInterval(String dest) {
		//Get the entry
		LocalParametricView view = this.get(dest);
		//Calculate the confidence interval
		double confidenceInterval = view.getAverage() + zFactor * (view.getDeviation() / Math.sqrt((double)view.getWindow()));
		//Return the value
		return confidenceInterval;
	}
	
	/**
	 * @return Returns the exponential factor.
	 */
	public double getExponentialFactor() {
		return exponentialFactor;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String dest: model.keySet()) {
			builder.append("Dest: ");
			builder.append(dest);
			builder.append(" - ");
			builder.append(this.get(dest).toString());
			builder.append("\n");
		}
		return builder.toString();
	}
}
