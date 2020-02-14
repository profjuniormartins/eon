/**
 * AntNetCrankInterRoutingTable
 * Tabela de roteamento por ferormonio interdominio
 * @author andre filipe de m. batista
 * @version 1.0
 * 
 */

package rwa.crankback.antnetInter;

import graph.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import main.Link;

import antnet.AntPacket;
import antnet.LocalParametricView;
import antnet.NeighborAttr;
import antnet.PheromoneRoutingTable;
import antnet.StatisticalParametricModel;
import random.MersenneTwister;
import rwa.LinkState;
import rwa.crankback.antnet.AntNetCrankControlPlane;
import rwa.crankback.obgp.OBGPControlPlane;
//import sun.org.mozilla.javascript.internal.Interpreter;
//import sun.reflect.ReflectionFactory.GetReflectionFactoryAction;

/**
 * This class represents an antnet crank routing table. All nodes, from all
 * domain, will have access to it in order to process an interdomain path
 * request and update the Pheromone level
 * 
 * @author Andre Filipe de Moraes Batista
 * 
 */
public class AntNetCrankInterRoutingTable extends PheromoneRoutingTable {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** The power factor to enhance the difference in the heuristics correction. */
	protected static double powerFactor;
	/**
	 * Indicates if the routing of the RSVP messages are deterministic or
	 * stochastic. True means deterministic.
	 */
	protected boolean deterministic;
	protected boolean verbose = false;

	protected static HashMap<String, HashMap<String, ArrayList<Vector<String>>>> interRoutes;

	static double c1, c2, amplifier;
	static int asNumber;
	static MersenneTwister merse = new MersenneTwister();

	// static Random random = new Random();
	/** The z-factor derived from the confidence coefficient level. */

	// protected static Vector<LinkedHashMap<String, Vector<String>>> teste =
	// new Vector<LinkedHashMap<String, Vector<String>>>();

	/**
	 * Ideia da CrankRoutes
	 * 
	 * Sera uma nova tabela de roteamento que faz uso do crackback. A mesma
	 * usara a mesma string de identificacao para reconhecer o par SD E com
	 * isto, ira devolver um arraylist contendo todos os K possiveis caminhos
	 * interdominio
	 * 
	 * Para que esta possa funcionar corretamente, tenho que implementar
	 * multiplos caminhos interdominio. Logo, tenho que colocar esta informacao
	 * no XML e gerar novamente esta tabela de roteamento NAO USO MAIS
	 * CRANKROUTES. ELIMINAR DEPOIS.
	 */
	protected static LinkedHashMap<String, ArrayList<Vector<String>>> crankRoutes;

	/**
	 * The interPheromone table The first parameter represents the key
	 * "SourceDomain-DestinationDomain" The second parameter give us the
	 * neighborattr
	 */
	protected static LinkedHashMap<String, ArrayList<NeighborAttr>> interPheromone;

	public AntNetCrankInterRoutingTable(String source, double aConfidence,
			double firstWeight, double secondWeight, double aAmplifier,
			double aAlpha, double factor, boolean aDeterministic,
			LinkedHashMap<String, ArrayList<Vector<String>>> route, int asNumber) {
		super(source, aConfidence, firstWeight, secondWeight, aAmplifier,
				aAlpha);
		this.crankRoutes = route;
		this.powerFactor = factor;
		this.deterministic = aDeterministic;
		interPheromone = new LinkedHashMap<String, ArrayList<NeighborAttr>>();
		this.c1 = super.c1;
		this.c2 = super.c2;
		this.amplifier = super.amplifier;
		this.interRoutes = new HashMap<String, HashMap<String, ArrayList<Vector<String>>>>();
		this.asNumber = asNumber;

	}

	/**
	 * insere uma entrada para um AS informando na tabela de roteamento por
	 * ferormonio interdominio para este AS e inicializa os possiveis destinos
	 * (todos os demais ASes informados, exceto o proprio)
	 * 
	 * @param AS
	 *            uma string que represente o AS na topologia da simulacao
	 * @param asNumber
	 *            o numero de ASes vizinhos para inicializacao da tabela
	 */
	public void putASEntry(String AS) {
		HashMap<String, ArrayList<Vector<String>>> routeInfo = new HashMap<String, ArrayList<Vector<String>>>();
		// comeca com 1, pois os dominios sao numerados a partir de 01
		for (int i = 1; i <= asNumber; i++) {
			if (!String.valueOf(i).equals(AS)) {
				ArrayList teste = new ArrayList();
				routeInfo.put("" + i + "", teste);
			}
		}
		this.interRoutes.put(AS, routeInfo);
	}

	public void setDestNeighPair(String AS, String dest, Vector<String> neigh) {
		ArrayList lista = interRoutes.get(AS).get(dest);
		lista.add(neigh);
	}

	public void putPheromoneEntry(String key, NeighborAttr pheromoneLevel) {
		if (interPheromone.get(key) == null) {
			ArrayList entry = new ArrayList();
			interPheromone.put(key, entry);
		}

		ArrayList entry = interPheromone.get(key);
		entry.add(pheromoneLevel);
		// interPheromone.put(key, pheromoneLevel);
	}

	/*
	 * public static void putEntry(String key, ArrayList<Vector<String>> paths)
	 * {
	 * 
	 * crankRoutes.put(key, paths); System.out.println("Adicionando" + key +
	 * paths.toString()); }
	 */

