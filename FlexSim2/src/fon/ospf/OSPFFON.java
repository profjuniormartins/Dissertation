/**
 * 
 */
package fon.ospf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;

import event.Event;
import event.EventGenerator;
import event.EventSubscriber;
import fon.Connection;
import fon.FixedRequestTraffic;
import fon.FlexiLink;
import fon.LabelSet;
import fon.Metric;
import fon.Metric.OSPF;
import fon.NonUniformRequestTraffic;
import fon.RSVP;
import fon.Request;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import fon.ospf.llrsa.OSPFLLRSARefreshTraffic;
import fon.topological.FlexiNode;
import fon.topological.FlexiNode.ReRouting;
import graph.Edge;
import graph.Graph;
import graph.Path;
import graph.YEN;
import main.Accounting;
import main.Config;
import net.Error;
import net.Failure;
import net.Link;
import net.Message;
import net.Network;
import net.Node;

/**
 * @author Pavani
 *
 */
public class OSPFFON extends Network {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The set of links of this network. */
	protected LinkedHashMap<String, FlexiLink> links;
	/** The actual collection of shortest paths of this network. */
	public static LinkedHashMap<String,ArrayList<Path>> setPaths;
	/** The set of nodes of this simulation. */
	protected LinkedHashMap<String, OSPFNode> nodes;
	/** The maximum hop limit for a packet. */
	protected int hopLimit;
	/** The number of slots at each flexi-grid link. */
	protected int slots;
	/**
	 * The length in bytes for the identification (or label) of a link or a node.
	 */
	protected int identificationLength;
	/** The time of the last event. */
	protected double lastTime;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
	/** Time necessary to localize a failure. */
	protected double faultLocalizationTime;
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Maximum number of re-routing attempts allowed. */
	protected int maxReroutingAttempts;
	/** Number of re-routing attempts per LSR. */
	protected static int reroutingAttempts;
	/** The collection of Connection successfully re-routed after a failure. */
	protected Hashtable<String, Connection> reroutedConnection;
	/**
	 * The collection of Connection disrupted by a failure, which are eligible for
	 * full re-routing.
	 */
	protected Hashtable<String, Request> disruptedConnection;
	/** The chosen wavelength assignment algorithm. */
	protected SpectrumAssignment.Strategy sa;
	/** The chosen metric to calculate the link cost */
	protected static Metric.OSPF metric;
	/** The current sequence number. */
	protected static long current_sequence;
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
	 * Creates a new OSPF Control Plane object.
	 * @param aConfig The XML configuration file.
	 * @param aAccounting The accounting for the simulation results.
	 */
	public OSPFFON(Config aConfig, Accounting aAccounting) {
		super(aConfig, aAccounting);
		//Create the nodes of this network
		nodes = new LinkedHashMap<String,OSPFNode>();
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
		//Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get("/Outputs/Transient/@timeSlice").get(0));
		actualTimeSlice = timeSlice;
		//Failure parameters
		faultLocalizationTime = Double.parseDouble(parameters.get("/Failure/Timing/@localization").get(0));
		identificationLength = Integer.parseInt(parameters.get("/RSA/Overhead/@label").get(0));
		//Get details about the RSA algorithm used
		rerouting = ReRouting.valueOf(parameters.get("/RSA/Routing/@rerouting").get(0));
		reroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@attempts").get(0));
		maxReroutingAttempts = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
		sa = Strategy.valueOf(parameters.get("/RSA/SA/@strategy").get(0));
		metric = OSPF.valueOf(parameters.get("/RSA/Metric/@type").get(0));
		//Initialize each network node
		for (String id: graph.nodes()) {
			//Create the routing table for this node
			LSDB lsdb = new LSDB(id,reroutingAttempts + 1);	
			//Create the links adjacent to this node.
			ArrayList<String> adjacent = graph.adjacentNodes(id);
			LinkedHashMap<String,FlexiLink> linkStateSet = new LinkedHashMap<String,FlexiLink>();	
			//for each adjacent node do
			for (String adjId:adjacent) {
				FlexiLink linkState = links.get(id+"-"+adjId);
				linkStateSet.put(adjId.toString(),linkState);
			}
			//Create the node
			OSPFNode node = new OSPFNode(id, Node.Type.FIXED_ALTERNATE, lsdb, linkStateSet, graph, rerouting, maxReroutingAttempts, reroutingAttempts,metric,sa);
			//Add it to the set
			nodes.put(id, node);
		}
	}

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
		//For each event type
		switch (event.getType()) {
			case OSPF_UPDATE:
				ArrayList<Event> multiple = new ArrayList<Event>();
				//for all nodes do
				for (String node_id: graph.nodes()) {
					//Get the node
					OSPFNode node = nodes.get(node_id);
					//Generate the updates
					ArrayList<Event> list = node.getUpdates(event);
					//Add to the multiple list
					multiple.addAll(list);
				}
				//Return all updates
				response = new Event(event.getTimeStamp(),Event.Type.MULTIPLE,multiple);
				break;
			case CONNECTION_REQUEST: //Connection request
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
				//System.out.println(request.toString());
				//Account the request
				accounting.addEvent(Accounting.Type.SUCCESS, event);
				//Create a PATH message
				RSVP rsvpPath = new RSVP(request,hopLimit,this.slots);
				//Create a new event for setting up the lightpath
				response = new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpPath);
				break;			
			case CONNECTION_ESTABLISHED: //Connection established
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
				//Allocation of frequency slot contention problem
				if (errorCode.equals(Error.Code.ADMISSION_CONTROL_FAILURE)) {
					counterAdmissionControlFailure ++;
					//System.err.println("counterAdmissionControlFailure: "+counterAdmissionControlFailure);
					connectionRequest = (Request) ((Connection)rsvpErr.getContent()).getRequest();
					rsvpRetry = new RSVP(connectionRequest,hopLimit,slots);
					//System.out.println("Contention: "+connectionRequest.toString());
					if (disruptedConnection.containsKey(rsvpErr.getId())) {
						rsvpRetry.setReRouting(); //set the flag of re-routing
						//System.out.println(event.toString());
					}
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
					//Adds the connection to the list of disrupted LSP 
					disruptedConnection.put(rsvpErr.getId(),connectionRequest);
					//System.out.println("Adding LSP failure: "+rsvpErr.getFlowLabel()+" ,"+disrupted.toString());
				} else if(errorCode.equals(Error.Code.RP_REROUTING_LIMIT_EXCEEDED)){
					counterReroutingLimitExceeded ++;
					//System.err.println(counterReroutingLimitExceeded);
					//Accounts the failed connection request
					accounting.addEvent(Accounting.Type.FAILED, event);					
				}								
				//Now, return the result.
				if (rsvpRetry != null)
					response = new Event(event.getTimeStamp(),Event.Type.MESSAGE_ARRIVAL,rsvpRetry);
				else 
					return null;	
				break;
			case CONNECTION_TEARDOWN: //Remove connection
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
			case CONNECTION_FINISHED: //Confirmation of connection removal
				counterConnectionFinished ++;
				//System.err.println("counterConnectionFinished: "+counterConnectionFinished);
				//Account the finishing of a previously connection established
				accounting.addEvent(Accounting.Type.SUCCESS, event);	
				break;
			case MESSAGE_ARRIVAL: // Ospf
				//System.out.println(event);
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
				ArrayList<String> aNodes = (ArrayList<String>) graph.nodes().clone();
				for (String node : aNodes) {
					int degree = graph.adjacencyDegree(node);
					if (degree == 0) {
						try {
							System.out.println("Removing node: "+node);
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
				failuresLink.add(new Event(timeNotification,Event.Type.MESSAGE_ARRIVAL,failureFrom));
				failuresLink.add(new Event(timeNotification,Event.Type.MESSAGE_ARRIVAL,failureTo));	
				failuresLink.add(new Event(timeNotification,Event.Type.MESSAGE_ARRIVAL,revFailureFrom));
				failuresLink.add(new Event(timeNotification,Event.Type.MESSAGE_ARRIVAL,revFailureTo));		
				return new Event(timeNotification,Event.Type.MULTIPLE,failuresLink);
				
			case FAILURE_NODE: //For node failure
				System.out.println("Failure node: "+event.toString());
				//Get the node associated with the failure
				id = (String)event.getContent();
				ArrayList<Event> failuresNode = new ArrayList<Event>();
				//Gets the neighbors of this graph
				ArrayList<String> neighbors = graph.adjacentNodes(id);
				for (String neighId:neighbors) {
					//Add the edge of the removed node
					failuresNode.add(new Event(lastTime,Event.Type.FAILURE_LINK,new String(id+"-"+neighId)));
					////Add the edge "to" the removed node
					////*failuresNode.add(new Event(lastTime,Event.Type.FAILURE_LINK,new String(neighId+"-"+id)));
				}
				//Remove the failure node from the graph
				try {					
					graph.removeNode(id);
				} catch(Exception e) {e.printStackTrace();}	
				//Recalculate the set of paths
				setPaths = this.getPaths(graph,reroutingAttempts);
				//Remove the node from the list of nodes 
				nodes.remove(id);
				//this.printAllConnections();
				//Return the response containing the failure of the multiple links
				return new Event(lastTime,Event.Type.MULTIPLE,failuresNode);
			default: System.err.println("Unknown event: "+event.toString());
			return null;
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
			long seed_bandwidth = Long.parseLong(parameters.get("/Generators/Traffic/@seed_bandwidth").get(index));
			double[] bandwidths = stringToDoubleArray(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			//System.out.println("Bandwiths: " + Arrays.toString(bandwidths));
			double[] probabilities = stringToDoubleArray(parameters.get("/Generators/Traffic/@bandwidths_probabilities").get(index));	
			//System.out.println("Probabilities: " + Arrays.toString(probabilities));
			subscriber = new NonUniformRequestTraffic(tries,seed_bandwidth,bandwidths,probabilities);	
		} else if(nameClass.equals("fon.FixedRequestTraffic")) {
			int tries = Integer.parseInt(parameters.get("/RSA/Routing/@maxAttempts").get(0));
			double bandwidth = Double.parseDouble(parameters.get("/Generators/Traffic/@bandwidth").get(index));
			subscriber = new FixedRequestTraffic(tries,bandwidth);				
		} else if (nameClass.equals("fon.ospf.OSPFRefreshTraffic")) {
			subscriber = new OSPFRefreshTraffic();
		} else if (nameClass.equals("fon.ospf.llrsa.OSPFLLRSARefreshTraffic")) {
			subscriber = new OSPFLLRSARefreshTraffic();
		}
		return subscriber;
	}

	@Override
	public ArrayList<Event> getOtherEvents() {
		//Empty list
		return new ArrayList<Event>();
	}

	/**
	 * Prints the last simulation time.
	 */
	public void updateValues() {
		System.out.println("LastTime: "+lastTime);
		System.out.println("Disrupted: "+this.disruptedConnection.keySet().toString());
		System.out.println("Total disrupted: "+disruptedConnection.size());
		System.out.println("Rerouted: "+reroutedConnection.toString());
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
	 * Recalculate the routing table accordingly to the LSAs received.
	 * @param lsas
	 * @return
	 */
	public static void updateRoutingTable(long sequence, LSDB lsdb, ArrayList<LSA> lsas) {
		if (sequence > current_sequence) { //Recalculate the set of paths.
			setPaths = getPaths(lsas, reroutingAttempts);
			current_sequence = sequence;
		} 
		//Update the topology
		//System.out.println("setPaths: "+setPaths);
		lsdb.updateFromTopology(graph,setPaths);
	}
	
	
	/**
	 * Create the set of shortest paths based on the LSAs received by the node
	 * @param lsas The LSAs received by the node
	 * @param alternative The number of alternative paths
	 * @return 1+alternatives paths for each pair source-destination of the topology.
	 */
	protected static LinkedHashMap<String,ArrayList<Path>> getPaths(ArrayList<LSA> lsas, int alternative) {
		//Create the graph
		Graph LSAGraph = LSAToGraph(lsas);
		LinkedHashMap<String,ArrayList<Path>> routes = new LinkedHashMap<String,ArrayList<Path>>();
		YEN yen = new YEN();
		for(String src: LSAGraph.nodes()) {
			for (String tgt: LSAGraph.nodes()) {
				if (!src.equals(tgt)) { //Assure different nodes in the pair
					ArrayList<Path> paths = null;;
					try { 
						paths = yen.getShortestPaths(src,tgt,LSAGraph,1+alternative);
					} catch (Exception e) {e.printStackTrace();}
					routes.put(src+"-"+tgt,paths);
				}
			}
		}
		//System.out.println("-ROUTES: "+routes);
		return routes;		
	}
	
	/**
	 * Create the set of shortest paths for the first time
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
	 * Creates a new graph with de LSAs informations received by the node
	 * @param lsas The LSAs received by the node
	 * @return the graph.
	 */
	protected static Graph LSAToGraph (ArrayList<LSA> lsas) {
		//The metric value for validation
		//double value = 1.0;
		//Create the graph
		Graph LSAGraph = new Graph();		
		// Sort LSA's
		Collections.sort(lsas);		
		//Add the nodes to the graph
		for(LSA lsa:lsas) {
			try {
				LSAGraph.addNode(lsa.nodeLSA);
			}catch (Exception e) {} //do nothing - method not implemented
		}
		
		//Add the edges to the graph with the metric values
		for(LSA lsa:lsas) {
			LinkedHashMap<String,boolean[]> neighborsLinkStates = lsa.getLinkState().getNeighborsLinksStates();
			for (String adjacentNode: neighborsLinkStates.keySet()) {
				try {
					boolean[] slots = neighborsLinkStates.get(adjacentNode);
					LabelSet ls = new LabelSet(slots);
					//metric to OSPF
					
					//metric 1
					//double value = 1 + ((ls.getNumberSlots()-(ls.freeBandwidth()/FlexiLink.SLOT_WIDTH_GRANULARITY))*0.1);
					
					//metric 2
					//double value = 1 + ((((ls.getNumberSlots()-(ls.freeBandwidth()/FlexiLink.SLOT_WIDTH_GRANULARITY)))/(ls.getNumberSlots()/4)));
					
					//metric 3
					//get distance
					//double distance = (double) graph.getEdge(lsa.nodeLSA, adjacentNode).getValue();	
					//calculate the value
					//double value = 	distance + ((((ls.getNumberSlots()-(ls.freeBandwidth()/FlexiLink.SLOT_WIDTH_GRANULARITY)))/(ls.getNumberSlots()/4)));					
					
					//metric 4
					//double value = 1 - ((ls.freeSlots())/(ls.getNumberSlots()));
					
					double value = 1.0;
					
					//Calculate the link cost.
					if (metric.equals(OSPF.TESTE))
						value = Metric.Teste(ls);		
					else if (metric.equals(OSPF.RFI))
						value = Metric.Teste(ls);					
					
					//add to the graph
					LSAGraph.addEdge(lsa.nodeLSA, adjacentNode, value);
					//System.out.println(LSAGraph);
				}catch (Exception e) {} //do nothing - method not implemented
			}
		}
		//LSAGraph.print();
		//Return the graph created
		return LSAGraph;		
	}

}
