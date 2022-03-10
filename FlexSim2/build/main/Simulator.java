/*
 * Created on 2013-03-06 by Pavani. 
 */
package main;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import distribution.QueueDistribution;
import net.Network;
import event.Event;
import event.EventGenerator;
import event.EventSubscriber;
import event.Scheduler;

/**
 * This class is the main entry for the event-driven simulator.
 * 
 * @author Pavani
 * @version 1.1
 */
public class Simulator {
    /** The logging generator. */
    private static Logger logger = Logger.getLogger(Simulator.class.getName());	
	/** The XML configuration file for this simulation. */
    protected Config config;
    /** The simulation parameters. */
    protected LinkedHashMap<String,ArrayList<String>> simulation;
    /** The loader of the objects related to the simulation. */
    protected transient Loader loader;
    /** The scheduler of the simulation. */
    protected Scheduler scheduler;
    /** The variable of this simulation. */
    protected String variable;
    /** The variable values of this simulation. */
    public static double[] variableValue;
    /** The initial values of the variable. */
    protected double[] initialValue;
    /** The step value of this simulation. */
    protected double stepValue = 0.0;
    /** The multiply value of this simulation for the initial value. */
    protected double multValue = 0.0;
    /** The multiply value of this simulation for the actual value. */
    protected double expValue = 0.0;
    /** The stop value of this simulation. */
    protected double stopValue;
    /** The grace period of this simulation, i.e, the time the simulation continues after reaching the maximum number of requests. */
    protected double gracePeriod;
    /** The time of the last request, i.e., the time the last request-related event was generated. */
    protected double timeLastRequest;
    /** The time the simulation was ended. */
    protected static double lastSimulationTime;
    /** The network of this simulation. */
    protected Network network;
    /** The accounting class for this simulation. */
    protected Accounting accounting;
    /** Number of requests. */
    protected long numberOfRequests;
	/** The amount of time for the time slice for transient accounting. */
	protected double timeSlice;
	/** Counter of time slices. */
	protected double actualTimeSlice;
   /** Requests to be counted. */
    protected ArrayList<String> related;    
    /** The run counter. */
    protected static int runCounter=0;

    /**
     * Creates a new Simulator object.
     * @param fileConfig The name of the configuration file.
     */
    public Simulator(String fileConfig) {
    	//Initialize the configuration of the simulation
		try {
			this.config = new Config(fileConfig);
		} catch (Exception e) {e.printStackTrace();}
		//Gets the parameters
		this.simulation = config.getSimulationParameters();
		//System.out.println(config.toString());
		//Initialize the variables that defines the scope of the simulation
		this.init();
		//Create the loader and load the appropriate classes via Reflection API
		this.loader = new Loader(config);
    }
    
	/**
	 * Creates a new simulation.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		//Verify if the arguments are correct. Otherwise, print usage information.
		if (args.length != 1) {
			System.err.println("Usage: java main.Simulator config_file.xml");
			return;
		}
		Simulator simulator = new Simulator(args[0]);
		logger.info("Starting simulation at: "+(new Date()).toString());
		simulator.run();
		logger.info("Simulation finished at:"+(new Date()).toString());		
	}
	
	/**
	 * Initialize the variables that define the scope of the simulation.
	 */
	protected void init() {
		//Gets the variable of the simulation
		this.variable = simulation.get("/Main/Variable/@name").get(0);
		int sizeVar = simulation.get(variable).size();
		variableValue = new double[sizeVar];
		initialValue = new double[sizeVar];
		//Necessary when the same variable has to be updated in many generators, which is defined by sizeVar.
		for (int i=0; i < sizeVar; i++) {
			variableValue[i] = Double.parseDouble(simulation.get(variable).get(i));
			initialValue[i] = variableValue[i];
		}
		//Allows 3 kinds of sweeping. See the end of method run to understand the difference between each way.
		ArrayList<String> step = simulation.get("/Main/Variable/@step");
		ArrayList<String> mult = simulation.get("/Main/Variable/@mult");
		ArrayList<String> exp = simulation.get("/Main/Variable/@exp");
		if (step != null) {
			this.stepValue = Double.parseDouble(step.get(0));
		} 
		if (mult != null) {
			this.multValue = Double.parseDouble(mult.get(0));
		}
		if (exp != null) {
			this.expValue = Double.parseDouble(exp.get(0));
		}
		this.stopValue = Double.parseDouble(simulation.get("/Main/Variable/@stop").get(0));    	
		//Parses the total number of requests.  
		String requests = simulation.get("/Main/Requests/@value").get(0);		
		this.numberOfRequests = Long.parseLong(requests);
		//Parses the subscribers name that will be counted for the number of requests.
		this.related = simulation.get("/Accounting/RequestRelated/@class");
		//Gets the grace period
		this.gracePeriod = Double.parseDouble(simulation.get("/Main/Requests/@gracePeriod").get(0));
		//Gets the time slice
		this.timeSlice = Double.parseDouble(simulation.get("/Outputs/Transient/@timeSlice").get(0));
		this.actualTimeSlice = timeSlice;
	}
	