	/*
	 * public void removeEntry(String source, String dest) { String key = source
	 * + "-" + dest; crankRoutes.remove(key); }
	 */
	public ArrayList<Vector<String>> getEntry(String key) {
		return crankRoutes.get(key);
	}

	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (String key : crankRoutes.keySet()) {
			buffer.append(key + ":");
			buffer.append("\n");
			buffer.append(crankRoutes.get(key).toString() + "\n");
		}

		return buffer.toString();
	}

	/**
	 * Obtem o proximo Hop (AS) em uma requisicao interdominio. Com base no
	 * nivel de ferormonio da tabela de roteamento inter o ASBR de ingresso do
	 * dominio Source, ira determinar qual sera o ASBR de egresso, escolhendo um
	 * link entre o ASBR de egresso e o proximo AS.
	 * 
	 * @param source
	 *            o AS que esta processando a requisicao, que ira realizar a
	 *            expansao do caminho (tecnica per-domain path computation)
	 * @param target
	 *            o AS de destino, normalmente o AS de destino da requisicao.
	 * @param selected
	 *            qual vizinho sera escolhido para ser o proximo AS da
	 *            requisicao. Este eh resultado do método getNextRoute.
	 * @return uma string contendo o ID do nó que ira representar o ASBR de
	 *         egresso do dominio atual.
	 */
	public static String getInterNextHop(String source, String target,
			int selected, Vector<String> historyTable) {

		String Source = source.split(":")[0];
		String Target = target.split(":")[0];

		// System.out.println(interRoutes.get(Source).get(Target).toString());

		// int selected = getNextRoute(Source,Target) ;

		// System.out
		// .println("um possivel indice para a rota determinada por ferormonio eh "
		// + selected);
		// System.out
		// .println(interPheromone.get(Source + "-" + Target).toString());
		// System.out.println("Entao, o nextHop por formiga eh "
		// + interRoutes.get(Source).get(Target).get(selected)
		// .firstElement());

		String nextHop = interRoutes.get(Source).get(Target).get(selected)
				.firstElement();
		if (nextHop.equals(source)) {
			nextHop = interRoutes.get(Source).get(Target).get(selected)
					.lastElement();
		}

		return nextHop;

		// return null;
	}

	public static String getNextHop(String source, String target, int trying) {
		String Source = source;
		if (source.contains(":")) {
			source = source.split(":")[0];
		}
		if (target.contains(":")) {
			target = target.split(":")[0];
		}
		String key = source + "-" + target;
		// System.out.println("Me pediram o nexthop, estou devolvendo: " +
		// crankRoutes.get(key).get(trying).firstElement());
		// String probnextHop = interRoutes.get(source).get(target).get(arg0)
		String nextHop = crankRoutes.get(key).get(trying).firstElement();
		if (nextHop.equals(Source)) {
			nextHop = crankRoutes.get(key).get(trying).get(1); // o elemento na
																// posicao 1 eh
																// sempre o
																// segundo
																// elemento da
																// rota
		}
		return nextHop;
	}

	public static Vector<String> getASPath(String source, String target,
			int route) {
		if (source.contains(":")) {
			source = source.split(":")[0];
		}
		if (target.contains(":")) {
			target = target.split(":")[0];
		}
		String key = source + "-" + target;
		// System.out.println("Retornando = "
		// + interRoutes.get(source).get(target).get(route));
		return interRoutes.get(source).get(target).get(route);
	}

	public static Vector<String> getEntireASPath(String source, String target,
			int route) {
		if (source.contains(":")) {
			source = source.split(":")[0];
		}
		if (target.contains(":")) {
			target = target.split(":")[0];
		}
		String key = source + "-" + target;
		return crankRoutes.get(key).get(route);
	}

	/*
	 * public String printPherormones() {
	 * 
	 * StringBuilder buffer = new StringBuilder(); for (String key :
	 * crankRoutes.keySet()) { buffer.append(key + ":"); buffer.append("\n"); //
	 * buffer.append(crankRoutes.get(key).toString()+"\n"); for (int i = 0; i <
	 * crankRoutes.get(key).size(); i++) {
	 * buffer.append(crankRoutes.get(key).get(i) + " " +
	 * interPheromone.get(key).get(i) + "\n"); } //
	 * buffer.append(interPheromone.get(key).toString() + "\n") ; } return
	 * buffer.toString(); }
	 */

	public static String printPherormones() {

		StringBuilder buffer = new StringBuilder();
		for (String source : interRoutes.keySet()) {
			String chave = "";
			// key eh o dominio de origem
			// agora eu tenho que pegar o dominio de destino
			for (String dest : interRoutes.get(source).keySet()) {
				chave = source + "-" + dest;
				buffer.append("\n" + chave + ":");
				buffer.append("\n");
				for (int i = 0; i < interRoutes.get(source).get(dest).size(); i++) {
					buffer.append("\nRota: "
							+ interRoutes.get(source).get(dest).get(i)
							+ "\n "
							+ "Nivel de Ferormonio: "
							+ interPheromone.get(chave).get(i)
									.getPheromoneLevel());
				}
			}

		}
		return buffer.toString();
	}

	
	
	
	public static  void updateAfterLinkFailure(String sourceEdge,
			String destinationEdge) {
		
		//nivel de feromonio para distribuir
		double levelToDistribute = 0.0;
		String sourceDomain = AntNetCrankInterControlPlane
				.getDomain(sourceEdge);
		String destinationDomain = AntNetCrankInterControlPlane
				.getDomain(destinationEdge);
		ArrayList<Vector<String>> rotas = interRoutes.get(sourceDomain).get(destinationDomain);
		for(int i = 0; i < rotas.size();i++) {
			if(rotas.get(i).firstElement().equals(sourceEdge) && rotas.get(i).lastElement().equals(destinationEdge)) {
				levelToDistribute = interPheromone.get(sourceDomain+"-"+destinationDomain).get(i).getPheromoneLevel();
				rotas.remove(i);
				interPheromone.get((sourceDomain+"-"+destinationDomain)).remove(i);
				
			}
		}
		
		
		//agora redistribui o nivel entre as demais rotas
		
				for(int i = 0; i < rotas.size();i++) {
					double oldLevel = interPheromone.get(sourceDomain+"-"+destinationDomain).get(i).getPheromoneLevel();
					interPheromone.get(sourceDomain+"-"+destinationDomain).get(i).setPheromoneLevel(oldLevel+(levelToDistribute/rotas.size()));
				}
		
		
		//verifica todas as possiveis rotas interdominio que utilizam o link como utilizacao
		for(String possibleDestination : interRoutes.get(sourceDomain).keySet()) {
			ArrayList<Vector<String>> Rotas = interRoutes.get(sourceDomain).get(possibleDestination); 
			for(int i =0; i < Rotas.size(); i++) {
				if(Rotas.get(i).firstElement().equals(sourceEdge) && Rotas.get(i).lastElement().equals(destinationEdge)) {
					//TO DO: preciso distribuir este nivel de ferorominio entre as demais
					levelToDistribute = interPheromone.get(sourceDomain+"-"+possibleDestination).get(i).getPheromoneLevel();
					Rotas.remove(i);
					interPheromone.get((sourceDomain+"-"+possibleDestination)).remove(i);
					
					for(int j = 0; j < Rotas.size(); j++) {
						double OldLevel = interPheromone.get(sourceDomain+"-"+possibleDestination).get(j).getPheromoneLevel();
						interPheromone.get(sourceDomain+"-"+possibleDestination).get(j).setPheromoneLevel(OldLevel+(levelToDistribute/Rotas.size()));
					}
					
					
				}
			}
		}
	
		
		

	}

	/**
	 * Define o nivel de ferormonio inicial a partir da topologia interdominio
	 * 
	 */
	public void generateInitialPheromoneLevel() {

		for (String AS : interRoutes.keySet()) {
			for (String dest : interRoutes.keySet()) {
				if (!AS.equals(dest)) {
					// System.out.println("Possivel destino de  " + AS +
					// " para "
					// + dest);
					// System.out.println(interdomainRoutes.getEntry(AS + "-"
					// + dest));

					for (int i = 0; i < getEntry(AS + "-" + dest).size(); i++) {
						Vector<String> path = new Vector<String>();
						path.add(getEntireASPath(AS, dest, i).get(0));
						path.add(getEntireASPath(AS, dest, i).get(1));
						// System.out.println("SUB CAMINHO " + path.toString());
						setDestNeighPair(AS, dest, path);
					}

				}

			}
			// System.out
			// .println("ROTAS INICIAIS PARA INTERDOMINIO COM BASE NA POLITICA - AS "
			// + AS);
			// System.out.println(interRoutes.get(AS).toString());

		}

		for (String source : interRoutes.keySet()) {
			// System.out.println("Obtendo nivel de ferormonio para " + source);
			for (String dest : interRoutes.get(source).keySet()) {
				String chave = source + "-" + dest;
				// System.out.println("\n... destino = " + dest);
				int numVizinhos = interRoutes.get(source).get(dest).size();

				// System.out.println("Foram encontradas " + numVizinhos
				// + " para este destino");
				ArrayList<Vector<String>> rotas = interRoutes.get(source).get(
						dest);

				double soma = 0.0;
				double value[] = new double[numVizinhos];
				double pherormone[] = new double[numVizinhos];
				// verifica se a rota (par ASBR-origem -- ASBR-destino) possui
				// o ASBR-destino pertencendo ao dominio destino

				for (int i = 0; i < numVizinhos; i++) {
					Vector<String> route = rotas.get(i);

					if (AntNetCrankInterControlPlane.getDomain(route.get(1))
							.equals(dest)) {
						value[i] = 2;
						soma += 2; // ou soma +=numVizinhos;
					} else {
						value[i] = 3;
						soma += 3;
					}

				}
				// for (int i = 0; i < value.length; i++)
				// System.out.print(value[i] + " ");
				// System.out.println("\n Sum = " + soma);
				double fator = soma / numVizinhos;
				// System.out.println("Fator eh " + fator);
				soma = 0.0;
				for (int j = 0; j < value.length; j++) {
					value[j] = fator / value[j];
					soma += value[j];
				}

				for (int j = 0; j < value.length; j++) {
					pherormone[j] = value[j] / soma;
					NeighborAttr neighAttr = new NeighborAttr(chave);
					neighAttr.setPheromoneLevel(pherormone[j]);
					putPheromoneEntry(chave, neighAttr);
				}

				// for (int i = 0; i < pherormone.length; i++)
				// System.out.print(pherormone[i] + " ");

			}

		}

	}

	/**
	 * Define o nivel de ferormonio inicial a partir da topologia interdominio
	 * 
	 * @param ASPath
	 *            um mapa contendo toda a topologia e rotas iniciais (n rotas)
	 * @param antRouteTable
	 *            a tabela de roteamento por ferormonio que recebera os niveis
	 *            iniciais
	 */
	/*
	 * public void updateInterPheromonefromTopology( LinkedHashMap<String,
	 * ArrayList<Vector<String>>> ASPath) {
	 * 
	 * // para cada par dominio_Origem-dominio_Destino na interdomain routing //
	 * table for (String chave : ASPath.keySet()) { //
	 * System.err.println("definindo niveis de ferormonio para entrada: " // +
	 * chave); // obtem o numero de rotas int size = ASPath.get(chave).size();
	 * // para cada vizinhos irei armazenar o numero de hops que ele passa //
	 * (hops = dominios - ASPATH) double sum[] = new double[size]; // preciso
	 * descobrir o numero de hops, entao este laco faz isto. for (int i = 0; i <
	 * ASPath.get(chave).size(); i++) { // obtem a rota Vector<String> rota =
	 * ASPath.get(chave).get(i); if (verbose) System.out.println("Rota: " +
	 * rota.toString()); sum[i] = 0; for (int j = 0; j < rota.size() - 1; j++) {
	 * // verifica se eh um link interdominio if
	 * (!AntNetCrankInterControlPlane.getDomain(rota.get((j)))
	 * .equals(AntNetCrankInterControlPlane.getDomain(rota .get(j + 1)))) { //
	 * System.out.println("eh um link interdominio"); sum[i]++; } } //
	 * System.out.println("Sum na posicao " + i + " recebera " + // sum[i]); }
	 * // calculando o nivel de ferormonio inicial de cada possivel rota //
	 * interdominio double soma = 0.0; // size eh o tamanho de todas as
	 * possiveis rotas for (int c = 0; c < size; c++) {
	 * 
	 * // System.out.println(sum[c]); soma = soma + sum[c]; } // soma ira
	 * armazenar o numero total de hops // System.out.println("o total eh " +
	 * soma); double fator = soma / size; // fator = soma / numero de elementos
	 * if (verbose) System.out.println("O fator eh " + fator); // newsum
	 * apresenta o valor "normalizado" pelo fator double newsum[] = new
	 * double[size]; // somatoria eh utilizada para calcular o nivel de
	 * ferormonio double newSomatoria = 0.0; for (int c = 0; c < size; c++) {
	 * newsum[c] = fator / sum[c]; newSomatoria = newSomatoria + newsum[c]; } if
	 * (verbose) System.out.println("NewSomatoria = " + newSomatoria); //
	 * calcula o nivel de ferormonio para cada rota // size = tamanho da rota
	 * for (int m = 0; m < size; m++) { if (verbose) {
	 * System.out.println("newSomatoria eh " + newSomatoria);
	 * System.out.println("newsum[" + m + "] eh " + newsum[m]); } // ou level =
	 * (newsum[m] / newSomatoria) // deixei assim para arredondar melhor double
	 * level = (1 / newSomatoria) * newsum[m]; // cria netighborAttr
	 * NeighborAttr neighAttr = new NeighborAttr(chave); // define o nivel de
	 * ferormonio neighAttr.setPheromoneLevel(level); // inclui o nivel de
	 * ferormonio para a chave // se existem N rotas, o nivel de ferormonio da
	 * rota I estara na // posicao I-1 do arrayList da entrada // Por exemplo, a
	 * rota 1 possui nivel de ferormonio 0.25. Entao, // este estara armazenado
	 * na posicao 0 do arraylist // de ferormonios // P.S: estou considerando
	 * que contamos rotas como 1 e o // arraylist inicia-se como 0.
	 * 
	 * putPheromoneEntry(chave, neighAttr);
	 * 
	 * if (verbose) System.out.println("A rota " +
	 * ASPath.get(chave).get(m).toString() +
	 * " recebera o nivel de ferormonio igual a " + level);
	 * 
	 * }
	 * 
	 * }
	 * 
	 * }
	 */

	/**
	 * Obtem o nextHop de uma formiga interdominio. Sempre é retornado o ASBR
	 * egresso do dominio. Caso o nextHop obtido seja igual ao nó de origem,
	 * isto indica que este é um ASBR de egresso. Logo, é preciso pegar proximo
	 * elemento da lista, após este elemento, pois assim este sera o ASBR de
	 * ingresso do outro dominio.
	 * 
	 * 
	 * @param source
	 *            o dominio de origem
	 * @param target
	 *            o dominio de destino
	 * @param trying
	 *            a k rota a ser escolhida, obtido pelo valor do ferormonio,
	 *            pelo metodo getNextRoute
	 * @return o NextHop da formiga inter.
	 */
	public static String getAntNextHop(String source, String target, int trying) {

		String nextHop = interRoutes
				.get(AntNetCrankInterControlPlane.getDomain(source))
				.get(AntNetCrankInterControlPlane.getDomain(target))
				.get(trying).get(0);
		// String nextHop = crankRoutes
		// .get(AntNetCrankInterControlPlane.getDomain(source) + "-"
		// + AntNetCrankInterControlPlane.getDomain(target))
		// .get(trying).get(0);
		// System.out.println(" Retornando:  " + source + " nexthop: "
		// + nextHop);

		if (nextHop.equals(source)) {

			nextHop = interRoutes
					.get(AntNetCrankInterControlPlane.getDomain(source))
					.get(AntNetCrankInterControlPlane.getDomain(target))
					.get(trying).get(1);
			// nextHop = crankRoutes
			// .get(AntNetCrankInterControlPlane.getDomain(source) + "-"
			// + AntNetCrankInterControlPlane.getDomain(target))
			// .get(trying).get(1);
			// System.out.println(" * Retornando:  " + source + " nexthop: "
			// + nextHop);

		}
		return nextHop;

	}

	/**
	 * ESCOLHA DE ROTA INTERDOMINIO PARA PACOTES RSVP. Escolhe a rota
	 * interdominio a ser utilizada por um pacote RSVP, por meio do nivel de
	 * ferormônio existente nos links. Em principio, ele é uma roleta
	 * computacional (nao viciada)
	 * 
	 * @param source
	 *            o dominio de origem
	 * @param target
	 *            o dominio de destino
	 * @return o indice para o mapa de possiveis rotas entre origem-destino
	 */
	public static int getNextRoute(String source, String target,
			Vector<String> historyTable, Path caminho) {

		// gera um valor aleatorio, para uso da roleta computacional
		Double value = merse.nextDouble();
		// System.out.println("Valor double gerado = " + value);
		// variavel que ira armazenar o valor da soma a ser feita, conforme o
		// giro da roleta computacional
		Double sum = 0.0;
		// hashmap contendo o nivel de ferormonio de cada rota
		HashMap<String, Double> pherormones = new HashMap<String, Double>();
		// a rota a ser devolvida
		String route = null;

		/*
		 * ------------- Nao utilizado mais ------------------- // classe que
		 * implementa como se compara os niveis de cada rota // ValueComparator
		 * doubleComparator = new ValueComparator(pherormones); // treemap - uma
		 * especie de hashmap, porem que ordena durante a insercao //
		 * TreeMap<String, Double> sortedPherormones = new TreeMap<String, //
		 * Double>( // doubleComparator);
		 */

		// obtem todas as possiveis rotas entre o dominio Source -- Destination.
		ArrayList<NeighborAttr> rotas = interPheromone
				.get(AntNetCrankInterControlPlane.getDomain(source) + "-"
						+ AntNetCrankInterControlPlane.getDomain(target));

		ArrayList<NeighborAttr> rotas2 = (ArrayList<NeighborAttr>) rotas
				.clone();

		// POR ENQUANTO FUNCIONA PARA CRANKBACK EM ENLACES INTERDOMINIO.
		// FAZ TUDO NO VETOR DE ROTAS, ROTAS2, QUE EH FAKE
		// SOH PARA TESTAR SE CRANKBACK FUNCIONA BEM
		// preciso eliminar todas as rotas que possuem os que estao na tabela de
		// historico.
		// eliminar significa "zerar" o nivel de ferormonio por esta trilha.

		if (historyTable != null && !historyTable.isEmpty()) {
			Double distribuirFeromonio = 0.0;
			Double quantidadedeVizinhosBons = (double) rotas.size();
			for (int i = 0; i < historyTable.size(); i++) {
				String hopToEliminate = historyTable.get(i);
				for (int j = 0; j < rotas.size(); j++) {
					// System.out
					// .println(interRoutes.get(
					// AntNetCrankInterControlPlane
					// .getDomain(source)).get(
					// AntNetCrankInterControlPlane
					// .getDomain(target)));
					if (interRoutes
							.get(AntNetCrankInterControlPlane.getDomain(source))
							.get(AntNetCrankInterControlPlane.getDomain(target))
							.get(j).contains(hopToEliminate)) {
						distribuirFeromonio += rotas2.get(j)
								.getPheromoneLevel();
						rotas2.get(j).setPheromoneLevel(0.0);
						quantidadedeVizinhosBons--;
					}

				}
			}

			// agora distribuir o nivel de ferorominio removido entre os demais.
			for (int i = 0; i < historyTable.size(); i++) {
				String hopToEliminate = historyTable.get(i);
				for (int j = 0; j < rotas.size(); j++) {
					// System.out
					// .println(interRoutes.get(
					// AntNetCrankInterControlPlane
					// .getDomain(source)).get(
					// AntNetCrankInterControlPlane
					// .getDomain(target)));
					if (!(interRoutes
							.get(AntNetCrankInterControlPlane.getDomain(source))
							.get(AntNetCrankInterControlPlane.getDomain(target))
							.get(j).contains(hopToEliminate))) {
						Double incremento = distribuirFeromonio
								/ quantidadedeVizinhosBons;
						rotas2.get(j).setPheromoneLevel(
								rotas2.get(j).getPheromoneLevel() + incremento);

					}

				}
			}

		}

		for (String node : caminho.nodes()) {
			for (int j = 0; j < rotas.size(); j++) {
				Vector<String> possibleRoute = interRoutes
						.get(AntNetCrankInterControlPlane.getDomain(source))
						.get(AntNetCrankInterControlPlane.getDomain(target))
						.get(j);
				if (possibleRoute.contains(node)) {
					rotas2.get(j).setPheromoneLevel(0.0);
				}
			}
		}

		// System.out.println("FERORMONIO SEM ORDEM " + rotas.toString());
		// insere no mapa de ferormonio todos os valores de cada possivel rota
		// preenche mapa de ferormonnios
		// MUDAR DE ROTAS2 PARA ROTAs.
		for (int i = 0; i < rotas.size(); i++) {
			// pherormones.put("" + i + "", rotas.get(i).getPheromoneLevel());
			pherormones.put("" + i + "", rotas2.get(i).getPheromoneLevel());
		}

		// sortedPherormones.putAll(pherormones);
		// System.out.println("FERORMONIO ORDENADOS "
		// + sortedPherormones.toString());

		/*
		 * Boolean hasProblem = false;
		 * 
		 * if (sortedPherormones.size() != pherormones.size()) { hasProblem =
		 * true; }
		 */

		// System.out.println("Ordenados!");
		@SuppressWarnings("unchecked")
		// ordena o nivel dos ferormonio em ordem crescente, para uso da roleta
		// computacional
		Map<String, Double> sortedPherormonesLevel = util.MapSortter
				.sortByComparator(pherormones);

		/*
		 * for (Map.Entry entry : sortedMap.entrySet()) {
		 * System.out.println("Key : " + entry.getKey() + " Value : " +
		 * entry.getValue()); } System.out.println("TUDO ORDENADO!:" +
		 * sortedMap);
		 * 
		 * /* BUG BUG BUG BUG Havia um problema no ordenamento no mapa de
		 * ferormonios quando existem 2 entradas com 50% cada ele remove uma
		 * delas. Solucao temporaria: identificar quando o tamanho do vetor
		 * ordenado eh diferente do original e usar o original Solucao
		 * definitiva (feita): buscar uma forma melhor e mais recomendada para
		 * ordenar um hashmap pelo valor de seus elementos, mantendo a chave.
		 * 
		 * if (hasProblem) { for (String key : pherormones.keySet()) { route =
		 * key; sum = sum + pherormones.get(key); if (sum >= value) break; } }
		 * else {
		 */

		// aqui acontece o giro da roleta
		for (String key : sortedPherormonesLevel.keySet()) {
			// System.out.println("A KEY eh " + key);
			// System.out.println(" O valor nesta key eh " +
			// sortedPherormones.get(key));
			// if(sortedPherormones.get(key)==null) {
			// System.out.println(sortedPherormones.get("2"));
			// System.out.println("Pediu para parar, parou!");
			// }
			route = key;
			// System.out.println("KEY= " + sortedMap.get(key));
			sum = sum + sortedPherormonesLevel.get(key);
			if (sum >= value)
				break;
		}

		// } //PARA BUG (se precisar, descomentar)
		// while (sum < value && rotas.get(contador + 1) != null) {
		// contador++;

		// sum = sum + rotas.get(contador).getPheromoneLevel();
		// }

		// System.out.println("A rota que sera selecionada eh " + route);
		return Integer.parseInt(route);
	}

	/***
	 * ESCOLHA DE ROTAS INTERDOMINIO PARA FORMIGAS Obtem o indice para a rota
	 * interdominio a ser percorrido por uma formiga
	 * 
	 * @param ant
	 * @param source
	 * @param target
	 * @param links
	 * @return
	 */
	public static int getNextRoute(Ant ant, String source, String target,
			LinkedHashMap<String, LinkState> links) {
		// TODO: usar links!

		/**
		 * ARRUMAR
		 * 
		 * Ver como eh o metodo select do AntNetCrankControlPlane, para usar o
		 * nivel de links e ver se evitamos link.
		 */

		double totalFreeWavelengths = 0.0; // Total number of free wavelengths
		double totalPheromoneLevel = 0.0;

		// gera um valor aleatorio, para uso da roleta computacional
		// Double value = merse.nextDouble();
		// System.out.println("Valor double gerado = " + value);
		// variavel que ira armazenar o valor da soma a ser feita, conforme o
		// giro da roleta computacional
		// Double sum = 0.0;

		// hashmap contendo o nivel de ferormonio de cada rota
		// HashMap<String, Double> pherormones = new HashMap<String, Double>();
		// a rota a ser devolvida
		// String route = null;

		/*
		 * ------------- Nao utilizado mais ------------------- // classe que
		 * implementa como se compara os niveis de cada rota // ValueComparator
		 * doubleComparator = new ValueComparator(pherormones); // treemap - uma
		 * especie de hashmap, porem que ordena durante a insercao //
		 * TreeMap<String, Double> sortedPherormones = new TreeMap<String, //
		 * Double>( // doubleComparator);
		 */

		// obtem todas as possiveis rotas entre o dominio Source -- Destination.
		ArrayList<NeighborAttr> rotas = interPheromone
				.get(AntNetCrankInterControlPlane.getDomain(source) + "-"
						+ AntNetCrankInterControlPlane.getDomain(target));

		Vector<String> availableNeighbors = new Vector<String>();

		// obtem as possiveis enlaces interdominio a ser percorrido pela formiga
		ArrayList<Vector<String>> neigh = interRoutes.get(
				AntNetCrankInterControlPlane.getDomain(source)).get(
				AntNetCrankInterControlPlane.getDomain(target));

		// Preciso somar todos os comprimentos de onda disponiveis para cada uma
		// das possiveis rotas:
		LinkState nextLink = null;

		for (int d = 0; d < neigh.size(); d++) {

			// String node = neigh.get(d).firstElement();

			String elemento;
			if (neigh.get(d).firstElement().equals(source)) {
				elemento = neigh.get(d).lastElement();
			} else {
				elemento = neigh.get(d).firstElement();
			}
			if (!ant.isTabu(elemento)) {
				availableNeighbors.add(elemento);
			}

			// SUGESTAO: PRECISO VER COMO DETECTAR LOOPS E JA ELIMINAR DAQUI,
			// porem o AntNetLSR ja resolve isto.

			// System.out.println(AntNetCrankInterControlPlane.antNetLSRLink.get(neigh.get(d).firstElement()));

			// obtem o link entre uma das possiveis rotas (que eh um link
			// interdominio entre o dominio atual e o proximo dominio)

			nextLink = AntNetCrankInterControlPlane.antNetLSRLink.get(
					neigh.get(d).firstElement())
					.get(neigh.get(d).lastElement());
			// AntNetCrankInterRoutingTable. Link link =
			// AntNetCrankInterControlPlane.links.get(neigh.get(d).firstElement()+"-"+neigh.get(d).lastElement());
			// para calcular o numero de comprimentos de onda livre
			double free;

			// System.out.println("d= " + d + "\n neigh" + neigh.get(d));
			// Get the total number of free wavelengths
			// System.out.println("elem = " + neigh.get(d).firstElement());

			free = (double) nextLink.getMask().freeWavelengths();

			totalFreeWavelengths = totalFreeWavelengths
					+ Math.pow(free, powerFactor);
			// And the total pheromone level
			totalPheromoneLevel = totalPheromoneLevel
					+ rotas.get(d).getPheromoneLevel();
		}

		double[] probabilityDistribution = new double[neigh.size()]; // Probability
																		// distribution
		for (int k = 0; k < neigh.size(); k++) {
			nextLink = AntNetCrankInterControlPlane.antNetLSRLink.get(
					neigh.get(k).firstElement())
					.get(neigh.get(k).lastElement());
			// Now calculate the probability
			double freeLambdas = nextLink.getMask().freeWavelengths();
			probabilityDistribution[k] = ((rotas.get(k).getPheromoneLevel() / totalPheromoneLevel) + alpha
					* (Math.pow(freeLambdas, powerFactor) / totalFreeWavelengths))
					/ (1.0 + alpha);

		}
		// Spins the wheel
		double sample = merse.nextDouble();
		// Set sum to the first probability
		double sume = probabilityDistribution[0];
		int n = 0;
		while (sume < sample) {
			n = n + 1;
			sume = sume + probabilityDistribution[n];
		}

	
		return n;
		// return n;
	}

	/**
	 * Atualiza a tabela de roteamento interdominio, por ferormonio
	 * 
	 * @param ant
	 *            a formiga backward para atualizacao
	 */
	public static void update(Ant ant) {
		boolean verbose = false;

		// Gets the ant target
		String target = ant.getTarget();

		// Gets the processing node
		String procId = ant.getNode();

		/*
		 * ROTINA PARA OBTER NOS QUE IRAO COMPOR O CAMINHO PARA ATUALIZACAO DA
		 * TABELA DE FERORMONIO
		 */

		// System.out.println("SUBCAMINHO: " + ant.getSubPath());
		Path tempCaminho = ant.getPath();
		Path tempNewCaminho = new Path();
		String Dominio = "";
		// gera um caminho contendo apenas os ASs que estao foram atravessados
		// pela formiga.

		for (int i = 0; i < tempCaminho.size(); i++) {

			if (tempNewCaminho.size() == 0) {
				// entao eh o primeiro elemento, sempre adiciono
				tempNewCaminho.addNode(tempCaminho.getNode(i).split(":")[0]);
				Dominio = AntNetCrankInterControlPlane.getDomain(tempCaminho
						.getNode(i));
			}

			if (!AntNetCrankInterControlPlane.getDomain(tempCaminho.getNode(i))
					.equals(Dominio)) {
				tempNewCaminho.addNode(tempCaminho.getNode(i).split(":")[0]);
				Dominio = AntNetCrankInterControlPlane.getDomain(tempCaminho
						.getNode(i));
			}

		}
		// add o no de destino
		// tempNewCaminho.addNode(tempCaminho.lastNode());
		/*
		 * FIM DA ROTINA
		 */
		if (verbose)
			System.out.println("Caminho completo: " + tempCaminho.toString());
		if (verbose)
			System.out.println("Caminho completo para update: "
					+ tempNewCaminho.toString());

		// Obtem o node apos o node atual, existente no caminho da formiga.
		String nextId = ant.getPath().getNextNode(procId);

		// isto é, existe um link interdominio. Entao devo atualizar o nivel de
		// ferormonio e tambem atualizar o modelo parametrico
		if (!AntNetCrankInterControlPlane.getDomain(nextId).equals(
				AntNetCrankInterControlPlane.getDomain(procId))) {

			// System.out.println("PROC ID: " + procId);
			if (verbose)
				System.out.println("Atualizando link interdominio  " + procId
						+ "-" + nextId);
			// sourceDomain eh o dominio que tera suas tabelas atualizadas
			String sourceDomain = AntNetCrankInterControlPlane
					.getDomain(procId);

			// preciso obter todos os dominios que fazem parte deste subcaminho
			try {

				// obtem o subcaminho composto pelo no forward ate o
				// antepenultimo no da trilha
				List subCaminho = tempNewCaminho.subPath(
						AntNetCrankInterControlPlane.getDomain(nextId),
						AntNetCrankInterControlPlane.getDomain(target));
				// adiciona o ultimo, pois o metodo subPath exclui o ultimo
				// elemento.
				subCaminho.add(AntNetCrankInterControlPlane.getDomain(target));

				if (verbose)
					System.out.println("SUBCAMINHO PARA UPDATE: "
							+ subCaminho.toString());
				// atualizacao dos subcaminhos
				for (int k = 0; k < subCaminho.size(); k++) {
					// obtem o destino
					String destinationDomain = (String) subCaminho.get(k);
					if (verbose)
						System.out.println("Assumindo como destino:"
								+ destinationDomain);

					// obtem o modelo parametrico do AS sourceDomain com relacao
					// ao AS destinationDomain
					LocalParametricView view = InterStatisticalParametricModel
							.get(sourceDomain, destinationDomain);

					// obtem o intervalo de confianca
					double upperCondidenceInterval = view.getAverage()
							+ zFactor
							* (view.getDeviation() / Math.sqrt((double) view
									.getWindow()));

					// o tamanho do caminho eh sempre o valor de k acrescido de
					// 1, pois o vetor SubCaminho ja esta ordenado por distancia
					double pathValue = k + 1;
					if (verbose)
						System.out.println("Tamanho do subcaminho: "
								+ pathValue);

					// verifico se compensa atualizar (sempre atualiza quando o
					// no forward eh o no de destino da requisicao)
					if ((pathValue < upperCondidenceInterval)
							|| AntNetCrankInterControlPlane.getDomain(target)
									.equals(destinationDomain)) {
						// atualiza a visao local
						InterStatisticalParametricModel.update(pathValue,
								sourceDomain, destinationDomain);

						// obtem a visao atualizada
						view = InterStatisticalParametricModel.get(
								sourceDomain, destinationDomain);

						// obtem todas as possiveis rotas
						ArrayList<Vector<String>> rotas = interRoutes.get(
								sourceDomain).get(destinationDomain);

						// obtem o numero de possiveis rotas entre sourceDomain
						// e destinationDomain
						int neighborhoodSize = rotas.size();
						if (verbose)
							System.out
									.println("O numero de possiveis rotas  de "
											+ sourceDomain + " para "
											+ destinationDomain + " eh "
											+ neighborhoodSize);
						// calcula o reforco
						double reforco = getReforco(ant, view,
								neighborhoodSize, pathValue);

						if (verbose)
							System.out
									.println("O reforco obtido eh " + reforco);

						/*
						 * if (verbose) System.out
						 * .println("O caminho que a formiga percorreu foi " +
						 * ant.getPath().toString()); if (verbose) System.out
						 * .println
						 * ("As possiveis rotas que a formiga poderia percorrer: "
						 * + rotas.toString()); if (verbose) System.out
						 * .println("Olha, o que eu tenho sobre a formiga eh " +
						 * ant.getASRecord().toString());
						 */

						// gera a chave para atualizar os niveis de ferormonio
						// importante: eh sempre o domino TARGET (alvo da
						// requisicao mesmo), a segunda parte da chave.
						String key = sourceDomain
								+ "-"
								+ AntNetCrankInterControlPlane
										.getDomain(target);

						if (verbose)
							System.out
									.println("A formiga escolheu  a rota entre "
											+ sourceDomain
											+ " e  "
											+ destinationDomain
											+ " = "
											+ +ant.getASRecord(key));

						for (int i = 0; i < interPheromone.get(key).size(); i++) {

							double oldLevel = interPheromone.get(key).get(i)
									.getPheromoneLevel();
							double newLevel;
							if (verbose)
								System.out
										.println("Nivel antigo de ferormonio "
												+ oldLevel);
							if (i == ant.getASRecord(key)) {
								// reforco positivo
								newLevel = oldLevel
										+ (reforco * (1.0 - oldLevel));
							} else {
								// reforco negativo
								newLevel = oldLevel - (reforco * oldLevel);
							}
							if (verbose)
								System.out.println("Nivel novo de ferormonio "
										+ key + " " + newLevel);
							// define o novo nivel de ferormonio
							interPheromone.get(key).get(i)
									.setPheromoneLevel(newLevel);

						}

					} else {
						if (verbose)
							System.out
									.println("Nao eh bom atualizar este caminho.");
					}

				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	protected static double getReforco(AntPacket ant, LocalParametricView view,
			int size, double subPathLegth) {
		// The reinforcement given by the specified ant.
		double reinforcement;
		// String procId = ant.getNode();
		double subPath = subPathLegth;
		// System.out.println("SubPath: "+subPath);
		// System.out.println("View: "+view.toString());
		double firstTerm = (view.getBest() / subPath);
		double upperInterval = view.getAverage() + zFactor
				* (view.getDeviation() / Math.sqrt((double) view.getWindow()));
		// System.out.println("upperInterval: "+upperInterval);
		double denominator = ((upperInterval - view.getBest()) + (subPath - view
				.getBest()));
		double secondTerm = (upperInterval - view.getBest()) / denominator;
		// System.out.println("SecondTerm: "+secondTerm);
		if (denominator == 0.0) { // Singularity problems!
			reinforcement = firstTerm;
			// System.out.println(".....");
		} else { // Normal case
			reinforcement = c1 * firstTerm + c2 * secondTerm;
		}
		// Now uses the squash function to compress the lower scale
		reinforcement = Squash(reinforcement, size);
		// Verifies if the reinforcement is above the maximum level
		// System.out.println("Pheromone reinforcement: "+reinforcement);
		if (reinforcement > MAX_REINFORCEMENT) {
			return MAX_REINFORCEMENT;
		} else {
			return reinforcement;
		}
	}

	/**
	 * Squash function.
	 * 
	 * @param value
	 *            The value to be squashed.
	 * @param neighborhoodSize
	 *            The number of neighbors
	 * @return The squashed value.
	 */
	protected static double Squash(double value, int neighborhoodSize) {
		return 1.0 / (1.0 + Math.exp(amplifier / (value * neighborhoodSize)));
	}

	protected boolean hasAS(String asNumber) {
		return interRoutes.containsKey(asNumber);
	}

}



