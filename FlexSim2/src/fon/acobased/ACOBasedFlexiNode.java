/*
 * Created on 01/05/2020.
 */

package fon.acobased;

import event.Event;
import graph.Graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import net.Message;
import net.RoutingTable;
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
 * Colony as described for the ACO based algorithm.
 */
public class ACOBasedFlexiNode extends fon.topological.FlexiNode {
	/** Serial version UID. */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;
	/** The time when the routing table is updated */
	protected int timeUpdateRT;
	
	/**
	 * Creates a new AntNetLSR object.
	 * @param identification The identification of this node in the network.
	 * @param FlexiNode type
	 * @param routingTable The routing table associated with this node.
	 * @param flexiLinks The set of link states that belongs to this node.
	 * @param aGraph The graph representing the network.
	 * @param sa The chosen spectrum assignment algorithm.
	 * @param behavior The re-routing behavior.
	 * @param attempts The local number of re-routing attempts.
	 * @param maxAttempts The maximum number of re-routing attempts.
	 * @param aTimeUpdateRT Select when to update the routing table

	 */
	public ACOBasedFlexiNode(String id, Type type, RoutingTable routingTable, LinkedHashMap<String, FlexiLink> flexiLinks, Graph graph, ReRouting behavior, int maxAttempts, int attempts, Strategy sa, int aTimeUpdateRT) {
		super(id, type, routingTable, flexiLinks, graph, behavior, maxAttempts, attempts, sa);
		this.timeUpdateRT = aTimeUpdateRT;
	}
	
	
	/**
	 * Process the specified event.
	 * @param event The event to be processed.
	 * @return The processed event.
	 */
	@SuppressWarnings("unused")
	@Override
	public Event process(Event event) {
		//Get the packet associated to this event
		Message msg = (Message)event.getContent();
		//if (msg.getId() != null && msg.getId().equals("43001"))
		//System.out.print("event:" + event.toString() );
		//Inspects its header and decides what to do with it.
	    Message.Type type = msg.getType();
	    Request req = null; //Request
		Connection conn = null; //Connection
	    Event response=null; //The response to the processing 
	    String nextHop; //The next hop in the path
		ACOBasedAnt ant; //Ant packet
		Error error; //The associated error specification
		String destination; //Destination node
		String source; //Source node
		FrequencySlot fs = null; //Frequency Slot 
		//System.out.println(event.toString());
	    switch (type) {
	    	case ANT_FORWARD:	    		
	    		ant = (ACOBasedAnt) msg;	    		
	    		//System.out.println("Ant: "+ ant);		    		
				//Get the destination node
				destination = ant.getDestination();
				source = ant.getSource();
				//If the parameter is chosen to change the routing table update time (parameter 2)
				if (!(source.equals(id)) && timeUpdateRT == 2){ //Only if it is not a source node
					((ACOBasedRoutingTable)routingTable).update(ant);
				}
				//Verify the TTL (TTL > 0?)      Check if TTL is greater than zero, if not, kill the ant.
				if (ant.getTTL() > 0) {   				
					//Verify if the ant has reached the destination node. (if yes, kill the ant, if not, go to the next node)
					if (destination.equals(id)) { //Ant reached the destination node 
						//((ACOBasedRoutingTable)routingTable).update(ant);
			    		event.setType(Event.Type.ANT_KILLED);
			    		response = event;
			    		break;
				   	} else { //Not arrived in the destination node
				    	//Decide the next hop based on the information of the routing table (and the number of free slots if the parameter is set to 2) 
						nextHop = ((ACOBasedRoutingTable)routingTable).nextHop(ant,flexiLinks);
			    		//System.out.println("nextHop: "+nextHop);
			    		if ((nextHop == null) ) { //Ant killed if the next hop is not found 
				    		event.setType(Event.Type.ANT_KILLED);
				    		response = event;
				    		break;
			    		} else {
			    			//Update Routing Table (in the original ACO Based algorithm -- parameter 1)
							if (!(source.equals(id)) && timeUpdateRT == 1){ //Only if it is not a source node
					    		/* Update the pheromone routing table */
								((ACOBasedRoutingTable)routingTable).update(ant);
							}
							//Add the wavelength mask to the spectrum usage in ant data 
							FlexiLink mask = flexiLinks.get(nextHop);
							//ant.addMask(mask);
							ant.addSpectrumUsage(mask);
							//Decrement the TTL
							ant.decrementTTL();			    			
							//Set the next hop in the packet
							ant.setNode(nextHop);
			    		}
			    	}	
				}else {
		    		event.setType(Event.Type.ANT_KILLED);
		    		response = event;
		    		break;					
				}
				//Increase the number of hops
				ant.increasePathLength();				
				//Return the response
		    	response = event;
	    		break;
	    	case RSVP_PATH:
    			//System.out.println("EVENTO:"+event.toString());
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
					fs = SpectrumAssignment.firstFit(labelSet, numberSlotsFS);		
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
				//There is no free frequency slot to allocate in the intermediate node
				} else if (labelSet.biggestContiguousFreeBandwidth() < req.getBandwidth()) {
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
					nextHop = ((ACOBasedRoutingTable)routingTable).nextHop(path,new ArrayList<String>());					
		    		if (nextHop == null || (msg.getHopLimit() == 0) || !(ACOBasedFON.hasConnectivity(id,nextHop))) { //dead-end 
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
		    			//Updates the mask
		    			if (labelSet == null) {
	    					labelSet = new LabelSet(this.flexiLinks.get(nextHop).getNumberSlots());
		    				path.setLabelSet(labelSet);
		    			}		    		
		    			path.updateMask(flexiLinks.get(nextHop).getMask());
		    			//Decrement the number of hops
		    			path.decrementHopLimit();
		    			path.addEffectiveHop();
		    	    }
				}
				//System.out.println("nextHop:"+nextHop);
				//Set the next hop in the packet
				path.setNode(nextHop);		
				//Add the delay
				if (flexiLinks.get(nextHop)==null) { //Do nothing if there is no route in the return path, since the PATH_ERR message could not return to the original source node
					event.setType(Event.Type.IGNORE);
					//System.out.println("path_tear: "+event.toString());
				} else { //There is a route to return
					//Set the new time of the event due to transmission time
					double delay = flexiLinks.get(nextHop).getLink().getDelay();
					event.setTimeStamp(event.getTimeStamp()+delay);
				}
				
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
			default:
				System.err.println("Unknown message: "+event.toString());
				break;
		}
	    //Return the response
		return response;
	}
	

}
