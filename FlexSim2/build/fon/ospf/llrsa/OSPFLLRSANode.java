package fon.ospf.llrsa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import event.Event;
import fon.Connection;
import fon.FlexiLink;
import fon.FrequencySlot;
import fon.LabelSet;
import fon.RSVP;
import fon.Request;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import fon.ospf.llrsa.LSDB;
import fon.topological.ExplicitRoutingTable;
import fon.topological.FON;
import fon.topological.FlexiNode;
import graph.Edge;
import graph.Graph;
import graph.Path;
import net.Error;
import net.Failure;
import net.Link;
import net.Message;
import net.RoutingTable;
import net.Failure.Location;

public class OSPFLLRSANode extends FlexiNode {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The list of OSPF updates already seen by this node. */
	LinkedHashMap<Long,ArrayList<LSA>> updates;
	/** LSDB */
	protected LSDB lsdb;
	/** The current sequence number. */
	protected long current_sequence;
	
	/**
	 * Creates a new AntNetLSR object.
	 * @param identification The identification of this node in the network.
	 * @param aRoutingTable The routing table associated with this node.
	 * @param aLinks The set of link states that belongs to this node.
	 * @param aGraph The graph representing the network.
	 * @param sa The chosen spectrum assignment algorithm.
	 * @param behavior The re-routing behavior.
	 * @param attempts The number of re-routing attempts.
	 * @param aModel The set of parametric models of this node.
	 * @param usageTable The lambda usage table of this node.
	 */
	public OSPFLLRSANode(String id, Type type, RoutingTable routingTable, LinkedHashMap<String, FlexiLink> flexiLinks, Graph graph, ReRouting behavior, int maxAttempts, int attempts, Strategy sa) {
		super(id, type, routingTable, flexiLinks, graph, behavior, maxAttempts, attempts, sa);
		//Initialize the list of seen updates
		this.updates = new LinkedHashMap<Long,ArrayList<LSA>>(); 
		//System.out.println("UPDATES NO CONSTRUTOR: "+updates);
	}