    /**
     * Executes the simulation.
     */
	public void run() {
		//Get the accounting related class
		accounting = loader.getAccounting();
		/** The simulation main loop. */
		while (variableValue[0] <= stopValue) {
			logger.info(variable+": "+variableValue[0]);
			/* Start the network part. */
			network = loader.getNetwork(config,accounting);
			//Reset the counter of requests.
			long requestCounter = 0;
			/* Start the event-driven part. */
			//Initialize the scheduler
			scheduler = new Scheduler();
			//Add the traffic generators to the scheduler
			loader.addGenerators(scheduler,network);
			//Add failure events, if any
			loader.addFailures(scheduler);		
			//Add other events to the scheduler, if any
			loader.addOtherEvents(scheduler,network);
			//Flag for stopping the simulation after the grace period
			boolean stopSim = false;
			//Time of the very last request event. Initialized with a very big value
			double bigValue = Double.MAX_VALUE - 10.0*gracePeriod;
			timeLastRequest = bigValue;
			/* Ready to run! */
			//Run the simulation and print statistics information
			while ((requestCounter < numberOfRequests) || (!stopSim)) {
				Event event = null, response = null;
				double timeStamp=0.0; //time stamp of the event
				try {
					//Gets the next event on the queue
					event = scheduler.step();
					//Get its time stamp
					timeStamp = event.getTimeStamp();
					//Do transient accounting, if applicable
					if (timeStamp > actualTimeSlice) {
						//System.out.print(".");
						//Updates the periodical accounting, if applicable
						try {
							Method periodical = accounting.getClass().getMethod("periodical",new Class[] { Double.class } );
							periodical.invoke(accounting,actualTimeSlice);
						}catch (Exception e) {} //do nothing - method not implemented					
						//Update the actual time slice
						actualTimeSlice = actualTimeSlice + timeSlice;
					}
					//Verify if the grace period is ended after the maximum number of request is reached.
					if ((timeLastRequest+gracePeriod)<timeStamp) {
						//stop the simulation of this run
						stopSim = true;
						//Update the time of the last simulation. Not necessarily the real time of the last event in the simulation.
						lastSimulationTime = timeLastRequest+gracePeriod;
					} else if (event.getType().equals(Event.Type.TERMINATE)) {
						//System.out.println("terminate");						
						//Update the time of the last simulation
						lastSimulationTime = timeStamp;
						//stop the simulation of this run
						break;
					} else if (event.getType().equals(Event.Type.MULTIPLE)) { //Multiple events - break into single ones
						@SuppressWarnings("unchecked")
						ArrayList<Event> multiple = (ArrayList<Event>) event.getContent();
						//for each event do
						for (Event single:multiple) {
							if (single != null)
								//Insert each event separately
								scheduler.insertEvent(single);						
						}
					} else { //Process the event at network level
						response = network.process(event);
					}
				} catch (Exception e) {e.printStackTrace();}
				if (response != null) { //response from the network
					if (response.getType().equals(Event.Type.MULTIPLE)) { //Multiple events generated
						@SuppressWarnings("unchecked")
						ArrayList<Event> multiple = (ArrayList<Event>) response.getContent();
						//for each event do
						for (Event single:multiple) {
							if (single != null)
								//Insert each event separately
								scheduler.insertEvent(single);
						}
					} else { //Single response
						scheduler.insertEvent(response);
					}
				}
				//Count the actual number of request till now.
				long allCounters = 0;
				for (String subscriber: related) {
					allCounters = allCounters + scheduler.getCounter(subscriber); 
				}
				requestCounter = allCounters;
				//Verify if the maximum number of requests is reached.
				if ((requestCounter == numberOfRequests) && (timeLastRequest == bigValue)) { 
					//Remove the generators for the request related class
					for (String subscriber: related) {
						scheduler.removeGenerator(subscriber);
					}
					//Set the time of the last request event ever
					timeLastRequest = timeStamp;
				}
				//System.out.println("Req #: "+requestCounter);
			}
			//Update the specified values at the network, after the run is finished, if implemented
			try {
				Method updateValues = network.getClass().getMethod("updateValues",(Class[])null);
				updateValues.invoke(network,(Object[])null);
			}catch (Exception e) {} //do nothing - method not implemented
			//Write and reset the values gathered by the accounting
			accounting.write();
			accounting.reset();
			//Set the new value for the simulation
			int sizeVar = simulation.get(variable).size();
			for (int i=0; i < sizeVar; i++) {
				if (stepValue != 0.0) {
					variableValue[i] = variableValue[i] + stepValue;
				} else if (multValue != 0.0) {
					variableValue[i] = variableValue[i] + (multValue * initialValue[i]);
				} else if (expValue != 0.0) {
					variableValue[i] = variableValue[i] * expValue;	
				}
				String newValue = Double.toString(variableValue[i]);
				simulation.get(variable).set(i,newValue);
			}
			//Increment the counter of runs
			runCounter ++;
		} 
		//Closes the accounting part
		accounting.close();   	
	}
	
