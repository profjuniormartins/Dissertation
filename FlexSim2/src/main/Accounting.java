/*
 * Created on 2013-03-06 by Pavani. 
 */
package main;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import event.Event;
import fon.FlexiLink;
import net.Node;

/**
 * Accounts the statistics and results in the simulation.
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 */
public abstract class Accounting {
	/** The XML configuration file. */
	protected transient Config config;
	/** The simulation parameters. */
	protected LinkedHashMap<String,ArrayList<String>> parameters;
	/** The values that can be selected to the output. */
	public enum Values {
		/** The total load of the network. */ LOAD,
		/** The variable value of the simulator. */ VARIABLE,
		/** The number of requests. */ REQUESTS,
		/** The number of blocked requests. */ BLOCKING,
		/** The bandwidth blocking request.*/ BBR,
		/** The setup time.*/ SETUP_TIME, 
		/** The number of successful connections */ SUCCESS,
		/** The number of failed connections */ FAILED, 
		/** The number of teardown messages. */ TEARDOWN,
		/** The number of finished connections of previously established message. */ FINISHED,
		/** Per-link utilization. */ UTILIZATION,
		/** The average control overhead in each network link. */ OVERHEAD,
		/** The average number of exchanged control messages in each network link. */ MSG_EXCHANGED,
		/** Restorability ratio. */ RESTORABILITY,
		/** The average number of hops per succesfull request. */ AVG_HOP,
		/** The average number of EFFECTIVE hops per succesfull request. */ AVG_EFF_HOP,	
		/** The restoration time after a failure.*/ RESTORATION_TIME	
	}
	
	/** The types of accounted events. */
	public enum Type {
		/** Success. */ SUCCESS,
		/** Failed. */ FAILED,
	}
    /** The selected values to be sent to the output. */
    protected ArrayList<Values> print;
    /** The file prefix to be used for the output. */
    protected String fileName;
	/** The table of all network nodes. */
	protected LinkedHashMap<String,Node> nodes;

	/**
	 * Creates a new Accounting object.
	 * @param aConfig The configuration from the XML file. 
	 */
	public Accounting(Config aConfig) {
		config = aConfig;
		parameters = config.getSimulationParameters();
         //Select the values that are present in the output
        ArrayList<String> selected = parameters.get("/Outputs/Print");
        print = new ArrayList<Values>();
        for (String value: selected) {
        	print.add(Values.valueOf(value));
        }
        //File prefix
        fileName = parameters.get("/Outputs/Output/@file").get(0);
        //Initialize the node array
        this.nodes = new LinkedHashMap<String,Node>();
	}
	
	/**
	 * Add a network node to the Accounting.
	 * @param id The identification of the node.
	 * @param node The node object.
	 */
	public void addNode(String id, Node node) {
		nodes.put(id, node);
	}
	
	/**
	 * Accounts the added event.
	 * @param type The type of the event
	 * @param event The added event.
	 */
	public abstract void addEvent(Type type, Event event);
	
	/** 
	 * Writes the desired simulation results to the output file.
	 */
	public abstract void write();

	/** 
	 * Resets all the statistics gathered by this object.
	 */
	public abstract void reset();

	/**
	 * Closes the output file. 
	 */
	public abstract void close();
	
	/**
	 * Periodically accounts the values
	 */
	public abstract void periodical(double time);
	
	/**
	 * Set the utilization using a channel other than the data plane.
	 */
	public abstract void setUtilization(LinkedHashMap<String, FlexiLink> links, double lastSimulationTime, double dataRate);

	
	/**
	 * Returns a String representation of this object. 
	 * @return A String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("File prefix: ");
		buf.append(fileName);
		buf.append(" - Printing: ");
		buf.append(print.toString());
		return buf.toString();
	}
	
	/**
	 * Create a new file for output, adding a suffix to the newly created file.
	 * @param suffix The String to be added at the end of the main prefix file name. 
	 * @return A FileWriter object, referring to the new file created.
	 */
	protected FileWriter createOutput(String suffix) {
        //Create a file to write the simulation output for other type of statistics.
        File file = new File(fileName+suffix);
        FileWriter newWriter = null;
        try {
        	//Create a new file 
        	file.createNewFile();
        	//Create a new 
            newWriter = new FileWriter(file);
        } catch(Exception e){e.printStackTrace();}
         return newWriter;
	}
	
	/**
	 * Closes the specified output file.
	 * @param fileWriter The descriptor of the file writer object.
	 */
	protected void closeOutput(FileWriter fileWriter) {
    	if (fileWriter != null) {
    		try{
    			fileWriter.flush();
    			fileWriter.close();
    		} catch(Exception e){e.printStackTrace();}                		
    	}
	}
	
	/**
	 * Write the string to the appropriate file writer.
	 * @param fileWriter The specified file writer.
	 * @param buffer The string to written. 
	 */
	protected void writeOutput(FileWriter fileWriter, String string) {
		try {
			//Write the results
			fileWriter.write(string);
			//Flush the file
			fileWriter.flush();
		} catch (Exception e) {e.printStackTrace();}		
	}

}
