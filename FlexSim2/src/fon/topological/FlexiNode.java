/**
 * Created on 27/09/2016.
 */
package fon.topological;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;

import event.Event;
import fon.Connection;
import fon.LabelSet;
import fon.FlexiLink;
import fon.FrequencySlot;
import fon.RSVP;
import fon.Request;
import fon.SpectrumAssignment;
import fon.SpectrumAssignment.Strategy;
import graph.Edge;
import graph.Graph;
import net.RoutingTable;
import net.Failure;
import net.Message;
import net.Node;
import net.Failure.Location;
import net.Error;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FlexiNode extends Node {
	/** The set of adjacent flexi links of this node. */
	protected LinkedHashMap<String,FlexiLink> flexiLinks;
	/** The unique IDs of the failures already processed. */
	protected ArrayList<Integer> failureID;
	/** The routing table of this node. */
	protected RoutingTable routingTable;
	/** The physical topology of the network. */
	protected Graph graph;
	/** Delta introduced to avoid race conditions between
	 *  flooding and failure notification. */
	public static final double DELTA_TIME = 1E-7; 
	/** The table of active connections in that node. */
	protected Hashtable<String,Connection> activeConnections;
	/** Sets the desire of re-routing in case of LSP establishment failure. */
	public enum ReRouting {
		/** No re-route retry after LSP establishment failure. */ NONE,
		/** Only the ingress node re-routes after LSP establishment failure. */ END_TO_END,
		/** Area border router re-routes after LSP establishment failure. */ BOUNDARY,
		/** Any intermediate node re-routes after LSP establishment failure. */ SEGMENT
	}	
	/** Indicates the re-routing behavior. */
	protected ReRouting rerouting;
	/** Local number of re-routing attempts. */
	protected int reroutingAttempts;
	/** Maximum number of re-routing attempts. */
	protected int maxReroutingAttempts;	
	/** The spectrum assignment strategy. */
	protected Strategy sa;	
	/** The table for temporarily storing Label Set objects. */
	protected Hashtable<String,LabelSet> labelSetTable;
	/** The history table serving as tabu in segment re-routing. */
	protected Hashtable<String,ArrayList<String>> historyTable;


	/**
	 * Creates a new FlexiNode object with Rerouting parameters.
	 * @param aId The id of the node.
	 * @param aType
	 * @param aTable
	 * @param aLinks
	 * @param aGraph
	 * @param aBehavior
	 * @param aMaxAttempts
	 * @param aAttempts
	 * @param sa
	 */
	
	public FlexiNode(String aId, Type aType, RoutingTable aTable, LinkedHashMap<String,FlexiLink> aLinks, Graph aGraph, ReRouting aBehavior, int aMaxAttempts, int aAttempts, Strategy sa) {
		super(aId, aType);
		this.routingTable = aTable;
		this.flexiLinks = aLinks;
		this.activeConnections = new Hashtable<String,Connection>();
		this.failureID = new ArrayList<Integer>();
		this.graph = aGraph;
		this.rerouting = aBehavior;
		this.maxReroutingAttempts = aMaxAttempts;
		this.reroutingAttempts = aAttempts;
		this.sa = sa;
		if (this.rerouting.equals(ReRouting.SEGMENT)) {
			labelSetTable = new Hashtable<String,LabelSet>();
			historyTable = new Hashtable<String,ArrayList<String>>();
		}	
	}

	
	@Override
	public Event process(Event event) {
		//Get the message
		Message msg = (Message) event.getContent();
		//Get the type 
		Message.Type type = msg.getType();
		//Request / connection
		Request req = null;
		Connection conn = null;
		//Response
		Event response = null;
		//The associated error specification
		Error error; 
		//Destination node
		String destination;
		//The next hop in the path
	    String nextHop; 
	    //RSVP message
		RSVP rsvp; 
		//Frequency Slot 
		FrequencySlot fs = null;
		//Debug
		//if (((RSVP) msg).getId().equals("36921") || ((RSVP) msg).getId().equals("36921r")) {
			//System.out.println("EVENTO:"+event.toString());
		//}		
		switch(type) {
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
				//} else if (labelSet.freeBandwidth() < req.getBandwidth() ) {//There is no free frequency slot to allocate 
				} else if (labelSet.biggestContiguousFreeBandwidth() < req.getBandwidth() ) {//There is no free frequency slot to allocate in the intermediate node
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
						nextHop = ((ExplicitRoutingTable)routingTable).nextHop(path, req.getCurrentTry());
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
				rsvp = (RSVP) msg;
				//System.out.println(rsvp.toString());
				//Remove this connection from the list of active connections
    			activeConnections.remove(rsvp.getId());
	    		//Gets the connection
	    		Connection teared = (Connection)rsvp.getContent(); 		
				//Get the destination node
				destination = rsvp.getDestination();		
		    	//Verify if the RSVP message has reached the destination node. 
		    	if (destination.equals(id)) { //RSVP reached the destination node
		    		event.setType(Event.Type.CONNECTION_FINISHED);
		    		//* Remove the last reverse (bidirectional) segment
		    		String backNode = rsvp.getBackwardNode();
		    		if (backNode != null) { //Test in case of failure, where the path tear is at the first node, i.e., destination==source.
		    			FlexiLink reLinkMask = flexiLinks.get(backNode);
		    			if (reLinkMask == null){ //Do nothing because the link was removed by the failure.
		    				//System.out.println("flexiLinks: "+flexiLinks);
		    				//System.out.println("backNode: "+backNode);
		    				//System.out.println("evento: "+event.toString());
		    				///System.out.println("reLinkMask: "+reLinkMask);
		    			} else {
		    				reLinkMask.removeFrequencySlot(teared.getId());		
		    			}
		    			//System.err.println(reLinkMask.toString());
		    		} else {
		    			//System.out.println("Node: "+ this.id + "\n" + this.debugFlexiLinks());	
		    			//System.out.println("Active connections:  \n"+this.debugActiveConnections());
		    			//System.out.println("History table: \n"+this.debugHistoryTable());
		    		}
		    	} else {
		    		//Clean the temporary label set and the history table, if applicable
		    		if (this.rerouting.equals(ReRouting.SEGMENT)) {
		    			labelSetTable.remove(rsvp.getId());
		    			historyTable.remove(rsvp.getId());
		    		}
		    		nextHop = teared.getPath().getNextNode(id);
	    			FlexiLink link = flexiLinks.get(nextHop);
	    			if (link != null) { //Maybe the state was removed due to failure
	    				//Get the associated mask
	    				FlexiLink linkMaskTear = link;
	    				//Clear the frequency slot	    				
	    				linkMaskTear.removeFrequencySlot(teared.getId());
	    			} else {
	    				//System.out.print(event.toString());
	    			}
					//* Clean the reverse (bidirectional) segment, if not source
					if (!id.equals(rsvp.getSource())) {
						FlexiLink reLink = flexiLinks.get(rsvp.getBackwardNode());
						if (reLink != null) {
							FlexiLink reLinkMask = reLink;
							reLinkMask.removeFrequencySlot(teared.getId());	
							//System.err.println(reLinkMask);
						}
					}		
					//Set the next hop in the packet
					rsvp.setNode(nextHop);
					if (flexiLinks.get(nextHop)==null) { //Do nothing if there is no route in the return path, since the PATH_TEAR message could not return to the original source node
						event.setType(Event.Type.IGNORE);
						//System.out.println("path_tear: "+event.toString());
					} else { //There is a route to return
						//Set the new time of the event due to transmission time
						event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
					}
		    	}
				//Return the response
		    	response = event;		    	
	    		break;	
			case RSVP_PATH_ERR:
				rsvp = (RSVP) msg;
	    		//System.out.println(rsvp.toString());
	    		//Get the error 
	    		error = rsvp.getError();
				//Remove this connection from the list of active connections
	    		//if the remove flag is enabled
	    		if (error.getRemoveFlag()) {
	    			activeConnections.remove(rsvp.getId());	    			
	    			Connection removed_perr = (Connection)rsvp.getContent();
	    			String forwardHop = removed_perr.getPath().getNextNode(id);
	    			FlexiLink link = flexiLinks.get(forwardHop);
	    			if (link != null) { //Maybe the state was removed due to failure
			    		//Get the associated mask
	    				FlexiLink linkMaskRem = link;
	    				//Clear the frequency slot
	    				linkMaskRem.removeFrequencySlot(removed_perr.getId());
	    			}
	    			//* Remove the reverse (bidirectional) segment, if not source of the connection
					if (!id.equals(removed_perr.getSource())) {
						FlexiLink reLink = flexiLinks.get(rsvp.getBackwardNode());
						if (reLink != null) {
							FlexiLink reLinkMaskRem = reLink;
							reLinkMaskRem.removeFrequencySlot(removed_perr.getId());
							//System.err.println(reLinkMaskRem);
						}
					}
	    		}
				//Gets the destination node
				destination = rsvp.getDestination();
				Request request=null;
				//Process the message
				Error.Code code = error.getErrorCode(); 
				switch (code) {
					case LSP_FAILURE:
						if (destination.equals(id)) { //RSVP reached the destination node
							event.setType(Event.Type.CONNECTION_PROBLEM);
						} else { //intermediate nodes
							nextHop = ((Connection)rsvp.getContent()).getPath().getPreviousNode(id);
							//Set the next hop in the packet
							rsvp.setNode(nextHop);
							//Set the new time of the event due to transmission time
							event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
						}
						break;
					case ADMISSION_CONTROL_FAILURE:
						if (this.rerouting.equals(ReRouting.SEGMENT)) {
							//Remove the connection from the history table
							this.historyTable.remove(rsvp.getId());	
						}
						if (destination.equals(id)) { //RSVP reached the destination node
							event.setType(Event.Type.CONNECTION_PROBLEM);
						} else { //intermediate nodes
							nextHop = rsvp.getBackwardNode();
							//Set the next hop in the packet
							rsvp.setNode(nextHop);
							if (flexiLinks.get(nextHop) == null) { //Do nothing if there is no route in the return path
								//System.err.println("null rp admission control failure: "+event.toString());
								event.setType(Event.Type.IGNORE);
							} else {
								//Set the new time of the event due to transmission time
								event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
							}
						}
						break;
					case RP_LABEL_SET: 
						if (this.rerouting.equals(ReRouting.SEGMENT)) { //Intermediate node re-routing
							//Remove the last visited node from the record route
							String last = rsvp.removeLastVisited();
							//Put it in the history table
							String label = rsvp.getId();
							this.putHistoryTable(label, last);
						    //System.out.println("ProcLA: "+id+" last: "+last);
							//Gets the request and update the try counter
							request = (Request) rsvp.getContent();
							request.addTry();
							//Verify if we can make another re-routing attempt
							int currentAttempt = this.sizeHistoryTable(label);
							//Gets the number of available neighbors
							int neighbors;
							if (id.equals(request.getSource())) 
								neighbors = this.flexiLinks.size();
							else
								neighbors = this.flexiLinks.size() - 1;
							//Allow a re-routing if there is allowed attempts and sufficient neighbors
							if ((currentAttempt <= this.reroutingAttempts) && (currentAttempt < neighbors) && (request.getTry() <= this.maxReroutingAttempts)&& (msg.getHopLimit() > 0)) {
								//Set as path message
								rsvp.setType(Message.Type.RSVP_PATH);
								//Sets the previous label set
								rsvp.setLabelSet(labelSetTable.get(rsvp.getId()));
								//Set the record route in the RSVP
								rsvp.setRecordRoute(true);
								//Reset the error
								rsvp.setError(null);
								//Reset the source-destination pair
								rsvp.setSDPair(request.getSource(),request.getDestination());
							} else { //Limit exceeded!
				    			//Set the error
				    			error = new Error(Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
				    			rsvp.setError(error);
				    			if (id.equals(request.getSource())) {
				    				event.setType(Event.Type.CONNECTION_PROBLEM);
				    			} else {
				    				//Set the backward node
				    				nextHop = rsvp.getBackwardNode();
				    				rsvp.addEffectiveHop();
				    				//Set the next hop in the packet
				    				rsvp.setNode(nextHop);
									//Set the new time of the event due to transmission time
									if (flexiLinks.get(nextHop) == null) { //Do nothing if there is no route in the return path
										//System.err.println("null rp label set: "+event.toString());
										event.setType(Event.Type.IGNORE);
									} else {
										event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
									}
				    				//Remove the entry in the history table
				    				this.historyTable.remove(rsvp.getId());
				    			}
							}
						} else { //None or end-to-end routing
							if (destination.equals(id)) { //RSVP reached the destination node
								event.setType(Event.Type.CONNECTION_PROBLEM);
							} else { //intermediate nodes
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								//Set the next hop in the packet
								rsvp.setNode(nextHop);
								//Set the new time of the event due to transmission time
								if (flexiLinks.get(nextHop) == null) { //Do nothing if there is no route in the return path
									//System.err.println("null rp label set: "+event.toString());
									event.setType(Event.Type.IGNORE);
								} else {
									event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
								}
							}
						}
						break;
					case  RP_NO_ROUTE_AVAILABLE:
						if (this.rerouting.equals(ReRouting.SEGMENT)) { //Intermediate node re-routing
							//Remove the last visited node from the record route, if is not the first
							if (rsvp.getPathLength() != 0) {
								String last = rsvp.removeLastVisited();
								//Put it in the history table
								this.putHistoryTable(rsvp.getId(), last);
								//System.out.println("ProcRT: "+id+" last: "+last);
							}
    						//Gets the request and update the try counter
							request = (Request) rsvp.getContent();
							request.addTry();
							//Verify if we can make another re-routing attempt
							int currentAttempt = this.sizeHistoryTable(rsvp.getId());
							//System.out.println("Cur: "+currentAttempt+" max: "+this.reroutingAttempts);
							//Gets the number of available neighbors
							int neighbors;
							if (id.equals(request.getSource())) 
								neighbors = this.flexiLinks.size();
							else
								neighbors = this.flexiLinks.size() - 1;
							//Allow a re-routing if there is allowed attempts and sufficient neighbors
							if ((currentAttempt <= this.reroutingAttempts) && (currentAttempt < neighbors) && (request.getTry() <= this.maxReroutingAttempts)&& (msg.getHopLimit() > 0)) {
								//Set as path message
								rsvp.setType(Message.Type.RSVP_PATH);
								//Sets the previous label set, if applicable
								LabelSet previous = labelSetTable.get(rsvp.getId());
								if (previous != null)
									rsvp.setLabelSet(previous);
								//Set the record route in the RSVP
								rsvp.setRecordRoute(true);
								//Reset the error
								rsvp.setError(null);
								//Reset the source-destination pair
								rsvp.setSDPair(request.getSource(),request.getDestination());
							} else { //Limit exceeded!
				    			//Set the error
				    			error = new Error(Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
				    			rsvp.setError(error);
				    			if (id.equals(request.getSource())) {
				    				event.setType(Event.Type.CONNECTION_PROBLEM);
				    			} else {
				    				//Set the backward node
				    				nextHop = rsvp.getBackwardNode();
				    				rsvp.addEffectiveHop();
				    				//Set the next hop in the packet
				    				rsvp.setNode(nextHop);
									//Set the new time of the event due to transmission time
									if (flexiLinks.get(nextHop) == null) { //Do nothing if there is no route in the return path
										//System.err.println("null rp no route available: "+event.toString());
										event.setType(Event.Type.IGNORE);
									} else {
										event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
									}
				    				//Remove the entry in the history table
				    				this.historyTable.remove(rsvp.getId());
				    			}
							}
						} else { //None or end-to-end routing
							if (destination.equals(id)) { //RSVP reached the destination node
								event.setType(Event.Type.CONNECTION_PROBLEM);
							} else { //intermediate nodes
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								//Set the next hop in the message
								rsvp.setNode(nextHop);
								//Set the new time of the event due to transmission time
								if (flexiLinks.get(nextHop) == null) { //Do nothing if there is no route in the return path
									//System.err.println("null rp no route: "+event.toString());
									event.setType(Event.Type.IGNORE);
								} else {
									event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
								}
							}
						}
						break;
					case RP_REROUTING_LIMIT_EXCEEDED: //Only for segment re-routing
						//Remove the last visited node from the record route
						String last = rsvp.removeLastVisited();
						//Put it in the history table
						this.putHistoryTable(rsvp.getId(),last);
					    //System.out.println("ProcRE: "+id+" last: "+last);
						//Gets the request and update the try counter
						request = (Request) rsvp.getContent();
						request.addTry(); //Observation: May exceed the maxReRoutingAttempts when the maximum is reached. However, it does not influence the result as the else clause will be selected in any limit 
						//Verify if we can make another re-routing attempt
						int currentAttempt = this.sizeHistoryTable(rsvp.getId());
						//System.out.println("Cur: "+currentAttempt);
						//Gets the number of available neighbors
						int neighbors;
						if (id.equals(request.getSource())) 
							neighbors = this.flexiLinks.size();
						else
							neighbors = this.flexiLinks.size() - 1;
						//Allow a re-routing if there is allowed attempts and sufficient neighbors
						if ((currentAttempt <= this.reroutingAttempts) && (currentAttempt < neighbors) && (request.getTry() <= this.maxReroutingAttempts) && (msg.getHopLimit() > 0)) {
							//Set as path message
							rsvp.setType(Message.Type.RSVP_PATH);
							//Sets the previous label set
							rsvp.setLabelSet(labelSetTable.get(rsvp.getId()));
							//Set the record route in the RSVP
							rsvp.setRecordRoute(true);
							//Reset the error
							rsvp.setError(null);
							//Reset the source-destination pair
							rsvp.setSDPair(request.getSource(),request.getDestination());
						} else { //Limit exceeded! - Give up connection setup and signal connection problem at the source node
							if(id.equals(request.getSource())) {
								event.setType(Event.Type.CONNECTION_PROBLEM);
//								if (rsvp.getPath().size() == 0)
//									rsvp.setNode(request.getSource());
							} else { //Not in the source node
								//Set the backward node
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								//Set the next hop in the packet
								rsvp.setNode(nextHop);
								//Set the new time of the event due to transmission time
								if (flexiLinks.get(nextHop) == null) {  //Do nothing if there is no route in the return path
									//System.err.println("null rp rerouting exceeded: "+event.toString());
									event.setType(Event.Type.IGNORE);
								} else {
									event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
								}
			    				//Remove the entry in the history table
			    				this.historyTable.remove(rsvp.getId());
							}
						}
						break;
				default:
					break;
				}
		    	response = event;
	    		break;		
			case RSVP_RESV:								
				//Get the PATH message
				rsvp = (RSVP) msg;
	    		/* Update the the frequency slot mask of this node. */
	    		Connection connection = (Connection) rsvp.getContent();
	    		//Get the forward node
	    		String fwdId = rsvp.getForwardNode();
	    		//Get the associated mask
	    		FlexiLink linkMask = flexiLinks.get(fwdId);	    		
	    		if (linkMask == null) {  //Do nothing if there is no route in the return path
	    			System.err.println("no routing in return path (rsvp_path): "+event.toString());
	    			event.setType(Event.Type.IGNORE);
	    		} else {
	    			//See the status of the frequency slot
	    			if (linkMask.isAvailable(connection.getFS())) {
	    				//System.out.println("Adding conn: "+rsvp.getId()+" with: "+connection.toString()+" to intermediate node: "+id);
	    				activeConnections.put(rsvp.getId(),connection);
	    				//Set the frequency slot
	    				linkMask.addFrequencySlot(connection.getId(), connection.getFS());
	    				if (this.rerouting.equals(ReRouting.SEGMENT)) {
	    					//Remove the connection from the history table
	    					this.historyTable.remove(rsvp.getId());
	    				}
	    				//Verify if the resv message reached the destination node.
	    				destination = rsvp.getDestination();
	    				if (destination.equals(id)) {
	    					event.setType(Event.Type.CONNECTION_ESTABLISHED);
	    				} else { //Intermediate node
	    					//See the next hop
	    					nextHop = rsvp.getBackwardNode();
	    					//In case of failure
	    					if (!(FON.hasConnectivity(id,nextHop))) {
	    						System.err.println("connectivity:"+event.toString());
	    						//Send a resvErr msg to the sender
	    						rsvp.setType(Message.Type.RSVP_RESV_ERR);
	    						rsvp.setSDPair(id,connection.getDestination());
	    						//Add the error
	    						error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
	    						rsvp.setError(error);
	    						//Change the next hop
	    						nextHop = rsvp.getForwardNode();
	    					}else {
	    						//* Set the reverse (bidirectional) connection
	    						FlexiLink reLinkMask = flexiLinks.get(nextHop);
	    						reLinkMask.addFrequencySlot(connection.getId(), connection.getFS());
	    						//System.err.println(reLinkMask);
	    						//System.out.println("LinkMask backward id: "+id+" node: "+nextHop);
	    					}
	    					//Set the next hop in the packet
	    					rsvp.setNode(nextHop);
	    					//Set the new time of the event due to transmission time
	    					event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
	    				}	    			    			
	    			} else { //Contention problem!
	    				//Send a resvErr msg to the sender
	    				rsvp.setType(Message.Type.RSVP_RESV_ERR);
	    				rsvp.setSDPair(id,connection.getDestination());
	    				//Set the error
	    				error = new Error(Error.Code.ADMISSION_CONTROL_FAILURE);
	    				rsvp.setError(error);
	    				//See the next hop
	    				nextHop = rsvp.getForwardNode();
	    				//Set the next hop in the packet
	    				rsvp.setNode(nextHop);
	    				//Set the new time of the event due to transmission time
	    				event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
	    			}
	    		}
	    		response = event;
	    		break;									    		
			case RSVP_RESV_ERR:				
				rsvp = (RSVP) msg;
	    		//Remove the connection from the table of connections
	    		activeConnections.remove(rsvp.getId());
	    		Connection removed_rerr = (Connection) rsvp.getContent();
	    		destination = rsvp.getDestination(); 
	    		if (destination.equals(id)) { //Now, send a PathErr to the ingress node
	    			//* Clear the last reverse (bidirectional) segment
					FlexiLink reLinkMaskErr = flexiLinks.get(rsvp.getBackwardNode());
					reLinkMaskErr.removeFrequencySlot(removed_rerr.getId());	
					//System.err.println(reLinkMaskErr);
	    			//Convert to pathErr msg, if not RP_NOT_ROUTE_AVAILABLE
					if (!rsvp.getError().getErrorCode().equals(Error.Code.RP_NO_ROUTE_AVAILABLE)) {
						rsvp.setType(Message.Type.RSVP_PATH_ERR);
						//System.out.println("Creating: "+rsvp.toString());
						//Reset the SD pair to the new values
						rsvp.setSDPair(id,removed_rerr.getSource());
					} else { //Do nothing if there is no route in the return path, since the PATH_ERR message could not return to the original source node
			    		event.setType(Event.Type.IGNORE);
					}
	    		} else {
		    		nextHop = rsvp.getForwardNode();
		    		//Get the associated mask
		    		FlexiLink linkMaskErr = flexiLinks.get(nextHop);
		    		//Clear the frequency slot
		    		linkMaskErr.removeFrequencySlot(removed_rerr.getId());
		    		//* Clear the reverse (bidirectional) segment, if not source of the connection
					if (!id.equals(removed_rerr.getSource())) {
						FlexiLink reLinkMaskErr = flexiLinks.get(rsvp.getBackwardNode());
						reLinkMaskErr.removeFrequencySlot(removed_rerr.getId());
						//System.err.println(reLinkMaskErr);
					}
			    	//Set the next hop in the packet
			    	rsvp.setNode(nextHop);
		    		//Set the new time of the event due to transmission time
		    		event.setTimeStamp(event.getTimeStamp() + flexiLinks.get(nextHop).getDelay());
	    		}
	    		response = event;
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
		    		((ExplicitRoutingTable)routingTable).updateFromTopology(graph,FON.getPaths());
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
	 * Calculates the number of slots required according to the requested bandwidth
	 * @param bandwidth The requested bandwidth
	 * @param slotGranularity The slot width granularity, in GHz
	 */
	protected int calculateNumberSlotsFS(double bandwidth, double slotGranularity) {		
		//Calculates the number of slots required (request bandwidth divided by slot granularity).
		int numberSlots = (int)(bandwidth/slotGranularity);	
		//Returns the result, increasing one slot if the result is not integer.
		return (bandwidth%slotGranularity == 0 ? numberSlots : numberSlots + 1);
	}

	/**
	 * Associates the neighbor id of a failed routed node to a flow label.
	 * @param label The label of the RSVP message.
	 * @param neigh The id of the visited neighbor.
	 */
	protected void putHistoryTable(String label, String neigh) {
		ArrayList<String> history = historyTable.get(label);
		if (history == null)  //no neighbor id in the list
			history = new ArrayList<String>();
		//Add the neighbor id to the list
		if (!history.contains(neigh)) //no repeated elements
			history.add(neigh);
		this.historyTable.put(label,history);
	}
	
	/**
	 * Gets the number of already visited neighbor nodes by the RSVP Path message.
	 * @param label The label of the RSVP message.
	 * @return The number of already visited neighbor nodes by the RSVP Path message.
	 */
	protected int sizeHistoryTable(String label) {
		ArrayList<String> history = historyTable.get(label);		
		if (history == null)  //no neighbor id in the list
			return 0;
		else //return the number of labels in the history table
			return history.size();		
	}
	
	/**
	 * Gets the 
	 * @param label
	 * @return
	 */
	protected ArrayList<String> getHistoryTable(String label) {
		ArrayList<String> history = historyTable.get(label);
		if (history == null)  //no neighbor id in the list
			return new ArrayList<String>();
		else
			return historyTable.get(label);
	}
	
	public String debugFlexiLinks() {
		StringBuilder builder = new StringBuilder();
		for(FlexiLink f: flexiLinks.values()) {
			builder.append(f.toString());
		}
		return builder.toString();
	}
	
	public String debugActiveConnections() {
		StringBuilder builder = new StringBuilder();
		for(Connection conn: this.activeConnections.values()) {
			builder.append(conn.toString());
			builder.append("\n");
		}
		return builder.toString();
	}
	
	public String debugHistoryTable() {
		StringBuilder builder = new StringBuilder();
		for(String conn:this.historyTable.keySet()) {
			builder.append(conn);
			builder.append(" - ");
			builder.append(this.historyTable.get(conn).toString());
		}
		return builder.toString();
	}

}
