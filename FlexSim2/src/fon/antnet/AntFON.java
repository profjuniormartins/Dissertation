/*
 * Created on 15/02/2008.
 */
package fon.antnet;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

import ant.StatisticalParametricModel;
import net.Message;
import ant.Ant;
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
import fon.antnet.AntHeuristic.Heuristic;
import util.QuickSort;
import event.Event;
import event.EventSubscriber;
import graph.Edge;
import graph.Graph;
import main.Config;
import net.Network;
import net.Node;
import net.Failure;
import net.Link;
import net.RoutingTableEntry;
import main.Accounting;

/**
 * This class is a control plane for the AntNet framework with crankback
 * capabilities, but for the RWA problem.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 */
public class AntFON extends Network {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** Workaround for transporting static fields. */
	StaticTransporter transporter;
	/** The set of links of this network. */
	protected LinkedHashMap<String, FlexiLink> links;	
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String,AntFlexiNode> nodes;
	/** The maximum hop limit for a packet. */
	protected int hopLimit;
	/** The number of slots at each flexi-grid link. */
	protected int slots;	
	/** The time of the last event. */
	protected double lastTime;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
	/** Time necessary to localize a failure. */
	protected double faultLocalizationTime;
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
	/** The chosen heuristic for Ant algorithm. */
	protected AntHeuristic.Heuristic heuristic;
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
	public AntFON(Config aConfig, Accounting aAccounting) {
		super(aConfig, aAccounting);				
		//Create the nodes of this network
		nodes = new LinkedHashMap<String,AntFlexiNode>();
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
		hopLimit = Integer.parseInt(parameters.get("/RSA/Routing/@hopLimit").get(0));
		holdoff = Double.parseDouble(parameters.get("/Ant/Holdoff/@timer").get(0));
		restoreAntRate = Double.parseDouble(parameters.get("/Ant/Holdoff/@antRate").get(0));
		long seedAnt = Long.parseLong(parameters.get("/Ant/Seed/@value").get(0));
		rngAnt = new MersenneTwister(seedAnt);
		//The parametric model characteristics
		double exponentialFactor = Double.parseDouble(parameters.get("/Ant/Parametric/@factor").get(0));
		double reductor = Double.parseDouble(parameters.get("/Ant/Parametric/@reductor").get(0));
		//The pheromone routing table parameters
		double confidenceLevel = Double.parseDouble(parameters.get("/Ant/Pheromone/@confidence").get(0));
		double firstWeight = Double.parseDouble(parameters.get("/Ant/Pheromone/@firstWeight").get(0));
		double secondWeight = Double.parseDouble(parameters.get("/Ant/Pheromone/@secondWeight").get(0));
		double amplifier = Double.parseDouble(parameters.get("/Ant/Pheromone/@amplifier").get(0));
		//The power factor to enhance the difference in the heuristics correction. 
		double powerFactor = Double.parseDouble(parameters.get("/Ant/Routing/@power").get(0));
		//Gets the correction (alpha) parameter for routing forward ants.
		double correction = Double.parseDouble(parameters.get("/Ant/Routing/@correction").get(0));		
		//Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get("/Outputs/Transient/@timeSlice").get(0));
		actualTimeSlice = timeSlice;
		//Failure parameters
		faultLocalizationTime = Double.parseDouble(parameters.get("/Failure/Timing/@localization").get(0));
		identificationLength = Integer.parseInt(parameters.get("/RSA/Overhead/@label").get(0));
		//Get details about the RSA algorithm used
		boolean deterministic = Boolean.parseBoolean(parameters.get("/RSA/Routing/@deterministic").get(0));
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
		//RSA params
		int saWindow = (int)Double.parseDouble(parameters.get("/RSA/SA/@window").get(0));
		boolean saSliding = Boolean.parseBoolean(parameters.get("/RSA/SA/@sliding").get(0));
		heuristic = Heuristic.valueOf(parameters.get("/Ant/Heuristic/@type").get(0));
		//Initialize each network node
		//Initialize the state of each node
		for (String id: graph.nodes()) {
			//Create the pheromone routing table for this node
			AntRoutingTable art = new AntRoutingTable(id,confidenceLevel,firstWeight,secondWeight,amplifier,correction, powerFactor, deterministic, heuristic);
			art.updateFromTopology(graph);	
			//Create the local parametric view for all destinations of this 			
			StatisticalParametricModel model = new StatisticalParametricModel(id,graph,exponentialFactor,reductor);
			//Create the links adjacent to this node.
			LinkedHashMap<String,FlexiLink> antLinks = new LinkedHashMap<String,FlexiLink>(); 
			ArrayList<String> adjacent = graph.adjacentNodes(id);
			//for each adjacent node do
			for (String adjId:adjacent) {
				FlexiLink antLink = links.get(id+"-"+adjId);
				antLinks.put(adjId.toString(),antLink);
			}
			//Create the wavelength usage table.
			FrequencySlotUsageTable usageTable = new FrequencySlotUsageTable(id,graph,slots,saWindow,saSliding);
			//Create the node and put it into the table.
			AntFlexiNode colony = new AntFlexiNode(id,Node.Type.FIXED_ALTERNATE,art,antLinks,graph,rerouting,maxReroutingAttempts,reroutingAttempts,sa,model,usageTable,bandwidths);
			nodes.put(id,colony);
		}
	}