	/**
	 * Returns the last simulation time of the run.
	 * @return The last simulation time of the run.
	 */
	public static double getLastSimulationTime() {
		return lastSimulationTime;
	}
	
	/**
	 * Returns the run index of this simulation, starting with zero.
	 * @return The run index of this simulation.
	 */
	public static int getRunCounter() {
		return runCounter;
	}

}

/**
 * Helper class for loading the needed classes at run-time.
 * 
 * @author Gustavo S. Pavani
 * @version 1.0
 * 
 */
class Loader {
	/** The XML configuration file. */
	Config config;
	/** The simulation parameters. */
	LinkedHashMap<String, ArrayList<String>> parameters;
	/** The logging generator. */
	private static Logger logger = Logger.getLogger(Loader.class.getName());

	Loader(Config aConfig) {
		this.config = aConfig;
		this.parameters = config.getSimulationParameters();
	}
	
	/**
	 * Get the a specific version of the control plane, which is responsible for 
	 * the network simulation. 
	 * @return The specific version of the control plane.
	 */
	public Network getNetwork(Config config, Accounting accounting) {
		Network net = null;
		String networkClass = parameters.get("/Main/Network/@class").get(0);
		try {
			Class<?>[] argsClass = new Class[] {Config.class, Accounting.class};
			Object[] aArgs = new Object[] {config,accounting};
			Class<?> sNetwork = Class.forName(networkClass);		
			Constructor<?> argsConstructor = sNetwork.getConstructor(argsClass);
			net = (Network) argsConstructor.newInstance(aArgs);
		} catch (Exception e) {e.printStackTrace(); }		
		return net;
	}
	
