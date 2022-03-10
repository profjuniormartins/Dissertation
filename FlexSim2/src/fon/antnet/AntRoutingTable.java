/*
 * Created on 15/02/2008.
 */
package fon.antnet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.RoutingTableEntry;
import ant.Ant;
import ant.LocalParametricView;
import ant.StatisticalParametricModel;
import net.LocalRoutingTable;
import net.Message;
import ant.NeighborAttr;
import fon.FlexiLink;
import fon.RSVP;
import fon.antnet.AntHeuristic.Heuristic;
import graph.Graph;

/**
 * Routing table using AntNet characteristics.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class AntRoutingTable extends LocalRoutingTable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The power factor to enhance the difference in the heuristics correction. */ 
	protected double powerFactor;
	/** Indicates if the routing of the RSVP messages are deterministic or stochastic. True means deterministic. */
	protected boolean deterministic;
	/** Maximum allowed reinforcement, to prevent pheromone values going to 100% .*/
	public static final double MAX_REINFORCEMENT = 0.9;
	/** The confidence level. */
	protected double confidence;
	/** The z-factor derived from the confidence coefficient level. */
	protected double zFactor;
	/** The constant that weights the best value in the reinforcement equation. */
	protected double c1;
	/** The constant that weights the confidence interval in the reinforcement equation. */
	protected double c2;
	/** The amplifier of the squash function. */
	protected double amplifier;
	/** It weights the relative importance of the heuristic correction 
	 * with respect to the pheromone values stored in the routing table.*/
	protected double alpha;	
	/**The heuristic adopted */
	protected Heuristic heuristic;
	
	/**
	 * Creates a new AntNetCrankRoutingTable object.
	 * @param source The id of the node associated with this routing table.
	 * @param aConfidence The confidence level.
	 * @param firstWeight The constant that weights the best value in the reinforcement equation.
	 * @param secondWeight The constant that weights the confidence interval in the reinforcement equation.
	 * @param aAmplifier The amplifier of the squash function.
	 * @param alpha It weights the relative importance of the heuristic correction 
	 * @param factor Enhances the difference of available wavelengths in the heuristic calculation
	 * @param aDeterministic Indicates if the routing of the RSVP messages are deterministic or stochastic.
	 * @param aHeuristic Indicates the heuristic adopted.
	 */
	public AntRoutingTable(String source, double aConfidence, double firstWeight, double secondWeight, double aAmplifier, double aAlpha, double factor, boolean aDeterministic, Heuristic aHeuristic) {
		super(source);
		this.confidence = aConfidence;
		zFactor = 1.0 / (Math.sqrt(1.0 - confidence));
		this.c1 = firstWeight;
		this.c2 = secondWeight;
		this.amplifier = aAmplifier;
		this.alpha = aAlpha;
		this.powerFactor = factor;
		this.deterministic = aDeterministic;
		this.heuristic = aHeuristic;
	}		
	
	/**
	 * Gets the next hop, using local heuristics variables, i.e., the status of the 
	 * free wavelengths.
	 * @param ant The ant to be processed.
	 * @param links The state of the neighbor links.
	 * @return The next hop, using local heuristics variables.
	 */
	public String nextHop(Message ant, LinkedHashMap<String,FlexiLink> flexiLinks) {
		//Get the target node
		String dest = ant.getDestination();
		//Get the appropriate destination routing table
		Integer index = destinationMap.get(dest);
		if (index == null) //removed node from the topology
			return null;
		RoutingTableEntry neighborhood = destination[index];
		return AntFON.select(neighborhood,flexiLinks,(Ant)ant,alpha,powerFactor, heuristic);
	}

	/**
	 * Gets the next hop for the packet.
	 * @param packet The specified packet.
	 */
	public String nextHop(Message msg, ArrayList<String> history) {
		//Get the target node
		String dest = msg.getDestination();
		//Get the appropriate destination routing table
		Integer index = destinationMap.get(dest);
		if (index == null) { //removed node from the topology
			//System.out.println(packet.toString());
			return null;
		}
		RoutingTableEntry neighborhood = destination[index];
		//Gives the appropriate processing to the RSVP packet
		if (deterministic)
			return AntFON.select(neighborhood,(RSVP)msg,history);
		else 
			return AntFON.select(neighborhood,msg);
	}

	/**
	 * Updates the pheromone routing table using the informations contained
	 * in the backward ant and in the local parametric model. 
	 * @param ant The backward ant responsible for updating the node.
	 * @param view The local parametric view.
	 */
	public void update(Ant ant, StatisticalParametricModel model) {
		//Gets the ant target
		String dest = ant.getDestination();
		//Gets the processing node 
		String procId = ant.getProcNode();
		//Get the list of possible destination nodes to sub-path updating
		List<String> subPath = ant.getSubPath();
		//For each node in the subpath
		for (String nodeId:subPath) {
			//Get the appropriate view
			LocalParametricView view = model.get(nodeId);
			//Verify if it is good to update the sub-path
			double upperCondidenceInterval = view.getAverage() + zFactor * (view.getDeviation() / Math.sqrt((double)view.getWindow()));
			//Get the traveling value of the subpath
			double pathValue = (double)ant.getSubPathLength(procId,nodeId);
			//If it is a good sub-path or it is the "true" target node do
			if ((pathValue < upperCondidenceInterval) || dest.equals(nodeId)) {
				//Updates the local view
				model.update(pathValue,nodeId);
				view = model.get(nodeId);
				//Gets the appropriate routing table 
				int index = destinationMap.get(nodeId);
				RoutingTableEntry rTable = destination[index];
				int neighborhoodSize = rTable.size();
				//Gets the reinforcement value.
				double reinforcement = this.getReinforcement(ant,view,neighborhoodSize,nodeId);
				//System.out.println("---reinforcement: "+ reinforcement);
				//Gets the node who is the one chosen as forward node
				String forwardId = ant.getForwardNode();
				//For each neighbor do		
				for (String neighId : rTable.neighborhood()) {
					//Gets the old pheromone level
					NeighborAttr attr = (NeighborAttr)rTable.getEntry(neighId);
					double oldLevel = attr.getPheromoneLevel();
					double newLevel; //New pheromone level
					if (neighId.equals(forwardId)) { //Positive reinforcement
						newLevel = oldLevel + (reinforcement * (1.0 - oldLevel));
					} else { //Negative reinforcement
						newLevel = oldLevel - (reinforcement * oldLevel);
					}
					//Set the new level and update the pheromone routing table
					attr.setPheromoneLevel(newLevel);
					rTable.putEntry(neighId,attr);
				}
			}
		}		
	}
	
	/**
	 * Constructs the initial neighborhood from the initial topology or
	 * updates the neighbors changes from topology. 
	 * It assumes a neighbor discovery or failure detection
	 * mechanisms in the control plane. 
	 * @param graph The actual topology of the network.
	 */
	public void updateFromTopology(Graph graph) {
		if ((localGraph != null) && localGraph.equals(graph))
			return; //nothing to do
		super.updateFromTopology(graph);
		//for each destination of the routing table do
		for (Object destinationId : destinationMap.keySet()) {
			int index = destinationMap.get(destinationId);
			RoutingTableEntry rTable = destination[index];
			//Find the neighbors that must be updated
			ArrayList<String> update = new ArrayList<String>();  
			//The sum of the probabilities of all neighbors.
			double sum = 0;
			//for each neighbor link of the destination do			
			for (String neighId : rTable.neighborhood()) {
				if (rTable.getEntry(neighId) == null) { 
					update.add(neighId); //add the id of the neighbor to the vector
				} else { //valid neighbor object
					sum = sum + ((NeighborAttr)rTable.getEntry(neighId)).getPheromoneLevel();
				}
			}
			//Initialize or update the routing table
			int size = update.size(); //number of neighbors to be updated. 
			if (size == rTable.size()) { //Initialization of the routing table
				if (update.contains(destinationId)) {  //Inteligent initialization
					//for each new neighbor link of the destination do			
					for (Object neighId : update) { 
						//Create a new Neighbor object
						NeighborAttr neigh = new NeighborAttr(neighId);
						if (destinationId.equals(neighId)) { //Neighbor == destination
							double level = (1.0/((double)size)) + 1.5 * (((double)size - 1.0) / ((double)size*size));
							neigh.setPheromoneLevel(level);
						} else { //Rest of neighbors
							if (size == 1) {
								neigh.setPheromoneLevel(0.0);
							} else {
								double level = (1.0/((double)size)) - 1.5 * (1.0 / ((double)size*size));
								neigh.setPheromoneLevel(level);
							}
						}
						//Update the neighbor link information
						rTable.putEntry(neighId,neigh);
					}
				} else { //Conventional initialization
					//for each new neighbor link of the destination do			
					for (String neighId : update) {
						//Create a new Neighbor object
						NeighborAttr neigh = new NeighborAttr(neighId);
						//Normalize the feromone level for each neighbor of this node					
						neigh.setPheromoneLevel(1.0/((double)size));
						//Update the neighbor link information
						rTable.putEntry(neighId,neigh);
					}
				}
			} else if(size > 0) { //Update of just few new (added) neighbors. 
				//for each new neighbor link of the destination do			
				for (String neighId : update) {
					//Create a new Neighbor oject
					NeighborAttr neigh = new NeighborAttr(neighId);
					//Set the pheromone level for each new neighbor of this node to zero.					
					neigh.setPheromoneLevel(0.0);
					//Update the neighbor link information
					rTable.putEntry(neighId,neigh);					
				}				
			} else { //Check for removed link or nodes (use of the Baran & Sosa algorithm)
				//for each neighbor link of the destination do			
				for (String neighId : rTable.neighborhood()) {
					NeighborAttr neigh = (NeighborAttr) rTable.getEntry(neighId);
					//Get the actual pheromone level
					double pLevel = neigh.getPheromoneLevel();
					//Distribute the remaing level among the other neighbors, i.e., the total sum od
					//the pheromone levels is now equal to one.
					neigh.setPheromoneLevel(pLevel/sum);
				}				
			}
		}		
	}

	/**
	 * Returns the adaptive reinforcement given by the backward ant in respect
	 * to the local parametric model of the node.
	 * @param ant The backward ant;
	 * @param view The local parametric view associated with the target of the backward ant.
	 * @param size The size of the neighborhood.
	 * @param destId The identification of the destination node
	 * @return The adaptive reinforcement value.
	 */
	protected double getReinforcement(Ant ant, LocalParametricView view, int size, String destId) {
		//The reinforcement given by the specified ant.
		double reinforcement;
		String procId = ant.getProcNode();
		double subPath = (double) ant.getSubPathLength(procId,destId); 
//		System.out.println("SubPath: "+subPath);
//		System.out.println("View: "+view.toString());
		double firstTerm = (view.getBest() / subPath);
		double upperInterval = view.getAverage() + zFactor * (view.getDeviation() / Math.sqrt((double)view.getWindow()));
//		System.out.println("upperInterval: "+upperInterval);
		double denominator = ((upperInterval - view.getBest()) + (subPath - view.getBest()));
		double secondTerm = (upperInterval - view.getBest()) / denominator;
//		System.out.println("SecondTerm: "+secondTerm);
		if (denominator == 0.0) { //Singularity problems!
			reinforcement = firstTerm;
//			System.out.println(".....");
		} else { //Normal case
			reinforcement = this.c1 * firstTerm + this.c2 * secondTerm;
		}
		//Now uses the squash function to compress the lower scale
		reinforcement = squash(reinforcement,size);
		//Verifies if the reinforcement is above the maximum level
//		System.out.println("Pheromone reinforcement: "+reinforcement);
		if (reinforcement > MAX_REINFORCEMENT) {
			return MAX_REINFORCEMENT;
		} else {
			return reinforcement;
		}
	}
	
	/**
	 * Squash function.
	 * @param value The value to be squashed.
	 * @param neighborhoodSize The number of neighbors 
	 * @return The squashed value.
	 */
	protected double squash(double value, int neighborhoodSize) {
		return 1.0 / (1.0 + Math.exp(amplifier / (value * neighborhoodSize))); 
	}
	
}
