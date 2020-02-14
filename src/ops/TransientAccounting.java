/*
 * Created on Oct 28, 2005.
 */
package ops;

import graph.Edge;

import java.io.FileWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Vector;

import main.Config;
import main.Link;

/**
 * This class accounts for the transients (in time) of the simulation,
 * like instantaneous delay or throughput. Each run of the simulation is 
 * written in a separated file.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class TransientAccounting extends antnet.AntAccounting {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The time interval used for accounting the transients. */
	protected double timeSlice;
	/** The number of bytes accumulated till the last accounting. */
	protected long accumulatedBytes[];
	/** The number of accumulated packets dropped and successful packets */
	protected long accumulatedFailed, accumulatedSuccessful;
	/** The storage for the instantaneous utilization accounting. */
	protected Vector<Double>[] throughput;
	/** The storage for instantaneous loss accounting. */
	protected Vector<Double> instLoss;
	/** Counter for the number of runs of the algorithm. */
	protected int runTP=0;
	/** Counter for the total number of time slices whithin a run. */
	protected int runTimeSlice = 0;
	
	/**
	 * Creates a new TransientAccounting object.
	 * 
	 * @param aConfig The XML configuration file.
	 */
	@SuppressWarnings("unchecked")
	public TransientAccounting(Config aConfig) {
		super(aConfig);
		//Gets the size of the time slice
		timeSlice = Double.parseDouble(parameters.get("/Outputs/Transient/@timeSlice").firstElement());
		//Creates the storage for the values of throughput 
		throughput = new Vector[edgeSet.length];
		for (int i=0; i < edgeSet.length; i++) {
			throughput[i] = new Vector<Double>();
		}
		//And instantaneous loss
		this.instLoss = new Vector<Double>();
		//Create the space for the accumulated bytes since the start of the simulation
		accumulatedBytes = new long[edgeSet.length];
		Arrays.fill(accumulatedBytes,0L);
	}

	/** 
	 * Writes the desired simulation results to the output file.
	 */
	@Override
	public void write() {
		super.write();
		//For each desired value
		for (Values value: print) {
			switch(value) {
				case INST_UTILIZATION:
					//Create appropriate output file
					FileWriter writerTP = this.createOutput("_throughput_"+runTP+".txt");
					//Write the heading line to the file
					StringBuilder builderTP = new StringBuilder();
					for (Edge edge: edgeSet) {
						builderTP.append(edge.toString());
						builderTP.append("\t");
					}
					builderTP.append("\n");
					//Now, write the thropughput to the file
					for (int row=0; row < runTimeSlice; row++) {
						for(int col=0; col < edgeSet.length; col ++) {
							builderTP.append(throughput[col].get(row));
							builderTP.append("\t");
						}
						builderTP.append("\n");
					}
					//Write to the file
					this.writeOutput(writerTP,builderTP.toString());
					this.close(writerTP);
				break;
				case INST_LOSS:
					//Create appropriate output file
					FileWriter writerILoss = this.createOutput("_instLoss_"+runTP+".txt");			
					StringBuilder builderLoss = new StringBuilder();
					for (int i=0; i < instLoss.size();i++) {
						//Adds the actual time slice
						builderLoss.append(timeSlice*(i+1));
						builderLoss.append("\t");
						builderLoss.append(instLoss.get(i));
						builderLoss.append("\n");
					}
					//Write to the file
					this.writeOutput(writerILoss,builderLoss.toString());
					this.close(writerILoss);
				break;					
			}
		}	
	}

	/** 
	 * Resets all the statistics gathered by this object.
	 */
	@Override
	public void reset() {
		super.reset();
		//Now, reset the accounted values
		Arrays.fill(accumulatedBytes,0L);
		for(int i=0; i < edgeSet.length; i++) {
			throughput[i] = new Vector<Double>();
		}
		this.instLoss = new Vector<Double>();
		//Increment the counter for the number of runs.
		runTP ++; 
		//Reset the counter for the time slices.
		runTimeSlice = 0;
		//Now reset the counters
		this.accumulatedFailed = 0L;
		this.accumulatedSuccessful = 0L;
	}
	
	/**
	 * Initialize the counter statistics and create a new file with appropriate
	 * heading, if necessary.
	 */
	protected void initializeCounters() {
		//Calls super class
		super.initializeCounters();
		//Now reset the counters
		this.accumulatedFailed = 0L;
		this.accumulatedSuccessful = 0L;
	}

	/** 
	 * Calculates the instantaneous values when a time slice is reached.
	 * @param linkSet The set of link status of the network.
	 */
	public void setInstantaneousValues(LinkedHashMap<String,Link> linkSet) {
		/* For loss */
		long dropped = this.getFailed() - this.accumulatedFailed;
		long routed = this.getSuccessful() - this.accumulatedSuccessful;
		double total = (double)(dropped+routed);
		instLoss.add(((double)dropped) / total);
		//Update the accumulated counters.
		this.accumulatedFailed = this.getFailed();
		this.accumulatedSuccessful = this.getSuccessful();
		/* For throughput */
		for (Edge edge: edgeSet) {
			Link link = linkSet.get(edge.toString());
			double dataRate = link.getDataRate() / 8.0; //data rate in bytes/sec
			int numberWavelength = link.getNumberWavelengths(); //number of wavelengths
			long bytes = link.getCounter(); //get the total number of bytes carried by the link
			int index = edgeMap.get(edge.toString());
			//Calculate the instantaneous throughput
			double result = ((double) (bytes - accumulatedBytes[index])) / (numberWavelength * dataRate * timeSlice);
			throughput[index].add(result); //adds the result to the storage
			//Updates the accumulated number of bytes
			accumulatedBytes[index] = bytes;
		}		
		//Now increment the counter for the number of time slices in the total time of simulation
		runTimeSlice ++;
	}		
	
}
