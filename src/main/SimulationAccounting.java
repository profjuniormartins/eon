/*
 * Created on Oct 4, 2005.
 */
package main;

import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Abstract class for accounting the results of the simulation.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public abstract class SimulationAccounting implements Serializable{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The values that can be selected to the output. */
	public enum Values {
		/** The Delay Unit. */ DELAY_UNIT,
		/** Packet Loss Probability. */ LOSS,
		/** Per class of service. */ CLASS,
		/** Per processing node. */ NODE,
		/** Per output link. */ LINK,
		/** Per route. */ ROUTE, 
		/** Successful requests per number of hops. */ HOP,
		/** Effective number of hops. */ EFF_HOP,
		/** The total load of the network. */ LOAD,
		/** Fairness. */ FAIRNESS,
		/** Per-link utilization. */ UTILIZATION,
		/** Overhead of the routing packets. */ OVERHEAD,
		/** Average delay of the packets. */ DELAY,
		/** Ant-routing related statistics. */ ANT,
		/** Generational rate of ants. */ ANT_RATE,
		/** Buffer average occupancy. */ OCCUPANCY,
		/** Restorability ratio. */ RESTORABILITY,
		/** Blocking due to unavailability of resources. */ BLOCKING_RESOURCE,
		/** Instantaneous per-link utilization. */  INST_UTILIZATION,
		/** Instantaneous packet loss. */ INST_LOSS,
		/** Instantaneous workload. */ INST_WORKLOAD,
		/** The variable value of the simulator. */ VARIABLE,
	}	
	/** The simulation parameters. */
	protected Hashtable<String,Vector<String>> parameters;	
	/** The output descriptor. */
	transient protected File output;
	/** The file writer. */
    transient protected FileWriter writer=null;
    /** The selected values to be printed. */
    protected Vector<Values> print;
	/** The XML configuration file. */
	protected transient Config config;

	/**
	 * Creates a new SimulationAccounting object
	 * @param aConfig The XML configuration file.
	 */
	public SimulationAccounting(Config aConfig) {
		config = aConfig;
		parameters = config.getSimulationParameters();
        //Select the values that are present in the output
        Vector<String> selected = parameters.get("/Outputs/Print");
        print = new Vector<Values>();
        for (String value: selected) {
        	print.add(Values.valueOf(value));
        }
        //File to record the output
		this.open(config);
	}

	/** 
	 * Writes the desired simulation results to the output file.
	 */
	public abstract void write();

	/** 
	 * Resets all the statistics gathered by this object.
	 */
	public abstract void reset();

	/** Initialize other writers, if applicable. */
	public abstract void initializeWriters();

	/**
	 * Initialize the main output of the program.
	 */
	public void open(Config aConfig) {
		config = aConfig;
		parameters = config.getSimulationParameters();
		String fileName = parameters.get("/Outputs/Output/@file").firstElement();
        output = new File(fileName);
        //Create a file to write the simulation output
        try {
            output.createNewFile();
            writer = new FileWriter(output);
        } catch(Exception e){e.printStackTrace();}
	}
	
	/**
	 * Sets a new output for this object.
	 * @param newOutput The new output.
	 */
	public void setOutput(File newOutput) {
		this.output = newOutput;
	}
	
	/**
	 * Gets the output for this object.
	 * @return The output of this object.
	 */
	public File getOutput() {
		return this.output;
	}
	
	/** Gets the writers of this object. 
	 * @return The writers of this object.
	 */
	public Vector<FileWriter> getWriters() {
		Vector<FileWriter> writers = new Vector<FileWriter>();
		writers.add(writer);
		return writers;
	}
	
	/**
	 * Set the writers of this object
	 * @param writers The new list of writers.
	 */
	public void setWriters(Vector<FileWriter> writers) {
		writer = writers.firstElement();
	}
	
	/**
	 * Closes the output file. 
	 */
	public void close() {
		close(writer);
	}
	
	/**
	 * Closes the specified output file.
	 * @param fWriter The descriptor of the file writer object.
	 */
	public void close(FileWriter fWriter) {
        try{
            fWriter.flush();
            fWriter.close();
        } catch(Exception e){e.printStackTrace();}                		
	}
	
	/**
	 * Returns a String representation of this object. 
	 * @return A String representation of this object.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("File: ");
		buf.append(writer.toString());
		buf.append("\n Printing:");
		buf.append(print.toString());
		return buf.toString();
	}

}
