/*
 * Created on Sep 15, 2005.
 * Updated on 2011 - 2012
 */
package main;

import distribution.Distribution;
import event.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

import rwa.crankback.antnetInter.AntNetCrankInterRoutingTable;

/**
 * This class is the main entry for the event-driven simulator.
 * 
 * @author Gustavo S. Pavani, Andre Filipe M Batista
 * @version 1.0
 * 
 */
public class Simulator implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The logging generator. */
	private static Logger logger = Logger.getLogger(Simulator.class.getName());
	/** The XML configuration file for this simulation. */
	protected transient Config config;
	/** The simulation parameters. */
	protected transient Hashtable<String, Vector<String>> simulation;
	/** The loader of the objects related to the simulation. */
	protected transient Loader loader;
	/** The scheduler of the simulation. */
	protected Scheduler scheduler;
	/** The variable of this simulation. */
	protected String variable;
	/** The variable value of this simulation. */
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
	/** The control plane of this simulation. */
	protected ControlPlane controlPlane;
	/** The accounting class for this simulation. */
	protected SimulationAccounting accounting;
	/** Number of requests. */
	protected long numberOfRequests;
	/** Requests to be counted. */
	protected Vector<String> related;
	/** The run counter. */
	protected transient int runCounter = 0;

	/**
	 * Creates a new Simulator object.
	 * 
	 * @param fileConfig
	 *            The name of the configuration file.
	 */
	public Simulator(String fileConfig) {
		// Initialize the configuration of the simulation
		try {
			this.config = new Config(fileConfig);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Gets the parameters
		this.simulation = config.getSimulationParameters();
		// Gets the variable of the simulation
		this.variable = simulation.get("/Main/Variable/@name").firstElement();
		int sizeVar = simulation.get(variable).size();
		variableValue = new double[sizeVar];
		initialValue = new double[sizeVar];
		for (int i = 0; i < sizeVar; i++) {
			variableValue[i] = Double.parseDouble(simulation.get(variable).get(
					i));
			initialValue[i] = variableValue[i];
		}
		Vector<String> step = simulation.get("/Main/Variable/@step");
		Vector<String> mult = simulation.get("/Main/Variable/@mult");
		Vector<String> exp = simulation.get("/Main/Variable/@exp");
		if (step != null) {
			this.stepValue = Double.parseDouble(step.firstElement());
		}
		if (mult != null) {
			this.multValue = Double.parseDouble(mult.firstElement());
		}
		if (exp != null) {
			this.expValue = Double.parseDouble(exp.firstElement());
		}
		this.stopValue = Double.parseDouble(simulation.get(
				"/Main/Variable/@stop").firstElement());
		// Parses the total number of requests.
		String requests = simulation.get("/Main/Requests/@value")
				.firstElement();
		this.numberOfRequests = Long.parseLong(requests);
		// Parses the subscribers name that will be counted for the number of
		// requests.
		this.related = simulation.get("/Accounting/RequestRelated/@class");
		// Create the loader
		this.loader = new Loader(config);
	}

	/**
	 * Executes the simulation.
	 */
	@SuppressWarnings("unchecked")
	public void run() {
		// Creates the accounting part of the simulation
		if (!loader.isFresh()) { // Not needed on fresh simulations, already on
									// constructors
			loader.loadSimulation(0);
			accounting = loader.getAccounting();
			accounting.open(config); // Create the output
			accounting.initializeWriters(); // Create the writers
		} else { // Gets the fresh accounting
			accounting = loader.getAccounting();

		}
		// Get the output and file writers
		File output = accounting.getOutput();
		// System.out.println("File: "+output.toString());
		Vector<FileWriter> writers = accounting.getWriters();

		// The simulation main loop
		while (variableValue[0] <= stopValue) {
			logger.info(variable + ": " + variableValue[0]);
			// Load the simulator object, if applicable
			if (!(loader.isFresh()) && (runCounter > 0)) {
				loader.loadSimulation(runCounter);
			}
			// Start the event-driven part
			scheduler = loader.getScheduler();
			loader.addFailures(scheduler);
			loader.addTerminate(scheduler);
			loader.addSerializers(scheduler);
			if (loader.isFresh()) {
				loader.addGenerators(scheduler);
			} else if (runCounter > 0) { // Get the accounting part for
											// serialized states
				accounting = loader.getAccounting();
				accounting.setOutput(output); // Set the output to the actual
												// value
				accounting.setWriters(writers); // Set the writers to the actual
												// values
			}

			// Start the network part
			controlPlane = loader.getControlPlane(accounting);
			// System.out.println("Starting.\n Nodes:");
			// System.out.println(controlPlane.graph.nodes().toString());

			// Reset the counter of requests.
			long requestCounter = 0;
			// Run the simulation and print statistics information
			while (requestCounter < numberOfRequests) {
				Event event = null, response = null;
				try {
					// Gets the next event on the queue
					event = scheduler.step();
					if (event.getType().equals(Event.Type.TERMINATE)) {
						break; // stop the simulation of this run
					} else if (event.getType().equals(Event.Type.SERIALIZE)) { // Serialize
																				// the
																				// state
																				// of
																				// the
																				// simulator
						String preffix = (String) event.getContent(); // get the
																		// preffix
						double at = event.getTimeStamp(); // get the stamp
						String var = variable.substring(variable
								.lastIndexOf("@") + 1);
						String fileName = preffix + "_" + var + "_"
								+ variableValue[0] + "_t" + at + ".obj";
						logger.info("Writing state to disk: " + fileName);
						write(fileName, this);
					} else { // Process the event at the control plane level
						response = controlPlane.process(event);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (response != null) { // response from the control plane
					if (response.getType().equals(Event.Type.MULTIPLE)) { // Multiple
																			// events
																			// generated
						Vector<Event> multiple = (Vector<Event>) response
								.getContent();
						// for each event do
						for (Event single : multiple)
							// Insert each event separatedly
							scheduler.insertEvent(single);
					} else { // Single response
						scheduler.insertEvent(response);
					}
				}
				// Count the actual number of request till now.
				long allCounters = 0;
				for (String subscriber : related) {
					allCounters = allCounters
							+ scheduler.getCounter(subscriber);
				}
				requestCounter = allCounters;
				// System.out.println("Req #: "+requestCounter);
			}
			// Update the utilization parameters, if implemented
			try {
				Method updateValues = controlPlane.getClass().getMethod(
						"updateValues", (Class[]) null);
				updateValues.invoke(controlPlane, (Object[]) null);
			} catch (Exception e) {
			} // do nothing - method not implemented
			// Write and reset the values gathered by the accounting
			accounting.write();
			accounting.reset();

			// Set the new value for the simulation
			int sizeVar = simulation.get(variable).size();
			for (int i = 0; i < sizeVar; i++) {
				if (stepValue != 0.0) {
					variableValue[i] = variableValue[i] + stepValue;
				} else if (multValue != 0.0) {
					variableValue[i] = variableValue[i]
							+ (multValue * initialValue[i]);
				} else if (expValue != 0.0) {
					variableValue[i] = variableValue[i] * expValue;
				}
				String newValue = Double.toString(variableValue[i]);
				simulation.get(variable).set(i, newValue);
			}
			// Vector<String> vec = new Vector<String>(); //old code
			// vec.add(Double.toString(variableValue));
			// simulation.put(variable,vec);
			// Increment the counter of runs
			this.runCounter++;
		}
		// Closes the accounting part
		accounting.close();
	}

	/**
	 * Main entry for the simulator program.
	 * 
	 * @param args
	 *            The name of the XML configuration file in the command line.
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		// Verify if the arguments are correct. Otherwise, print usage
		// information.
		if (args.length != 1) {
			System.err.println("Usage: java main.Simulator config_file.xml");
			return;
		}

		Simulator simulator = new Simulator(args[0]);
		logger.info("Starting simulation at: " + (new Date()).toString());

		simulator.run();
		logger.info("Simulation finished at:" + (new Date()).toString());
		// System.out.println(AntNetCrankInterRoutingTable.printPherormones());
	}

	/**
	 * Write the simulator object to the output file.
	 * 
	 * @param file
	 *            The output file.
	 * @param sim
	 *            The simulator object.
	 */
	public static void write(String file, Simulator sim) {
		try {
			FileOutputStream fos = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(sim);
			oos.flush();
			oos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reads the simulator object from the file.
	 * 
	 * @param file
	 *            The name of the file containing the simulator object.
	 * @return The simulator object.
	 */
	public static Simulator read(String file) {
		Simulator result = null;
		try {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			result = (Simulator) ois.readObject();
			ois.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
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
	Hashtable<String, Vector<String>> parameters;
	/**
	 * The vector for storing the name of the state of the serialized
	 * simulations, if applicable.
	 */
	Vector<String> simState;
	/** The simulation object. To be used on loaded simulations. */
	Simulator sim;
	/** The logging generator. */
	private static Logger logger = Logger.getLogger(Loader.class.getName());

	Loader(Config aConfig) {
		this.config = aConfig;
		this.parameters = config.getSimulationParameters();
		// Verify if the simulation is to be loaded from the disk
		this.simState = this.getState();
	}

	/**
	 * Gets the name of the serialized simulation states. If nothing is found,
	 * then return null.
	 * 
	 * @return
	 */
	public Vector<String> getState() {
		Vector<String> ser = parameters.get("/State/Load/@serialized");
		if (ser == null) {
			return null; // No serialization for loading supported
		}
		// Now see the flag
		boolean fresh = !Boolean.parseBoolean(ser.firstElement());
		if (fresh) {
			return null;
		} else {
			return parameters.get("/State/File");
		}
	}

	/**
	 * Load the simulation object with the specified index.
	 * 
	 * @param index
	 *            The index of the object
	 */
	public void loadSimulation(int index) {
		String fileName = simState.get(index);
		logger.info("Reading state from file: " + fileName);
		this.sim = Simulator.read(fileName);
	}

	/**
	 * Verifies if the simulation is to be done with fresh objects. True, if the
	 * simulation has to be done with fresh objects. False, otherwise.
	 * 
	 * @return
	 */
	public boolean isFresh() {
		return (simState == null);
	}

	/**
	 * Get the a specific version of the control plane, which is responsible for
	 * the network simulation.
	 * 
	 * @return The specific version of the control plane.
	 */
	@SuppressWarnings("unchecked")
	public ControlPlane getControlPlane(SimulationAccounting accounting) {
		ControlPlane cp = null;

		if (this.isFresh()) { // fresh simulation

			// System.out.println("Eh fresh. PAssei por aqui");

			Hashtable<String, Vector<String>> parameters = config
					.getSimulationParameters();

			String controlClass = parameters.get("/Main/ControlPlane/@class")
					.firstElement();

			try {
				Class[] argsClass = new Class[] { Config.class,
						SimulationAccounting.class };

				Object[] aArgs = new Object[] { config, accounting };

				Class sControlPlane = Class.forName(controlClass);
				Constructor argsConstructor = sControlPlane
						.getConstructor(argsClass);
				cp = (ControlPlane) argsConstructor.newInstance(aArgs);

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else { // loaded simulation
			// System.out.println("Nao eh  fresh. PAssei por aqui");
			cp = sim.controlPlane;
			cp.updateConfig(this.config); // update the configuration file
		}
		return cp;
	}

	/**
	 * Gets the scheduler for this simulator.
	 * 
	 * @return The scheduler.
	 */
	public Scheduler getScheduler() {
		if (this.isFresh()) { // fresh simulation
			return new Scheduler();
		} else { // loaded simulation
			return sim.scheduler;
		}
	}

	/**
	 * Reads the parameters hashtable and returns the appropriate distribution.
	 * 
	 * @return The Vector containing the appropriate distributions.
	 */
	@SuppressWarnings("unchecked")
	public Vector<Distribution> getDistribution() {
		Vector<Distribution> distrib = new Vector<Distribution>();
		// Get the traffic (Java) classes
		Vector<String> classes = parameters.get("/Generators/Traffic/@class");
		int counter1 = 0;
		int counter2 = 0;
		int counter3 = 0;
		// For each traffic generator
		for (@SuppressWarnings("unused")
		String nClass : classes) {
			String trafficType = parameters.get("/Generators/Traffic/@type")
					.get(counter1 + counter2 + counter3);
			Distribution traffic = null;
			try {
				Class[] argsClass = null;
				Object[] aArgs = null;
				Class sTraffic = Class.forName("distribution."
						.concat(trafficType));
				if (trafficType.equals("Poissonian")) {
					String seed = parameters.get("/Generators/Traffic/@seed")
							.get(counter1);
					if (parameters.get("/Generators/Traffic/@averagePacket") == null) { // setting
																						// with
																						// only
																						// load
						if ((seed == null) || (seed.equals(""))) {
							argsClass = new Class[] { double.class,
									double.class };

							double load = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@load").get(counter1));
							double averageDuration = Double
									.parseDouble(parameters.get(
											"/Generators/Traffic/@duration")
											.get(counter1));
							aArgs = new Object[] {
									new Double(1.0 / averageDuration),
									new Double(load / averageDuration) };
						} else {
							argsClass = new Class[] { double.class,
									double.class, long.class };
							// System.out.println("User: "+parameters.get("/Generators/Traffic/@user").get(counter1));
							// System.out.println("Load: "+parameters.get("/Generators/Traffic/@load").toString());
							double load = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@load").get(counter1));
							double averageDuration = Double
									.parseDouble(parameters.get(
											"/Generators/Traffic/@duration")
											.get(counter1));
							aArgs = new Object[] {
									new Double(1.0 / averageDuration),
									new Double(load / averageDuration),
									Long.parseLong(seed) };
						}
					} else { // easy setting for poisson traffic
						if ((seed == null) || (seed.equals(""))) {
							argsClass = new Class[] { double.class,
									double.class };
							double length = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@averagePacket").get(
									counter1));
							double load = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@load").get(counter1));
							double rate = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@dataRate").get(
									counter1));
							double mu = 1.0 / ((length - ops.Packet.HEADER_LENGTH) / (rate / 8.0));
							double muReal = 1.0 / (length / (rate / 8.0));
							double lambda = muReal * load;
							logger.config("Mu: " + mu + " Mu(real):" + muReal
									+ " Lambda: " + lambda);
							aArgs = new Object[] { new Double(mu),
									new Double(lambda) };
						} else {
							argsClass = new Class[] { double.class,
									double.class, long.class };
							double length = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@averagePacket").get(
									counter1));
							double load = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@load").get(counter1));
							double rate = Double.parseDouble(parameters.get(
									"/Generators/Traffic/@dataRate").get(
									counter1));
							double mu = 1.0 / ((length - ops.Packet.HEADER_LENGTH) / (rate / 8.0));
							double muReal = 1.0 / (length / (rate / 8.0));
							double lambda = muReal * load;
							logger.config("Mu: " + mu + " Mu(real):" + muReal
									+ " Lambda: " + lambda);
							aArgs = new Object[] { new Double(mu),
									new Double(lambda), new Long(seed) };
						}
					}
					// Increment the counter
					counter1++;
				} else if (trafficType.equals("Constant")) {
					argsClass = new Class[] { double.class, double.class };
					double serviceRate = Double.parseDouble(parameters.get(
							"/Generators/Traffic/@serviceRate").get(counter2));
					double arrivalRate = Double.parseDouble(parameters.get(
							"/Generators/Traffic/@arrivalRate").get(counter2));
					aArgs = new Object[] { new Double(serviceRate),
							new Double(arrivalRate) };
					// Increment the counter
					counter2++;
				} else if (trafficType.equals("SelfSimilar")) {
					String seed = parameters.get("/Generators/Traffic/@seed")
							.get(counter1 + counter3);
					if ((seed == null) || (seed.equals(""))) {
						argsClass = new Class[] { double.class, double.class,
								double.class, double.class };
						double load = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@load").get(
								counter1 + counter3));
						double shape = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@shape").get(counter3));
						double minLen = Double
								.parseDouble(parameters.get(
										"/Generators/Traffic/@minPacket").get(
										counter3));
						double maxLen = Double
								.parseDouble(parameters.get(
										"/Generators/Traffic/@maxPacket").get(
										counter3));
						double dataRate = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@dataRate").get(counter3));
						double k = minLen / (dataRate / 8.0);
						double p = maxLen / (dataRate / 8.0);
						double alpha = shape;
						double mean = (Math.pow(k, alpha) / (1.0 - Math.pow(k
								/ p, alpha)))
								* (alpha / (alpha - 1.0))
								* ((1.0 / Math.pow(k, alpha - 1.0)) - (1.0 / Math
										.pow(p, alpha - 1.0)));
						aArgs = new Object[] { new Double(load / mean),
								new Double(shape), new Double(k), new Double(p) };
					} else {
						double load = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@load").get(
								counter1 + counter3));
						double shape = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@shape").get(counter3));
						double minLen = Double
								.parseDouble(parameters.get(
										"/Generators/Traffic/@minPacket").get(
										counter3));
						double maxLen = Double
								.parseDouble(parameters.get(
										"/Generators/Traffic/@maxPacket").get(
										counter3));
						double dataRate = Double.parseDouble(parameters.get(
								"/Generators/Traffic/@dataRate").get(counter3));
						double k = minLen / (dataRate / 8.0);
						double p = maxLen / (dataRate / 8.0);
						double alpha = shape;
						double mean = (Math.pow(k, alpha) / (1.0 - Math.pow(k
								/ p, alpha)))
								* (alpha / (alpha - 1.0))
								* ((1.0 / Math.pow(k, alpha - 1.0)) - (1.0 / Math
										.pow(p, alpha - 1.0)));
						aArgs = new Object[] { new Double(load / mean),
								new Double(shape), new Double(k),
								new Double(p), new Long(seed) };
					}
					// Increment the counter
					counter3++;
				}
				Constructor argsConstructor = sTraffic
						.getConstructor(argsClass);
				traffic = (Distribution) argsConstructor.newInstance(aArgs);
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Add the distribution to the vector
			distrib.add(traffic);
			// System.out.println("Class: "+nClass);
			// System.out.println("Mean interarrival time: "+traffic.getMeanInterarrivalTime());
			// System.out.println("Mean service time: "+traffic.getMeanServiceTime());
		}
		return distrib;
	}

	/**
	 * Adds all the traffic generators to the scheduler.
	 * 
	 * @param scheduler
	 *            The event-driven scheduler.
	 */
	@SuppressWarnings("unchecked")
	public void addGenerators(Scheduler scheduler) {
		// Get the distributions
		Vector<Distribution> distrib = getDistribution();
		// Get the traffic Java classes
		Vector<String> classes = parameters.get("/Generators/Traffic/@class");
		Vector<String> starts = parameters.get("/Generators/Traffic/@start");
		int counter1 = 0, counter2 = 0;
		// For each traffic class
		for (String nClass : classes) {
			double startTime = Double.parseDouble(starts.get(counter1));
			Distribution distribution = distrib.get(counter1);
			EventGenerator generator = new EventGenerator(distribution,
					startTime);
			// Create the event subscriber
			EventSubscriber subscriber = null;
			if (nClass.equals("ops.node.NodeTraffic")) {
				try {
					Class[] argsClass = new Class[] { double.class,
							boolean.class };
					double dataRate = Double.parseDouble(parameters.get(
							"/Generators/Traffic/@dataRate").get(counter2));
					boolean header = parameters
							.get("/Generators/Traffic/@type").get(counter2)
							.equals("SelfSimilar");
					Object[] aArgs = new Object[] { dataRate, header };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Increment the counter
				counter2++;
			} else if (nClass.equals("ops.NormalTraffic")
					|| nClass.equals("ops.PriorityTraffic")) {
				try {
					Class[] argsClass = new Class[] { double.class, int.class,
							boolean.class };
					double dataRate = Double.parseDouble(parameters.get(
							"/Generators/Traffic/@dataRate").get(counter2));
					int hopLimit = Integer.parseInt(parameters.get(
							"/OPS/Hop/@limit").firstElement());
					boolean header = parameters
							.get("/Generators/Traffic/@type").get(counter2)
							.equals("SelfSimilar");
					Object[] aArgs = new Object[] { dataRate, hopLimit, header };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Increment the counter
				counter2++;
			} else if (nClass.equals("ops.NormalColoredTraffic")) {
				try {
					Class[] argsClass = new Class[] { double.class, int.class,
							boolean.class, int.class };
					double dataRate = Double.parseDouble(parameters.get(
							"/Generators/Traffic/@dataRate").get(counter2));
					boolean header = parameters
							.get("/Generators/Traffic/@type").get(counter2)
							.equals("SelfSimilar");
					int hopLimit = Integer.parseInt(parameters.get(
							"/OPS/Hop/@limit").firstElement());
					int wavelengths = Integer.parseInt(parameters.get(
							"/OPS/Partial/@wavelengths").firstElement());
					Object[] aArgs = new Object[] { dataRate, hopLimit, header,
							wavelengths };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Increment the counter
				counter2++;
			} else if (nClass.equals("antnet.AntTraffic")
					|| nClass.equals("rwa.crankback.antnetInter.AntTraffic")
					|| nClass
							.equals("rwa.crankback.antnetInter.InterAntTraffic")
					|| nClass.equals("rwa.crankback.antnet.AntTraffic")
					|| nClass.equals("grid.antnet.GridAntTraffic")
					|| nClass.equals("grid.publish.antnet.GridAntTraffic")
					|| nClass.equals("grid.antnet.cpu.CPUGridAntTraffic")) {
				try {
					Class[] argsClass = new Class[] { int.class, int.class,
							long.class };
					int hopLimit = Integer.parseInt(parameters.get(
							"/OPS/Hop/@limit").firstElement());
					int bytesHop = Integer.parseInt(parameters.get(
							"/OPS/Hop/@bytes").firstElement());
					long seedAnt = Long.parseLong(parameters.get(
							"/Ant/Seed/@value").firstElement());
					Object[] aArgs = new Object[] { hopLimit, bytesHop, seedAnt };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// FOI AQUI QUE MUDEI PARA NOVO TRAFEGO
			} else if (nClass.equals("rwa.LightpathTraffic")) {
				try {

					Class[] argsClass = new Class[] { int.class };
					Vector<String> tryVec = parameters
							.get("/RWA/Routing/@alternative");
					if (tryVec == null) // alternate source
						tryVec = parameters.get("/RWA/Routing/@maxAttempts");
					int tries = Integer.parseInt(tryVec.firstElement());
					Object[] aArgs = new Object[] { tries };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// Trafego interdominio
			else if (nClass.equals("rwa.InterLightpathTraffic")) {
				try {
					// System.out.println("PASSEI AQUI");
					Class[] argsClass = new Class[] { int.class };
					Vector<String> tryVec = parameters
							.get("/RWA/Routing/@alternative");
					if (tryVec == null) // alternate source
						tryVec = parameters.get("/RWA/Routing/@maxAttempts");
					int tries = Integer.parseInt(tryVec.firstElement());
					Object[] aArgs = new Object[] { tries };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// Trafego intradomain topologico.
			} else if (nClass.equals("rwa.TopologicalIntraLightpathTraffic")) {
				try {
					// System.out.println("PASSEI AQUI");
					Class[] argsClass = new Class[] { int.class, long.class };
					Vector<String> tryVec = parameters
							.get("/RWA/Routing/@alternative");
					if (tryVec == null) // alternate source
						tryVec = parameters.get("/RWA/Routing/@maxAttempts");
					int tries = Integer.parseInt(tryVec.firstElement());
					long seedIntra = Long.parseLong(parameters.get(
							"/Generators/Traffic/@intraSeed").firstElement());
					// System.out.println(" semente intra para intralightpathtraffic"
					// + seedIntra);
					Object[] aArgs = new Object[] { tries, seedIntra };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (nClass.equals("rwa.AdaptativeIntraLightpathTraffic")) {
				try {
					// System.out.println("PASSEI AQUI");
					Class[] argsClass = new Class[] { int.class, long.class };
					Vector<String> tryVec = parameters
							.get("/RWA/Routing/@alternative");
					if (tryVec == null) // alternate source
						tryVec = parameters.get("/RWA/Routing/@maxAttempts");
					int tries = Integer.parseInt(tryVec.firstElement());
					long seedIntra = Long.parseLong(parameters.get(
							"/Generators/Traffic/@intraSeed").firstElement());
					// System.out.println(" semente intra para intralightpathtraffic"
					// + seedIntra);
					Object[] aArgs = new Object[] { tries, seedIntra };
					Class sSubscriber = Class.forName(nClass);
					Constructor argsConstructor = sSubscriber
							.getConstructor(argsClass);
					subscriber = (EventSubscriber) argsConstructor
							.newInstance(aArgs);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else { // No need for a constructor
				try {
					Class sSubscriber = Class.forName(nClass);
					subscriber = (EventSubscriber) sSubscriber.newInstance();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// Subscribe the traffic part
			generator.subscribe(subscriber);
			scheduler.addGenerator(generator);
			// Increment the counter
			counter1++;
		}

	}

	/**
	 * Gets the class for accounting the simulation results.
	 */
	@SuppressWarnings("unchecked")
	public SimulationAccounting getAccounting() {
		if (this.isFresh()) { // fresh simulation
			Hashtable<String, Vector<String>> parameters = config
					.getSimulationParameters();
			String accountingClass = parameters.get(
					"/Accounting/Accounting/@class").firstElement();
			SimulationAccounting sa = null;
			try {
				Class[] argsClass = new Class[] { Config.class };
				Object[] aArgs = new Object[] { config };
				Class sSimulationAccounting = Class.forName(accountingClass);
				Constructor argsConstructor = sSimulationAccounting
						.getConstructor(argsClass);
				sa = (SimulationAccounting) argsConstructor.newInstance(aArgs);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return sa;
		} else { // loaded simulation
			return sim.accounting;
		}
	}

	/**
	 * Adds failures that are configured for this simulation to the scheduler.
	 * 
	 * @param scheduler
	 *            The event-driven scheduler.
	 */
	public void addFailures(Scheduler scheduler) {
		Vector<String> nodeFailures = parameters
				.get("/Failure/NodeFailure/@node");
		Vector<String> linkFailures = parameters
				.get("/Failure/LinkFailure/@link");
		int nCounter = 0, lCounter = 0;
		if (nodeFailures != null) {
			// for each node failure do
			for (String node : nodeFailures) {
				double time = Double.parseDouble(parameters.get(
						"/Failure/NodeFailure/@time").get(nCounter));
				Event failure = new Event(time, Event.Type.FAILURE_NODE, node);
				scheduler.insertEvent(failure);
				// Increment counter
				nCounter++;
			}
		}
		// for each link failure do
		if (linkFailures != null) {
			for (String link : linkFailures) {
				double time = Double.parseDouble(parameters.get(
						"/Failure/LinkFailure/@time").get(lCounter));
				Event failure = new Event(time, Event.Type.FAILURE_LINK, link);
				scheduler.insertEvent(failure);
				// Increment counter
				lCounter++;
			}
		}
	}

	/**
	 * Adds a event that terminates the simulation.
	 * 
	 * @param scheduler
	 *            The event-driven scheduler.
	 */
	public void addTerminate(Scheduler scheduler) {
		Vector<String> terminate = parameters.get("/Terminate/@time");
		if (terminate != null) {
			double time = Double.parseDouble(terminate.firstElement());
			Vector<String> offset = parameters.get("/Terminate/@offset");
			if (offset != null)
				time = time + Double.parseDouble(offset.firstElement());
			Event event = new Event(time, Event.Type.TERMINATE, null);
			scheduler.insertEvent(event);
		}
	}

	/**
	 * Adds a event that terminates the simulation.
	 * 
	 * @param scheduler
	 *            The event-driven scheduler.
	 */
	public void addSerializers(Scheduler scheduler) {
		Vector<String> serialize = parameters.get("/Serialize/@time");
		int counter = 0;
		if (serialize != null) {
			for (String time : serialize) {
				String name = parameters.get("/Serialize/@name").get(counter);
				Event event = new Event(Double.parseDouble(time),
						Event.Type.SERIALIZE, name);
				scheduler.insertEvent(event);
				counter++; // Increment the counter
			}
		}
	}

}