	/**
	 * Returns the appropriate distributions for the traffic generators.
	 * @return The appropriate distributions for the traffic generators.
	 */
	protected ArrayList<QueueDistribution> getTrafficDistribution() {
		ArrayList<QueueDistribution> distrib = new ArrayList<QueueDistribution>();
		//Get the traffic (Java) classes
		ArrayList<String> classes = parameters.get("/Generators/Traffic/@class");
		//Counters for each type of traffic
		int counter1 = 0; //Poisson
		int counter2 = 0; //Constant
		//For each traffic generator
		for (@SuppressWarnings("unused") String nClass : classes) {
			String trafficType = parameters.get("/Generators/Traffic/@type").get(counter1+counter2);
			QueueDistribution traffic = null;
			try {
				Class<?>[] argsClass = null;
				Object[] aArgs = null;
				Class<?> sTraffic = Class.forName("distribution.".concat(trafficType));
				/* Poissonian traffic. */
				if (trafficType.equals("Poissonian")) {
					//Get the mandatory seed.
					String seed = parameters.get("/Generators/Traffic/@seed").get(counter1);
					//Specify duration in time.
					if (parameters.get("/Generators/Traffic/@duration") != null) { 						
						double load = Double.parseDouble(parameters.get("/Generators/Traffic/@load").get(counter1));
						double averageDuration = Double.parseDouble(parameters.get("/Generators/Traffic/@duration").get(counter1));
						if ((seed == null) || (seed.equals(""))) {
							argsClass = new Class[] { double.class, double.class};
							aArgs = new Object[] { new Double(1.0 / averageDuration), new Double(load / averageDuration)};
						} else {
							argsClass = new Class[] { double.class, double.class, long.class };
							aArgs = new Object[] { new Double(1.0 / averageDuration), new Double(load / averageDuration), Long.parseLong(seed) };
						}										
					//Specify average size
					} else if (parameters.get("/Generators/Traffic/@averageSize") != null) {						
						double length = Double.parseDouble(parameters.get("/Generators/Traffic/@averageLength").get(counter1));
						double load = Double.parseDouble(parameters.get("/Generators/Traffic/@load").get(counter1));
						double rate = Double.parseDouble(parameters.get("/Generators/Traffic/@dataRate").get(counter1));
						double mu = 1.0 / (length / (rate / 8.0));
						double lambda = mu * load;						
						if ((seed == null) || (seed.equals(""))) {
							argsClass = new Class[] {double.class,double.class};
							aArgs = new Object[] {new Double(mu),new Double(lambda)};
						} else {
							argsClass = new Class[] {double.class,double.class,long.class};
							aArgs = new Object[] {new Double(mu),new Double(lambda), new Long(seed)};
						}				
					} else {
						logger.severe("Unknown configuration of poisson traffic: "+trafficType);
					}
					//Increment the counter
					counter1 ++;
				/* Constant traffic. */
				} else if (trafficType.equals("Constant")) {
					argsClass = new Class[] {double.class,double.class};
					//Specify arrival and service rate
					if (parameters.get("/Generators/Traffic/@serviceRate") != null) {
						double serviceRate = Double.parseDouble(parameters.get("/Generators/Traffic/@serviceRate").get(counter2));
						double arrivalRate = Double.parseDouble(parameters.get("/Generators/Traffic/@arrivalRate").get(counter2));
						aArgs = new Object[] {new Double(serviceRate),new Double(arrivalRate)};
					} else if (parameters.get("/Generators/Traffic/@length") != null) {
						double length = Double.parseDouble(parameters.get("/Generators/Traffic/@length").get(counter2));
						double dataRate = Double.parseDouble(parameters.get("/Generators/Traffic/@dataRate").get(counter2));	
						double arrivalRate = Double.parseDouble(parameters.get("/Generators/Traffic/@arrivalRate").get(counter2));
						double serviceRate = 1.0 / (length / (dataRate / 8.0));
						aArgs = new Object[] {new Double(serviceRate),new Double(arrivalRate)};
					} else {
						logger.severe("Unknown configuration of constant traffic: "+trafficType);						
					}
					//Increment the counter
					counter2 ++;					
				}
				//Then, instantiate the traffic object
				Constructor<?> argsConstructor = sTraffic.getConstructor(argsClass);
				traffic = (QueueDistribution) argsConstructor.newInstance(aArgs);
			} catch (Exception e) {e.printStackTrace(); }
			//Add the distribution to the vector
			distrib.add(traffic);
		}
		return distrib;
	}
	
	
	
