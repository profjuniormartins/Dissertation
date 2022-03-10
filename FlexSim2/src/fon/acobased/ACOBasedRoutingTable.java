/*
 * Created on 01/05/2020.
 */
package fon.acobased;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import net.RoutingTableEntry;
import net.LocalRoutingTable;
import net.Message;
import ant.NeighborAttr;
import fon.FlexiLink;
import fon.LabelSet;
import fon.RSVP;
import graph.Graph;

/**
 * Routing table using ACO Based Algorithm characteristics.
 */
public class ACOBasedRoutingTable extends LocalRoutingTable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The balance factor*/
	protected double balanceFactor;
	/** The length reinforcement*/
	protected double lengthReinforcement;
	/** The contiguous reinforcement*/
	protected double contiguousReinforcement;
	/** The Sigma_S heuristic */
	protected int heuristicSigmaS;
	/** The ant heuristic */
	protected int heuristicAnt;
	/** The corrections (alphas) parameter for routing forward ants (for pheromone and free slots). */
	protected double correctionPheromone;
	protected double correctionFreeSlots;

	
	/**
	 * Creates a new ACOBasedRoutingTable object.
	 * @param source The id of the node associated with this routing table.
	 * @param aBalanceFactor The balance factor.
	 * @param aLengthReinforcement The length Reinforcement.
	 * @param aContiguousReinforcement The contiguous reinforcement.
	 * @param aHeuristicSigmaS The Sigma_S heuristic 
	 * @param int aHeuristicAnt The ant next hop heuristic
	 * @param aCorrectionPheromone The correction (alpha) parameter for routing forward ants for pheromone 
	 * @param aCorrectionFreeSlots The correction (alpha) parameter for routing forward ants for free slots
	 */
	public ACOBasedRoutingTable(String source, double aBalanceFactor, double aLengthReinforcement, double aContiguousReinforcement, int aHeuristicSigmaS, int aHeuristicAnt, double aCorrectionFreeSlots, double aCorrectionPheromone) {
		super(source);
		this.balanceFactor= aBalanceFactor;
		this.lengthReinforcement = aLengthReinforcement;
		this.contiguousReinforcement = aContiguousReinforcement;
		this.heuristicSigmaS = aHeuristicSigmaS;
		this.heuristicAnt = aHeuristicAnt;
		this.correctionFreeSlots = aCorrectionFreeSlots;
		this.correctionPheromone = aCorrectionPheromone;		
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
			return ACOBasedFON.select(neighborhood,(RSVP)msg,history);
	}
	
	/**
	 * Gets the next hop for the ant forward.
	 * @param ant The specified ant.
	 */
	public String nextHop(ACOBasedAnt ant, LinkedHashMap<String,FlexiLink> flexiLinks) {
		//Get the target node
		String dest = ant.getDestination();
		//Get the appropriate destination routing table
		Integer index = destinationMap.get(dest);
		if (index == null) { //removed node from the topology
			//System.out.println(packet.toString());
			return null;
		}
		RoutingTableEntry neighborhood = destination[index];
		//According to the selection of the heuristic defines the next hop
		if (heuristicAnt == 1) { //Original heuristic of the article
			return ACOBasedFON.selectAntNextHop(neighborhood,ant,flexiLinks);
		}else if (heuristicAnt ==2){ //Proposed heuristic 
			return ACOBasedFON.selectAntNextHop2(neighborhood,ant,flexiLinks,correctionPheromone,correctionFreeSlots);
		}else {
			System.err.println("Ant Heuristic not selected");
			return null;
		}
		
	}
		
	

	/**
	 * Updates the pheromone routing table using the informations contained
	 * in the ant.
	 * @param ant The ant responsible for updating the node.
	 * @param view The local parametric view.
	 */
	public void update(ACOBasedAnt ant) {

		String source = ant.getSource();
//		
		//Gets the appropriate routing table 
		Integer index = destinationMap.get(source);
//		System.out.println("index: "+ index);
		RoutingTableEntry rTable = destination[index];
				
		//Calculate the fn value (the number of free frequence slots)
		LabelSet labelSet = new LabelSet(ant.spectrumFragmentation);
		//System.out.println(labelSet);
		double fn = labelSet.getFN();
		//System.out.println("FN (free slots): "+ fn);
		//Get the fcn value (the number of joints between the free frequence slots)
		double fcn = labelSet.getFCN();		
		//System.out.println("FCN (joints between free slots): "+ fcn);
		//Get the F value (total frequence slots number in the spectrum)
		double F = ant.spectrumFragmentation.length;
		//System.out.println("F (total frequence slots): "+ F);
		//Get the fmcn value (the max value of slots contiguous in the spectrum)
		double fmcn = labelSet.biggestContiguousFreeBandwidth() / FlexiLink.NOMINAL_CENTRAL_FREQUENCY_GRANULARITY ;
		//Ant Path Length
		double antPathLength = ant.getPathLength();
		//System.out.println("antPathLength: "+ antPathLength);
		//Sigma_l
		double sigma_l = lengthReinforcement * ((Math.exp(-1/antPathLength)) - (Math.exp(-1)));
		//System.out.println("sigma_l: "+sigma_l);
		//Sigma_s heuristic (heuristic 1 from article or 2 proposed modification)
		double sigma_s = 0;				
		if (heuristicSigmaS == 1){			
			if (fn != 0) {
				sigma_s = Math.exp( contiguousReinforcement * ((fn/F)+(fcn/fn)) ) - 1;
			} else {
				sigma_s = Math.exp(0) - 1;
			}
		}
		if (heuristicSigmaS == 2) {
			if (fn != 0) {
				sigma_s = Math.exp( contiguousReinforcement * ((fn + fmcn)/F) ) - 1;
			} else {
				sigma_s = Math.exp(0) - 1;
			}
		}		
		//Calculates the reinforcement value.				
		double reinforcement = balanceFactor * sigma_l + (1 - balanceFactor) * sigma_s ;
		
		//System.out.println("sigma_l: "+sigma_l);
		//System.out.println("sigma_s: "+sigma_s);
		//System.out.println("Reinforcement: "+reinforcement);
				
		String backwardId = ant.getBackwardNode();
		//System.out.println("backwardID: "+backwardId);
		
		//For each neighbor do		
		for (String neighId : rTable.neighborhood()) {			
			//Gets the old pheromone level
			NeighborAttr attr = (NeighborAttr)rTable.getEntry(neighId);
			double oldLevel = attr.getPheromoneLevel();
			//System.out.println("oldLevel: "+oldLevel);
			double newLevel; //New pheromone level
			//System.out.println("Vizinho: "+ neighId + " - último nó visitado: "+backwardId);
			if (neighId.equals(backwardId)) { //Positive reinforcement
				newLevel = (oldLevel + reinforcement) / (1.0 + reinforcement); 

			} else { //Negative reinforcement
				newLevel = (oldLevel) / (1.0 + reinforcement);	

			}
			//if (Double.isNaN(newLevel)) 
				//System.out.println("NaN");			
			//Set the new level and update the pheromone routing table
			attr.setPheromoneLevel(newLevel);
			rTable.putEntry(neighId,attr);
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

	
}