	@Override
	public Event process(Event event) {
		//Get the packet associated to this event
		Message msg = (Message)event.getContent();
		//if (msg.getId() != null && (msg.getId().equals("1977r") || msg.getId().equals("1977")))
			//System.out.println(event.toString());
		//Inspects its header and decides what to do with it.
	    Message.Type type = msg.getType();
	    Event response=null; //The response to the processing 
	    String nextHop; //The next hop in the path
		Error error; //The associated error specification
		LSA lsa = null; // Link-State Advertising
		Link link; //The link		
		Request req = null; 		//Request 
		Connection conn = null; 	//Connection
		FrequencySlot fs = null; 	//Frequency Slot 
		//System.out.println(event.toString());
	    switch (type) {
	    	case LSA:
	    		lsa = (LSA) msg;
	    		//Get the sequence number
	    		Long sequence = Long.parseLong(lsa.getId());
	    		//Get the list of already seen for this sequence number
	    		ArrayList<LSA> seen = updates.get(sequence);
	    		//Flag for updating the list of seen messages
	    		boolean update = true;
	    		//for all seen LSAs do
	    		for(LSA s:seen) {
	    			if ((sequence.equals(Long.parseLong(s.getId()))) && (s.nodeLSA.equals(lsa.nodeLSA)) /*((nodesLSAs.contains(lsa.nodeLSA)))*/) { //Already seen
	    				update = false;	    				
	    				break;
	    			}
	    		}
	    		if (update) { //Received a brand new LSA update from a neighbor
	    			//Add to the list of seen LSAs
	    			seen.add(lsa);
	    			//Send a copy to the neighbors via flooding
			    	String previousHop = msg.getSource();
			    	ArrayList<String> neighbors = graph.adjacentNodes(id);
			    	//System.out.println("vizinhos: "+neighbors);
			    	ArrayList<Event> broadcast = new ArrayList<Event>(); 
			    	//Adds the flooding information
			    	for(String neighId:neighbors) {
			    		if (!neighId.equals(previousHop)) { //not visited
			    			//Retransmit the message
			    			Message clonedFrom = (Message) msg.clone();
			    			clonedFrom.retransmit(id,neighId);
			    			clonedFrom.setNode(neighId);
					    	//Set the utilization of the control channel
					    	link = flexiLinks.get(neighId).getLink();
							link.setCounter(lsa.getLength());
							//System.out.println(link.getCounter());
			    			//Set new time stamp
			    			double transmissionTime = this.flexiLinks.get(neighId).getDelay();
			    			double newTimeStamp = event.getTimeStamp() + transmissionTime;
			    			//Add to the list of broadcast
			    			broadcast.add(new Event(newTimeStamp,Event.Type.MESSAGE_ARRIVAL,clonedFrom));
			    		}
			    	}
		    		//Return the multiple packets associated with the LSA
		    		response = new Event(event.getTimeStamp(),Event.Type.MULTIPLE,broadcast);	    			
		    		//If complete, calculate the new topology
		    		if (seen.size() == (graph.size() )) {
		    			//Update the LSDB
		    			this.updateLSDB(sequence, seen);	    			
			    		//System.out.println(this.lsdb);
		    			//System.out.println(lsdb.flexiLinksStates.size());
		    		}
		    	} else { //Already seen the LSA.
		    		//System.out.println("Node "+id+" already seen this LSA (Sequence "+sequence+")"+lsa.toString());
		    		event.setType(Event.Type.IGNORE);
		    		response = event;
		    	}
	    		break;
    		case RSVP_PATH:
				//Get the request 
				req = (Request)msg.getContent();
				//System.out.println(event.toString());
				//Get the PATH message
				RSVP path = (RSVP) msg;
				//Get the label set object
				LabelSet labelSet = path.getLabelSet();
				//Determines the number of slots demand
				double bandwidth = req.getBandwidth();
				double slotGranularity = FlexiLink.SLOT_WIDTH_GRANULARITY;
				int numberSlotsFS = calculateNumberSlotsFS(bandwidth, slotGranularity);				
				//Determines the path according to the LLRSA at the source node
				int pathNumberOrder;
				if (path.getPathLength() == 0) {
					pathNumberOrder = pathLLRSA(req, numberSlotsFS);
					req.setTry(pathNumberOrder);
				} else {
					pathNumberOrder = req.getTry();
				}
				//Verify if the message arrived at the destination node				
				if (req.getDestination().equals(id)) {					
					//Verify if there is an available frequency slot
					if (sa.equals(Strategy.FIRST_FIT))
						fs = SpectrumAssignment.firstFit(labelSet, numberSlotsFS);		
					else if (sa.equals(Strategy.BEST_FIT))
						fs = SpectrumAssignment.bestFit(labelSet, numberSlotsFS);
					//System.out.println(labelSet.toString());
					//System.err.println("\nFrequency Slot: "+ fs);
					if (fs != null) {	//there is frequency slot to allocate					
						//Create a connection
						conn = new Connection(path.getPath(), req, fs);
						//Convert to a RESV message
						path.setType(Message.Type.RSVP_RESV);
		    			//Reset the SD pair to the new values
		    			path.setSDPair(req.getDestination(),req.getSource());		
			    		//Set the start time of the connection, which starts after arriving at the source node.
			    		//Because of that, it uses the round trip time as the time to start it
			    		conn.setStartTime(event.getTimeStamp() + (event.getTimeStamp() - event.getInitialTimeStamp()));
			    		//Set the object to the message
			    		path.setContent(conn);
			    		//Add the connection to the table of active connections. 
			    		activeConnections.put(path.getId(),conn);
			    		//System.out.println(activeConnections.toString());
		    			//* Set the reverse (bidirectional) connection
						FlexiLink linkMask = flexiLinks.get(path.getBackwardNode());
						linkMask.addFrequencySlot(conn.getId(), conn.getFS());
						//System.err.println(linkMask.toString());
					} else {	//There is no free frequency slot to allocate										
						//Convert to a PATH_ERR message
						path.setType(Message.Type.RSVP_PATH_ERR);
		    			//Reset the SD pair to the new values
		    			path.setSDPair(id,req.getSource());
		    			//Set the error
		    			error = new Error(Error.Code.RP_LABEL_SET);
		    			path.setError(error);
		    			path.addEffectiveHop();
					}
	    			//Get the next hop
	    			nextHop = path.getBackwardNode();
					//Do not record the route anymore
					path.setRecordRoute(false);		
				} else if (labelSet.freeBandwidth() < req.getBandwidth() ) {//There is no free frequency slot to allocate 									
					//Convert to a PATH_ERR message
					path.setType(Message.Type.RSVP_PATH_ERR);
					//Reset the SD pair to the new values
					path.setSDPair(id,req.getSource());
					//Set the error
					error = new Error(Error.Code.RP_LABEL_SET);
					path.setError(error);					
					//Get the next hop
	    			nextHop = path.getBackwardNode();
					//Do not record the route anymore
					path.setRecordRoute(false);	
					path.addEffectiveHop();
				} else { //Intermediate node 
		    		//Get the next hop
					if (this.rerouting.equals(ReRouting.SEGMENT)) { //Intermediate node re-routing
						int routingTry = this.sizeHistoryTable(path.getId());
						//System.out.println("routingTry:  "+routingTry);
						nextHop = ((ExplicitRoutingTable)routingTable).nextHop(path, routingTry);
					} else { //None or end-to-end routing
						nextHop = ((ExplicitRoutingTable)routingTable).nextHop(path, pathNumberOrder);
					}
		    		if (nextHop == null || (msg.getHopLimit() == 0) || !(FON.hasConnectivity(id,nextHop))) { //dead-end 
		    			//request.addTry(); //add to the counter of tries
		    			path.setType(Message.Type.RSVP_PATH_ERR); //change type to problem
		    			//Reset the SD pair to the new values
		    			path.setSDPair(id,req.getSource());
		    			//Create the error information
		    			error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
		    			path.setError(error);
			    		//Do not record the route anymore
			    		path.setRecordRoute(false);
			    		if(!id.equals(req.getSource())) {
			    			//Get the next hop (backward)
			    			nextHop = path.getBackwardNode();
			    			path.addEffectiveHop();
			    		} else { //Failure after the first link of the node
			    			response = event;
			    			break;
			    		}
		    	    } else {
		    			//Store the label set, if applicable
		    			if ((this.rerouting.equals(ReRouting.SEGMENT)) && (labelSet != null) )  {
		    				labelSetTable.put(path.getId(), (LabelSet)labelSet.clone());		    				
		    			}
		    			//Updates the mask
		    			if (labelSet == null){
		    				path.setLabelSet(new LabelSet(this.flexiLinks.get(nextHop).getNumberSlots()));
		    			}
		    			//System.out.println(flexiLinks.get(nextHop).getMask());
		    			path.updateMask(flexiLinks.get(nextHop).getMask());
		    			//Decrement the number of hops
		    			path.decrementHopLimit();
		    			path.addEffectiveHop();
		    	    }
				}
				//System.out.println("Next hop: "+nextHop);
				//Set the next hop
				path.setNode(nextHop);		    
				//Add the delay
				double delay = flexiLinks.get(nextHop).getLink().getDelay();
				event.setTimeStamp(event.getTimeStamp()+delay);
 		    	//Return the response
	    		response = event;
	    		break;	 
	    	case RSVP_PATH_TEAR:
	    		response = super.process(event);
	    		break;
	    	case RSVP_PATH_ERR:
	    		response = super.process(event);
	    		break;
	    	case RSVP_RESV:
	    		response = super.process(event);
	    		break;
	    	case RSVP_RESV_TEAR:
	    		response = super.process(event);
	    		break;
	    	case RSVP_RESV_ERR:
	    		response = super.process(event);
	    		break;
	    	case FAILURE_LINK:   
	    		//System.out.println("Link Failure @"+id+": "+event.toString());
   			    //Packet associated with control plane (separated channel)
		    	//Identify the failure
		    	Failure failure = (Failure) msg.getContent();
		    	//Flooding of the failure
		    	if (!failureID.contains(failure.getID())) { //First time
		    		//Mock local update of the topology
		    		//System.out.println("Graph nodes: " + graph.nodes());
		    		//routingTable.updateFromTopology(graph);
		    		((ExplicitRoutingTable)routingTable).updateFromTopology(graph,OSPFLLRSAFON.getPaths());
		    		//Broadcast the failure to the neighbors
		    		String previousHop = msg.getSource();
		    		ArrayList<String> neighbors = graph.adjacentNodes(id);
		    		ArrayList<Event> broadcast = new ArrayList<Event>(); 
		    		//Adds the flooding information
		    		for(String neighId:neighbors) {
		    			if (!neighId.equals(previousHop)) { //not visited
		    				//Retransmit the message
		    				Message clonedFrom = (Message)msg.clone();
		    				clonedFrom.retransmit(id,neighId);
		    				clonedFrom.setNode(neighId);
		    				//Set new time stamp
		    				double transmissionTime = this.flexiLinks.get(neighId).getDelay();
		    				double newTimeStamp = event.getTimeStamp() + transmissionTime;
		    				//Add to the list of broadcast
		    				broadcast.add(new Event(newTimeStamp,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,clonedFrom));
		    			}
		    		}
		    		//Add the failure to the list of processed ones.
		    		failureID.add(failure.getID());
		    		/* Remove the link states affected by the failure. */
		    		if (((Edge)failure.getInformation()).getSource().equals(id)) {
		    			flexiLinks.remove(((Edge)failure.getInformation()).getDestination());
		    		}
		    		/* Treatment the failure by the neighbor nodes */
		    		//Now, if it is the closest node upstream to the failure
		    		if(id.equals(((Edge)failure.getInformation()).getSource())) {
		    			//Adds the notification of broken LSP to the event
		    			for(String activeID:activeConnections.keySet()) {
		    				//Gets the connection
		    				Connection active = activeConnections.get(activeID);
		    				//Locate the failure
		    				Location location = failure.locate(id,active.getPath());
		    				if(!location.equals(Failure.Location.NOT_APPLICABLE)) {
		    					//Create the PathErr packet
		    					RSVP pathErr = new RSVP(active,Message.Type.RSVP_PATH_ERR,id,active.getSource());
		    					//Create the error with path remove flag
		    					error = new Error(Error.Code.LSP_FAILURE,true);		    				
		    					pathErr.setError(error);
		    					//System.out.println(pathErr.toString());
		    					double transmissionTime=0;
		    					//Not the source node to treat the failure
		    					if(!id.equals(active.getPath().firstNode())) {
		    						nextHop = active.getPath().getPreviousNode(id);
		    						pathErr.setNode(nextHop);
		    						//Set new time stamp
		    						transmissionTime = this.flexiLinks.get(nextHop).getDelay();	
		    					}
		    					double newTimeStamp = event.getTimeStamp() + transmissionTime + DELTA_TIME;
		    					broadcast.add(new Event(newTimeStamp,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,pathErr));
		    				}
		    			}
		    			//Now, if it is the closest node downstream to the failure
		    		} else if(id.equals(((Edge)failure.getInformation()).getDestination())) {
		    			//Adds the notification of broken LSP to the event
		    			for(String activeID:activeConnections.keySet()) {
		    				//Gets the connection
		    				Connection active = activeConnections.get(activeID);
		    				//Locate the failure
		    				Location location = failure.locate(id,active.getPath());
		    				if(!location.equals(Failure.Location.NOT_APPLICABLE)) {
		    					//Create the PathErr packet
		    					RSVP pathTear = new RSVP(active,Message.Type.RSVP_PATH_TEAR,id,active.getDestination());
		    					//Create the error
		    					error = new Error(Error.Code.LSP_FAILURE);
		    					pathTear.setError(error);
		    					//System.out.println(pathTear.toString());
		    					//Not the destination node to treat the failure
		    					double transmissionTime = 0;
		    					//Not the last node to tackle the failure
		    					if(!id.equals(active.getPath().lastNode())) {
		    						nextHop = active.getPath().getNextNode(id);
		    						pathTear.setNode(nextHop);
		    						transmissionTime = this.flexiLinks.get(nextHop).getDelay();	
		    					}
	    						//Set new time stamp
		    					double newTimeStamp = event.getTimeStamp() + transmissionTime + DELTA_TIME;
		    					//Add to the list of broadcast
		    					broadcast.add(new Event(newTimeStamp,event.getInitialTimeStamp(),Event.Type.MESSAGE_ARRIVAL,pathTear));
		    				}
		    			}
		    		}	    		
		    		//System.out.println("Broadcast:"+broadcast.toString());
		    		//Return the multiple packets associated with the failure
		    		response = new Event(event.getTimeStamp(),event.getInitialTimeStamp(),Event.Type.MULTIPLE,broadcast);
		    	} else { //Already processed the failure.
		    		//System.out.println("Node "+id+" already processed this failure");
		    		event.setType(Event.Type.IGNORE);
		    		response = event;
		    	}
		    	return response;
			default:
				System.err.println("Unknown message: "+event.toString());
				break;		
	    }				
	    //Return the response
		return response;	
	}
	
