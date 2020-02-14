/*
 * Created on Sep 29, 2005.
 */
package ops;

import main.*;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Vector;
import java.util.Arrays;

import rwa.RSVP;
import rwa.Error;

import graph.Dijkstra;
import graph.Edge;
import graph.Graph;
import graph.Path;
/**
 * This class is responsible for accounting the data of the simulation.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Accounting extends SimulationAccounting {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
   /** The array for storing the number of successful delivered packets. */
	protected long[] successful;
	/** The file writer for per-distance statistics for successful routed packets. */
	transient protected FileWriter writerHop=null;    	
    /** The array for storing the number of dropped packets. */
	protected long[] failed;
    /** The array that stores the processing node of the dropped packets. */
	protected long[] nodes;
	/** The file writer for per-node statistics. */
	transient protected FileWriter writerNodes=null;    
    /** The array that stores the link of the dropped packets. */
    protected long[] links;
	/** The file writer for loss per-link statistics. */
    transient protected FileWriter writerLinks=null;
    /** The hashtable that stores the link and the number of bytes that passed by it. */
    protected double[] utilization;
	/** The file writer for utilization per-link statistics. */
    transient protected FileWriter writerUtilization =null;
    /** The array that stores the traffic class of the dropped packets. */
    protected long[] classService;
	/** The file writer for per-class of service statistics. */
    transient protected FileWriter writerClass=null;
    /** The array that stores the pair source-destination of dropped packets. */
    protected long[] routes;
    /** The file writer for loss per pair source-destination statistics. */
    transient protected FileWriter writerRoute=null;
	/** Set of the edges of this network. */
	protected Edge[] edgeSet;
	/** Mapping between indexes and edges. */
	protected LinkedHashMap<String,Integer> edgeMap;
	/** Set of nodes of this network. */
	protected Vector<String> nodeSet;
	/** Mapping between indexes and edges. */
	protected LinkedHashMap<String,Integer> nodeMap;
	/** Set of class of service of this  network. */
    protected Packet.Priority[] classSet;
    /** Set of shortest paths of this network. */
    protected Vector<Path> routeSet;
    /** Mapping between pairs source-destination and shortest paths. */
    protected LinkedHashMap<String,Integer> routeMap;
    /** The maximum length of a successful routed packet. */
	protected int maxLength;
	/** Number of restored connections after a failure. */
	protected long restoredSuccessful;
	/** Number of NOT restored connections after a failure. */
	protected long restoredFailed;
	/** Number of connections failed due to unavailability of resources. */
	protected long blockedResource;
	/** The physical topology. */
	protected Graph graph;
	/** Buffer for writing the main file. */
	protected StringBuilder bufferMain;
	/** Flag for appending more things and avoiding writing to the main output file. */
	boolean append = false;	
		
	/** 
	 * Creates a new Accounting object.
	 * @param aConfig The configuration from the XML file. 
	 */
	public Accounting(Config aConfig) {
		super(aConfig);
		//Get the graph
		graph = config.getGraph();		
		//Get the edges of the graph
		edgeSet = graph.edges();
		edgeMap = new LinkedHashMap<String,Integer>();
		for(int i=0; i < edgeSet.length; i++) {
			edgeMap.put(edgeSet[i].toString(),i);
		}
		//Get the nodes of the graph
		nodeSet = graph.nodes(); 
		nodeMap = new LinkedHashMap<String,Integer>();
		for(int i=0; i < nodeSet.size(); i++) {
			nodeMap.put(nodeSet.get(i),i);
		}		
		//Get the routes of the graph.
		routeMap = new LinkedHashMap<String,Integer>();
		routeSet = new Vector<Path>();
		int indexRoute = 0;
		for(int i=0; i < nodeSet.size(); i++) {
			for(int j=0; j < nodeSet.size(); j++) {
				if (i!=j) { //avoids same source-destination pairs
					routeMap.put(nodeSet.get(i)+"-"+nodeSet.get(j),indexRoute);
					routeSet.add(new Dijkstra().getShortestPath(nodeSet.get(i),nodeSet.get(j),graph));
					indexRoute ++; //increment the counter
				}
			}
		}
		//Get the classes of service 
		classSet = Packet.Priority.values();
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
        nodes = new long[nodeSet.size()]; 
        links = new long[edgeSet.length];
        classService = new long[classSet.length];
        routes = new long[nodeSet.size()*(nodeSet.size() - 1)];
        maxLength = 0;
        //Create space for the buffer of the main file
        bufferMain = new StringBuilder();  
        //Get the variable parameter name
        
        //Initialize the other counters
        this.initializeCounters();
        this.initializeWriters();
    }
	
	/**
	 * Creates a new Accouting object.
	 * @param aConfig The XML configuration file.
	 * @param endOfLine Flag to indicate if it must not write 
	 * to the main output file, if false. Write 
	 */
	public Accounting(Config aConfig, boolean aAppend) {
		this(aConfig);
		this.append = aAppend;
	}
	
	/** 
	 * Writes the desired simulation results to the output file.
	 */
	public void write() {
		//See the global values
		long dropped = this.getFailed();
		long routed = this.getSuccessful();
		double total = (double)(dropped+routed);
		//System.out.println("Routed: "+routed+", dropped: "+dropped+" , total: "+total);
		//For each desired value
		for (Values value: print) {
			switch(value) {
				case VARIABLE: //The variable of the simulation
					//String param = config.getSimulationParameters().get("/Main/Variable/@name").firstElement();
					//bufferMain.append(config.getSimulationParameters().get(param).firstElement());
					bufferMain.append(Simulator.variableValue[0]);
					//Add a separator between values
					bufferMain.append("\t");
					break;
				case LOSS: //Packet loss probability
					double loss = ((double)dropped) / total; 
					bufferMain.append(loss);
					//Add a separator between values
					bufferMain.append("\t");
					break;
				case CLASS: /* For loss probability per-class of service statistics */
					StringBuilder bufferClass = new StringBuilder();
					for(Packet.Priority cos: classSet) {
						int ordinal = cos.ordinal();
						long droppedClass = classService[ordinal];
						bufferClass.append((double)droppedClass/total);
						bufferClass.append("\t");
					}
					bufferClass.append("\n");
					this.writeOutput(writerClass,bufferClass.toString());
					break;
				case HOP: /* The percentage of SUCESSFULL routed packets in ascending number of hops. */
					StringBuilder bufferHop = new StringBuilder();
					for(int i=1; i<=maxLength;i++) {
						long success = successful[i];
						double resultHop = (double)success / total;
						bufferHop.append(resultHop);
						bufferHop.append("\t");
					}
					bufferHop.append("\n");
					this.writeOutput(writerHop,bufferHop.toString());
					break;
				case NODE: /* For loss probability per-node statistics */
					StringBuilder bufferNodes = new StringBuilder();
					for(Object id: nodeSet) {
						int index = nodeMap.get(id.toString());
						long droppedId = nodes[index];
						bufferNodes.append((double)droppedId/total);
						bufferNodes.append("\t");
					}
					bufferNodes.append("\n");
					this.writeOutput(writerNodes,bufferNodes.toString());
					break;
				case LINK: /* For loss probability per-link statistics */
					StringBuilder bufferLinks = new StringBuilder();
					for (Edge edge: edgeSet) {
						int index = edgeMap.get(edge.toString());
						long droppedLink = links[index];
						bufferLinks.append((double)droppedLink/total);
						bufferLinks.append("\t");
					}
					bufferLinks.append("\n");
					this.writeOutput(writerLinks,bufferLinks.toString());
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
				case DELAY_UNIT: /* Delay Unit without any normalization. */
					String delay = parameters.get("/OPS/Buffer/@delayUnit").firstElement();
					bufferMain.append(delay);
					//Add a separator between values
					bufferMain.append("\t");
					break;
				case LOAD: /* Traffic load.*/
					String load = parameters.get("/Generators/Traffic/@load").firstElement();
					bufferMain.append(load);
					//Add a separator between values
					bufferMain.append("\t");
					break;		
				case RESTORABILITY: /* Restorability ratio. */
					//Calculate the restorability ratio.
					double ratio = (double) restoredSuccessful / (double)(restoredSuccessful + restoredFailed);
					bufferMain.append(ratio);
					//Add a separator between values
					bufferMain.append("\t");					
					break;
				case ROUTE: /* For per (source-destination) pair statistics. */
					StringBuilder bufferRoutes = new StringBuilder();
					for (int i=0; i < routes.length; i++) {
						bufferRoutes.append((routes[i]/total));
						bufferRoutes.append("\t");
					}
					bufferRoutes.append("\n");
					this.writeOutput(writerRoute,bufferRoutes.toString());
					break;
				case BLOCKING_RESOURCE:
					double blocked = ((double)blockedResource) / total; 
					bufferMain.append(blocked);
					//Add a separator between values
					bufferMain.append("\t");
					break;
			}
		}
		//Write to the main output file if the append flag is false.
		if (!append) {
			bufferMain.append("\n");
			//Write it to the file.
			this.writeOutput(writer,bufferMain.toString());
			//Reset the buffer.
			bufferMain.delete(0,bufferMain.length());
		}
	}

	/**
	 * Write the string to the appropriate file writer.
	 * @param fWriter The specified file writer.
	 * @param buffer The string to written. 
	 */
	protected void writeOutput(FileWriter fWriter, String string) {
		try {
			//Write the results
			fWriter.write(string);
			//Flush the file
			fWriter.flush();
		} catch (Exception e) {e.printStackTrace();}		
	}
	
	/**
	 * Create a new file for output, adding a suffix to the newly created file.
	 * @param suffix The String to be added at the end of the main file name. 
	 * @return A FileWriter object, refering to the new file created.
	 */
	protected FileWriter createOutput(String suffix) {
		//Get the name of the main file
		String outputName = output.getName();
        //Create a file to write the simulation output for other type of statistics.
        File file = new File(outputName+suffix);
        FileWriter newWriter = null;
        try {
        	//Create a new file 
        	file.createNewFile();
        	//Create a new 
            newWriter = new FileWriter(file);
        } catch(Exception e){e.printStackTrace();}
         return newWriter;
	}

	/** Gets the writers of this object. 
	 * @return The writers of this object.
	 */
	public Vector<FileWriter> getWriters() {
		Vector<FileWriter> writers = new Vector<FileWriter>();
		writers.add(this.writer);
		writers.add(this.writerClass);
		writers.add(this.writerHop);
		writers.add(this.writerLinks);
		writers.add(this.writerNodes);
		writers.add(this.writerRoute);
		writers.add(this.writerUtilization);
		return writers;
	}
	
	/**
	 * Set the writers of this object
	 * @param writers The new list of writers.
	 */
	public void setWriters(Vector<FileWriter> writers) {
		this.writer = writers.get(0);
		this.writerClass = writers.get(1);
		this.writerHop = writers.get(2);
		this.writerLinks = writers.get(3);
		this.writerNodes = writers.get(4);
		this.writerRoute = writers.get(5);
		this.writerUtilization = writers.get(6);
	}

	/**
	 * Initialize the counter statistics and create a new file with appropriate
	 * heading, if necessary.
	 */
	protected void initializeCounters() {
        //For all values of desired output do
		for (Values value: print) {
			switch(value) {
				case LINK: /* For loss probability per-link statistics */
					//Initialize loss per-link statistics
					Arrays.fill(links,0L);
					break;
				case UTILIZATION: /* For per-link utilization. */
					//Initialize utilization per-link statistics
					Arrays.fill(utilization,0.0);
					break;					
				case NODE: /* For loss probability per-node statistics */
					//Initialize per-node statistics
					Arrays.fill(nodes,0L);
					break;
				case CLASS: /* For loss probability per-class of service statistics */
					Arrays.fill(classService,0L);
					break;	
				case HOP: /* The percentage of SUCESSFULL routed packets in ascending number of hops. */
					break;
				case ROUTE: /* For loss probabibility per source-destination pairs. */
					//Initialize loss per-pair statistics
					Arrays.fill(routes,0L);
					break;
			}
		}
		//Initialize the counter of restored connections
		this.restoredFailed = 0L;
		this.restoredSuccessful = 0L;
		//For the blocked due to the lack of resources
		this.blockedResource = 0L;
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
				case LINK: /* For loss probability per-link statistics */
					for(Object edge: edgeSet) {
						buffer.append(edge.toString());
						buffer.append("\t");						
					}
					buffer.append("\n");
					//Create appropriate output file
					writerLinks = this.createOutput("_links.txt");
					//Write the heading line to the file
					this.writeOutput(writerLinks,buffer.toString());
					break;
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
				case NODE: /* For loss probability per-node statistics */
					for (Object node: nodeSet) {
						buffer.append(node.toString());
						buffer.append("\t");
					}
					buffer.append("\n");
					//Create appropriate output file
					writerNodes = this.createOutput("_nodes.txt");
					//Write the heading line to the file
					this.writeOutput(writerNodes,buffer.toString());
					break;
				case CLASS: /* For loss probability per-class of service statistics */
					for(Packet.Priority cos: classSet) {
						buffer.append(cos.toString());
						buffer.append("\t");
					}
					buffer.append("\n");
					//Create appropriate output file
					writerClass = this.createOutput("_class.txt");
					//Write the heading line to the file
					this.writeOutput(writerClass,buffer.toString());
					break;	
				case HOP: /* The percentage of SUCESSFULL routed packets in ascending number of hops. */
					//Create appropriate output file
					writerHop = this.createOutput("_hop.txt");
					break;
				case ROUTE: /* For loss probabibility per source-destination pairs. */
					//Write the routes
					for(Path route:routeSet) {
						buffer.append(route.toString());
						buffer.append("\n");
					}
					//Write the pairs of the routes
					for(String pair:routeMap.keySet()) {
						buffer.append(pair);
						buffer.append("\t");
					}
					//Create appropriate output file
					writerRoute = this.createOutput("_route.txt");
					//Write the heading line to the file
					this.writeOutput(writerRoute,buffer.toString());
					break;
			}
		}
	}

	
	/**
	 * Accounts the dropped packet.
	 * @param packet The packet that was dropped.
	 */
	public void addFailed(Packet packet) {
		//Failure of re-routing
		if (packet.getNextHeader().equals(Packet.Header.RSVP_PATH_ERR) && ((RSVP)packet).isReRouting()) {
			this.restoredFailed ++; //Increment the counter.
		} else { //Normal failed  packets		
			/* Accounts the dropped packets per path length. */
			int hop = packet.getPathLength();
			//System.out.println(packet.toString());
			try {
				failed[hop] = failed[hop] + 1;
			} catch (Exception e) {System.out.println(packet.toString()); e.printStackTrace();}			
			//For all other values of desired output do
			for (Values value: print) {
				switch(value) {
				case LINK: /* For loss probability per-link statistics */
					//Get the link that the packet would have traversed.
					if (packet.getPathLength() > 0) { //at least one hop
						Edge edge = packet.getLastLink();
						//Increment the counter for the links
						int indexL = edgeMap.get(edge.toString());
						links[indexL] = links[indexL] + 1;
					}
					break;
				case NODE: /* For loss probability per-node statistics */
					//Get the processing node of the packet.
					Path path = packet.getPath(); 
					String id;
					if (path.size() > 1) { //at least one hop
						id = packet.getLastLink().getSource();
					} else { //get the first node of the path
						id = path.getNode(0);
					}
					//Increment the counter for the processing nodes
					int indexN = nodeMap.get(id.toString());
					nodes[indexN] = nodes[indexN] + 1;
					break;
				case CLASS: /* For loss probability per-class of service statistics */
					Packet.Priority priority = packet.getTrafficClass();
					int ordinal = priority.ordinal();
					classService[ordinal] = classService[ordinal] + 1;
					break;
				case ROUTE: 
					//Gets the source-destination pair
					String pair = packet.getSource() + "-" + packet.getTarget();
					//Increment the counter for the pair
					int indexR = routeMap.get(pair);
					routes[indexR] = routes[indexR] + 1;
					break;
				case BLOCKING_RESOURCE: //Lack of resources at the node
					if (packet.getNextHeader().equals(Packet.Header.JOB_REQUEST) || packet.getNextHeader().equals(Packet.Header.JOB_SETUP)) { 
						blockedResource ++;
					} else if ((packet.getNextHeader().equals(Packet.Header.RSVP_PATH_ERR))) {
						if (((RSVP)packet).getError().getErrorCode().equals(Error.Code.SERVICE_PREEMPTED)) {
							blockedResource ++;
						}
					}
				}
			}
		}
	}
	
	/** 
	 * Accounts the successfully routed packets. 
	 * @param packet The successfully routed packet.
	 */
	public void addSuccesful(Packet packet) {
		//Re-routing of failed requests
		if (packet.getNextHeader().equals(Packet.Header.RSVP_RESV) && ((RSVP)packet).isReRouting()) {
			this.restoredSuccessful ++; //Increment the counter.
		} else { //Normal routed packets
			int pathLength = packet.getPathLength();
			if (pathLength > maxLength)
				maxLength = pathLength;
			successful[pathLength]=successful[pathLength] + 1;
		}
	}

	/**
	 * Sets the utilization of each network link in the simulation.
	 * @param linkSet The set of links in the network.
	 * @param totalTime The total simulation time.
	 */
	public void setUtilization(LinkedHashMap<String,Link> linkSet, double totalTime) {
		for (Edge edge: edgeSet) {
			Link link = linkSet.get(edge.toString());
			double dataRate = link.getDataRate() / 8.0; //data rate in bytes/sec
			int numberWavelength = link.getNumberWavelengths(); //number of wavelengths
			long bytes = link.getCounter(); //get the total number of bytes carried by the link
			double result = ((double) bytes) / (numberWavelength * dataRate * totalTime);
			int index = edgeMap.get(edge.toString());
			utilization[index]=result;
		}
	}

	/**
	 * Set the utilization using a channel other than the data plane.
	 * @param linkSet The set of links in the network.
	 * @param totalTime The total simulation time.
	 * @param dataRate The data rate (in bps) of the control channel.
	 */
	public void setUtilization(LinkedHashMap<String,Link> linkSet, double totalTime, double dataRate) {
		for (Edge edge: edgeSet) {
			Link link = linkSet.get(edge.toString());
			//System.out.println("Link: "+edge.toString());
			long bytes = link.getCounter(); //get the total number of bytes carried by the link
			//System.out.println("Bytes: "+bytes);
			double result = ((double) bytes) / ((dataRate / 8.0) * totalTime);
			int index = edgeMap.get(edge.toString());
			utilization[index]=result;		
		}
	}
	/**
	 * Get the total number of dropped packets.
	 * @return The total number of dropped packets.
	 */
	protected long getFailed() {
		long counter = 0;
		for (long number: failed) {
			counter = counter + number;
		}
		return counter;
	}
	
	/**
	 * Get the total number of successfully delivered packets.
	 * @return The total number of successfully delivered packets.
	 */
	protected long getSuccessful() {
		long counter = 0;
		for (long number: successful) {
			counter = counter + number;
		}
		return counter;
	}
		
	/** 
	 * Resets all the statistics gathered by this object.
	 */
	public void reset() {
		//Reset the arrays
		Arrays.fill(this.failed,0L);
		Arrays.fill(this.successful,0L);
		Arrays.fill(this.classService,0L);
		Arrays.fill(this.links,0L);
		Arrays.fill(this.nodes,0L);
		Arrays.fill(this.utilization,0.0);
		Arrays.fill(this.routes,0L);
		//Reset the indicator for the maximum length of hops
		this.maxLength = 0;
		//Reset the counter of number of restored or not failed connections
		this.restoredFailed = 0L;
		this.restoredSuccessful = 0L;
		//Reset the number of blocking requests due to the lack of resources
		this.blockedResource = 0L;
	}
	
	/**
	 * Closes the output file. 
	 */
	public void close() {
		super.close();
        //Flush the output buffer and close the files
		if (writerClass !=null) 
			close(writerClass);
		if (writerHop != null) 
			close(writerHop);
		if (writerLinks != null) 
			close(writerLinks);
		if (writerNodes != null) 
			close(writerNodes);
		if (writerRoute != null) 
			close(writerRoute);
		if (writerUtilization != null) 
			close(writerUtilization);
 	}

}
