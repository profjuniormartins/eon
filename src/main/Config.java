/*
 * Config.java
 *
 * Created on May 8, 2003.
 * Modified on March 23, 2004 - Added support for dynamic traffic
 * Modified on February 23, 2005 - Modified for ACO algorithm
 * Modified on September 16, 2005 - Modified for OPS simulator.
 */

package main;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import graph.*;
import java.util.logging.*;

/**
 * This class reads the configuration data in XML for the problem of Routing
 * and Wavelength Assignment (RWA) with Ant Colony Optimization (ACO). 
 *
 * @author Gustavo Sousa Pavani
 * @version 1.2
 */
public class Config extends DefaultHandler implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
    /** Buffer for SAX. */
    protected StringBuilder buffer = null;
    /** The graph representing the network. */
    protected Graph graph;
    /** The links of the network. */
    protected LinkedHashMap<String,Link> links;
    /** The position of the inline EDFA's. */
    Hashtable<String,Vector<String>> inline;
    /** Flag to indicate the building of the simulation parameters section. */
    protected boolean flag_simulation = false;
    /** Path in relation to the simulation section. */
    protected StringBuilder path;
    /** The hashtable for storing the configuration of the dynamic simulation. */
    protected Hashtable<String,Vector<String>> simulation;
    /** The logging generator. */
    private static Logger logger = Logger.getLogger(Config.class.getName());
    
    /** Creates new Config.
     * @param fn The name of the XML file containing the configuration for the Simulator.
     */
    public Config(String fn) throws SAXException, IOException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        
        this.graph = new Graph();
        logger.info("Reading configuration file: "+fn);
        this.links = new LinkedHashMap<String,Link>();
        this.simulation = new Hashtable<String,Vector<String>>();
        this.inline = new Hashtable<String,Vector<String>>();
        this.path = new StringBuilder();
        this.flag_simulation = false;
        parser.parse(fn, this);
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
            String wString = atts.getValue("lambda");
            if (wString==null){
                logger.severe("Attribute 'lambda' missing");
                throw new SAXException("Attribute 'lambda' missing");
            }
            String rateString = atts.getValue("dataRate");
            if (rateString==null){
                logger.severe("Attribute 'dataRate' missing");
                throw new SAXException("Attribute 'dataRate' missing");
            }
            String lengthString = atts.getValue("length");
            logger.config("from= "+fromString+" to= "+toString+" value= "+costString+" lambda="+wString+" dataRate="+rateString);
            try {
                Edge edge = graph.addEdge(fromString,toString,new Double(costString));
                Link link = null;
                if (lengthString != null)
                	link = new Link(edge,Integer.parseInt(wString),Double.parseDouble(rateString),Double.parseDouble(lengthString));
                else
                	link = new Link(edge,Integer.parseInt(wString),Double.parseDouble(rateString));
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
				addValue(simulation,path.toString()+"/@"+atts.getQName(i),atts.getValue(i));
				logger.config(path.toString()+"/@"+atts.getQName(i)+" = "+atts.getValue(i));
			}        	
            this.buffer = new StringBuilder();
        } 
        //For inline EDFA placement
	    else if (qname.equals("Inline")) {
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
            String atString = atts.getValue("at");
            if (atString==null){
                logger.severe("Attribute 'at' missing");
                throw new SAXException("Attribute 'at' missing");
            }
            this.addValue(inline,(new Edge(fromString,toString,null)).toString(),atString);
            logger.fine("from= "+fromString+" to= "+toString+" at= "+atString);
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
                buffer = null;
            } catch(Exception e) {logger.severe(e.toString());};
        }
        /* Turns off the simulation flag, i.e, no more elements in configuration hashtable. */
        else if (qname.equals("Simulation")){
        	flag_simulation = false;
        }
        else if (flag_simulation) {
        	if (this.buffer != null && this.buffer.length()>0) {
            	addValue(simulation,path.toString(), this.buffer.toString());
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
    
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (this.buffer != null) {
            this.buffer.append(ch, start, length);
        }
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
     * Get the hashtable containing the placement of inline EDFAs
     * @return The hashtable containing the placement of inline EDFAs
     */
    public Hashtable<String,Vector<String>> getInline() {
        return this.inline;
    }

               
	/**
	 * Returns the hashtable containing the simulation parameters. 
	 * <p>The keys (String) are in the following form:
	 * <p>/qname1/qname2/@attribute or
	 * <p>/qname1/qname2
	 * <p>And the values inside a Vector of String.
	 * @return The hashtable containing the simulation parameters.
	 */
	public Hashtable<String,Vector<String>> getSimulationParameters() {
		return this.simulation;
	}
    
    /**
     * Adds a value of to the Vector with same key.
     * @param hashtable The hashtable containing the simulation parameters/values.
     * @param key The key indicating the configuration parameter.
     * @param value The value to be added to the Vector.
     */
    protected void addValue(Hashtable<String,Vector<String>> hashtable, String key, String value) {
        Vector<String> vec = hashtable.get(key);
        if (vec == null) {
            vec = new Vector<String>();
            hashtable.put(key,vec);
        }
        vec.add(value);
    }

}