	public ArrayList<Event> getUpdates(Event update) {
		//Get the sequence number
		Long sequence = (Long) update.getContent();
		//Get the time stamp
		double timeStamp = update.getTimeStamp();
		//List of multiple events
		ArrayList<Event> multiple = new ArrayList<Event>();
		//Create the state advertisement values
		LinkedHashMap<String, boolean[]> neighborLinks = new LinkedHashMap<String,boolean[]>();
		for (String neighbor_id: flexiLinks.keySet()) {			
			boolean[] slots = flexiLinks.get(neighbor_id).getMask();
			neighborLinks.put(neighbor_id, slots);
		}	
		//Create the state advertisement
		LinkState adv = new LinkState(neighborLinks);		
		//Add the entry to the list
		ArrayList<LSA> list = new ArrayList<LSA>();
		list.add(new LSA(sequence.toString(),this.id,this.id,this.id,adv));
		//Clear the previous updates 
		this.updates.clear();
		//add sequence and LSA list to updates
		this.updates.put(sequence,list);
		//for all neighbors flood its current state
		for (String neighbor: flexiLinks.keySet()) {
			//Create the LSA
			LSA lsa = new LSA(sequence.toString(),this.id,neighbor,this.id,adv);
			//Send the LSA to the neighbor
			lsa.setNode(neighbor);
			//Get the new times
			double transmissionTime = this.flexiLinks.get(neighbor).getDelay();
			//System.out.println("lsa :"+lsa);
			Event neigh = new Event(timeStamp+transmissionTime,Event.Type.MESSAGE_ARRIVAL,lsa);
			//Add to the list
			multiple.add(neigh);
		}
		return multiple;
	}
	
	
	/**
	 * Recalculate the LSDB with the LSAS.
	 * @param sequence
	 * @param lsas
	 */
	public void updateLSDB(long sequence, ArrayList<LSA> lsas) {
		//If it is the last sequence, recalculate the LSDB with the LSAS.
		if (sequence > current_sequence) { 
			this.lsdb = new LSDB(LSAsToLSDB(lsas));
			current_sequence = sequence;
		} 
	}

