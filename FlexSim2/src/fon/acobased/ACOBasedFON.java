/*
 * Created on 01/05/2020.
 */

package fon.acobased;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import net.Message;
import ant.NeighborAttr;
import random.MersenneTwister;
import fon.Connection;
import fon.FixedRequestTraffic;
import net.Error;
import fon.Request;
import fon.FlexiLink;
import fon.NonUniformRequestTraffic;
import fon.RSVP;
import fon.topological.FlexiNode;
import fon.topological.FlexiNode.ReRouting;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import fon.antnet.AntHeuristic;
import util.QuickSort;
import event.Event;
import event.EventSubscriber;
import graph.Graph;
import main.Config;
import net.Network;
import net.Node;
import net.Link;
import net.RoutingTableEntry;
import main.Accounting;

/**
 * This class is a control plane for the Aco Based algorithm
 */
public class ACOBasedFON extends Network {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** Workaround for transporting static fields. */
	StaticTransporter transporter;
	/** The set of links of this network. */
	protected LinkedHashMap<String, FlexiLink> links;	
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String,ACOBasedFlexiNode> nodes;
	/** The maximum hop limit for a packet. */
	protected int ttl;
	/** The number of slots at each flexi-grid link. */
	protected int slots;	
	/** The time of the last event. */
	protected double lastTime;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
	/** The delay for resending a path message for restoring LSP. */
	protected double holdoff;
	/** The rate for launching ants during the hold-off timer. */
	protected double restoreAntRate; 
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Maximum number of re-routing attempts allowed. */
	protected int maxReroutingAttempts;	
	/** Number of re-routing attempts per LSR. */
	protected int reroutingAttempts;
	/** The chosen wavelength assignment algorithm. */
	protected SpectrumAssignment.Strategy sa;
	/** The length in bytes for the identification of a link or a node. */
	protected int identificationLength;
	/** The collection of Connection successfully re-routed after a failure. */
	protected Hashtable<String,Connection> reroutedConnection;
	/** The collection of Connection disrupted by a failure, which
	 * are eligible for full re-routing. */
	protected Hashtable<String,Request> disruptedConnection;
	/** Random number generator for ants. */
	public static MersenneTwister rngAnt;
	/** Counters */
	int counterConnectionRequest;
	int counterConnectionEstablished;
	int counterConnectionTeardown;
	int counterConnectionFinished;
	int counterConnectionProblem;
	int counterAdmissionControlFailure;
	int counterLSPFailure;
	int counterRPProblem;
	int counterRPProblemLabelSet;
	int counterRPProblemNoRouteAvailable;
	int counterReroutingLimitExceeded;
	/** The logging generator. */
//	private static Logger logger = Logger.getLogger(AntNetCrankControlPlane.class.getName());