	/**
	 * Adds all the traffic and mobility generators to the scheduler.
	 * @param scheduler The event-driven scheduler. 
	 * @param net The network that will manage the simulation.
	 */
	public void addGenerators(Scheduler scheduler, Network net) {
		/* TRAFFIC. */
		//Get the traffic distributions 
		ArrayList<QueueDistribution> trafficDistrib = getTrafficDistribution();
		//Get the traffic Java classes
		ArrayList<String> traffics = parameters.get("/Generators/Traffic/@class");
		ArrayList<String> traffic_starts= parameters.get("/Generators/Traffic/@start");
		//Counter for occurrences of the same traffic class
		LinkedHashMap<String,Integer> counterTraffic = new LinkedHashMap<String,Integer>(); 
		int counter1 = 0; //counter for traffic class
		/* For each traffic class. */
		for (String nClass : traffics) {
			//Get start time
			double startTime = Double.parseDouble(traffic_starts.get(counter1));
			QueueDistribution distribution = trafficDistrib.get(counter1);
			//Create an event generator
			EventGenerator generator = new EventGenerator(distribution,startTime);
			//Get the occurrence of the traffic class
			Integer index = counterTraffic.get(nClass);
			if (index == null) { //first occurrence
				index = new Integer(0);
				counterTraffic.put(nClass, index);
			}
			//Create the event subscriber;
			EventSubscriber subscriber = net.createTrafficSubscriber(nClass, index);
			//Subscribe the traffic part
			generator.subscribe(subscriber);
			scheduler.addGenerator(generator);
			//Increment the counter for traffic classes
			counter1 ++;
			//Increment the counter for number of occurrences of the same class;
			index ++;
		}
	}
	
	/** Adds failures that are configured for this simulation to the scheduler.
	 * @param scheduler The event-driven scheduler. 
	 */
	public void addFailures(Scheduler scheduler) {
		ArrayList<String> nodeFailures = parameters.get("/Failure/NodeFailure/@node");
		ArrayList<String> linkFailures = parameters.get("/Failure/LinkFailure/@link");
		int nCounter=0, lCounter = 0;
		if (nodeFailures != null) {
			//for each node failure do
			for (String node: nodeFailures) {
				double time = Double.parseDouble(parameters.get("/Failure/NodeFailure/@time").get(nCounter));
				Event failure = new Event(time,Event.Type.FAILURE_NODE,node);
				scheduler.insertEvent(failure);
				//Increment counter
				nCounter++;
			} 
		}
		//for each link failure do
		if (linkFailures != null) {
			for (String link: linkFailures) {
				double time = Double.parseDouble(parameters.get("/Failure/LinkFailure/@time").get(lCounter));
				Event failure = new Event(time,Event.Type.FAILURE_LINK,link);
				scheduler.insertEvent(failure);
				//Increment counter
				lCounter++;			
			}	
		}
	}
	
	/**
	 * Adds special events, such as Failures, Terminate, etc, to the scheduler.
	 * @param scheduler The event-driven scheduler. 
	 * @param net The network that will manage the simulation.
	 */
	public void addOtherEvents(Scheduler scheduler, Network net) {
		//Terminate events.
		ArrayList<String> terminate = parameters.get("/Terminate/@time");
		if (terminate != null) {
			double time = Double.parseDouble(terminate.get(0));
			ArrayList<String> offset = parameters.get("/Terminate/@offset");
			if (offset != null)
				time = time + Double.parseDouble(offset.get(0));
			Event event = new Event(time,Event.Type.TERMINATE,null);
			scheduler.insertEvent(event);
		}
		//Add other events related to the network simulation
		ArrayList<Event> others = net.getOtherEvents();
		for (Event other: others) {
			if (other != null) {
				scheduler.insertEvent(other);
			}
		}

	}

	/**
	 * Gets the class for accounting the simulation results.
	 */
	public Accounting getAccounting() {
		String accountingClass = parameters.get("/Accounting/Accounting/@class").get(0);
		Accounting accounting = null;
		try {
			Class<?> sAccounting = Class.forName(accountingClass);		
			Constructor<?> argsConstructor = sAccounting.getConstructor(Config.class);
			accounting = (Accounting) argsConstructor.newInstance(config);
		} catch (Exception e) {e.printStackTrace(); }		
		return accounting;
	}

}