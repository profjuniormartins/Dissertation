/*
 * Created on 2013-03-06 by Pavani. 
 */
package net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import random.MersenneTwister;
import main.Accounting;
import main.Config;
import event.Event;
import event.EventGenerator;
import event.EventSubscriber;
import graph.Graph;

/**
 * Generic framework for network simulation.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public abstract class Network {
	/** The random generator. */
	protected static MersenneTwister random;
	/** The XML configuration file. */
	protected transient Config config;
	/** The simulation parameters. */
	protected LinkedHashMap<String,ArrayList<String>> parameters;
	/** The accounting class. */
	protected Accounting accounting;
	/** The physical topology of the network. */
	protected static Graph graph;

	/**
	 * Creates a new Network object.
	 * @param simParameters The simulation parameters.
	 * @param aAccounting The accounting class.
	 */
	@SuppressWarnings("static-access")
	public Network(Config aConfig, Accounting aAccounting) {
		this.config = aConfig;
		this.parameters = config.getSimulationParameters();
		this.graph = config.getGraph(); //gets the graph
		this.accounting = aAccounting;
		ArrayList<String> seed = parameters.get("/Main/Network/@seed");
		if (seed != null) 
			random = new MersenneTwister(Long.parseLong(seed.get(0)));
		else 
			random = new MersenneTwister();
	}

	/**
	 * Process the specified event.
	 * @param event The event to be processed.
	 * @return The response for the event processed. Null, if no response is returned.
	 */
	public abstract Event process(Event event);
	
	/**
	 * Create a traffic event subscriber of this network.
	 * @param nameClass The name of the traffic event subscriber.
	 * @index The index of the class for the same name occurrences.
	 * @return A traffic event subscribers of this network.
	 */
	public abstract EventSubscriber createTrafficSubscriber(String nameClass, int index);
	
	
	/**
	 * Return the others events created at the initialization of the network simulation.
	 * @return The others events created at the initialization of the network simulation.
	 */
	public abstract ArrayList<Event> getOtherEvents();

	/**
	 * Update values of this network, after a run is finished.
	 */
	public abstract void updateValues();
	
	/**
	 * Toss a random source node.
	 * @param The list of nodes of this network.
	 * @return A random source node.
	 */	
	public static String getSourceNode() {
		return getSourceNode(random);
	}

	/**
	 * Toss a random destination node, different from the source node.
	 * @param sourceNode The source node.
	 * @param The list of nodes of this network.
	 * @return A random destination node.
	 */	
	public static String getDestinationNode(String sourceNode) {
		return getDestinationNode(random,sourceNode);
	}
	
	/**
	 * Toss a random source node.
	 * @param rng Random number generator.
	 * @return A random source node.
	 */	
	public static String getSourceNode(MersenneTwister rng) {
		int size = graph.size();
		double[] probabilityDistribution = new double[size];
		//Normalize the weights
		for (int i=0; i < size; i++) {
			probabilityDistribution[i] = 1.0 / ((double)size);
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return graph.getNode(n);		
	}
	
	/**
	 * Toss a random destination node, different from the source node.
	 * @param rng Random number generator.
	 * @param sourceNode The source node.
	 * @return A random destination node.
	 */	
	public static String getDestinationNode(MersenneTwister rng, String sourceNode) {
		int size = graph.size();
		double[] probabilityDistribution = new double[size];
		int index = graph.getNodeIndex(sourceNode);
		//Normalize the weights
		for (int i=0; i < size; i++) {
			if (i != index) {
				probabilityDistribution[i] = 1.0 / ((double)(size-1)); 
			} else {
				probabilityDistribution[i] = 0.0;
			}
		}
		//Spins the wheel
		double sample = rng.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}
		return graph.getNode(n);		
		
	}
	
	/**
	 * Returns True, if the two points are connected by a link. False, otherwise.
	 * @param sourceId The source node of the link.
	 * @param destinationId The destination node of the link.
	 * @return True, if the two points are connected by a link. False, otherwise.
	 */
	public static boolean hasConnectivity(String sourceId, String destinationId) {
		return graph.hasEdge(sourceId,destinationId);
	}

	/**
	 * Convert String do Double Array.
	 * @param text The text(String) that will be converted to a double array.
	 * @return array The array of Doubles extracted from String
	 */
	// Convert String do Double Array
	public static double[] stringToDoubleArray (String text) {
		double[] array = Arrays.stream(text.substring(0,text.length()).split(",")).mapToDouble(Double::parseDouble).toArray();
		return array;		
	}
	
	/**
	 * Converts String to a Double Array with the weights of each bandwidth.
	 * @param text The text(String) that will be converted to a double array of weights.
	 * @return array The array of Weighted Double Array extracted from String
	 */
	protected static double[] stringToWeightedDoubleArray (String text) {
		double[] array = Arrays.stream(text.substring(0,text.length()).split(",")).mapToDouble(Double::parseDouble).toArray();
		double sum = 0.0;
		for (double s: array)
			sum = sum + s;
		for (int i = 0; i<array.length; i++) {
			array[i] = array[i]/sum;
		}		
		return array;		
	}
	

}
