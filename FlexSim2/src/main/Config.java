/*
 * Created on 2013-03-06 by Pavani. 
 */
package main;

import graph.Edge;
import graph.Graph;

import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.Link;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  This class reads the configuration data in XML in order to provide the parameters for simulation.
 *  Uses SAX for parsing the XML file.
 * 
 * @author Gustavo S. Pavani
 * @version 1.2
 */
public class Config extends DefaultHandler {
    /** The logging generator. */
    private static Logger logger = Logger.getLogger(Config.class.getName());
    /** Buffer for SAX. */
    protected StringBuilder buffer = null;
    /** Path in relation to the root of the XML file. */
    protected StringBuilder path;
    /** The hashmap for storing the configuration of the dynamic simulation. */
    protected LinkedHashMap<String,ArrayList<String>> config;
    /** The graph representing the network. */
    protected Graph graph;
    /** The links of the network. */
    protected LinkedHashMap<String,Link> links;
    /** Flag to indicate the building of the simulation parameters section. */
    protected boolean flag_simulation = false;

	/**
	 * Creates a new Config object.
	 * 
	 * @param fileConfig The name of the XML file containing the configuration for the Simulator.
	 */
	public Config(String fileConfig) throws SAXException, IOException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
		logger.info("Reading XML configuration file: "+fileConfig);
		this.config = new LinkedHashMap<String,ArrayList<String>>();
		this.path = new StringBuilder();
		this.graph = new Graph();
        this.links = new LinkedHashMap<String,Link>();
        this.flag_simulation = false;
        //Parse the XML file
		parser.parse(fileConfig, this);
	}
	
    public void startElement(String uri, String local, String qname, Attributes atts) throws SAXException {
        /* The graph representing the network - nodes. */
         if (qname.equals("Node")) {
             this.buffer = new StringBuilder();
         } 
         /* The graph representing the network - arcs. */
         else if (qname.equals("Path")) {
             String fromString = atts.getValue("from");
             if (fromString==null) {
                 logger.severe("Attribute 'from' missing");
                 throw new SAXException("Attribute 'from' missing");
             }
             String toString = atts.getValue("to");
             if (toString==null){
                 logger.severe("Attribute 'to' missing");
                 throw new SAXException("Attribute 'to' missing");
             }
             String costString = atts.getValue("value");
             if (costString==null){
                 logger.severe("Attribute 'value' missing");
                 throw new SAXException("Attribute 'value' missing");
             }
             String lengthString = atts.getValue("length");
             logger.config("from= "+fromString+" to= "+toString+" value= "+costString);
             try {
                 Edge edge = graph.addEdge(fromString,toString,new Double(costString));
                 //System.out.println(graph);
                 Link link = null;
                 if (lengthString != null)
                 	link = new Link(edge,Double.parseDouble(lengthString));
                 else
                 	link = new Link(edge);
                 links.put(fromString+"-"+toString,link);
             } catch(Exception e) {logger.severe(e.toString());};
         }
        /* Simulation Parameters */
         else if (flag_simulation){
         	//Starts with the path
         	path.append('/');
             path.append(qname);
 			//Adds the parameters of the traffic to the hashtable simulation
 			int nattrs = atts.getLength();
 			for(int i=0; i<nattrs; i++) {
 				addValue(config,path.toString()+"/@"+atts.getQName(i),atts.getValue(i));
 				logger.config(path.toString()+"/@"+atts.getQName(i)+" = "+atts.getValue(i));
 			}        	
             this.buffer = new StringBuilder();
         } else if (qname.equals("Simulation")) {
         	flag_simulation = true;
         }
        else {
             logger.fine("Ignoring Start: "+qname);
         }
     }
     
     public void endElement(String uri, String local, String qname) throws SAXException {
         logger.fine("End: "+qname);
        /* The graph representing the network - nodes. */
         if (qname.equals("Node")) {
             try{
                 logger.fine("Node="+this.buffer.toString());
                 graph.addNode(new String(this.buffer));
                 //System.out.println(graph);
                 buffer = null;
             } catch(Exception e) {logger.severe(e.toString());};
         }
         /* Turns off the simulation flag, i.e, no more elements in configuration hashtable. */
         else if (qname.equals("Simulation")){
         	flag_simulation = false;
         }
         else if (flag_simulation) {
         	if (this.buffer != null && this.buffer.length()>0) {
             	addValue(config,path.toString(), this.buffer.toString());
             	logger.config(path.toString()+" = "+this.buffer.toString());
                 this.buffer = null;         
             }
         	logger.fine("path: "+path.toString());
         	int pathlen = path.length();
         	path.delete(pathlen-qname.length()-1,pathlen);        	
         }
         else {
             logger.fine("Ignoring End: "+qname);
         }
     }

	
	  /**
     * Adds a value of to the ArrayList with same key.
     * @param hashmap The hashmap containing the simulation parameters/values.
     * @param key The key indicating the configuration parameter.
     * @param value The value to be added to the ArrayList.
     */
    protected void addValue(HashMap<String,ArrayList<String>> hashmap, String key, String value) {
        ArrayList<String> vec = hashmap.get(key);
        if (vec == null) {
            vec = new ArrayList<String>();
            hashmap.put(key,vec);
        }
        vec.add(value);
    }
    
    /**
     * Returns the graph of the network.
     * @return The graph of the network.
     */
    public Graph getGraph() {
        return (Graph) this.graph.clone();
    }
    
    /**
     * Returns the set of links of this network with appropriate attributes. 
     */
    public LinkedHashMap<String,Link> getLinks() {
    	return this.links;
    }
	
	/**
	 * Returns the hashmap containing the simulation parameters. 
	 * <p>The keys (String) are in the following form:
	 * <p>/qname1/qname2/@attribute or
	 * <p>/qname1/qname2
	 * <p>And the values inside a ArrayList of Strings.
	 * @return The hashmap containing the simulation parameters.
	 */
	public LinkedHashMap<String,ArrayList<String>> getSimulationParameters() {
		return this.config;
	}
	
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (this.buffer != null) {
			this.buffer.append(ch, start, length);
		}
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (String key: config.keySet()) {
			ArrayList<String> al = config.get(key);
			builder.append(key);
			builder.append(" - ");
			builder.append(al.toString());
			builder.append("\n");
		}
		return builder.toString();
	}
}