	/**
	 * Join the LSAs informations received by the node to use in the LSDB
	 * @param lsas The LSAs received by the node
	 * @return the informations joined .
	 */
	protected static LinkedHashMap<String, boolean[]> LSAsToLSDB (ArrayList<LSA> lsas) {
		// The flexilinks states of the LSAs to create the LSDB. 
		LinkedHashMap<String,boolean[]> flexiLinksStates = new LinkedHashMap<String,boolean[]>();
		// The source node
		String src = null;
		// The destination node
		String dst = null;	
		// Sort LSA's
		Collections.sort(lsas);		
		//Add the edges to the LSDB with the slots states.
		//System.out.println("LSAs: "+ lsas);
		//For each LSA
		for(LSA lsa:lsas) {
			//Get the flexilinks state of neighboring nodes. 
			LinkedHashMap<String,boolean[]> neighborsLinkStates = lsa.getLinkState().getNeighborsLinksStates();
			//System.out.println("neighborsLinkStates: "+neighborsLinkStates);
			// For each neighbor
			for (String adjacentNode: neighborsLinkStates.keySet()) {
					//Clone the slots of the neighbor
					boolean[] slots = neighborsLinkStates.get(adjacentNode).clone();
					//System.out.println("slots: "+Arrays.toString(slots));
					//Add to the flexilinks states to create the LSDB.
					src = lsa.getNodeLSA();
					dst = adjacentNode;
					flexiLinksStates.put(src+"-"+dst,slots);
			}
		}
		//Return the flexiLinkStates created
		return flexiLinksStates;		
	}		
	