	/**
	 * Creates a new AntNetControlPlane object.
	 * @param aConfig The XML configuration file.
	 * @param aAccounting The accounting for the simulation results.
	 */
	public ACOBasedFON(Config aConfig, Accounting aAccounting) {
		super(aConfig, aAccounting);				
		//Create the nodes of this network
		nodes = new LinkedHashMap<String,ACOBasedFlexiNode>();
		//Create the links of this network
		links = new LinkedHashMap<String,FlexiLink>();
		//Create the storage of disrupted connections by failure
		reroutedConnection = new Hashtable<String,Connection>();
		disruptedConnection = new Hashtable<String,Request>();			
		//Get the links of the network graph
		LinkedHashMap<String,Link> graphLinks = config.getLinks();
		//Number of slots per link
		slots = Integer.parseInt(parameters.get("/RSA/Link/@slots").get(0));
		//For each link do
		for (String l: graphLinks.keySet()) {
			links.put(l,new FlexiLink(graphLinks.get(l), slots));
		}		
		//Reset the number of bytes accounted for each link
		for (FlexiLink link:links.values())
			link.getLink().resetCounter();
		//Get the configuration parameters
		ttl = Integer.parseInt(parameters.get("/RSA/Routing/@ttl").get(0));
		holdoff = Double.parseDouble(parameters.get("/Ant/Holdoff/@timer").get(0));
		restoreAntRate = Double.parseDouble(parameters.get("/Ant/Holdoff/@antRate").get(0));
		long seedAnt = Long.parseLong(parameters.get("/Ant/Seed/@value").get(0));
		rngAnt = new MersenneTwister(seedAnt);
		//The pheromone routing table parameters to ACO Based
		double balanceFactor = Double.parseDouble(parameters.get("/Ant/Pheromone/@balanceFactor").get(0));
		double lengthReinforcement = Double.parseDouble(parameters.get("/Ant/Pheromone/@lengthReinforcement").get(0));
		double contiguousReinforcement = Double.parseDouble(parameters.get("/Ant/Pheromone/@contiguousReinforcement").get(0));
		int heuristicSigmaS = Integer.parseInt(parameters.get("/Ant/Pheromone/@heuristicSigmaS").get(0));
		int heuristicAnt = Integer.parseInt(parameters.get("/Ant/Pheromone/@heuristicAnt").get(0));
		int timeUpdateRT = Integer.parseInt(parameters.get("/Ant/UpdateRoutingTable/@time").get(0));
		//Gets the corrections (alphas) parameter for routing forward ants.
		double correctionFreeSlots = Double.parseDouble(parameters.get("/Ant/Routing/@correctionFreeSlots").get(0));
		double correctionPheromone = Double.parseDouble(parameters.get("/Ant/Routing/@correctionPheromone").get(0));		
		//Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get("/Outputs/Transient/@timeSlice").get(0));
		actualTimeSlice = timeSlice;
		//Get details about the RSA algorithm used
		rerouting = ReRouting.valueOf(parameters.get("/RSA/Routing/@rerouting").get(0));
		reroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@attempts").get(0));
		maxReroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
		sa = Strategy.valueOf(parameters.get("/RSA/SA/@strategy").get(0));
		//The bandwidhts
		double[] array = (Network.stringToDoubleArray(parameters.get("/RSA/Class/@bandwidth").get(0)));
		ArrayList<Double> bandwidths = new ArrayList<Double>();		
		for(double d: array) {
			bandwidths.add(d);		
		}
		//Initialize each network node
		//Initialize the state of each node
		for (String id: graph.nodes()) {
			//Create the pheromone routing table for this node
			ACOBasedRoutingTable artACOBased = new ACOBasedRoutingTable(id, balanceFactor, lengthReinforcement, contiguousReinforcement, heuristicSigmaS, heuristicAnt, correctionFreeSlots, correctionPheromone);
			artACOBased.updateFromTopology(graph);	
			//Create the links adjacent to this node.
			LinkedHashMap<String,FlexiLink> antLinks = new LinkedHashMap<String,FlexiLink>(); 
			ArrayList<String> adjacent = graph.adjacentNodes(id);
			//for each adjacent node do
			for (String adjId:adjacent) {
				FlexiLink antLink = links.get(id+"-"+adjId);
				antLinks.put(adjId.toString(),antLink);
			}
			//Create the node and put it into the table.
			ACOBasedFlexiNode colony = new ACOBasedFlexiNode(id,Node.Type.FIXED_ALTERNATE,artACOBased,antLinks,graph,rerouting,maxReroutingAttempts,reroutingAttempts,sa,timeUpdateRT);
			nodes.put(id,colony);
		}
	}