	/** Process the specified event.
	 * @param event The event to be processed.
	 * @return The processed event. Null, if nothing else is 
	 * to be returned to the scheduler.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Event process(Event event) {
		//The id of the processing node
		String id;
		//Event response object
		Event response = null;
		//Update the time stamp of the last event to be processed processed.		
		lastTime = event.getTimeStamp(); 
		//Use to clone the nodes to remove orphans nodes in Failure Link and Failure Node.
		ArrayList<String> aNodes;
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
				RSVP rsvpPath = new RSVP(request,hopLimit,slots);
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
					rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
					//System.out.println("Contention: "+lRequest.toString());
					if (disruptedConnection.containsKey(rsvpErr.getId())) {
						rsvpRetry.setReRouting(); //set the flag of re-routing
						rsvpRetry.setId(rsvpErr.getId()); //fix the id since it should contain the "r" suffix
						//System.out.println(event.toString());
					}
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
					rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
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
						rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
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
								Ant ant = new Ant(id, connectionRequest.getSource(),connectionRequest.getDestination(),hopLimit,bytesHop);
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
			case FAILURE_LINK:  //For link failure
				System.out.println("Failure link: "+event.toString());
				//* Bi-directional code
				//Get the edge associated with the failure
				String sEdge = (String) event.getContent();
				Edge edge = links.get(sEdge).getLink().getEdge();
				Edge revEdge = links.get(edge.getDestination().toString()+"-"+edge.getSource().toString()).getLink().getEdge();
				//Remove the failure edge from the graph
				try { //Do it only if it is not a node failure
					if (nodes.containsKey(edge.getSource()) && nodes.containsKey(edge.getDestination())) {
						graph.removeEdge(edge.getSource(),edge.getDestination());
						graph.removeEdge(revEdge.getSource(),revEdge.getDestination());
					}
				} catch(Exception e) {e.printStackTrace();}	
				//detect and remove "orphan" nodes, i.e., disconnected ones.
				aNodes = (ArrayList<String>) graph.nodes().clone();
				for (String node : aNodes) {
					int degree = graph.adjacencyDegree(node);
					if (degree == 0) {
						try {
							//System.out.println("Removing node: "+node);
							graph.removeNode(node);
							//Remove the node from the list of nodes 
							nodes.remove(node);
						} catch (Exception e) {e.printStackTrace();}
					}
				}
				//Notifies the end nodes of the failure after the localization time
				double timeNotification = lastTime + this.faultLocalizationTime;
				int lengthFailure = 2 * this.identificationLength;
				//Creates the packets of notification
				Message failureTo = new Message(sEdge,Message.Type.FAILURE_LINK,edge.getDestination(),edge.getDestination(),lengthFailure,graph.size());
				Message failureFrom = new Message(sEdge,Message.Type.FAILURE_LINK,edge.getSource(),edge.getSource(),lengthFailure,graph.size());
				Message revFailureTo = new Message(sEdge,Message.Type.FAILURE_LINK,revEdge.getDestination(),revEdge.getDestination(),lengthFailure,graph.size());
				Message revFailureFrom = new Message(sEdge,Message.Type.FAILURE_LINK,revEdge.getSource(),revEdge.getSource(),lengthFailure,graph.size());
				//Adds the edge to the packets.
				Failure failureLinkAdv = new Failure(edge); 
				failureTo.setContent(failureLinkAdv); 
				failureFrom.setContent(failureLinkAdv);
				Failure revFailureLinkAdv = new Failure(revEdge);
				revFailureTo.setContent(revFailureLinkAdv); 
				revFailureFrom.setContent(revFailureLinkAdv);	
				//Add to the vector of events
				ArrayList<Event> failuresLink = new ArrayList<Event>();
				failuresLink.add(new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,failureFrom));
				failuresLink.add(new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,failureTo));	
				failuresLink.add(new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,revFailureFrom));
				failuresLink.add(new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,revFailureTo));		
				return new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MULTIPLE,failuresLink);
			case FAILURE_NODE: //For node failure
				System.out.println("Failure node: "+event.toString());
				//Get the node associated with the failure
				id = (String)event.getContent();
				ArrayList<Event> failuresNode = new ArrayList<Event>();
				//Gets the neighbors of this graph
				ArrayList<String> neighbors = graph.adjacentNodes(id);
				for (String neighId:neighbors) {
					//Add the edge "from" the removed node
					failuresNode.add(new Event(lastTime,event.getInitialTimeStamp(),Event.Type.FAILURE_LINK,new String(id+"-"+neighId)));
					////Add the edge "to" the removed node
					////*failuresNode.add(new Event(lastTime,Event.Type.FAILURE_LINK,new String(neighId+"-"+id)));
				}
				//Remove the failure node from the graph
				try {					
					graph.removeNode(id);
				} catch(Exception e) {e.printStackTrace();}	
				//detect and remove "orphan" nodes, i.e., disconnected ones.
				aNodes = (ArrayList<String>) graph.nodes().clone();
				for (String node : aNodes) {
					int degree = graph.adjacencyDegree(node);
					if (degree == 0) {
						try {
							//System.out.println("Removing node: "+node);
							graph.removeNode(node);
							//Remove the node from the list of nodes 
							nodes.remove(node);
						} catch (Exception e) {e.printStackTrace();}
					}
				}
				//Remove the node from the list of nodes 
				nodes.remove(id);
				//Return the response containing the failure of the multiple links
				return new Event(lastTime,event.getInitialTimeStamp(),Event.Type.MULTIPLE,failuresNode);
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
			int hopLimit = Integer.parseInt(parameters.get("/RSA/Routing/@hopLimit").get(0));
			long seed_ant = Long.parseLong(parameters.get("/Ant/Seed/@value").get(0));
			subscriber = new AntTraffic(hopLimit,seed_ant);
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
	 * Selects the next hop based on the probabilities of the routing table and
	 * on the local statistics (free wavelength ratio).
	 * @param neighborhood The routing table of a given destination.
	 * @param links The state of the neighbor links.
	 * @param ant The packet ant.
	 * @param alpha Trade-off between shortest-path and heuristic correction (congestion).
	 * @param powerFactor to enhance the difference in the heuristics correction
	 * @return The id of the next hop.
	 */
	public static String select(RoutingTableEntry neighborhood, LinkedHashMap<String,FlexiLink> links, Ant ant, double alpha, double powerFactor, Heuristic heuristic){
		//Gets the total number of free points between the neighbors.
		double totalFreeWavelengths = 0.0;  //Total number of free wavelengths
		double totalPheromoneLevel = 0.0;
		//Gets the neighbors that are not in the tabu list.
		ArrayList<String> availableNeighbors = new ArrayList<String>();
 		for (String neighId: neighborhood.neighborhood()) { 
 			if (!ant.isTabu(neighId)) { //not in tabu list
 				availableNeighbors.add(neighId);
 				//Get the total number of free wavelengths
 				//double free = (double) links.get(neighId).freeSlots();
 				//double free = (double) links.get(neighId).biggestContiguousSlots(); 				
 				//Determine the value according to the adopted heuristic
				double free = 0.0;
				if (heuristic.equals(Heuristic.FREE_SLOTS))
	 				free  = AntHeuristic.freeSlots(links.get(neighId));
				else if (heuristic.equals(Heuristic.BIGGEST_FREE_CONTIGUOUS_SLOTS))
					free = AntHeuristic.biggestFreeContiguousSlots(links.get(neighId)); 				
 				totalFreeWavelengths = totalFreeWavelengths + Math.pow(free, powerFactor);
 				//And the total pheromone level
 				totalPheromoneLevel = totalPheromoneLevel + ((NeighborAttr)neighborhood.getEntry(neighId)).getPheromoneLevel(); 
 			}
 		}
 		//Verify the routing decision policy
 		if ((availableNeighbors.size() == 0)) { //all neighbors already visited - doing loop!
 			//Proceed like a data packet
 			String nextHop = null;
 			if (neighborhood.size() > 1) {
 				nextHop = select(neighborhood,ant);
 			} else {
 				//nextHop = ant.getLastVisited();
 				return null;
 			}
 			//Destroy the loop
// 			System.out.println("Loop: "+ant.toString());
 			int loopSize = ant.destroyLoop(nextHop);
 			//Set the loop flag
 			ant.setLoopFlag();
 			//Set the payload length
 			ant.setPayloadLength(ant.getPayloadLength() - loopSize * ant.getBytesHop());
 			//Return the next hop
 			return nextHop;
 		} else { //There are other nodes not already visited.
 			/* Now, use the pheromone values with the local heuristic to calculate the next hop. */
 			int size = availableNeighbors.size(); //Number of neighbors
 			double[] probabilityDistribution = new double[size]; //Probability distribution
 			String[] keys = new String[size]; //For storing the ids of the nodes.
 			int count=0; //Counter for the number of neighbors	 
 			//For each neighbor do
 			for (String neighId: availableNeighbors) {
 				//Get the appropriate neighbor and the associated link state
 				keys[count] = neighId;
 				NeighborAttr neigh = (NeighborAttr) neighborhood.getEntry(neighId);
				//Now calculate the probability 			 				
 				//double freeLambdas = links.get(neighId).freeSlots();
 				//double freeLambdas = 0.0;
 				//double freeLambdas = links.get(neighId).biggestContiguousSlots();
				double freeLambdas = 0.0;
				if (heuristic.equals(Heuristic.FREE_SLOTS))
	 				freeLambdas  = AntHeuristic.freeSlots(links.get(neighId));
				else if (heuristic.equals(Heuristic.BIGGEST_FREE_CONTIGUOUS_SLOTS))
					freeLambdas = AntHeuristic.biggestFreeContiguousSlots(links.get(neighId)); 		
 				//System.out.println("freeLambdas: "+freeLambdas);
 				probabilityDistribution[count] = ((neigh.getPheromoneLevel() / totalPheromoneLevel) + alpha*(Math.pow(freeLambdas,powerFactor)/totalFreeWavelengths)) / (1.0 + alpha);
 				//Increment the index
 				count++;
 			}	 			
 			//System.out.println("probabilityDistribution: "+ Arrays.toString(probabilityDistribution));
 			//Spins the wheel
 			double sample = rngAnt.nextDouble();
 			//Set sum to the first probability
 			double sum = probabilityDistribution[0];
 			int n = 0;
 			while (sum < sample) {
 				n = n + 1;
 				sum = sum + probabilityDistribution[n];
 			}		
 			return keys[n];							
 		}
	}

