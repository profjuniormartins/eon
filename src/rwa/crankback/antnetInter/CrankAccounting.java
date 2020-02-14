/*
 * Created on 19/02/2008.
 */
package rwa.crankback.antnetInter;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.Vector;

import graph.Edge;
import graph.Path;
import main.Config;
import ops.Accounting;
import ops.Packet;
import rwa.Error;

/**
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class CrankAccounting extends Accounting {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The array for storing the number of successful delivered ants. */
	protected long[] antRouted;
	/** The file writer for ant-routing statistics for dropped ants. */
	transient protected FileWriter writerAnt=null;    	
    /** The array for storing the number of dropped ant. */
	protected long[] antKilled;
	/** The array for storing the effective hop counting per connection. */
	protected long[] effectiveHop;
	/** The file writer for effective hop counting statistics. */
	transient protected FileWriter writerEff=null;
	/** The maximum number of effective hops. */
	protected int maxEffHops = 0;
	
	/** 
	 * Creates a new CrankAccounting object.
	 * @param aConfig The configuration from the XML file. 
	 */
	public CrankAccounting(Config aConfig) {
		super(aConfig);

	//	System.out.println("ACCOUTING! PASSEI!!!!");
		antRouted = new long[graph.size()];
		antKilled = new long[graph.size()];
		effectiveHop = new long[2*edgeSet.length];
		this.initializeWriters();
	}
	
	/**
	 * Accounts the dropped packet.
	 * @param packet The packet that was dropped.
	 */
	public void addFailed(Packet packet) {
		//Failure of re-routing
		if (packet.getNextHeader().equals(Packet.Header.RSVP_PATH_ERR) && ((CrankRSVP)packet).inRestoration()) {
			this.restoredFailed ++; //Increment the counter.
		} else if (packet.getNextHeader().equals(Packet.Header.ANT_FORWARD) || packet.getNextHeader().equals(Packet.Header.ANT_BACKWARD)) {
			int hop = packet.getPathLength();
			try {
				antKilled[hop] = antKilled[hop] + 1;
			} catch (Exception e) {System.out.println(packet.toString()); e.printStackTrace();}		
		} else { //Normal failed  packets		
		//	System.out.println("Marcando como falha " + packet.getFlowLabel());
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
						try{
						int indexL = edgeMap.get(edge.toString());
						links[indexL] = links[indexL] + 1;
						}catch(Exception e) {
							
						}
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
						if (((CrankRSVP)packet).getError().getErrorCode().equals(Error.Code.SERVICE_PREEMPTED)) {
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
		if (packet.getNextHeader().equals(Packet.Header.RSVP_RESV) && ((CrankRSVP)packet).inRestoration()) {
			this.restoredSuccessful ++; //Increment the counter.
		} else if (packet.getNextHeader().equals(Packet.Header.ANT_BACKWARD) || packet.getNextHeader().equals(Packet.Header.ANT_FORWARD)) {
			int hop = packet.getPathLength();
			antRouted[hop] = antRouted[hop] + 1; 
		} else { //Normal routed packets
			int pathLength = packet.getPathLength();
			if (pathLength > maxLength)
				maxLength = pathLength;
			successful[pathLength]=successful[pathLength] + 1;
			//Effective hop
			int effective = ((CrankRSVP)packet).getEffectiveHops();
			if (effective > this.maxEffHops)
				this.maxEffHops = effective;
			effectiveHop[effective] = effectiveHop[effective] + 1; 
		}
	}
	
	/** 
	 * Writes the desired simulation results to the output file.
	 */
	public void write() {
		//For each desired value
		for (Values value: print) {
			switch(value) {
				case ANT:	
					StringBuilder bufferAnt = new StringBuilder();
					long failedAnts=0;
					long routedAnts=0;
					for (long numberR:antRouted) {
						routedAnts = routedAnts + numberR;
					}
					bufferAnt.append(routedAnts);
					bufferAnt.append("\t");
					for (long numberF:antKilled) {
						failedAnts = failedAnts + numberF;
					}
					bufferAnt.append(failedAnts);
					bufferAnt.append("\n");
					writeOutput(writerAnt,bufferAnt.toString());
					break;
				case ANT_RATE:
					String rate = parameters.get("/Generators/Traffic/@arrivalRate").firstElement();
					bufferMain.append(rate);
					//Add a separator between values
					bufferMain.append("\t");					
					break;
				case EFF_HOP:
					StringBuilder bufferEff = new StringBuilder();
					long success = this.getSuccessful();
					for(int i=1; i < this.maxEffHops; i++) {
						long eff = this.effectiveHop[i];
						double resultEff = (double)eff/success ;
						bufferEff.append(resultEff);
						bufferEff.append("\t");						
					}
					bufferEff.append("\n");
					writeOutput(writerEff,bufferEff.toString());
					break;
			}
		}
		//Calls super class
		super.write();
//		System.out.println("Sucessfull: "+this.getSuccessful()+" Failed:"+this.getFailed());
	}
	
	/**
	 * Create a new file with appropriate heading, if necessary.
	 */
	public void initializeWriters() {
		//Calls super class
		super.initializeWriters();
		//For each desired value
		for (Values value: print) {
			switch(value) {
				case ANT:				
					writerAnt = this.createOutput("_ant.txt");
					break;
				case DELAY:
					break;
				case EFF_HOP:
					writerEff = this.createOutput("_eff.txt");
					break;
			}
		}
	}
	
	/** 
	 * Resets all the statistics gathered by this object.
	 */
	public void reset() {
		//Calls super class
		super.reset();
		Arrays.fill(this.antRouted,0L);
		Arrays.fill(this.antKilled,0L);	
		Arrays.fill(this.effectiveHop, 0L);
		//Reset the indicator for the maximum length of effective hops.
		this.maxEffHops = 0;
	}

	/** Gets the writers of this object. 
	 * @return The writers of this object.
	 */
	public Vector<FileWriter> getWriters() {
		Vector<FileWriter> writers = super.getWriters();
		writers.add(this.writerAnt);
		writers.add(this.writerEff);
		return writers;
	}
	
	/**
	 * Set the writers of this object
	 * @param writers The new list of writers.
	 */
	public void setWriters(Vector<FileWriter> writers) {
		super.setWriters(writers);
		this.writerAnt = writers.get(7);
		this.writerEff = writers.get(8);
	}
	
	/**
	 * Closes the output file. 
	 */
	public void close() {
		super.close();
        try{
        	//Flush the output buffer and close the files
        	if (writerAnt !=null) {
                this.writerAnt.flush();
                this.writerAnt.close();        		
        	}
        	if (writerEff != null) {
        		this.writerEff.flush();
        		this.writerEff.close();
        	}
        } catch(Exception e){e.printStackTrace();}                
	}

}