	/** Process the specified event.
	 * @param event The event to be processed.
	 * @return The processed event. Null, if nothing else is 
	 * to be returned to the scheduler.
	 */
	@Override
	public Event process(Event event) {
		//The id of the processing node
		String id;
		//Event response object
		Event response = null;
		//Update the time stamp of the last event to be processed processed.		
		lastTime = event.getTimeStamp(); 
		//Do transient accounting, if applicable
		if (lastTime > actualTimeSlice) {
			//Update the actual time slice
			actualTimeSlice = actualTimeSlice + timeSlice;
			System.out.print(".");
			//Updates the transient accounting, if applicable
			try {
				Method updateInstantaneous = accounting.getClass().getMethod("setInstantaneousValues",links.getClass());
				updateInstantaneous.invoke(accounting,links);
			}catch (Exception e) {} //do nothing - method not implemented					
		}
		//System.out.print(event);
		//For each event type
		switch (event.getType()) {
			case CONNECTION_REQUEST: //Connection request
				counterConnectionRequest ++;
				//Get the request
				Request request = (Request) event.getContent();
				//Send RSVP Path message
				RSVP rsvpPath = new RSVP(request,ttl,slots);
				//Create a new event for setting up the lightpath
				response = new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpPath);
				break;
			case CONNECTION_ESTABLISHED: //Connection established
				counterConnectionEstablished ++;
				RSVP rsvpConfirm = (RSVP) event.getContent();
				//Gets the connection object.
				Connection connectionEst = (Connection) rsvpConfirm.getContent();
				//Gets the duration of the lightpath
				double duration = connectionEst.getRequest().getDuration();
				//Accounts the successful lightpath establishment (event)
				accounting.addEvent(Accounting.Type.SUCCESS, event);				
				//See if it is a successful re-routing of a failed LSP
				if (rsvpConfirm.isReRouting()) {
					reroutedConnection.put(rsvpConfirm.getId(),connectionEst);
				}
				//System.out.println(event.toString());
				//Return a new event for tearing down the lightpath when appropriate
				response = new Event((event.getTimeStamp() + duration),Event.Type.CONNECTION_TEARDOWN,connectionEst);
				break;
			case CONNECTION_PROBLEM:
				counterConnectionProblem ++;
				RSVP rsvpErr = (RSVP) event.getContent();
				Request connectionRequest;
				//Get the error status
				Error error = rsvpErr.getError();
				RSVP rsvpRetry = null; //new Rsvp message
				Error.Code errorCode = error.getErrorCode();
				//Random time for solving race conditions in the Admission control failure. It also changes the initial time stamp to remove the bias in the setup time.
				double randomTime = 0.0;
				//Allocation of frequency slot contention problem
				if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {
					connectionRequest = (Request) ((Connection)rsvpErr.getContent()).getRequest();
					rsvpRetry = new RSVP(connectionRequest,ttl,slots);
					//System.out.println("Contention: "+lRequest.toString());
					if (disruptedConnection.containsKey(rsvpErr.getId())) {
						rsvpRetry.setReRouting(); //set the flag of re-routing
						rsvpRetry.setId(rsvpErr.getId()); //fix the id since it should contain the "r" suffix
						//System.out.println(event.toString());
					}
				counterAdmissionControlFailure  ++;
				randomTime = random.nextDouble(); //Adds a time between 0 and 1000 ms to break the race condition in concurrent setup messages
				//LSP failure forward or backward 	
				} else if(errorCode.equals(Error.Code.LSP_FAILURE)) {
					counterLSPFailure ++;
					Connection disrupted = (Connection)rsvpErr.getContent();
					connectionRequest = (Request) disrupted.getRequest();
					//reset the counter of retries
					connectionRequest.resetTry(); 
					//Calculates the rest of time of the connection
					double residualDuration = connectionRequest.getDuration() - (event.getInitialTimeStamp() - disrupted.getStartTime());
					//System.out.println("residual: "+residualDuration);
					connectionRequest.setDuration(residualDuration);
					//Create a new path message
					rsvpRetry = new RSVP(connectionRequest,ttl,slots);
					//Set the label indicating to tackle the failure
					rsvpRetry.setReRouting();
					//To avoid race conditions if the path_tear takes a different route and arrives after the restoring path message
					rsvpRetry.setId(connectionRequest.getId()+"r");
					//Adds the connection to the list of disrupted LSP 
					disruptedConnection.put(connectionRequest.getId()+"r",connectionRequest);
					//System.out.println("Adding LSP failure: "+rsvpErr.getFlowLabel()+" ,"+disrupted.toString());
				//Wavelength continuity constraint violated or no link available, use alternate path	
				} else if((errorCode.equals(Error.Code.RP_LABEL_SET)) || (errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE))) {
					counterRPProblem ++;
					if (errorCode.equals(Error.Code.RP_LABEL_SET)){
						counterRPProblemLabelSet ++;
					} else if (errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE)){
						counterRPProblemNoRouteAvailable ++;						
					}
					connectionRequest = (Request) rsvpErr.getContent();
					connectionRequest.addTry(); //add a try to the counter of tries		
					//Resend the request using holdoff-timer - Photonics Network Communications 2008 (Restoration)
					if (connectionRequest.tryAgain() && disruptedConnection.containsKey(rsvpErr.getId()) && this.rerouting.equals(ReRouting.END_TO_END)) { //resend the request
						rsvpRetry = new RSVP(connectionRequest,ttl,slots);
						if (disruptedConnection.containsKey(rsvpErr.getId())) {
							rsvpRetry.setReRouting(); //set the flag of re-routing
							rsvpRetry.setId(rsvpErr.getId()); //fix the id since it should contain the "r" suffix
							//System.out.println(event.toString());
							ArrayList<Event> multiple = new ArrayList<Event>();
							//add the hold-off timer for resending the message.
							multiple.add(new Event(holdoff+event.getTimeStamp(), event.getInitialTimeStamp(), Event.Type.MESSAGE_ARRIVAL,rsvpRetry));
							//System.out.println(event.toString());
							int times = (int)(holdoff * restoreAntRate);
							double delay = 0.0; //Delay between two consecutive ants
							int bytesHop = Integer.parseInt(parameters.get("/RSA/Overhead/@label").get(0));
							id = rsvpRetry.getId();
							for (int i=0; i < times; i++) {
								ACOBasedAnt ant = new ACOBasedAnt(id, connectionRequest.getSource(),connectionRequest.getDestination(),ttl,bytesHop);
								multiple.add(new Event(delay+event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,ant));
								delay = delay + (1.0 / restoreAntRate);
							}
							//Return the multiple events to the simulator
							return new Event(lastTime,Event.Type.MULTIPLE,multiple);												
						}
					} else { 
						//Accounts the failed connection request
						accounting.addEvent(Accounting.Type.FAILED, event);					
						//if (disruptedLSP.containsKey(rsvpErr.getFlowLabel())) 
							//System.out.println("Failed:"+event.toString());
					}					
				} else if(errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)){
					counterReroutingLimitExceeded ++;
					//Accounts the failed lightpath request
					//try {
					accounting.addEvent(Accounting.Type.FAILED, event);					
					//} catch(Exception e){System.err.println(event.toString());}
				}
				//Now, return the result.
				if (rsvpRetry != null)					
					response = new Event(event.getTimeStamp()+randomTime,event.getInitialTimeStamp()+randomTime, Event.Type.MESSAGE_ARRIVAL,rsvpRetry);
				else 
					return null;
				break;
			case CONNECTION_TEARDOWN: //Remove connectio
				counterConnectionTeardown ++;
				Connection connectionTear = (Connection) event.getContent();
				String connectionID = connectionTear.getId();
				if ((disruptedConnection.get(connectionID) == null) || ((reroutedConnection.get(connectionID) != null) && (reroutedConnection.get(connectionID).getPath().equals(connectionTear.getPath()) ) )) {
					//Send RSVP PathTear message
					RSVP rsvpTear = new RSVP(connectionTear,Message.Type.RSVP_PATH_TEAR,connectionTear.getSource(),connectionTear.getDestination());
					//System.out.println(rsvpTear.toString());
					return new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpTear);
				} else { //Ignore the teardown associated to a failed LSP, since it is already cleaned and rerouted.
					return null;
				}
			case CONNECTION_FINISHED: //Confirmation of connection removal
				counterConnectionFinished ++;
				//System.out.println(event.toString());
				accounting.addEvent(Accounting.Type.SUCCESS, event);	
				break;
			case MESSAGE_ARRIVAL: // Ant
				//Get the packet
				Message msg = (Message) event.getContent();
				//Get the node associated to this packet
				id = msg.getProcNode();
				//Give the packet to the right node
				FlexiNode procNode = nodes.get(id);
				if (procNode != null) { //Node functioning
					//Process the event	
					response = procNode.process(event);
					if (response.getType().equals(Event.Type.ANT_ROUTED)) {					
						accounting.addEvent(Accounting.Type.SUCCESS, event);				
						return null;
					} else if (response.getType().equals(Event.Type.ANT_KILLED)) {
						accounting.addEvent(Accounting.Type.FAILED, event);
						return null;
					} else if (response.getType().equals(Event.Type.IGNORE)) { 
						return null;
					} else {
						return response;
					}
				} else { //Failed node
					accounting.addEvent(Accounting.Type.FAILED, event);
					return null;					
				}			
			default: System.err.println("Unknown event: "+event.toString());
				return null;
		}

		return response;
	}

	/**
	 * Prints the last simulation time.
	 */
	public void updateValues() {
		accounting.setUtilization(links,lastTime,1); //bps.
		//accounting.setUtilization(links,lastTime,8); // 1 byte / sec
		System.out.println("LastTime: "+lastTime);
		System.out.println("Disrupted: "+this.disruptedConnection.keySet().toString());
		System.out.println("Total disrupted: "+disruptedConnection.size());
		System.out.println("Rerouted: "+reroutedConnection.keySet().toString());
		System.out.println("Total rerouted: "+reroutedConnection.size());
		//System.out.print("Not routed: ");
		//for (String conn : disruptedConnection.keySet()) {
			//if (!reroutedConnection.containsKey(conn)) {
				//System.out.print(conn+" ");
			//}
		//}
		System.out.println();		
		System.out.println("counterConnectionRequest: "+ counterConnectionRequest);
		System.out.println("counterConnectionEstablished: "+ counterConnectionEstablished);
		System.out.println("counterConnectionTeardown: "+ counterConnectionTeardown);
		System.out.println("counterConnectionFinished: "+ counterConnectionFinished);
		System.out.println("counterConnectionProblem: "+ counterConnectionProblem);
		System.out.println("- counterRPProblem: "+ counterRPProblem);
		System.out.println("-- counterRPProblemLabelSet: "+ counterRPProblemLabelSet);
		System.out.println("-- counterRPProblemNoRouteAvailable: "+ counterRPProblemNoRouteAvailable);
		System.out.println("- counterAdmissionControlFailure: "+ counterAdmissionControlFailure);
		System.out.println("- counterLSPFailure: "+ counterLSPFailure);
		System.out.println("- counterReroutingLimitExceeded: "+ counterReroutingLimitExceeded);
	}
	
	@Override
	public EventSubscriber createTrafficSubscriber(String nameClass, int index) {
		//Create the event subscriber
		EventSubscriber subscriber = null;
		//System.out.println("Class: "+nameClass);
		//for each class do		
		if (nameClass.equals("fon.NonUniformRequestTraffic")) {
			int tries = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));		
			long seed_bandwidth = Long.parseLong(parameters.get("/Generators/Traffic/@seed_bandwidth").get(index));
			double[] bandwidths = stringToDoubleArray(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			//System.out.println("Bandwiths: " + Arrays.toString(bandwidths));
			double[] ratios = stringToWeightedDoubleArray(parameters.get("/Generators/Traffic/@bandwidths_ratios").get(index));	
			//System.out.println("Probabilities: " + Arrays.toString(probabilities));
			subscriber = new NonUniformRequestTraffic(tries,seed_bandwidth,bandwidths,ratios);	
		} else if(nameClass.equals("fon.FixedRequestTraffic")) {
			int tries = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
			double bandwidth = Double.parseDouble(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			subscriber = new FixedRequestTraffic(tries,bandwidth);				
		} else if (nameClass.equals("fon.antnet.AntTraffic")) {
			int ttl = Integer.parseInt(parameters.get("/RSA/Routing/@ttl").get(0));
			long seed_ant = Long.parseLong(parameters.get("/Ant/Seed/@value").get(0));
			subscriber = new ACOBasedTraffic(ttl,seed_ant);
		} else if (nameClass.equals("fon.acobased.ACOBasedTraffic")) {
			int ttl = Integer.parseInt(parameters.get("/RSA/Routing/@ttl").get(0));
			long seed_ant = Long.parseLong(parameters.get("/Ant/Seed/@value").get(0));
			subscriber = new ACOBasedTraffic(ttl,seed_ant);
		}
		return subscriber;
	}

	
	@Override
	public ArrayList<Event> getOtherEvents() {
		//Empty list
		return new ArrayList<Event>();
	}

	/**
	 * Returns the next hop with a decreasing order of pheromone level.
	 * @param neighborhood The routing table of a given destination.
	 * @param rsvp The RSVP message.
	 * @param visited The ids of the already visited nodes.
	 * @return The next hop with a decreasing order of pheromone level.
	 */
	public static String select(RoutingTableEntry neighborhood, RSVP rsvp, ArrayList<String> visited) {
		//For each neighbor do
		ArrayList<NeighborAttr> neighs = new ArrayList<NeighborAttr>(); 
		for (String neighId: neighborhood.neighborhood()) {
			//Avoids the RSVP message to come back or visit another time.
			if (!rsvp.getPath().containNode(neighId) && !visited.contains(neighId))  
				neighs.add((NeighborAttr)neighborhood.getEntry(neighId));
		}
		//Sort the values of pheromone level
		if (neighs.size() > 0) {
			QuickSort.sort(neighs, false);
			return (String) neighs.get(0).getId();
		} else { //No neighbors available or not visited
			return null;
		}
	}
	
	
	/**
	 * Returns the next hop with a decreasing order of pheromone level.
	 * @param neighborhood The routing table of a given destination.
	 * @param ant The Ant message.
	 * @param links The flexilinks of the node
	 * @return The next hop with a decreasing order of pheromone level.
	 */
	public static String selectAntNextHop(RoutingTableEntry neighborhood, ACOBasedAnt ant, LinkedHashMap<String,FlexiLink> links) {
		double totalFreeSlots = 0.0;
		//For each neighbor do
		ArrayList<NeighborAttr> neighs = new ArrayList<NeighborAttr>(); 
		for (String neighId: neighborhood.neighborhood()) {
			double freeSlots = AntHeuristic.freeSlots(links.get(neighId));			
			//If the neighboring node is not on the ant's path, select the node and calculate the total number of free slots in the neighbors
			if (!ant.getPath().containNode(neighId)) {
				neighs.add((NeighborAttr)neighborhood.getEntry(neighId));
				totalFreeSlots = totalFreeSlots + freeSlots;
			}
		}				
		//If there are valid neighbors and available slots, the neighbor with the highest pheromone value is returned to the next node
		if (neighs.size() > 0 && totalFreeSlots > 0)  {
			//Sort the values of pheromone level
			QuickSort.sort(neighs, false);
			//Returns the node with the highest pheromone value
			return (String) neighs.get(0).getId();
		} else { //No neighbors available or not visited
			return null;
		}
	}
	
	
	/**
	 * Returns the next hop with a decreasing order of a heuristic value given by pheromone level and free available slots.
	 * @param neighborhood The routing table of a given destination.
	 * @param ant The Ant message.
	 * @param links The flexilinks of the node
	 * @param aCorrectionPheromone The correction (alpha) parameter for routing forward ants for pheromone 
	 * @param aCorrectionFreeSlots The correction (alpha) parameter for routing forward ants for free slots
	 * @return The next hop
	 */
	public static String selectAntNextHop2(RoutingTableEntry neighborhood, ACOBasedAnt ant, LinkedHashMap<String,FlexiLink> links, double correctionPheromone, double correctionFreeSlots) {
		double totalFreeSlots = 0.0;
		double totalPheromone = 0.0;
		//For each neighbor do
		ArrayList<NeighborAttr> neighs = new ArrayList<NeighborAttr>(); 
		for (String neighId: neighborhood.neighborhood()) {
			//Get the number os free slots of the neighboring node
			double freeSlots = AntHeuristic.biggestFreeContiguousSlots(links.get(neighId));			
			//If the neighboring node is not on the ant's path, select the node and calculate the total number of free slots in the neighbors
			if (!ant.getPath().containNode(neighId)) {
				neighs.add((NeighborAttr)neighborhood.getEntry(neighId));
				totalFreeSlots = totalFreeSlots + freeSlots;
				totalPheromone = totalPheromone + ((NeighborAttr)neighborhood.getEntry(neighId)).getPheromoneLevel();
			}
		}		
		//System.out.println("neighs: "+ neighs + " - totalfreeslots: "+totalFreeSlots);
		int size = neighs.size(); //Number of neighbors
		double[] probabilityDistribution = new double[size]; //Probability distribution
		String[] keys = new String[size]; //For storing the ids of the nodes.
		int count=0; //Counter for the number of neighbors	 
		//For each neighbor do
		//System.out.println(neighs);
		for (NeighborAttr neigh: neighs) {
			//Get the appropriate neighbor and the associated link state
			keys[count] = (String) neigh.getId();
			//Get the pheromone of the neighboring node
			double pheromoneLevel = neigh.getPheromoneLevel();
			//Get the number os free slots of the neighboring node
			double freeSlots  = AntHeuristic.biggestFreeContiguousSlots(links.get(neigh.getId()));	
			//Calculates the heuristic value
			double valueNeigh = (correctionPheromone*(pheromoneLevel/totalPheromone)+correctionFreeSlots*(freeSlots/totalFreeSlots))/ (correctionPheromone + correctionFreeSlots); 
			//Stores the heuristic value		
			probabilityDistribution[count] = valueNeigh;
			//System.out.println("id: "+ keys[count] + " pheromoneLevel: "+ pheromoneLevel + " - freeSlots: "+freeSlots +" freeSlotsAverage: "+freeSlots/totalFreeSlots+ " - value: "+valueNeigh);
			count++;
		}	 		
		//System.out.println(Arrays.toString(probabilityDistribution));
		//If there are valid neighbors and available slots, the neighbor with the highest heuristic value is returned to the next node
		if (probabilityDistribution.length > 0 && totalFreeSlots > 0) {
			//Random sample number
			double sample = rngAnt.nextDouble();
			//Starts variable that stores the highest value
			double maxValue = 0.0;
			int n = 0;
			//For all candidate neighbors
				for (int i = 0; i < probabilityDistribution.length; i++) {
					if (probabilityDistribution[i] > maxValue) {
						maxValue = probabilityDistribution[i];
						n = i;
					}
					//If the current maximum value is greater than the sample, it returns
					if (maxValue > sample) {
						break;
					}
				}
				//System.out.println("keys[n]: "+ keys[n]);
				//Returns node of the highest value
				return keys[n];	
		} else { //Returns null if there are no neighbors or free slots			
			return null;
		}
	}		
	

	
	/**
	 * Writes this class to a serialized object.
	 * @param s
	 * @throws IOException
	 */
	private void writeObject(ObjectOutputStream s) throws IOException {
	    transporter = new StaticTransporter(graph,random,rngAnt);
	    s.defaultWriteObject();
	}
	
	/**
	 * Reads the class from the serialization.
	 * @param s
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException  {
	    s.defaultReadObject();
	    // customized de-serialization code
	    ACOBasedFON.graph = transporter.graph;
	    ACOBasedFON.random = transporter.random;
	    ACOBasedFON.rngAnt = transporter.randomAnt;
	}


}

/**
 * Wrapper-class for transporting static variables.
 */
class StaticTransporter implements Serializable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	Graph graph;
	MersenneTwister random;
	MersenneTwister randomAnt;
	
	public StaticTransporter(Graph g, MersenneTwister r, MersenneTwister ant) {
		this.graph = g;
		this.random = r;
		this.randomAnt = ant;
	}
}
