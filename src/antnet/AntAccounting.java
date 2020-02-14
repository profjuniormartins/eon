/*
 * Created on 19/11/2005.
 */
package antnet;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.Vector;

import main.Config;
import ops.Accounting;
import ops.Packet;

/**
 * This class accounts for the simulation results specific
 * to ant routing simulations.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class AntAccounting extends Accounting {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The array for storing the number of successful delivered ants. */
	protected long[] antRouted;
	/** The file writer for ant-routing statistics for dropped ants. */
	transient protected FileWriter writerAnt=null;    	
    /** The array for storing the number of dropped ant. */
	protected long[] antKilled;
	
	/**
	 * Creates a new AntAccounting object.
	 * @param aConfig The XML configuration file.
	 */
	public AntAccounting(Config aConfig) {
		super(aConfig);
		antRouted = new long[graph.size()];
		antKilled = new long[graph.size()];
		this.initializeWriters();
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
			}
		}
		//Calls super class
		super.write();
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
	}

	/** Gets the writers of this object. 
	 * @return The writers of this object.
	 */
	public Vector<FileWriter> getWriters() {
		Vector<FileWriter> writers = super.getWriters();
		writers.add(this.writerAnt);
		return writers;
	}
	
	/**
	 * Set the writers of this object
	 * @param writers The new list of writers.
	 */
	public void setWriters(Vector<FileWriter> writers) {
		super.setWriters(writers);
		this.writerAnt = writers.get(7);
	}
	
	/**
	 * Accounts the dropped packet.
	 * @param packet The packet that was dropped.
	 */
	public void addFailed(Packet packet) {
		if (packet.getNextHeader().equals(Packet.Header.ANT_FORWARD) || packet.getNextHeader().equals(Packet.Header.ANT_BACKWARD)) {
			int hop = packet.getPathLength();
			try {
				antKilled[hop] = antKilled[hop] + 1;
			} catch (Exception e) {System.out.println(packet.toString()); e.printStackTrace();}
		} else {
			super.addFailed(packet);
		}
	}
	
	/** 
	 * Accounts the successfully routed packets. 
	 * @param packet The successfully routed packet.
	 */
	public void addSuccesful(Packet packet) {
		if (packet.getNextHeader().equals(Packet.Header.ANT_BACKWARD) || packet.getNextHeader().equals(Packet.Header.ANT_FORWARD)) {
			int hop = packet.getPathLength();
			antRouted[hop] = antRouted[hop] + 1; 
		} else {
			super.addSuccesful(packet);
		}
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
        } catch(Exception e){e.printStackTrace();}                
	}

	
}