	/**
	 * Returns the number order of the path determined by LLRSA.
	 * @param numberSlotsFS 
	 * @param paths 
	 * @return Returns the number order of the path determined by LLRSA
	 */
	public int pathLLRSA (Request req, int numberSlotsFS) {	
		
		String source = req.getSource(); // Source node
		String destination = req.getDestination(); //Destination node			
		
		//Get the paths to the source/destination pair
		
		ArrayList<Path> paths = OSPFLLRSAFON.setPaths.get(source+"-"+destination);
		
		//parameters LLRSA
		int flagRouteAvailable = 0;
		int routeAvailable = 0;	
		int noOfFSAvailable = 0;
		
		//Counter paths number order
		int i = 0;
			
		//LLRSA algorithm
		if (paths != null) {
			for (Path path : paths) {
				int availablesFS = countAvailablesFS(path);
				int demandFS = numberSlotsFS;
				if ((flagRouteAvailable == 0) && (availablesFS >=  demandFS )) {
					routeAvailable = i;
					flagRouteAvailable = 1;
					noOfFSAvailable = availablesFS;				
				} else if ((flagRouteAvailable == 1) && (availablesFS > noOfFSAvailable)){
					routeAvailable = i;
					noOfFSAvailable = availablesFS;
				}			
				i++;
			}
		}
		//Return route number order
		return routeAvailable;
	}
	
