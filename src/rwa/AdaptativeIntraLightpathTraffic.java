package rwa;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import main.ControlPlane;

import random.MersenneTwister;
import rwa.crankback.antnetInter.AntNetCrankInterControlPlane;
import rwa.crankback.obgp.OBGPControlPlane;
import distribution.Distribution;
import event.Event;
import event.EventSubscriber;
import event.Event.Type;

/**
 * This class represents the generation of lightpath requests for each node of
 * the network.
 * 
 * Sera utiliza para gerar eventos intradomain
 * 
 * @author Andre Filipe M. Batista
 * @version 1.0
 * 
 */

public class AdaptativeIntraLightpathTraffic implements EventSubscriber {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The distribution for generating the ligthpaths. */
	Distribution distribution;
	/** The maximum number of tries for establishing a lightpath. */
	int maxTries;
	boolean verbose = false;
	protected static MersenneTwister merse;
	long seed = 666; // DEFAULT SEED FOR MERSENNE TWISTER

	public AdaptativeIntraLightpathTraffic(int aMaxTries) {
		// System.out.println("Passei aqui tambem");
		this.maxTries = aMaxTries;
		merse = new MersenneTwister(seed);
	}

	public AdaptativeIntraLightpathTraffic(int aMaxTries, long seed) {
		// System.out.println("Me chamaram com seedintra");
		this.maxTries = aMaxTries;
		this.seed = seed;
		merse = new MersenneTwister(seed);

	}

	@Override
	public Object getContent() {

		int numDomains = AntNetCrankInterControlPlane.getNumberofDomains();
		int size = numDomains;
		double[] probabilityDistribution = new double[size];
		// Normalize the weights
		for (int i = 0; i < size; i++) {
			probabilityDistribution[i] = 1.0 / ((double) size);
		}

		// Spins the wheel
		double sample = merse.nextDouble();
		if (verbose)
			System.out.println("O numero gerado pelo mersenne eh " + sample);
		// Set sum to the first probability
		double sum = probabilityDistribution[0];
		int n = 0;
		while (sum < sample) {
			n = n + 1;
			sum = sum + probabilityDistribution[n];
		}

		if (verbose)
			System.out.println("Temos " + numDomains + "dominios");

		int chooseDomain = n + 1;
		if (verbose)
			System.out.println("o numero gerado foi " + chooseDomain);
		String source = AntNetCrankInterControlPlane.getSourceNode(String
				.valueOf(chooseDomain));
		if (verbose)
			System.out.println("o SOURCE sera " + source);
		String target = AntNetCrankInterControlPlane.getTargetNode(source);
		if (verbose)
			System.out.println("o TARGET sera " + target);
		double duration = distribution.getServiceTime();
		return new LightpathRequest(source, target, duration, maxTries);

	}

	@Override
	public Type getType() {
		return Event.Type.LIGHTPATH_REQUEST;
	}

	@Override
	public void setDistribution(Distribution distrib) {
		this.distribution = distrib;
	}

}
