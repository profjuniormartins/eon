package rwa.crankback.antnetInter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;

import antnet.LocalParametricView;

/**
 * Classe que implementa o modelo paramétrico estatístico do roteamento
 * interdominio
 * 
 * @author Andre Filipe de Moraes Batista
 * 
 */
public class InterStatisticalParametricModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// Mapa contendo os modelos estatisticos parametricos para cada dominio
	// a chave do Mapa eh um dominio
	// o retorno é um outro mapa contendo todos os dominios da rede, e o modelo
	// parametrico entre o dominio chave e o dominio obtido.
	protected static HashMap<String, HashMap<String, LocalParametricView>> interStatisticalParametricView;
	private double exponentialFactor;
	private double reductor;

	public InterStatisticalParametricModel(double exponentialFactor,
			double reductor) {
		interStatisticalParametricView = new HashMap<String, HashMap<String, LocalParametricView>>();
		this.exponentialFactor = exponentialFactor;
		this.reductor = reductor;
	}

	/**
	 * Cria o modelo parametrico estatistico para um determinado dominio
	 * 
	 * @param AS
	 *            o dominio que tera suas tabelas geradas
	 * 
	 * @param ASNumber
	 *            o numero de dominios da simulacao. TODO: melhorar como
	 *            informar este valor.
	 */
	public void create(String AS, int ASNumber) {

		if (!interStatisticalParametricView.containsKey(AS)) {

			// inicia com 1, pois a numeracao dos ASs eh iniciada em 1
			HashMap<String, LocalParametricView> ASModel = new HashMap<String, LocalParametricView>();
			for (int i = 1; i <= ASNumber; i++) {
				if (!(Integer.parseInt(AS) == i)) {
					LocalParametricView localView = new LocalParametricView(
							exponentialFactor, reductor);
					ASModel.put("" + i + "", localView);
				}
			}

			interStatisticalParametricView.put(AS, ASModel);
		}

	}

	/**
	 * Gets the local parametric view associated to the specified destination.
	 * 
	 * @param source
	 *            The id of the actual AS
	 * @param destination
	 *            The id of the destination AS
	 * @return The local parametric view associated to the specified
	 *         destination.
	 */
	public static LocalParametricView get(String source, String destination) {
		HashMap<String, LocalParametricView> ASModel = interStatisticalParametricView
				.get(source);
		return ASModel.get(destination);
	}

	/**
	 * Update the local model associated with the specified node.
	 * 
	 * @param metric
	 *            The value of the metric.
	 * @param nodeId
	 *            The specified node.
	 */
	public static void update(double metric, String sourceAS,
			String destinationAS) {
		HashMap<String, LocalParametricView> ASModel = interStatisticalParametricView
				.get(sourceAS);
		LocalParametricView view = ASModel.get(destinationAS);
		view.update(metric);
	}

	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Source AS: ");
		for (String SourceAS : interStatisticalParametricView.keySet()) {
			builder.append(SourceAS);
			builder.append("\n");

			for (String destinationAS : interStatisticalParametricView.get(
					SourceAS).keySet()) {
				builder.append("Destination: " + destinationAS);
				builder.append(" - ");
				LocalParametricView view = interStatisticalParametricView.get(
						SourceAS).get(destinationAS);
				builder.append(view.toString());
				builder.append("\n");

			}

		}
		return builder.toString();
	}

}
