/*
 * Created on Feb 14, 2008.
 */
package fon.antnet;

import event.Event;
import graph.Edge;
import graph.Graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import net.Message;
import net.RoutingTable;
import ant.Ant;
import ant.StatisticalParametricModel;
import net.Failure;
import net.Link;
import net.Failure.Location;
import fon.antnet.AntFON;
import fon.antnet.AntRoutingTable;
import fon.Connection;
import net.Error;
import fon.Request;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import fon.FlexiLink;
import fon.FrequencySlot;
import fon.RSVP;
import fon.LabelSet;

/**
 * Colony as described for the AntNet framework with crankback rerouting extensions.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class AntFlexiNode extends fon.topological.FlexiNode {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** Statistical Parametric Model for global-traffic statistics. */
	protected StatisticalParametricModel parametricModel;
	/** Frequency Slot usage table for Most-used spectrum assignment. */
	protected FrequencySlotUsageTable lambdaTable;
	
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
	public AntFlexiNode(String id, Type type, RoutingTable routingTable, LinkedHashMap<String, FlexiLink> flexiLinks, Graph graph, ReRouting behavior, int maxAttempts, int attempts, Strategy sa, StatisticalParametricModel aModel, FrequencySlotUsageTable usageTable) {
		super(id, type, routingTable, flexiLinks, graph, behavior, maxAttempts, attempts, sa);
		this.parametricModel = aModel;
		this.lambdaTable = usageTable;
	}
	
	/**
	 * Process the specified event.
	 * @param event The event to be processed.
	 * @return The processed event.
	 */
	@Override
	public Event process(Event event) {
		//Get the packet associated to this event
		Message msg = (Message)event.getContent();
		//if (msg.getId() != null && msg.getId().equals("3824r"))
			//System.out.println(event.toString());
		//Inspects its header and decides what to do with it.
	    Message.Type type = msg.getType();
	    Request req = null; //Request
		Connection conn = null; //Connection
	    Event response=null; //The response to the processing 
	    String nextHop; //The next hop in the path
		Ant ant; //Ant packet
		Error error; //The associated error specification
		String destination; //Destination node
		Link link; //The link		
		FrequencySlot fs = null; //Frequency Slot 
		//System.out.println(event.toString());
	    switch (type) {
	    	case ANT_FORWARD:
	    		ant = (Ant) msg;
				//Get the destination node
				destination = ant.getDestination();
		    	//Verify if the ant has reached the destination node. 
		    	if (destination.equals(id)) { //Ant reached the destination node 
		    		//Turn it into a backward ant
		    		ant.toBackward();
		    		//Set the next node as the last visited before the target
		    		nextHop = ant.getBackwardNode();
		    	} else { //Not arrived in the target node
			    	//Decide the next hop based on the information of the routing table
		    		//and on the information of the optical buffers
		    		nextHop = ((AntRoutingTable)routingTable).nextHop(ant,flexiLinks);
		    		if ((nextHop == null) ||  (msg.getHopLimit() == 0)) { //Ant killed due to loop or expired hop limit
			    		event.setType(Event.Type.ANT_KILLED);
			    		response = event;
			    		break;
		    		} else {
		    			//Decrement the number of hops
		    			ant.decrementHopLimit();
		    			//Add the wavelength mask to the ant payload
		    			FlexiLink mask = flexiLinks.get(nextHop);
		    			ant.addMask(mask);
		    		}
		    	}
		    	//Set the utilization of the control channel
		    	link = flexiLinks.get(nextHop).getLink();
				link.setCounter(Message.HEADER_LENGTH + ant.getPayloadLength());
		    	//Set the next hop in the packet
		    	ant.setNode(nextHop);
				//Return the response
		    	response = event;
	    		break;
	    	case ANT_BACKWARD:
	    		ant = (Ant) msg;
	    		/* Update the pheromone routing table */
	    		((AntRoutingTable)routingTable).update(ant,parametricModel);	    		
	    		//Verify if the ant reached the source node.
		    	if (ant.getSource().equals(id)) {
		    		//Add the memory of the ant to the wavelength usage table
		    		if (!ant.getLoopFlag()) //Not looped
		    			lambdaTable.update(ant.getCollector(), ant.getDestination());
		    		//Set the ant as routed
		    		event.setType(Event.Type.ANT_ROUTED);
		    	} else {
		    		//See the next hop
					nextHop = ant.getBackwardNode();
		    		//Verify if the link is not damaged.
		    		if (!AntFON.hasConnectivity(id,nextHop)) {
		    			event.setType(Event.Type.ANT_KILLED);
		    			response = event;
		    			//System.out.println("Back: "+event.toString());
		    			break;
		    		}
			    	//Set the utilization of the control channel
			    	link = flexiLinks.get(nextHop).getLink();
					link.setCounter(Message.HEADER_LENGTH + ant.getPayloadLength());
		    		//Set the next node in the path
		    		ant.setNode(nextHop);
		    	}	    		
	    		response = event;
	    		break;
	    	case RSVP_PATH:
	    		//Get the request
				req = (Request)msg.getContent();
				//Get the PATH message
				RSVP path = (RSVP) msg;
				//Get the label set object
				LabelSet labelSet = path.getLabelSet();
				if (labelSet == null) {
					System.err.println("label set null: "+event.toString());
				}
				//Verify if the message arrived at the destination node
				if (req.getDestination().equals(id)) {
					//System.out.println("(req.getDestination().equals(id))");
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
						//System.out.println("--(fs != null)");
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
						//System.out.println("--There is no free frequency slot to allocate");
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
				} else if (labelSet.freeBandwidth() < req.getBandwidth() ) {//There is no free frequency slot to allocate in the intermediate node
					//Convert to a PATH_ERR message
					path.setType(Message.Type.RSVP_PATH_ERR);
					//Reset the SD pair to the new values
					path.setSDPair(id,req.getSource());
					//Set the error
					error = new Error(Error.Code.RP_LABEL_SET);
					path.setError(error);					
					//Get the next hop					
	    			nextHop = path.getBackwardNode();
					path.addEffectiveHop();
					//Do not record the route anymore
					path.setRecordRoute(false);	
				} else { //Intermediate node
					//System.out.println("Intermediate node");
		    		//Get the next hop
					if (this.rerouting.equals(ReRouting.SEGMENT)) { //Intermediate node re-routing
						//no routingTry
						nextHop = ((AntRoutingTable)routingTable).nextHop(path,this.getHistoryTable(path.getId()));
					} else { //None or end-to-end routing
						nextHop = ((AntRoutingTable)routingTable).nextHop(path,new ArrayList<String>());
					}
		    		if (nextHop == null || (msg.getHopLimit() == 0) || !(AntFON.hasConnectivity(id,nextHop))) { //dead-end 
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
		    			if ((this.rerouting.equals(ReRouting.SEGMENT)) && (labelSet != null) )
		    				labelSetTable.put(path.getId(), (LabelSet)labelSet.clone());		    				
		    			//Updates the mask
		    			if (labelSet == null) {
		    				if (sa.equals(Strategy.FIRST_FIT))
		    					labelSet = new LabelSet(this.flexiLinks.get(nextHop).getNumberSlots());
		    				else if (sa.equals(Strategy.BEST_FIT))
								labelSet = new LabelSet(this.flexiLinks.get(nextHop).getNumberSlots());
//		    				else if (sa.equals(Strategy.MOST_USED)) 
//								labelSet = new LabelSet(getMostUsed(this.links.get(nextHop).getNumberWavelengths()));
//		    				else if (wa.equals(WavelengthAssignment.LEAST_USED)) 
//		    					labelSet = new LabelSet(getLeastUsed(this.links.get(nextHop).getNumberWavelengths()));
		    				path.setLabelSet(labelSet);
		    			}		    		
		    			//System.out.println(flexiLinks.get(nextHop).getMask());
		    			path.updateMask(flexiLinks.get(nextHop).getMask());
		    			//Decrement the number of hops
		    			path.decrementHopLimit();
		    			path.addEffectiveHop();
		    	    }
				}
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
		    		routingTable.updateFromTopology(graph);
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
	
	/**
	 * Returns the most used label set for wavelength assignment.
	 * @param usage The usage vector.
	 * @return The most used label set for wavelength assignment.
	 */
	protected int[] getMostUsed(long[] usage) {
		int[] order = new int[usage.length];
		for(int i=0; i < usage.length; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for(int j=0; j < usage.length; j++) {
				if (usage[j] > max) {
					max = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MIN_VALUE;
		}
		return order;
	}
	
	protected int[] getMostUsed(int w) {
		//Gets the total usage for all destinations
		FrequencySlotUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage,0L);
		for (int i=0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for(int j=0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		//Now order from the most used to the least used 
		int[] order = new int[w];
		for(int i=0; i < w; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for(int j=0; j < w; j++) {
				if (totalUsage[j] > max) {
					max = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MIN_VALUE;
		}
		return order;
	}
	
	/**
	 * Returns the least used label set for wavelength assignment.
	 * @param usage The usage vector.
	 * @return The least used label set for wavelength assignment.
	 */
	protected int[] getLeastUsed(long[] usage) {
		int[] order = new int[usage.length];
		for(int i=0; i < usage.length; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for(int j=0; j < usage.length; j++) {
				if (usage[j] < min) {
					min = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MAX_VALUE;
		}
		return order;
	}

	protected int[] getLeastUsed(int w) {
		//Gets the total usage for all destinations
		FrequencySlotUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage,0L);
		for (int i=0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for(int j=0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		//Now order from the least used to the most used 
		int[] order = new int[w];
		for(int i=0; i < w; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for(int j=0; j < w; j++) {
				if (totalUsage[j] < min) {
					min = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MAX_VALUE;
		}
		return order;
	}
}
