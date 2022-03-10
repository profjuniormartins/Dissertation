/**
 * Created on 26/09/2016.
 */
package fon.topological;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;

import event.Event;
import event.EventGenerator;
import event.EventSubscriber;
import fon.FixedRequestTraffic;
import fon.FlexiLink;
import fon.NonUniformRequestTraffic;
import fon.RSVP;
import fon.Request;
import fon.SpectrumAssignment.Strategy;
import graph.Edge;
import graph.Graph;
import graph.Path;
import graph.YEN;
import main.Accounting;
import main.Config;
import main.Simulator;
import net.Failure;
import net.Link;
import net.Message;
import net.Network;
import net.Node;
import fon.topological.FlexiNode.ReRouting;
import net.Error;
import fon.Connection;

/**
 * This class represents a flexible optical network with topological routing.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FON extends Network {
	/** The set of optical nodes of this simulation. */
	protected LinkedHashMap<String,FlexiNode> nodes;
	/** The set of links of this flexible optical network. */
	protected LinkedHashMap<String,FlexiLink> links;
	/** The actual collection of shortest paths of this network. */
	public static LinkedHashMap<String,ArrayList<Path>> setPaths;
	/** The maximum hop limit for a RSVP message. */
	protected int hopLimit;
	/** The number of slots at each flexi-grid link. */
	protected int slots;
	/** The length in bytes for the identification (or label) of a link or a node. */
	protected int identificationLength;
	/** Time necessary to localize a failure. */
	protected double faultLocalizationTime;
	/** The collection of Connection successfully re-routed after a failure. */
	protected Hashtable<String,Connection> reroutedConnection;
	/** The collection of Connection disrupted by a failure, which
	 * are eligible for full re-routing. */
	protected Hashtable<String,Request> disruptedConnection;
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Maximum number of re-routing attempts allowed. */
	protected int maxReroutingAttempts;	
	/** Number of re-routing attempts per LSR. */
	protected int reroutingAttempts;
	/** The spectrum assignment strategy. */
	protected Strategy sa;
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
	
	/**
	 * Creates a new FON object.
	 * 
	 * @param aConfigBinaryHeap
	 * @param aAccountingLightpathRequest
	 */
	public FON(Config aConfig, Accounting aAccounting) {
		super(aConfig, aAccounting);
		//Create the nodes of this network
		nodes = new LinkedHashMap<String,FlexiNode>();
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
		//Get the configuration parameters
		hopLimit = Integer.parseInt(parameters.get("/RSA/Routing/@hopLimit").get(0));
		//Failure parameters
		faultLocalizationTime = Double.parseDouble(parameters.get("/Failure/Timing/@localization").get(0));
		identificationLength = Integer.parseInt(parameters.get("/RSA/Overhead/@label").get(0));
		//Get details about the RSA algorithm used
		rerouting = ReRouting.valueOf(parameters.get("/RSA/Routing/@rerouting").get(0));
		reroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@attempts").get(0));
		maxReroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
		sa = Strategy.valueOf(parameters.get("/RSA/SA/@strategy").get(0));
		//Create the set of paths
		setPaths = this.getPaths(graph, reroutingAttempts);
		//System.out.println(setPaths);
		//Initialize each network node
		for (String id: graph.nodes()) {
			//Create the routing table for this node
			ExplicitRoutingTable ert = new ExplicitRoutingTable(id,reroutingAttempts + 1);			
			ert.updateFromTopology(graph,setPaths);
			//Create the links adjacent to this node.
			ArrayList<String> adjacent = graph.adjacentNodes(id);
			LinkedHashMap<String,FlexiLink> linkStateSet = new LinkedHashMap<String,FlexiLink>();	
			//for each adjacent node do
			for (String adjId:adjacent) {
				FlexiLink linkState = links.get(id+"-"+adjId);
				linkStateSet.put(adjId.toString(),linkState);
			}
			//Create the node
			FlexiNode node = new FlexiNode(id, Node.Type.FIXED_ALTERNATE, ert, linkStateSet, graph, rerouting, maxReroutingAttempts, reroutingAttempts,sa);
			//Add it to the set
			nodes.put(id, node);
		}

	}

	@Override
	public Event process(Event event) {
		//System.out.println(event.toString());
		//Event response object
		Event response = null;
		//Get the type of the event
		Event.Type type = event.getType();
		//Last time stamp of an event
		double timeStamp = event.getTimeStamp();
		// Identifier of an event
		String id;
		//Use to clone the nodes to remove orphans nodes in Failure Link and Failure Node.
		ArrayList<String> aNodes;
		//Accordingly to the type doBinaryHeap
		switch (type) {
			case CONNECTION_REQUEST:
				counterConnectionRequest ++;
				//System.err.println("counterConnectionRequest: "+counterConnectionRequest);
				//Get the request
				Request request = (Request) event.getContent();
				/* To help contention, always use the same order.*/
				String source = request.getSource();
				String destination = request.getDestination();
				if (Integer.parseInt(source) - Integer.parseInt(destination) > 0) {
					//need to invert source with destination
					request.setSource(destination);
					request.setDestination(source);
				}
				//Account the request
				accounting.addEvent(Accounting.Type.SUCCESS, event);
				//Create a PATH message
				RSVP rsvpPath = new RSVP(request,hopLimit,this.slots);
				//Create a new event for setting up the lightpath
				response = new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpPath);
				break;
			case CONNECTION_PROBLEM:
				counterConnectionProblem ++;
				//System.err.println("counterConnectionProblem: "+counterConnectionProblem);
				RSVP rsvpErr = (RSVP) event.getContent();
				Request connectionRequest;
				//Get the error status
				Error error = rsvpErr.getError();
				//Get the label
				RSVP rsvpRetry = null; //new Rsvp message
				Error.Code errorCode = error.getErrorCode();
				//Random time for solving race conditions in the Admission control failure. It also changes the initial time stamp to remove the bias in the setup time.
				double randomTime = 0.0;
				//Allocation of frequency slot contention problem
				if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {
					counterAdmissionControlFailure ++;
					//System.err.println("counterAdmissionControlFailure: "+counterAdmissionControlFailure);
					connectionRequest = (Request) ((Connection)rsvpErr.getContent()).getRequest();
					rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
					//System.out.println("Contention: "+connectionRequest.toString());
					if (disruptedConnection.containsKey(rsvpErr.getId())) {
						rsvpRetry.setReRouting(); //set the flag of re-routing
						rsvpRetry.setId(rsvpErr.getId()); //fix the id since it should contain the "r" suffix						
						//System.out.println(event.toString());
					}
					randomTime = random.nextDouble(); //Adds a time between 0 and 1000 ms to break the race condition in concurrent setup messages
				//Wavelength continuity constraint violated or no link available, use alternate path	
				} else if (errorCode.equals(Error.Code.RP_LABEL_SET) || errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE)) {
					counterRPProblem ++;
					if (errorCode.equals(Error.Code.RP_LABEL_SET)){
						counterRPProblemLabelSet ++;
					} else if (errorCode.equals(Error.Code.RP_NO_ROUTE_AVAILABLE)){
						counterRPProblemNoRouteAvailable ++;						
					}
					//System.err.println("counterRPProblem: "+counterRPProblem);
					connectionRequest = (Request) rsvpErr.getContent();
					connectionRequest.addTry(); //add a try to the counter of tries					
					if (connectionRequest.tryAgain()) { //resend the request
						rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
						//Add the effective hops of the previous Path message
						rsvpRetry.setEffectiveHops(rsvpErr.getEffectiveHops());
						if (disruptedConnection.containsKey(rsvpErr.getId())) {
							rsvpRetry.setReRouting(); //set the flag of re-routing
							rsvpRetry.setId(rsvpErr.getId()); //fix the id since it should contain the "r" suffix
							//System.out.println(event.toString());
						}
					} else { 
						//Account the failed connection establishment
						accounting.addEvent(Accounting.Type.FAILED, event);		
						//if (disruptedConnection.containsKey(rsvpErr.getId()));
							//System.out.println("Failed:"+event.toString());
					}
				//Connection failure forward or backward 	
				} else if(errorCode.equals(Error.Code.LSP_FAILURE)) {
					counterLSPFailure ++;
					//System.err.println("counterLSPFailure: "+counterLSPFailure);
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
				} else if(errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)){
					counterReroutingLimitExceeded ++;
					//System.err.println(counterReroutingLimitExceeded);
					//Accounts the failed connection request
					accounting.addEvent(Accounting.Type.FAILED, event);					
				}								
				//Now, return the result.
				if (rsvpRetry != null) {
					response = new Event(event.getTimeStamp()+randomTime,event.getInitialTimeStamp()+randomTime, Event.Type.MESSAGE_ARRIVAL,rsvpRetry);
				}
				else 
					return null;	
				break;
			case CONNECTION_ESTABLISHED:	
				counterConnectionEstablished ++;
				//System.err.println("counterConnectionEstablished : "+counterConnectionEstablished);
				RSVP rsvpConfirm = (RSVP) event.getContent();
				//Gets the connection object.
				Connection connectionEst = (Connection) rsvpConfirm.getContent();
				//Gets the duration of the lightpath
				double duration = connectionEst.getRequest().getDuration();
				//Account the successful connection establishment
				accounting.addEvent(Accounting.Type.SUCCESS, event);				
				//See if it is a succesful re-routing of a failed LSP
				if (rsvpConfirm.isReRouting()) {
					reroutedConnection.put(rsvpConfirm.getId(),connectionEst);
				}
				//System.out.println(event.toString());
				//Return a new event for tearing down the lightpath when appropriate
				response = new Event((event.getTimeStamp() + duration),Event.Type.CONNECTION_TEARDOWN,connectionEst);
				break;
			case CONNECTION_TEARDOWN:
				counterConnectionTeardown ++;
				//System.err.println("counterConnectionTeardown: "+counterConnectionTeardown);
				Connection connectionTear = (Connection) event.getContent();
				String connectionID = connectionTear.getId();
				if ((disruptedConnection.get(connectionID) == null) || ((reroutedConnection.get(connectionID) != null) && (reroutedConnection.get(connectionID).getPath().equals(connectionTear.getPath()) ) )) {
					//Send RSVP PathTear message
					RSVP rsvpTear = new RSVP(connectionTear,Message.Type.RSVP_PATH_TEAR,connectionTear.getSource(),connectionTear.getDestination());
					//System.out.println(rsvpTear.toString());
					response=  new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpTear);
				} else { //Ignore the teardown associated to a failed Connection, since it is already cleaned and rerouted.
					return null;
				}				
				break;
			case CONNECTION_FINISHED:
				counterConnectionFinished ++;
				//System.err.println("counterConnectionFinished: "+counterConnectionFinished);
				//Account the finishing of a previously connection established
				accounting.addEvent(Accounting.Type.SUCCESS, event);	
				break;
			case MESSAGE_ARRIVAL:
				//Get the message
				Message msg = (Message) event.getContent();
				//Get the node associated to this message
				String nodeId = msg.getProcNode();
				//Give the packet to the right node
				FlexiNode procNode = nodes.get(nodeId);
				if (procNode != null) { //Node functioning
					//Process the event
					response = procNode.process(event);
					if (response.getType().equals(Event.Type.IGNORE))  
						return null;
					else 
						return response;
				} else { //Failed node
					accounting.addEvent(Accounting.Type.FAILED, event);
					return null;					
				}
			case FAILURE_NODE:
				System.out.println("Failure node: "+event.toString());
				//Get the node associated with the failure
				id = (String)event.getContent();
				ArrayList<Event> failuresNode = new ArrayList<Event>();
				//Gets the neighbors of this graph
				ArrayList<String> neighbors = graph.adjacentNodes(id);
				for (String neighId:neighbors) {
					//Add the edge of the removed node
					failuresNode.add(new Event(timeStamp, event.getInitialTimeStamp(), Event.Type.FAILURE_LINK,new String(id+"-"+neighId)));
					////Add the edge "to" the removed node
					////*failuresNode.add(new Event(timeStamp,Event.Type.FAILURE_LINK,new String(neighId+"-"+id)));
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
							//System.out.println("Removing orphan node from the graph: "+node);
							graph.removeNode(node);
							//Remove the node from the list of nodes 
							nodes.remove(node);
						} catch (Exception e) {e.printStackTrace();}
					}
				}
				//Recalculate the set of paths
				setPaths = this.getPaths(graph,reroutingAttempts);
				//Remove the node from the list of nodes 
				nodes.remove(id);
				//this.printAllConnections();
				//Return the response containing the failure of the multiple links
				return new Event(timeStamp, event.getInitialTimeStamp(), Event.Type.MULTIPLE,failuresNode);
			case FAILURE_LINK:
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
							//System.out.println("Removing orphan node from the graph: "+node);
							graph.removeNode(node);
							//Remove the node from the list of nodes 
							nodes.remove(node);
						} catch (Exception e) {e.printStackTrace();}
					}
				}
				//Recalculate the set of paths
				setPaths = this.getPaths(graph,reroutingAttempts);
				//this.printAllConnections();
				//Notifies the end nodes of the failure after the localization time
				double timeNotification = timeStamp + this.faultLocalizationTime;
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
				failuresLink.add(new Event(timeNotification, event.getInitialTimeStamp(), Event.Type.MESSAGE_ARRIVAL,failureFrom));
				failuresLink.add(new Event(timeNotification, event.getInitialTimeStamp(), Event.Type.MESSAGE_ARRIVAL,failureTo));
				failuresLink.add(new Event(timeNotification, event.getInitialTimeStamp(), Event.Type.MESSAGE_ARRIVAL,revFailureFrom));
				failuresLink.add(new Event(timeNotification, event.getInitialTimeStamp(), Event.Type.MESSAGE_ARRIVAL,revFailureTo));				
				return new Event(timeNotification,event.getInitialTimeStamp(),Event.Type.MULTIPLE,failuresLink);
				//break;
			default: //other events not listed.
				System.err.println("Unknown event: "+event.toString());
				break;
		}

		return response;
	}

	@Override
	public EventSubscriber createTrafficSubscriber(String nameClass, int index) {
		//Create the event subscriber
		EventSubscriber subscriber = null;
		//System.out.println("Class: "+nameClass);
		//for each class do		
		if (nameClass.equals("fon.NonUniformRequestTraffic")) {
			int tries = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
			//The seed to choose bandwidth randomly. 
			long seed_bandwidth = Long.parseLong(parameters.get("/Generators/Traffic/@seed_bandwidth").get(index));
			//The bandwidths.
			double[] bandwidths = stringToDoubleArray(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			//The ratios of the bandwidths.
			double[] ratios = stringToWeightedDoubleArray(parameters.get("/Generators/Traffic/@bandwidths_ratios").get(index));	
			//System.out.println("Probabilities: " + Arrays.toString(probabilities));
			subscriber = new NonUniformRequestTraffic(tries,seed_bandwidth,bandwidths,ratios);	
		} else if(nameClass.equals("fon.FixedRequestTraffic")) {
			int tries = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
			double bandwidth = Double.parseDouble(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			subscriber = new FixedRequestTraffic(tries,bandwidth);				
		}
		return subscriber;
	}

	@Override
	public ArrayList<Event> getOtherEvents() {
		//Empty list
		return new ArrayList<Event>();
	}
		
	@Override
	public void updateValues() {
		System.out.println("Last Simulation Time: "+Simulator.getLastSimulationTime());
		System.out.println("Disrupted: "+this.disruptedConnection.keySet().toString());
		System.out.println("Total disrupted: "+disruptedConnection.size());
		System.out.println("Rerouted: "+reroutedConnection.keySet().toString());
		System.out.println("Total rerouted: "+reroutedConnection.size());
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
	
	/**
	 * Create the set of shortest paths
	 * @param topology The topology of the network
	 * @param alternative The number of alternative paths
	 * @return 1+alternatives paths for each pair source-destination of the topology.
	 */
	public LinkedHashMap<String,ArrayList<Path>> getPaths(Graph topology, int alternative) {
		LinkedHashMap<String,ArrayList<Path>> routes = new LinkedHashMap<String,ArrayList<Path>>();
		YEN yen = new YEN();
		for(String src: topology.nodes()) {
			for (String tgt: topology.nodes()) {
				if (!src.equals(tgt)) { //Assure different nodes in the pair
					ArrayList<Path> paths = null;;
					try { 
						paths = yen.getShortestPaths(src,tgt,topology,1+alternative);
					} catch (Exception e) {e.printStackTrace();}
					routes.put(src+"-"+tgt,paths);
				}
			}
		}
		//System.out.println("-------ROUTES: "+routes);
		return routes;
	}
		
	/**
	 * Returns the set of shortest paths of the actual topology.
	 * @return The set of shortest paths of the actual topology.
	 */
	public static LinkedHashMap<String,ArrayList<Path>> getPaths() {
		return setPaths;
	}

	
}
