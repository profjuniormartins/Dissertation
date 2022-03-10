package fon.ospf;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import event.Event;
import fon.Connection;
import fon.FlexiLink;
import fon.FrequencySlot;
import fon.LabelSet;
import fon.Metric.OSPF;
import fon.RSVP;
import fon.Request;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import fon.topological.FON;
import fon.topological.FlexiNode;
import graph.Edge;
import graph.Graph;
import net.Error;
import net.Failure;
import net.Link;
import net.Message;
import net.RoutingTable;
import net.Failure.Location;

public class OSPFNode extends FlexiNode {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The list of OSPF updates already seen by this node. */
	LinkedHashMap<Long,ArrayList<LSA>> updates;
	
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
	public OSPFNode(String id, Type type, RoutingTable routingTable, LinkedHashMap<String, FlexiLink> flexiLinks, Graph graph, ReRouting behavior, int maxAttempts, int attempts, OSPF metric, Strategy sa) {
		super(id, type, routingTable, flexiLinks, graph, behavior, maxAttempts, attempts, sa);
		//Initialize the list of seen updates
		this.updates = new LinkedHashMap<Long,ArrayList<LSA>>(); 
		//System.out.println("UPDATES NO CONSTRUTOR: "+updates);
	}

	@Override
	public Event process(Event event) {
		//Get the packet associated to this event
		Message msg = (Message)event.getContent();
//		if (msg.getId() != null && msg.getId().equals("210"))
//			System.out.println(msg.toString());
		//Inspects its header and decides what to do with it.
	    Message.Type type = msg.getType();
	    Request req = null; //Request
		Connection conn = null; //Connection
	    Event response=null; //The response to the processing 
	    String nextHop; //The next hop in the path
		Error error; //The associated error specification
		FrequencySlot fs = null; //Frequency Slot 
		LSA lsa = null; // Link-State Advertising
		Link link; //The link		
		//System.out.println(event.toString());
	    switch (type) {
	    	case LSA:
	    		//System.out.println("------------------------------------------------");
		    	//System.out.println("Nó: "+id);
    			//System.out.println("LSA @"+id+": "+event.toString());
	    		lsa = (LSA) msg;
	    		//Get the sequence number
	    		Long sequence = Long.parseLong(lsa.getId());
	    		//Get the list of already seen for this sequence number
	    		//System.out.println("---------------------------UPDATES: "+updates);
	    		ArrayList<LSA> seen = updates.get(sequence);
	    		//Flag for updating the list of seen messages
	    		boolean update = true;	    		
	    		/* Only for debug
	    		//Get the LSAs sources nodes seen by the node
	    		ArrayList<String> nodesLSAs = new ArrayList<String>();	    		
	    		for(LSA s:seen) {
	    			nodesLSAs.add(s.nodeLSA);	    			
	    		}
	    		*/	   			    		
	    		//System.out.println("LSA dos nós recebidos em sequence "+sequence+" nó "+id+": "+ nodesLSAs);
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
							link.setCounter(Message.HEADER_LENGTH + lsa.getPayloadLength());
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
		    			//System.out.println("CALCULAR SEQUENCE "+sequence+" NÃ“ "+id);
		    			//Remove the LSA node 0
		    			//seen.remove(0);
		    			//Calculate the new topology
		    			OSPFFON.updateRoutingTable(sequence, (LSDB)routingTable, seen);
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
				//Get the PATH message
				RSVP path = (RSVP) msg;
				//Get the label set object
				LabelSet labelSet = path.getLabelSet();
				//Verify if the message arrived at the destination node
				if (req.getDestination().equals(id)) {
					double bandwidth = req.getBandwidth();
					double slotGranularity = FlexiLink.SLOT_WIDTH_GRANULARITY;
					int numberSlotsFS = calculateNumberSlotsFS(bandwidth, slotGranularity); 					
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
						nextHop = ((LSDB)routingTable).nextHop(path, routingTry);
					} else { //None or end-to-end routing
						nextHop = ((LSDB)routingTable).nextHop(path, req.getCurrentTry());
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
	    		System.out.println("Link Failure @"+id+": "+event.toString());
   			    //Packet associated with control plane (separated channel)
		    	//Identify the failure
		    	Failure failure = (Failure) msg.getContent();
		    	//Flooding of the failure
		    	if (!failureID.contains(failure.getID())) { //First time
		    		//Mock local update of the topology
		    		System.out.println("Graph nodes: " + graph.nodes());
		    		routingTable.updateFromTopology(graph);
		    		//OU
		    		//((LSDB)routingTable).updateFromTopology(graph,OSPFFON.getPaths());
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
		    				broadcast.add(new Event(newTimeStamp,Event.Type.MESSAGE_ARRIVAL,clonedFrom));
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
		    					broadcast.add(new Event(newTimeStamp,Event.Type.MESSAGE_ARRIVAL,pathErr));
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
		    					broadcast.add(new Event(newTimeStamp,Event.Type.MESSAGE_ARRIVAL,pathTear));
		    				}
		    			}
		    		}	    		
		    		//System.out.println("Broadcast:"+broadcast.toString());
		    		//Return the multiple packets associated with the failure
		    		response = new Event(event.getTimeStamp(),Event.Type.MULTIPLE,broadcast);
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

}
