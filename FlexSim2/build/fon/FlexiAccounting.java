/**
 * Created on 27/09/2016.
 */
package fon;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import event.Event;
import graph.Edge;
import graph.Graph;
import main.Accounting;
import main.Config;
import main.Simulator;
import net.Counter;
import net.Message;
import net.Network;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FlexiAccounting extends Accounting {
	/** Main file writer. */
	protected FileWriter mainWriter;
	/** Buffer for writing the main file. */
	protected StringBuilder mainBuffer;
	/** Flag for appending more things and avoiding writing to the main output file. */
	boolean mainAppend = false;	
	/** The physical topology. */
	protected Graph graph;
	/** The total number of requests. */
	protected long requests;
	/** Success connection setup counter. */
	protected long successConn;
	/** Failed connection setup counter. */
	protected long failedConn;
	/**	Success connections setup counter per type of class. */
	protected long[] successClass;
	/** Failed connections setup counter per type of class. */
	protected long[] failedClass;
	/**	Values of bandwidths per type of class. */
	protected ArrayList<Double> bandwidthClass;
	/** Teardown connection counter. */
	protected long teardownConn;
	/** Finished a previously established connection counter. */
	protected long finishedConn;
	/** Setup time of successful connections counter. */
	protected Counter setupTime;
	/** Number of restored connections after a failure. */
	protected long restoredSuccessful;
	/** Number of NOT restored connections after a failure. */
	protected long restoredFailed;
	/** Restoration time of successful connections after a failure counter. */
	protected Counter restorationTime;
    /** The maximum length of a successful routed message. */
	protected int maxLength;
	/** The array for storing the number of successful delivered messages. */
	protected long[] successful;
	/** */
	protected Counter avgHops;
	/** */
	protected Counter avgEffHops;
	/** Mapping between indexes and edges. */
	protected LinkedHashMap<String,Integer> edgeMap;
	/** Set of the edges of this network. */
	protected Edge[] edgeSet;
    /** The table that stores the number of bytes per second that passed by it. */
    protected double[] utilization;
    /** The number of messages exchanged  per second in the control channels. */
    protected double msgRate;
    /** The array for storing the number of dropped packets. */
	protected long[] failed;
	/** The file writer for utilization per-link statistics. */
    transient protected FileWriter writerUtilization =null;
    /** Alert Setup */
    protected double alertSetup;
    /** Restoration Setup */
    protected double alertRestoration;    
	
	/**
	 * Creates a new FlexiAccounting object. 
	 * @param aConfig The configuration from the XML file. 
	 */
	public FlexiAccounting(Config aConfig) {		
		super(aConfig);		
		//Create alert to the output in setup time
		String alert = parameters.get("/Accounting/Alert/@setup").get(0);
		if (alert != null) {
			alertSetup = Double.parseDouble(alert);
		}
		// Create alert to the output in restoration time
		alert = parameters.get("/Accounting/Alert/@restoration").get(0);
		if (alert != null) {
			alertRestoration = Double.parseDouble(alert);
		}
		//Create the bandwidths classes to calculate BBR
		if (print.contains(Values.BBR)) {
			double[] array = (Network.stringToDoubleArray(parameters.get("/RSA/Class/@bandwidth").get(0)));
			this.bandwidthClass = new ArrayList<Double>();		
			for(double d: array) {
				bandwidthClass.add(d);		
			}
		}
		//System.out.println(bandwidthClass);
		//Create main file and buffer
		mainWriter = createOutput("");
		mainBuffer = new StringBuilder();
		//Get the graph
		graph = config.getGraph();	
		//Get the edges of the graph
		edgeSet = graph.edges();
		edgeMap = new LinkedHashMap<String,Integer>();
		for(int i=0; i < edgeSet.length; i++) {
			edgeMap.put(edgeSet[i].toString(),i);
		}
		//Create the place for storing the statistics for the packets
		if (graph.size() > 1) { 
			successful = new long[graph.size()];
        	failed = new long[graph.size()];
            utilization = new double[edgeSet.length];
		} else {
			successful = new long[2];
        	failed = new long[2];
            utilization = new double[1];
		}
		//reset avg hops
		this.avgHops = new Counter();
		this.avgEffHops = new Counter();
		
        //Initialize the other counters
        this.initializeCounters();
        this.initializeWriters();

	}

	/* Add the event to the Accounting.
	 * @see main.Accounting#addEvent(event.Event)
	 */
	@Override
	public void addEvent(Type type, Event event) {
		switch (type) {
			case SUCCESS:
				addSuccessEvent(event);
				break;
			case FAILED:
				addFailedEvent(event);
				break;
		}
	}
	
	/**
	 * Accounts a success event.
	 * @param event The event to be accounted.
	 */
	protected void addSuccessEvent(Event event) {
		//Get the time stamp of the event
		double timeStamp = event.getTimeStamp();
		//Get the type of the event
		Event.Type type = event.getType();
		//for each type of event
		switch(type) {
			case CONNECTION_REQUEST:
				//Increment the request counter
				this.requests++;
				break;
			case CONNECTION_ESTABLISHED:
				//Increment the success counter
				this.successConn++;
				//Increment the BBR counters for each type of class
				//Get bandwidth of the request (from the event message connection) to use in BBR counters
				Connection conn = (Connection) (((RSVP)event.getContent()).getContent());
				double bandwidth = conn.getRequest().getBandwidth();
				//double bandwidth = ((Connection)((RSVP)event.getContent()).getContent()).getBandwidth();
				if (print.contains(Values.BBR)) {

					//Get the index to get the counter
					int index = bandwidthClass.indexOf(bandwidth);
					//System.out.println("index: "+index+" bandwidth: "+bandwidth);
					//Take the counter and increment it
					//System.out.println("get: "+ successClass[index]);
					//Add counter value back to array list
					successClass[index]++;
				}
				//Print counters
				//System.out.println("SUCCESS: bandwidth: "+ bandwidth + " counter: " + this.successClass[index]+ " Evento: "+ event.toString());		
				//If setup time is enable, add to the counter
				if (print.contains(Values.SETUP_TIME)) {
					//Get the initial time stamp
					double initialTimeStamp = event.getInitialTimeStamp();
					//Add the difference to the counter
					double t1 = (timeStamp - initialTimeStamp);
					if (t1 > alertSetup) {
						System.err.println("ALERT - setup time: "+t1+" - "+event.toString());
					}
					setupTime.increment(t1);
					
				}
				Message msg = (Message)event.getContent();
				//this.addSuccesful(msg);
				
				//Re-routing of failed requests
				if (msg.getType().equals(Message.Type.RSVP_RESV) && ((RSVP)msg).isReRouting()) {
					this.restoredSuccessful ++; //Increment the counter.
					//System.out.println("Add to restored Successfull: "+msg.getId());
					if (print.contains(Values.RESTORATION_TIME)) {
						//Get the initial time stamp						
						double initialTimeStamp = event.getInitialTimeStamp();
						//Add the difference to the counter
						double t2 = timeStamp - initialTimeStamp;
						if (t2 > alertRestoration) {
							System.err.println("ALERT - restoration time: "+t2+" - "+event.toString());
						}
						restorationTime.increment(t2);
					}
				} else { //Normal routed packets
					int pathLength = msg.getPathLength();
					//Get the average number of hops
					avgHops.increment(pathLength);
					if (pathLength > maxLength)
						maxLength = pathLength;
					successful[pathLength]=successful[pathLength] + 1;
					//Effective hop
					int effective = ((RSVP)msg).getEffectiveHops();
					//Get the average effective hops
					avgEffHops.increment(effective);
					//System.out.println("eff:"+effective);			
				}				

				break;
				
			case CONNECTION_TEARDOWN:
				//Increment the teardown counter
				this.teardownConn++;
				break;				
			case CONNECTION_FINISHED:
				//Increment the finished counter
				this.finishedConn++;
				break;
		}	
	}
		
	
	/**
	 * Accounts a failed event.
	 * @param event The event to be accounted.
	 */
	protected void addFailedEvent(Event event) {
		//Get the time stamp of the event
		double timeStamp = event.getTimeStamp();
		//Get the type of the event
		Event.Type type = event.getType();
		//for each type of event

		switch(type) {
			case CONNECTION_PROBLEM:
				//Increment the failed counter
				this.failedConn++;
				//Increment the BBR counters for each type of class
				//Get bandwidth of the request (from the event message) to use in BBR counters
				double bandwidth = ((Request)((Message)event.getContent()).getContent()).getBandwidth();
				if (print.contains(Values.BBR)) {
					//Get the index to get the counter
					int index = bandwidthClass.indexOf(bandwidth);				
					//Add counter value back to array list
					this.failedClass[index]++;
				}
				//Print counters
				//System.out.println("FAILED: bandwidth: "+ bandwidth + " counter: " + this.failedClass.get(index));
				break;
		}	
		Message msg = (Message)event.getContent();
		this.addFailed(msg);
	}
	
	/**
	 * Accounts the dropped message.
	 * @param message The message that was dropped.
	 */
	public void addFailed(Message msg) {
		//Failure of re-routing
		if (msg.getType().equals(Message.Type.RSVP_PATH_ERR) && ((RSVP)msg).isReRouting()) {
			this.restoredFailed ++; //Increment the counter.
			//System.out.println("id do evento restoredFailed: "+ msg.getId());
		}
	}
	

	/* 
	 * @see main.Accounting#write()
	 */
	@Override
	public void write() {
		//For all values of desired output do
		for (Values value: print) {
			switch(value) {
				case LOAD: /* Traffic load. */
					String load = parameters.get("/Generators/Traffic/@load").get(0);
					mainBuffer.append(load);
					//Add a separator between values
					mainBuffer.append("\t");
					break;
				case VARIABLE: /* Current variable value. */
					mainBuffer.append(Simulator.variableValue[0]);
					//Add a separator between values
					mainBuffer.append("\t");
					break;
				case REQUESTS:
					mainBuffer.append(requests);
					//Add a separator between values
					mainBuffer.append("\t");
					break;
				case SUCCESS:
					mainBuffer.append(successConn);
					//Add a separator between values
					mainBuffer.append("\t");
					break;			
				case TEARDOWN:
					mainBuffer.append(teardownConn);
					//Add a separator between values
					mainBuffer.append("\t");
					break;							
				case FINISHED:
					mainBuffer.append(finishedConn);
					//Add a separator between values
					mainBuffer.append("\t");
					break;			
				case FAILED:
					mainBuffer.append(failedConn);
					//Add a separator between values
					mainBuffer.append("\t");
					System.out.println("failedConn: "+failedConn);
					break;
				case BLOCKING: /* Blocking probability. */
					double blocking = (double)failedConn / (double)(failedConn+successConn);
					mainBuffer.append(blocking);
					//Add a separator between values
					mainBuffer.append("\t");
					break;
				case BBR: /* Bandwidth blocking request. */
					for (int i = 0; i < this.bandwidthClass.size(); i++) { //for each class do 
						double bbr = (double)failedClass[i] / (double)(failedClass[i]+successClass[i]);
						mainBuffer.append(bbr);
						//Add a separator between values
						mainBuffer.append("\t");
						//System.out.println("failedClass: "+failedClass[i]+" successClass: "+successClass[i]);
					}
					double num = 0.0, den = 0.0;
					for (int i = 0; i < this.bandwidthClass.size(); i++) { //weighted sum
						num = num + (double)failedClass[i] * bandwidthClass.get(i);
						den = den + (double)failedClass[i] * bandwidthClass.get(i) + (double)successClass[i] * bandwidthClass.get(i);
					}
					//System.out.println("weighted sum: "+ (num/den));
					mainBuffer.append(num/den);
					//Add a separator between values
					mainBuffer.append("\t");
					break;
				case SETUP_TIME: /* Setup time. */
					mainBuffer.append(this.setupTime.getAverage());
					//Add a separator between values
					mainBuffer.append("\t");					
					break;		
				case RESTORABILITY: /* Restorability ratio. */
					//Calculate the restorability ratio.
					double ratio = (double) restoredSuccessful / (double)(restoredSuccessful + restoredFailed);
					mainBuffer.append(ratio);
					//Add a separator between values
					mainBuffer.append("\t");
					System.out.println("restoredSuccessful: "+restoredSuccessful+ " - restoredFailed: "+ restoredFailed);					
					break;
				case RESTORATION_TIME: /* Restoration time. */
					mainBuffer.append(this.restorationTime.getAverage());
					//Add a separator between values
					mainBuffer.append("\t");					
					break;	
				case AVG_HOP:
					double avg_hop = avgHops.getAverage();
					System.out.println("media de saltos    " + avg_hop);
					mainBuffer.append(avg_hop);
					//Add a separator between values
					mainBuffer.append("\t");			
					break;
				case AVG_EFF_HOP:
					double avg_eff_hop = avgEffHops.getAverage();
					System.out.println("media de saltos efetivos    " + avg_eff_hop);
					mainBuffer.append(avg_eff_hop);
					//Add a separator between values
					mainBuffer.append("\t");			
					break;
				case OVERHEAD: 
					double sumUtilization  = 0.0;
					for (double i : utilization)
					    sumUtilization += i;
					double overhead = sumUtilization / edgeSet.length; //per control channel
					System.out.println("overhead    " + overhead);
					mainBuffer.append(overhead);
					//Add a separator between values
					mainBuffer.append("\t");			
					break;
				case MSG_EXCHANGED:
					mainBuffer.append(this.msgRate / edgeSet.length);  //per control channel
					//Add a separator between values
					mainBuffer.append("\t");			
					break;
				case UTILIZATION: /* For per-link utilization. */
					StringBuilder bufferUtilization = new StringBuilder();
					for (Edge edge: edgeSet) {
						int index = edgeMap.get(edge.toString());
						double util = utilization[index];
						bufferUtilization.append(util);
						bufferUtilization.append("\t");
					}
					bufferUtilization.append("\n");
					this.writeOutput(writerUtilization,bufferUtilization.toString());
					break;	
			}
		}
		//Write to the main output file if the append flag is false.
		if (!mainAppend) {
			mainBuffer.append("\n");
			//Write it to the file.
			this.writeOutput(mainWriter,mainBuffer.toString());
			//Reset the buffer.
			mainBuffer.delete(0,mainBuffer.length());
		}		
	}
	
	/**
	 * Initialize the counter statistics and create a new file with appropriate
	 * heading, if necessary.
	 */
	protected void initializeCounters() {
        //For all values of desired output do
		for (Values value: print) {
			switch(value) {				
				case UTILIZATION: /* For per-link utilization. */
					//Initialize utilization per-link statistics
					Arrays.fill(utilization,0.0);
					break;
			}
		}
		//Initialize the counter of restored connections
		this.restoredFailed = 0L;
		this.restoredSuccessful = 0L;
		//For the blocked due to the lack of resources
		//Setup time
		this.setupTime = new Counter();
		//Restoration time
		this.restorationTime = new Counter();
		//Initialize the classes counters for BBR
		if (print.contains(Values.BBR)) {
			this.successClass = new long[bandwidthClass.size()];
			this.failedClass = new long[bandwidthClass.size()];	
		}
		//For msg counting
		this.msgRate = 0.0;
	}
	
	/**
	 * Initialize the counter statistics and create a new file with appropriate
	 * heading, if necessary.
	 */
	public void initializeWriters() {
        //For all values of desired output do
		for (Values value: print) {
			//Buffer for writing the heading line
			StringBuilder buffer = new StringBuilder();
			switch(value) {				
				case UTILIZATION: /* For per-link utilization. */
					for(Object edge: edgeSet) {
						buffer.append(edge.toString());
						buffer.append("\t");						
					}
					buffer.append("\n");
					//Create appropriate output file
					writerUtilization = this.createOutput("_utilization.txt");
					//Write the heading line to the file
					this.writeOutput(writerUtilization,buffer.toString());
					break;	
			}
		}
	}
	
	/**
	 * Set the utilization using a channel other than the data plane.
	 * @param linkSet The set of links in the network.
	 * @param totalTime The total simulation time.
	 * @param dataRate The data rate (in bps) of the control channel.
	 */
	public void setUtilization(LinkedHashMap<String,FlexiLink> linkSet, double totalTime, double dataRate) {
		long msgCounter = 0; //count the total number of messages carried by the link
		for (Edge edge: edgeSet) {
			FlexiLink flexiLink = linkSet.get(edge.toString());
			//System.out.println("Link: "+edge.toString());
			long bytes = flexiLink.getLink().getByteCounter(); //get the total number of bytes carried by the link
			//System.out.println("Bytes: "+bytes);
			double result = ((double) bytes) / ((dataRate / 8.0) * totalTime); //now get the rate
			int index = edgeMap.get(edge.toString());
			utilization[index]=result;		
			//Now count the msg exchanged
			msgCounter = msgCounter + flexiLink.getLink().getMessageCounter(); //get the total number of messages carried by the link
		}
		//now divide the total number of msg to get the rate
		this.msgRate = (double)msgCounter / totalTime;
	}


	/* 
	 * Reset the values of the metrics.
	 * @see main.Accounting#reset()
	 */
	@Override
	public void reset() {
		this.requests = 0L;
		this.successConn = 0L;
		this.failedConn = 0L;
		this.teardownConn = 0L;
		this.finishedConn = 0L;
		this.setupTime = new Counter();
		this.restorationTime = new Counter();
		//Reset the utilization array
		Arrays.fill(this.utilization,0.0);
		this.msgRate = 0.0;
		//Reset the counter of number of restored or not failed connections
		this.restoredFailed = 0L;
		this.restoredSuccessful = 0L;
		//reset avg hops
		this.avgHops = new Counter();
		this.avgEffHops = new Counter();
		//The BBR counters
		if (print.contains(Values.BBR)) {
			Arrays.fill(this.successClass,0);
			Arrays.fill(this.failedClass,0);
		}
	}

	/* Close output files.
	 * (non-Javadoc)
	 * @see main.Accounting#close()
	 */
	@Override
	public void close() {
		closeOutput(mainWriter);
	}

	@Override
	public void periodical(double time) {
		System.out.print(".");
		
	}

}