	/**
	 * Selects the next hop based ONLY on the probabilities of the routing table.
	 * It is not allowed to come back! 
	 * @param neighborhood The routing table of a given destination.
	 * @return The id of the next hop.
	 */
	public static String select(RoutingTableEntry neighborhood,Message msg){
		int count=0; //Counter for the number of neighbors	 
		int size; //Number of neighbors
		//Get the last edge visited.
		String lastVisited = null;
		if (msg.getPathLength() > 0) { // not first hop
			lastVisited = msg.getPath().getLastEdge().getSource();
			size = neighborhood.size() - 1;
		} else { //First hop
			size = neighborhood.size();
		}
		if (size == 0) {
			return null;
		}
		double[] probabilityDistribution = new double[size]; //Probability distribution
		String[] keys = new String[size]; //For storing the ids of the nodes.
		double totalLevel = 0.0; //Sum of probabilities
		//For each neighbor do
		for (String neighId: neighborhood.neighborhood()) {
			if (!neighId.equals(lastVisited)) {
				double level = ((NeighborAttr)neighborhood.getEntry(neighId)).getPheromoneLevel();
				probabilityDistribution[count] = level;
				totalLevel = totalLevel + level;
				keys[count] = neighId;
				count++;
			}
		}
		//Normalize the values
		for (int i=0; i<size;i++) {
			probabilityDistribution[i] = probabilityDistribution[i]/totalLevel;
		}
		//Spins the wheel
		double sample = rngAnt.nextDouble();
		//Set sum to the first probability
		double sum = probabilityDistribution[0];
		//System.out.println("sum: "+sum + " - sample: "+sample);
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}		
		return keys[n];		
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
	    AntFON.graph = transporter.graph;
	    AntFON.random = transporter.random;
	    AntFON.rngAnt = transporter.randomAnt;
	}


}

/**
 * Wrapper-class for transporting static variables.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 *
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