	/**
	 * Returns the number of FSs available on the path 
	 * @param path
	 * @return The number of FSs available on the path
	 */
	public int countAvailablesFS (Path path) {
		//parameters
		String source = null; // Source node
		String destination = null; // Destination node
		int numberFSAvailable = 0; // Available FS on path
		
		boolean[] compareSlots = null; //Initialize the variable to use in slot comparison
		
		//Find free slots all the way through all the edges 
		for (Edge edge : path.edges()) {
			//Get edge source
			source = edge.getSource();
			//Get edge destination
			destination = edge.getDestination();			
			//Get the links states of the edge (s-d) in the LSDB
			//System.out.println("source: "+source+" destination: "+destination);	
			//System.out.println(this.lsdb);			
			boolean[] slots = (this.lsdb.flexiLinksStates.get(source+"-"+destination));
			//System.out.println(Arrays.toString(slots));
			//If first node
			if (source == path.firstNode()){
				compareSlots = slots;
			}
			if (compareSlots == null) {
				System.out.println("null1: "+path.toString()+" from "+source+"-"+destination+ " @ "+id);
				System.out.println(lsdb.toString());
			}
			if (slots == null) {
				System.out.println("null2: "+path.toString()+" from "+source+"-"+destination+ " @ "+id);
				System.out.println(lsdb.toString());
			}
		//	Compare slots availability
			compareSlots = compareSlots(compareSlots, slots);		
		}		
		//Create label set with the link states slots
		LabelSet ls = new LabelSet(compareSlots);		
		//Calculate FS available slots in the ls
		numberFSAvailable = (int) ((ls.freeBandwidth()/FlexiLink.SLOT_WIDTH_GRANULARITY));			
		//Return the available FS on path
		return numberFSAvailable;		
	}
	
	
	/**
	 * Compare two booleans[] with the "and" operation and return a new boolean[] as a result
	 * @param slots1
	 * @param slots2
	 * @return A boolean[] as result
	 */
	boolean[] compareSlots (boolean[] slots1, boolean[] slots2) {	
		//Initialize the boolean[] newSlots
		boolean[] newSlots = new boolean[slots1.length];
		//Do the "and" operation between the two slots
		for (int i = 0; i< slots1.length; i++) {
			newSlots[i] = slots1[i] && slots2[i];
		}		
		//Return the result
		return newSlots;		
	}
	
		
}
