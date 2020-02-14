/*
 * Created on Feb 14, 2008.
 */
package rwa.crankback.antnetInter;

import event.Event;
import graph.Edge;
import graph.Graph;
import graph.Path;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;
import ops.Packet;
import antnet.StatisticalParametricModel;
import main.Failure;
import main.RoutingTable;
import main.Failure.Location;
import rwa.Connection;
import rwa.Error;
import rwa.LightpathRequest;
import rwa.LinkState;
import rwa.WavelengthMask;
import rwa.crankback.LabelSwitchRouter.ReRouting;
import rwa.crankback.antnetInter.CrankRSVP;
import rwa.crankback.CrankControlPlane;
import rwa.crankback.LabelSet;
import rwa.crankback.LabelSwitchRouter;

/**
 * @author Gustavo S. Pavani, Andre Filipe M. Batista
 * @version 1.0
 * 
 */
public class AntNetInterLSR extends LabelSwitchRouter {
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	/** Statistical Parametric Model for global-traffic statistics. */
	protected StatisticalParametricModel parametricModel;
	/** Wavelength usage table for Most-used wavelength assignment. */
	protected WavelengthUsageTable lambdaTable;
	/**
	 * mapa que contem a rota escolhida por uma formiga assim que ela entra em
	 * um dominio
	 */
	protected static HashMap<String, Integer> antSelectedInterRoute = new HashMap<String, Integer>();

	boolean verbose = false;

	/**
	 * Creates a new AntNetLSR object.
	 * 
	 * @param identification
	 *            The identification of this node in the network.
	 * @param aRoutingTable
	 *            The routing table associated with this node.
	 * @param aLinks
	 *            The set of link states that belongs to this node.
	 * @param aGraph
	 *            The graph representing the network.
	 * @param aWa
	 *            The chosen wavelength assignment algorithm.
	 * @param behavior
	 *            The re-routing behavior.
	 * @param attempts
	 *            The number of re-routing attempts.
	 * @param aModel
	 *            The set of parametric models of this node.
	 * @param usageTable
	 *            The lambda usage table of this node.
	 */
	public AntNetInterLSR(String identification, RoutingTable routingTable,
			LinkedHashMap<String, LinkState> links, Graph graph,
			WavelengthAssignment aWa, ReRouting behavior, int maxAttempts,
			int attempts, StatisticalParametricModel aModel,
			WavelengthUsageTable usageTable) {
		super(identification, routingTable, links, graph, aWa, behavior,
				maxAttempts, attempts);
		this.parametricModel = aModel;
		this.lambdaTable = usageTable;

	}

	/**
	 * Process the specified event.
	 * 
	 * @param event
	 *            The event to be processed.
	 * @return The processed event.
	 */
	@Override
	public Event process(Event event) {
		// Get the packet associated to this event
		Packet packet = (Packet) event.getContent();
		// Inspects its header and decides what to do with it.
		Packet.Header header = packet.getNextHeader();
		Event response = null; // The response to the processing
		String nextHop = null; // The next hop in the path
		Ant ant; // Ant packet
		Error error; // The associated error specification
		String target; // Target node
		CrankRSVP rsvp; // RSVP message

		boolean intra = false;
		boolean inter = false;
		boolean interDomainAnt = false;

		switch (header) {
		case ANT_FORWARD:
			//
			ant = (Ant) packet;
			// Get the target node

			int selectedRoute = 0;

			target = ant.getTarget();
			if (verbose)
				System.out.println(id + " - Recebi uma formiga indo para "
						+ target);

			// Verify if its a interdomain or intradomain request
			// Check if the target is in the same domain of the current node
			if (AntNetCrankInterControlPlane.getDomain(target).equals(
					AntNetCrankInterControlPlane.getDomain(id))) {
				if (verbose)
					System.out.println(id
							+ " - Received an Intra-domain Ant from: "
							+ ant.getSource() + " to: " + target);

				intra = true;
				inter = false;

			} else {
				if (verbose)
					System.out.println(id
							+ " - Received an Inter-domain ant from "
							+ ant.getSource() + " to: " + target);
				inter = true;
				intra = false;
			}

			if (!AntNetCrankInterControlPlane.getDomain(target).equals(
					AntNetCrankInterControlPlane.getDomain(ant.getSource()))) {
				// System.out.println("marquei formiga como interDomainAnt");
				interDomainAnt = true; // marca como uma formiga interdominio
			}

			// Verify if the ant has reached the target node.
			if (target.equals(id)) { // Ant reached the target node
				if (verbose)
					System.out
							.println("ANT'S TARGET!!!!!!!!!! Transformando-se em Backward Ant");

				// Turn it into a backward ant
				ant.toBackward();
				// Set the next node as the last visited before the target
				nextHop = ant.getBackwardNode();
			} else { // Not arrived in the target node
				// Decide the next hop based on the information of the routing
				// table
				// and on the information of the optical buffers

				if (intra && !interDomainAnt) {
					// se for INTRA e nao eh uma formiga interdominio

					nextHop = ((AntNetCrankRoutingTable) routingTable).nextHop(
							ant, links);
					if (verbose)
						System.out.println("Nexthop calculado por intra : "
								+ nextHop);
				} else if (intra && interDomainAnt) {
					if (verbose)
						System.out
								.println("Eh, a formiga eh InterDomainAnt. Calculando NextHop");
					// se o pedido eh intra e a formiga eh interDomainAnt,
					// entao, eu ja estou dentro do dominio de destino
					// Neste caso, uma maneira "simples" de fazer eh definir
					// como nextHop o destino, e assumir que existe um
					// tunel que ira entregar

					nextHop = ant.getTarget();

					if (verbose)
						System.out.println("O nexthop eh " + nextHop);
				} else {
					// o pedido eh inter

					/*
					 * COMPORTAMENTO DA FORMIGA INTER
					 */
					// ela nao se move pelo link fisico, e sim pelo tunel
					// existente entre os ASBRs
					// logo, a formiga inter sempre é gerada em um ASBR
					// (dominio
					// origem) e tenta ir para outro ASBR (em outro dominio)

					// TO DO: o trafego soh podera gerar a partir de ASBRS, por
					// enquanto gera a partir de qualquer um

					// SUGESTAO:
					// se a formiga inter ja escolheu estocasticamente um
					// dominio de egresso, entao ela
					// apenas vai para o dominio escolhido (minimizando os loops
					// e observando apenas
					// os enlaces interdominio)
					if (ant.isAsFlag()) {
						if (verbose)
							System.out.println("Usando ASFLAG.");
						selectedRoute = ant
								.getASRecord(AntNetCrankInterControlPlane
										.getDomain(id)
										+ "-"
										+ AntNetCrankInterControlPlane
												.getDomain(target));
						// o lastelement é sempre o ASBR de ingresso no outro
						// AS.
						nextHop = AntNetCrankInterRoutingTable.getASPath(id,
								target, selectedRoute).lastElement();
						ant.setAsFlag(false);
					} else {
						
						// A formiga tem que usar as informacoes dos links para
						// escolher a proxima rota
						// sugestão: tentar na hora de selecionar a rota, passar
						// os links

						selectedRoute = AntNetCrankInterRoutingTable
								.getNextRoute(ant, id, target, links);

						// salva na memoria da formiga o enlace interdominio
						// escolhido.
						ant.putASRecord(
								AntNetCrankInterControlPlane.getDomain(id)
										+ "-"
										+ AntNetCrankInterControlPlane
												.getDomain(target),
								selectedRoute);

						nextHop = AntNetCrankInterRoutingTable.getAntNextHop(
								id, target, selectedRoute);
					}

					// verifica se o nexthop esta no mesmo dominio, e entao,
					// liga a flag ASFLAG para a formiga ant
					if (getDomain(id).equals(getDomain(nextHop))) {
						ant.setAsFlag(true);
					}

					// System.out.println("O NextHop inter eh " + nextHop);

				}

				/*
				 * if (inter) { if
				 * (AntNetCrankInterControlPlane.getDomain(nextHop).equals(
				 * AntNetCrankInterControlPlane.getDomain(id)) &
				 * !AntNetCrankInterControlPlane.hasConnectivity(id, nextHop) &
				 * !id.equals(nextHop)) { // tenho que fazer a expansao do PATH
				 * para o dominio // interno Ant formiga = new Ant(id, nextHop,
				 * maxReroutingAttempts, maxReroutingAttempts);
				 * 
				 * nextHop = ((AntNetCrankRoutingTable) routingTable)
				 * .nextHop(formiga, links);
				 * 
				 * System.out
				 * .println("Intra-domain Path Computation: next hop is --> " +
				 * nextHop);
				 * 
				 * } }
				 */

				if ((nextHop == null) || (packet.getHopLimit() == 0)) { // Ant
																		// killed
																		// due
																		// to
																		// loop
																		// or
																		// expired
																		// hop
																		// limit
					// System.err.println("Ant KILLED");
					event.setType(Event.Type.ANT_KILLED);
					response = event;
					break;
				} else {

					// if (nextHop.equals(id)) {
					// System.err.println("PASSEI AQUI");
					// // I need to go to anothert domain
					// // nextHop will be the second element of Route,
					// // which is
					// // the next domain
					// Vector<String> rota = AntNetCrankInterRoutingTable
					// .getASPath(id, target, selectRoute);
					// //
					// System.out.println("Tenho que pegar um cara diferente "
					// // + rota.toString());
					// nextHop = rota.get(1); // pega o segundo
					// // elemento
					// // do ASPath, sempre o
					// // outro
					// // dominio
					//
					// // System.out
					// //
					// .println(" Inter-domain Path Computation --> next hop is: "
					// // + nextHop);
					// }
					// Decrement the number of hops
					ant.decrementHopLimit();
					// Add the wavelength mask to the ant payload
					if (intra && !interDomainAnt) {
						WavelengthMask mask = links.get(nextHop).getMask();
						ant.addMask(mask);
					}
				}
				// Set the next hop in the packet
				if (ant.isTabu(nextHop)) {
					if (verbose)
						System.out
								.println("LOOP - Ja passei por este nexthop no caminho "
										+ ant.getPath().toString());
					int loopSize = ant.destroyLoop(nextHop);
					if (verbose)
						System.out
								.println("O tamanho do loop eh " + loopSize
										+ ". Novo caminho: "
										+ ant.getPath().toString());
					// Set the loop flag
					ant.setLoopFlag();
					// Set the payload length
					ant.setPayloadLength(ant.getPayloadLength() - loopSize
							* ant.getBytesHop());

				}
			}
			if (inter)
				if (verbose)
					System.out
							.println("Sou uma formiga INTER e tenho como nexthop "
									+ nextHop);
			ant.setNode(nextHop);

			// Return the response
			response = event;
			break;
		case ANT_BACKWARD:

			ant = (Ant) packet;
			if (verbose)
				System.out
						.println("Opa, temos uma ant backward. Ela percorreu: "
								+ ant.getPath().toString());
			String alvo = ant.getTarget();

			// a atualizacao da tabela de ferormonio sera da mesma maneira se
			// for intra
			// logo, preciso saber se é inter ou intra

			// Check if the target is in the same domain of the current node
			String myDomain = AntNetCrankInterControlPlane.getDomain(id);
			if (getDomain(alvo).equals(myDomain)
					&& getDomain(ant.getSource()).equals(myDomain)) {
				// a formiga backward só é considerada intra se o dominio target
				// dela for igual ao dominio do
				// no que ela esta passando e se a origem tambem for do mesmo
				// dominio. Caso contrario, é uma formiga inteiramente inter
				if (verbose)

					System.out.println(id
							+ " - Received a BACKWARD Intra-domain Ant from: "
							+ ant.getSource() + " to: " + alvo);
				intra = true;
				inter = false;

			} else {
				if (verbose)
					System.out.println(id
							+ " - Received a BACKWARD Inter-domain ant from "
							+ ant.getSource() + " to: " + alvo);
				inter = true;
				intra = false;
			}

			if (intra) {

				/* Update the pheromone routing table */
				if (verbose)
					System.out.println("atualizando tabela de ferormonio");

				((AntNetCrankRoutingTable) routingTable).update(ant,
						parametricModel);
			} else if (inter) {

				/**
				 * ATUALIZACAO DA TABELA DE FERORMONIO INTER
				 */
				// preciso verificar se o link que a formiga veio é um link
				// interdominio
				// se for, entao existe a atualizacao da tabela de
				// ferormonio/roteamento

				if (ant.getPath().getNextNode(id) != null
						&& !getDomain(ant.getPath().getNextNode(id)).equals(
								getDomain(id))) {
					if (verbose)
						System.out
								.println("atualizando tabela de ferormonio - formiga INTER");

					// eh um link interdominio, entao preciso atualizar a tabela
					// de ferormonio/roteamenoto
					AntNetCrankInterRoutingTable.update(ant);
				}

			}

			// Verify if the ant reached the source node.
			if (ant.getSource().equals(id)) {
				// System.out.println("CHEGUEI NA SOURCE");

				if (intra) {// ver se isto faz sentido para a formiga inter
					// Add the memory of the ant to the wavelength usage table
					if (!ant.getLoopFlag()) // Not looped
						lambdaTable.update(ant.getCollector(), ant.getTarget());
					// Set the ant as routed

				}
				event.setType(Event.Type.ANT_ROUTED);
			} else {
				// See the next hop
				nextHop = ant.getBackwardNode();
				// System.out.println("Voltando para " + nextHop);

				// Verify if the link is not damaged.
				// APENAS VERIFICA PARA INTRA
				if (intra
						&& !AntNetCrankInterControlPlane.hasConnectivity(id,
								nextHop)) {
					event.setType(Event.Type.ANT_KILLED);
					response = event;
					// System.out.println("Back: "+event.toString());
					break;
				}
				// Set the next node in the path
				ant.setNode(nextHop);
			}
			response = event;
			break;

		/**
		 * Conjunto de regras para processamento de pacotes RSVP.
		 */

		case RSVP_PATH:

			rsvp = (CrankRSVP) packet;

			if(verbose)
				System.out.println(id + " - request: " + rsvp.getFlowLabel()
						+ " de: " + rsvp.getSource() + "  para: "
						+ rsvp.getTarget());
			// System.out.println("caminho " + rsvp.getPath().toString());
			// Get the target node
			target = rsvp.getTarget();
			// Verify if its a interdomain or intradomain request
			// Check if the target is in the same domain of the current node
			if (getDomain(target).equals(getDomain(id))) {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Intra-domain request from: "
							+ rsvp.getSource() + " to: " + target);
				intra = true;
				inter = false;
			} else {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Inter-domain request from "
							+ rsvp.getSource() + " to: " + target);
				inter = true;
				intra = false;
			}
			// sets the nextHop
			nextHop = "";

			// Get the lightpath request
			LightpathRequest request = (LightpathRequest) rsvp.getObject();
			// Get the label set object
			LabelSet labelSet = rsvp.getLabelSet();

			if ((labelSet != null) && (labelSet.size() == 0)) { // There is no
																// free
																// wavelengths
																// to allocate
				// wavelength continuity constraint violated
				rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); // change
																	// header to
				if (verbose) // problem
					System.err
							.println(rsvp.getFlowLabel()
									+ " "
									+ id
									+ " - NAO TENHO LAMBDA! Marcando como RSVP_PATH_ERR, RP_LABEL_SET");
				rsvp.addIgnoreASBR(id);

				if (inter) {
					// remove o link de saida selecionado, para garantir que nao
					// foi ali o problema.
					rsvp.selectedDomain.remove(getDomain(id) + "-"
							+ getDomain(rsvp.getTarget()));
				}

				// codigo comum tanto para inter quanto para intra

				// Reset the SD pair to the new values
				rsvp.setSDPair(id, request.getSource());
				// Set the error
				error = new Error(Error.Code.RP_LABEL_SET);
				rsvp.setError(error);
				// Do not record the route anymore
				rsvp.resetRecordRoute();
				// Get the next hop
				nextHop = rsvp.getBackwardNode();
				// if the request source is the same of the current node and
				// thereis no nextHop (when the source node does not have
				// lambdas)
				// so I need to set nextHop the current node
				if (request.getSource().equals(id) && nextHop == null) {
					// System.out.println("Tive que adaptar o nexthop");
					nextHop = id;
					// rsvp.setNode(nextHop);
					// response = event;
				}
				if (verbose)
					System.out.println("Out of lambda, going back to source "
							+ request.getSource() + " -  the nexthop is "
							+ nextHop + "\n The PATH is "
							+ rsvp.getPath().toString());

				rsvp.addEffectiveHop();
				// System.out.println(rsvp.toString());
			} else if (target.equals(id)) { // RSVP reached the target node
				if (verbose) {
					System.out
							.println("PACKAGE! TARGET!!!!!!!!!! RSVP ANT WORKS!");
					System.out.println("Todo o caminho percorrido foi "
							+ rsvp.getPath().toString());
				}

				// change header to reservation
				rsvp.setNextHeader(Packet.Header.RSVP_RESV);
				// Reset the SD pair to the new values
				rsvp.setSDPair(target, request.getSource());
				// Choose the first free wavelength
				int lambda = labelSet.getFirstLabel();
				// Create a connection and add it as a object.
				Connection connectionEst = new Connection(rsvp.getPath(),
						lambda, rsvp.getFlowLabel(), request);
				// Set the start time of the connection, which starts after
				// arriving at the source node.
				// Because of that, it uses the round trip time as the time to
				// start it
				connectionEst.setStartTime(event.getTimeStamp()
						+ (event.getTimeStamp() - event.getInitialTimeStamp()));
				// Set the object to the message
				rsvp.setObject(connectionEst);
				// Add the connection to the table of active connections.
				activeConnections.put(rsvp.getFlowLabel(), connectionEst);
				// System.out.println(activeConnections.toString());
				// Do not record the route anymore
				rsvp.resetRecordRoute();
				// Get the next hop
				nextHop = rsvp.getBackwardNode();
			} else { // Intermediate node
				// Get the next hop

				if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																// node
																// re-routing

					if (intra) {

						// LightpathRequest pedido = new LightpathRequest(id,
						// target, 0);
						// CrankRSVP pac = new CrankRSVP(pedido, 0,
						// Long.parseLong("0"));

						// pac.setSDPair(id, target);

						/*
						 * if (!this.getHistoryTable(rsvp.getFlowLabel())
						 * .isEmpty()) { System.out .println("PARE!: " +
						 * this.getHistoryTable(rsvp .getFlowLabel())); }
						 */

						nextHop = ((AntNetCrankRoutingTable) routingTable)
								.nextHop(rsvp, this.getHistoryTable(rsvp
										.getFlowLabel()));

						// talvez ver se tem loop

						/**
						 * Exclusao de loops quando da escolha do k caminho
						 * alternativo Preciso verificar se alem de eu ser o
						 * ultimo no no caminho, se existe mais uma ocorrencia
						 * minha em outra posicao caso sim, preciso remover todo
						 * os os nos ate a minha primeira ocorrencia eliminando,
						 * assim, o loop
						 */
						if (rsvp.getPath().containNode(id)
								&& rsvp.getPath().getNodePosition(id) != rsvp
										.getPath().size() - 1) {

							if (verbose)
								System.out
										.println("Opa tenho um loop no caminho "
												+ rsvp.getPath().toString());
							/*
							 * System.out.println("Estou na posicao " +
							 * rsvp.getPath().getNodePosition(id));
							 */
							int i = rsvp.getPath().getNodePosition(id);
							int size = rsvp.getPath().size();
							String source = rsvp.getPath().firstNode();
							rsvp.selectedDomain.remove(getDomain(id) + "-"
									+ getDomain(target));
							/*
							 * System.out.println("O tamanho do vetor eh " +
							 * size);
							 */
							for (int a = size - 1; a > i; a--) {
								/*
								 * System.out
								 * .println("tentando remover o no na posicao "
								 * + a + " " + rsvp.getPath().getNode(a));
								 */
								rsvp.getPath().removeNodeAt(a);

							}
							if (verbose)
								System.out.println("CAMINHO SEM LOOP = "
										+ rsvp.getPath().toString());
						}

						// nextHop = ((AntNetCrankRoutingTable) routingTable)
						// .nextHop(rsvp, this.getHistoryTable(rsvp
						// .getFlowLabel()));
					}
					if (inter) {
						// processamento inter
						// PROCESSAMENTO DE REQUISICOES INTER
						// se chegou aqui, ainda tenho lambda livre, entao vou
						// zerar o lastSelected
						rsvp.resetSelectedRoute();
						if (verbose)
							System.out.println("OBTENDO NEXTHOP INTER");

						String tipo = null;
						// verificando se existe algum ASBR para ignorar
						// if (!rsvp.ignoreASBR.isEmpty()) {
						// if (verbose)
						// System.out.println("Preciso ignorar: "
						// + rsvp.ignoreASBR.toString());

						// agora tenho que ver como que é isto.

						// Se for no mesmo dominio que eu, entao eu vou
						// tratar um bloqueio de RSVP interno
						//
						// for (String node : rsvp.ignoreASBR) {
						// if (getDomain(node).equals(getDomain(id))) {
						// ou seja, eh do tipo crank inter
						// tipo = "crank intra";
						// }
						// }

						// if(tipo!= null && tipo.equals("crank intra")) {
						// nao considera mais a rota selecionada para saida.
						// rsvp.resetSelectedRoute();
						// }

						// }

						// o pacote rsvp possui um campo chamado selectedDomain
						// que ira ser utilizado para o Per Domain Path
						// Computation
						// Quando a requisicao chega no ASBR de ingresso de um
						// dominio
						// Este calcula qual link de egresso sera utilizado
						// tendo por
						// base a tabela de roteamento interdominio por
						// ferormonio.

						// Apos calculado, há uma expansao dos caminhos intra
						// utilizando
						// a tabela de roteamento intradominio de cada no (por
						// ferormonio).

						// verifico, entao, se ja nao foi feito o calculo do
						// link egresso ao dominio
						// PARA CRANKBACK PENSAR NA IDEIA DA LINHA ABAIXO.
						// if (!rsvp.selectedDomain.containsKey(getDomain(id)
						// + "-" + getDomain(target)) || rsvp.ignoreASBR!="") {

						if (!rsvp.selectedDomain.containsKey(getDomain(id)
								+ "-" + getDomain(target))) {
							// caso nao tenha sido realizado nenhum calculo
							// (ASBR de ingresso)
							// entao ira selecionar

							// armazena a rota que foi selecionada pelo metodo
							// probabilistico
							int selected = AntNetCrankInterRoutingTable
									.getNextRoute(id, target, this.historyTable
											.get(rsvp.getFlowLabel()), rsvp
											.getPath());

							// obtem o nexthop
							nextHop = AntNetCrankInterRoutingTable
									.getInterNextHop(id, target, selected, this
											.getHistoryTable(rsvp
													.getFlowLabel()));

							// apos obter o nextHop, vou marcar qual rota eu
							// utilizei
							rsvp.putSelectedRoute(rsvp.getSelectedRoute());
							// sempre quando eu conseguir chegar ate esta parte,
							// eu marco a ultima que foi utilizada
							// o momento em que nao chegar, se houver mais
							// tentativas, a requisicao se tornara RSVP_PATH
							// novamente
							// e podera tratar isto de maneira diferente.

							// insere a informacao do link de egresso que sera
							// utiizadopor este dominio
							rsvp.selectedDomain.put(
									getDomain(id) + "-" + getDomain(target),
									AntNetCrankInterRoutingTable.interRoutes
											.get(getDomain(id))
											.get(getDomain(target))
											.get(selected));

						} else {
							// CASO ja tenha sido selecionado o link de egresso,
							// nao eh feito um calculo novamente.
							// System.out.println("Nao vou calcular novamente");
							Vector<String> route = rsvp.selectedDomain
									.get(getDomain(id) + "-"
											+ getDomain(target));
							nextHop = route.firstElement();
							// A rota de retorno eh um link ASBR AS-1 <--> ASBR
							// AS-2
							// Caso o retorno seja o ASBR AS-1 e o no atual for
							// este,
							// significa que ja estamos no link e que o ASBR
							// AS-2 deve
							// ser o nextHop (que eh o ultimo elemento da rota)
							if (nextHop.equals(id)) {
								nextHop = route.lastElement();
							}

						} // fim do else

						if (verbose)
							System.out.println(id + " O nexthop eh " + nextHop);

						if (AntNetCrankInterControlPlane.getDomain(nextHop)
								.equals(AntNetCrankInterControlPlane
										.getDomain(id))
								& !AntNetCrankInterControlPlane
										.hasConnectivity(id, nextHop)
								& !id.equals(nextHop)) {
							// tenho que fazer a expansao do PATH para o dominio
							// interno
							LightpathRequest pedido = new LightpathRequest(id,
									nextHop, 0);
							CrankRSVP pac = new CrankRSVP(pedido, 0,
									Long.parseLong("0"));
							pac.setSDPair(id, nextHop);

							// mantem o caminho que a requisicao ja passou.
							Path pathOriginal = rsvp.getPath();

							// preciso mesmo zerar o vetor gerado.
							pac.getPath().removeNode(id);

							for (String node : pathOriginal.nodes()) {
								// System.out.println("Tentando adicionar " +
								// node);
								// System.out.println("O caminho que ja existe eh "
								// + pac.getPath().toString());
								if (!pac.getPath().containNode(node)) {

									pac.getPath().addNode(node);
								}
							}
							// caminho = (Path) rsvp.getPath().clone();
							// System.out.println("O caminho eh " +
							// pac.getPath().toString());
							// eu uso a AntNetCrankRoutingTable ao inves da
							// AntNetCrankInterRoutingTable
							// pois eh uma expansao a nivel intradominio. Entao,
							// a tabela de roteamento
							// intra que deve ser utilizada.
							nextHop = ((AntNetCrankRoutingTable) routingTable)
									.nextHop(pac, this.getHistoryTable(rsvp
											.getFlowLabel()));
							if (verbose)
								System.out
										.println(rsvp.getFlowLabel()
												+ " - "
												+ "Intra-domain Path Computation: next hop is --> "
												+ nextHop);

						}

						/**
						 * Exclusao de loops quando da escolha do k caminho
						 * alternativo Preciso verificar se alem de eu ser o
						 * ultimo no no caminho, se existe mais uma ocorrencia
						 * minha em outra posicao caso sim, preciso remover todo
						 * os os nos ate a minha primeira ocorrencia eliminando,
						 * assim, o loop
						 */
						if (rsvp.getPath().containNode(id)
								&& rsvp.getPath().getNodePosition(id) != rsvp
										.getPath().size() - 1) {

							if (verbose)
								System.out
										.println("Opa tenho um loop no caminho "
												+ rsvp.getPath().toString());
							/*
							 * System.out.println("Estou na posicao " +
							 * rsvp.getPath().getNodePosition(id));
							 */
							int i = rsvp.getPath().getNodePosition(id);
							int size = rsvp.getPath().size();
							// String source = rsvp.getPath().firstNode();
							rsvp.selectedDomain.remove(getDomain(id) + "-"
									+ getDomain(target));
							/*
							 * System.out.println("O tamanho do vetor eh " +
							 * size);
							 */
							for (int a = size - 1; a > i; a--) {
								/*
								 * System.out
								 * .println("tentando remover o no na posicao "
								 * + a + " " + rsvp.getPath().getNode(a));
								 */
								rsvp.getPath().removeNodeAt(a);

							}
							if (verbose)
								System.out.println("CAMINHO SEM LOOP = "
										+ rsvp.getPath().toString());
						}

						if (nextHop != null
								&& !getDomain(nextHop).equals(getDomain(id))) {
							// estou indo para um novo dominio, entao vou
							// remover
							// o asbr de egresso gerado para esta requisicao
							rsvp.selectedDomain.remove(getDomain(id) + "-"
									+ getDomain(target));
						}

					}

				} else { // None or end-to-end routing
					// nao utilizado.
					if (intra) {
						// PROCESSAMENTO DE REQUISICOES INTRA
						// isto eh como estava no codigo do gustavo
						// perguntas: onde esta routing try?
						// provavelmente dentro do objetov rsvp

						nextHop = ((AntNetCrankRoutingTable) routingTable)
								.nextHop(rsvp, new Vector<String>());
					}
					// this.getHistoryTable(rsvp.getFlowLabel());
					if (inter) {
						// PROCESSAMENTO DE REQUISICOES INTER QUANDO ROUTING
						// NONE
						if (verbose)
							System.out.println("OBTENDO NEXTHOP INTER");
						nextHop = AntNetCrankInterRoutingTable.getNextHop(id,
								rsvp.getTarget(), 0);
						if (verbose)
							System.out.println(id + " O nexthop eh " + nextHop);

						if (AntNetCrankInterControlPlane.getDomain(nextHop)
								.equals(AntNetCrankInterControlPlane
										.getDomain(id))
								& !AntNetCrankInterControlPlane
										.hasConnectivity(id, nextHop)
								& !id.equals(nextHop)) {
							// tenho que fazer a expansao do PATH para o dominio
							// interno

							LightpathRequest pedido = new LightpathRequest(id,
									nextHop, 0);
							CrankRSVP pac = new CrankRSVP(pedido, 0,
									Long.parseLong("0"));
							pac.setSDPair(id, nextHop);
							nextHop = ((AntNetCrankRoutingTable) routingTable)
									.nextHop(pac);
							if (verbose)
								System.out
										.println(rsvp.getFlowLabel()
												+ " - "
												+ "Intra-domain Path Computation: next hop is --> "
												+ nextHop);

						}

						Vector<String> Route = AntNetCrankInterRoutingTable
								.getASPath(id, target, request.getTry());
						/**
						 * Exclusao de loops quando da escolha do k caminho
						 * alternativo Preciso verificar se alem de eu ser o
						 * ultimo no no caminho, se existe mais uma ocorrencia
						 * minha em outra posicao caso sim, preciso remover todo
						 * os os nos ate a minha primeira ocorrencia eliminando,
						 * assim, o loop
						 */

						if (rsvp.getPath().containNode(id)
								&& rsvp.getPath().getNodePosition(id) != rsvp
										.getPath().size() - 1) {
							if (verbose)
								System.out
										.println("Opa tenho um loop no caminho "
												+ rsvp.getPath().toString());
							/*
							 * System.out.println("Estou na posicao " +
							 * rsvp.getPath().getNodePosition(id));
							 */
							int i = rsvp.getPath().getNodePosition(id);
							int size = rsvp.getPath().size();
							String source = rsvp.getPath().firstNode();

							/*
							 * System.out.println("O tamanho do vetor eh " +
							 * size);
							 */
							for (int a = size - 1; a > i; a--) {
								/*
								 * System.out
								 * .println("tentando remover o no na posicao "
								 * + a + " " + rsvp.getPath().getNode(a));
								 */
								rsvp.getPath().removeNodeAt(a);

							}
						}

					}
				}

				// aqui volta o processamento para o simulador indepentende de
				// ser SEGMENT ou NONE.
				if (nextHop == null
						|| (packet.getHopLimit() == 0)
						|| !(AntNetCrankInterControlPlane.hasConnectivity(id,
								nextHop))) { // dead-end
					// request.addTry(); //add to the counter of tries
					if (verbose)
						System.out.println(id
								+ "  POR ALGUM BO - RSVP PATH ERROR! "
								+ nextHop);
					rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR); // change
																		// header
																		// to
																		// problem

					// if (id.equals(request.getSource())) {
					// System.out.println("STOP!!!");
					// }
					// Reset the SD pair to the new values
					rsvp.setSDPair(id, request.getSource());
					// Create the error information
					error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
					rsvp.setError(error);
					// Do not record the route anymore
					rsvp.resetRecordRoute();
					if (!id.equals(request.getSource())) {
						// Get the next hop (backward)
						nextHop = rsvp.getBackwardNode();
						rsvp.addEffectiveHop();
					} else { // Failure after the first link of the node
						response = event;
						break;
					}
				} else {
					// Store the label set, if applicable
					if ((labelSet != null)
							&& (this.rerouting.equals(ReRouting.SEGMENT)))
						labelSetTable.put(rsvp.getFlowLabel(),
								(rwa.crankback.LabelSet) labelSet.clone());
					// Updates the mask
					if (labelSet == null) {
						if (wa.equals(WavelengthAssignment.FIRST_FIT))
							labelSet = new LabelSet(this.links.get(nextHop)
									.getNumberWavelengths());
						else if (wa.equals(WavelengthAssignment.MOST_USED))
							// labelSet = new
							// LabelSet(getMostUsed(lambdaTable.get(target).getUsage()));
							labelSet = new LabelSet(getMostUsed(this.links.get(
									nextHop).getNumberWavelengths()));
						else if (wa.equals(WavelengthAssignment.LEAST_USED))
							// labelSet = new
							// LabelSet(getLeastUsed(lambdaTable.get(target).getUsage()));
							labelSet = new LabelSet(getLeastUsed(this.links
									.get(nextHop).getNumberWavelengths()));
						rsvp.setLabelSet(labelSet);
					}
					// System.out.println(links.get(nextHop).getMask().toString());
					rsvp.updateMask(links.get(nextHop).getMask());
					// Decrement the number of hops
					rsvp.decrementHopLimit();
					rsvp.addEffectiveHop();
				}
			}
			// Set the next node

			rsvp.setNode(nextHop);

			// Set the new time of the event due to transmission time
			// System.out.println(event.toString());
			if (nextHop != null && nextHop != id)
				try {
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				} catch (Exception e) {
					System.out.println("Bizarro!");
				}
			// Return the response
			response = event;
			break;

		case RSVP_PATH_TEAR:

			rsvp = (CrankRSVP) packet;
			if (verbose)
				System.out.println(id + "removendo conexão"
						+ rsvp.getFlowLabel() + " - CAMINHO "
						+ rsvp.getPath().toString());
			// System.err.println(id +
			// " - finalizando reservada do pedido nro. " +
			// rsvp.getFlowLabel());
			// Remove this connection from the list of active connections
			activeConnections.remove(rsvp.getFlowLabel());
			// Get the target node
			target = rsvp.getTarget();
			// Verify if the RSVP message has reached the target node.
			if (target.equals(id)) { // RSVP reached the target node
				event.setType(Event.Type.LIGHTPATH_REMOVED);
			} else {
				// Clean the temporary label set and the history table, if
				// applicable
				if (this.rerouting.equals(ReRouting.SEGMENT)) {
					labelSetTable.remove(rsvp.getFlowLabel());
					historyTable.remove(rsvp.getFlowLabel());
				}
				// Gets the connection
				Connection teared = (Connection) rsvp.getObject();
				nextHop = teared.getPath().getNextNode(id);
				LinkState link = links.get(nextHop);
				if (link != null) { // Maybe the state was removed due to
									// failure
					// Get the associated mask
					WavelengthMask linkMaskTear = link.getMask();
					// Clear the wavelength
					linkMaskTear.clearWavelength(teared.getWavelength());
				} else {
					System.out.print(event.toString());
				}
				// Set the next hop in the packet

				rsvp.setNode(nextHop);
				// Set the new time of the event due to transmission time
				event.setTimeStamp(event.getTimeStamp()
						+ links.get(nextHop).getDelay());
			}
			// Return the response
			response = event;
			break;

		case RSVP_PATH_ERR:
			rsvp = (CrankRSVP) packet;

			if (verbose)
				System.out.println("PATH_ERR - " + rsvp.getFlowLabel() + " "
						+ id + " - processando.");
			// problema intradominio
			if (getDomain(rsvp.getSource()).equals(getDomain(rsvp.getTarget()))) {
				if (verbose)
					System.out.println("PATH_ERR INTRA");
				// TRATAMENTO RSVP_PATH_ERR PARA INTRADOMIO.

				rsvp = (CrankRSVP) packet;
				// System.out.println(rsvp.toString());
				// Get the error
				error = rsvp.getError();
				// Remove this connection from the list of active connections
				// if the remove flag is enabled
				if (error.getRemoveFlag()) {
					activeConnections.remove(rsvp.getFlowLabel());
					Connection removed_perr = (Connection) rsvp.getObject();
					String forwardHop = removed_perr.getPath().getNextNode(id);
					LinkState link = links.get(forwardHop);
					if (link != null) { // Maybe the state was removed due to
										// failure
						// Get the associated mask
						WavelengthMask linkMaskRem = link.getMask();
						// Clear the wavelength
						linkMaskRem.clearWavelength(removed_perr
								.getWavelength());
					}
				}
				// Gets the target node
				target = rsvp.getTarget();
				LightpathRequest lRequest = null;
				// Process the message
				Error.Code code = error.getErrorCode();
				switch (code) {
				case LSP_FAILURE:
					if (target.equals(id)) { // RSVP reached the target node
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						nextHop = ((Connection) rsvp.getObject()).getPath()
								.getPreviousNode(id);
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
					break;
				case ADMISSION_CONTROL_FAILURE:
					if (this.rerouting.equals(ReRouting.SEGMENT)) {
						// Remove the connection from the history table
						this.historyTable.remove(rsvp.getFlowLabel());
					}
					if (target.equals(id)) { // RSVP reached the target node
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						nextHop = rsvp.getBackwardNode();
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
					break;
				case RP_LABEL_SET:
					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																	// node
																	// re-routing
						// Remove the last visited node from the record route
						String last = rsvp.removeLastVisited();
						// Put it in the history table
						String label = rsvp.getFlowLabel();
						this.putHistoryTable(label, last);
						// System.out.println("ProcLA: "+id+" last: "+last);
						// Gets the request and update the try counter
						lRequest = (LightpathRequest) rsvp.getObject();
						lRequest.addTry();
						// Verify if we can make another re-routing attempt
						int currentAttempt = this.sizeHistoryTable(label);
						// Gets the number of available neighbors
						int neighbors;
						if (id.equals(lRequest.getSource()))
							neighbors = this.links.size();
						else
							neighbors = this.links.size() - 1;
						// Allow a re-routing if there is allowed attempts and
						// sufficient neighbors
						if ((currentAttempt <= this.reroutingAttempts)
								&& (currentAttempt < neighbors)
								&& (lRequest.getTry() <= this.maxReroutingAttempts)
								&& (packet.getHopLimit() > 0)) {

							// marca que esta tentando fazer rerouting.
							rsvp.setRerouting(true);

							// Set as path message
							rsvp.setNextHeader(Packet.Header.RSVP_PATH);
							// Sets the previous label set
							rsvp.setLabelSet(labelSetTable.get(rsvp
									.getFlowLabel()));
							// Set the record route in the RSVP
							rsvp.setRecordRoute();
							// Reset the error
							rsvp.setError(null);
							// Reset the source-destination pair
							rsvp.setSDPair(lRequest.getSource(),
									lRequest.getTarget());
						} else { // Limit exceeded!
							// Set the error
							error = new Error(
									Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
							rsvp.setError(error);
							if (id.equals(lRequest.getSource())) {
								event.setType(Event.Type.LIGHTPATH_PROBLEM);
							} else {
								// Set the backward node
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								// Set the next hop in the packet
								rsvp.setNode(nextHop);
								// Set the new time of the event due to
								// transmission
								// time
								event.setTimeStamp(event.getTimeStamp()
										+ links.get(nextHop).getDelay());
								// Remove the entry in the history table
								this.historyTable.remove(rsvp.getFlowLabel());
							}
						}
					} else { // None or end-to-end routing
						if (target.equals(id)) { // RSVP reached the target node
							if (verbose)
								System.out.println(id
										+ " Marcando como LIGHTPATH_PROBLEM");
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
						} else { // intermediate nodes
							nextHop = rsvp.getBackwardNode();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
						}
					}
					break;
				case RP_NO_ROUTE_AVAILABLE:
					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																	// node
																	// re-routing
						// Remove the last visited node from the record route

						String last = rsvp.removeLastVisited();
						if (rsvp.getPathLength() == 0) {
							rsvp.getPath().addNode(last);
						}
						// Put it in the history table
						this.putHistoryTable(rsvp.getFlowLabel(), last);
						// System.out.println("ProcRT: "+id+" last: "+last);
						// Gets the request and update the try counter
						lRequest = (LightpathRequest) rsvp.getObject();
						lRequest.addTry();
						// Verify if we can make another re-routing attempt
						int currentAttempt = this.sizeHistoryTable(rsvp
								.getFlowLabel());
						// System.out.println("Cur: "+currentAttempt+" max: "+this.reroutingAttempts);
						// Gets the number of available neighbors
						int neighbors;
						if (id.equals(lRequest.getSource()))
							neighbors = this.links.size();
						else
							neighbors = this.links.size() - 1;
						// Allow a re-routing if there is allowed attempts and
						// sufficient neighbors
						if ((currentAttempt <= this.reroutingAttempts)
								&& (currentAttempt < neighbors)
								&& (lRequest.getTry() <= this.maxReroutingAttempts)
								&& (packet.getHopLimit() > 0)) {
							// Set as path message
							rsvp.setNextHeader(Packet.Header.RSVP_PATH);
							// Sets the previous label set
							rsvp.setLabelSet(labelSetTable.get(rsvp
									.getFlowLabel()));
							// Set the record route in the RSVP
							rsvp.setRecordRoute();
							// Reset the error
							rsvp.setError(null);
							// Reset the source-destination pair
							rsvp.setSDPair(lRequest.getSource(),
									lRequest.getTarget());
						} else { // Limit exceeded!
							// Set the error
							error = new Error(
									Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
							rsvp.setError(error);
							if (id.equals(lRequest.getSource())) {
								event.setType(Event.Type.LIGHTPATH_PROBLEM);
							} else {
								// Set the backward node
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								// Set the next hop in the packet
								rsvp.setNode(nextHop);
								// Set the new time of the event due to
								// transmission
								// time
								event.setTimeStamp(event.getTimeStamp()
										+ links.get(nextHop).getDelay());
								// Remove the entry in the history table
								this.historyTable.remove(rsvp.getFlowLabel());
							}
						}
					} else { // None or end-to-end routing
						if (target.equals(id)) { // RSVP reached the target node
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
						} else { // intermediate nodes
							nextHop = rsvp.getBackwardNode();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
						}
					}
					break;
				case RP_REROUTING_LIMIT_EXCEEDED: // Only for segment re-routing
					// Remove the last visited node from the record route
					String last = rsvp.removeLastVisited();
					// Put it in the history table
					this.putHistoryTable(rsvp.getFlowLabel(), last);
					// System.out.println("ProcRE: "+id+" last: "+last);
					// Gets the request and update the try counter
					lRequest = (LightpathRequest) rsvp.getObject();
					lRequest.addTry();
					// Verify if we can make another re-routing attempt
					int currentAttempt = this.sizeHistoryTable(rsvp
							.getFlowLabel());
					// System.out.println("Cur: "+currentAttempt);
					// Gets the number of available neighbors
					int neighbors;
					if (id.equals(lRequest.getSource()))
						neighbors = this.links.size();
					else
						neighbors = this.links.size() - 1;
					// Allow a re-routing if there is allowed attempts and
					// sufficient neighbors
					if ((currentAttempt <= this.reroutingAttempts)
							&& (currentAttempt < neighbors)
							&& (lRequest.getTry() <= this.maxReroutingAttempts)
							&& (packet.getHopLimit() > 0)) {
						// Set as path message
						rsvp.setNextHeader(Packet.Header.RSVP_PATH);
						// Sets the previous label set
						rsvp.setLabelSet(labelSetTable.get(rsvp.getFlowLabel()));
						// Set the record route in the RSVP
						rsvp.setRecordRoute();
						// Reset the error
						rsvp.setError(null);
						// Reset the source-destination pair
						rsvp.setSDPair(lRequest.getSource(),
								lRequest.getTarget());
					} else { // Limit exceeded!
						if (id.equals(lRequest.getSource())) {
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
							// if (rsvp.getPath().size() == 0)
							// rsvp.setNode(lRequest.getSource());
						} else { // Not in the source node
							// Set the backward node
							nextHop = rsvp.getBackwardNode();
							rsvp.addEffectiveHop();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
							// Remove the entry in the history table
							this.historyTable.remove(rsvp.getFlowLabel());
						}
					}
					break;
				}
				response = event;

			} else {
				// TRATAMENTO RESV_PATH_ERR INTERDOMINO.
				if (verbose)
					System.out.println("PATH_ERR INTER");
				// problema interdomonio - aqui é onde o codigo muda

				rsvp = (CrankRSVP) packet;
				// System.out.println(rsvp.toString());
				// Get the error
				error = rsvp.getError();
				// Remove this connection from the list of active connections
				// if the remove flag is enabled
				if (error.getRemoveFlag()) {
					activeConnections.remove(rsvp.getFlowLabel());
					Connection removed_perr = (Connection) rsvp.getObject();
					String forwardHop = removed_perr.getPath().getNextNode(id);
					LinkState link = links.get(forwardHop);
					if (link != null) { // Maybe the state was removed due to
										// failure
						// Get the associated mask
						WavelengthMask linkMaskRem = link.getMask();
						// Clear the wavelength
						linkMaskRem.clearWavelength(removed_perr
								.getWavelength());
					}
				}

				// Gets the target node
				target = rsvp.getTarget();
				LightpathRequest lRequest = null;
				// Process the message
				Error.Code code = error.getErrorCode();
				if (verbose)
					System.out.println("ERROR CODE: " + code);

				// TRATANDO ERROS QUANDO FOR INTERDOMIO.
				switch (code) {
				case RP_LABEL_SET: // tratando out of lambda

					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
						// node
						// re-routing
						// Remove the last visited node from the record route
						if (verbose)
							System.out.println("Tentando tentar novamente");
						String last = rsvp.removeLastVisited();

						// TENTATIVA DE EVITAR DEIXAR PATH VAZIO QUANDO CRANK NA
						// ORIGEM
						if (rsvp.getPathLength() == 0) {
							rsvp.getPath().addNode(last);
						}
						// Put it in the history table
						String label = rsvp.getFlowLabel();
						this.putHistoryTable(label, last);
						// System.out.println("ProcLA: "+id+" last: "+last);
						// Gets the request and update the try counter
						lRequest = (LightpathRequest) rsvp.getObject();
						lRequest.addTry();
						// Verify if we can make another re-routing attempt
						int currentAttempt = this.sizeHistoryTable(label);
						// Gets the number of available neighbors
						int neighbors;
						if (id.equals(lRequest.getSource()))
							neighbors = this.links.size();
						else
							neighbors = this.links.size() - 1;

					//	if (!getDomain(rsvp.getBackwardNode()).equals(
						//		getDomain(id))
							//	&& neighbors == 1) {
						//	neighbors = this.links.size();
					//	}
						// Allow a re-routing if there is allowed attempts and
						// sufficient neighbors
						if ((currentAttempt <= this.reroutingAttempts)
								&& (currentAttempt < neighbors)
								&& (lRequest.getTry() <= this.maxReroutingAttempts)
								&& (packet.getHopLimit() > 0)) {

							// marca que esta tentando fazer rerouting.
							rsvp.setRerouting(true);
							// Set as path message
							rsvp.setNextHeader(Packet.Header.RSVP_PATH);
							// Sets the previous label set
							rsvp.setLabelSet(labelSetTable.get(rsvp
									.getFlowLabel()));
							// Set the record route in the RSVP
							rsvp.setRecordRoute();
							// Reset the error
							rsvp.setError(null);
							rsvp.addIgnoreASBR(last);

							// Reset the source-destination pair
							rsvp.setSDPair(lRequest.getSource(),
									lRequest.getTarget());
							if (verbose)
								System.out.println(id + "- "
										+ rsvp.getFlowLabel()
										+ " - tentando novamente.");
						} else { // Limit exceeded!
							// Set the error
							error = new Error(
									Error.Code.RP_REROUTING_LIMIT_EXCEEDED);

							rsvp.setError(error);

							if (id.equals(lRequest.getSource())) {
								event.setType(Event.Type.LIGHTPATH_PROBLEM);
							} else {
								// Set the backward node
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								// Set the next hop in the packet
								rsvp.setNode(nextHop);

								// Set the new time of the event due to
								// transmission
								// time
								event.setTimeStamp(event.getTimeStamp()
										+ links.get(nextHop).getDelay());
								// Remove the entry in the history table
								this.historyTable.remove(rsvp.getFlowLabel());
							}
						}
					}
					break;
				case LSP_FAILURE:
					if (target.equals(id)) { // RSVP reached the target node
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						try{
						nextHop = rsvp.getPath().getPreviousNode(id);
						}catch(Exception e){
							nextHop = null;
						}
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						if(nextHop !=null)
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
					break;
				case ADMISSION_CONTROL_FAILURE:
					if (this.rerouting.equals(ReRouting.SEGMENT)) {
						// Remove the connection from the history table
						this.historyTable.remove(rsvp.getFlowLabel());
					}
					if (target.equals(id)) { // RSVP reached the target node
						event.setType(Event.Type.LIGHTPATH_PROBLEM);
					} else { // intermediate nodes
						nextHop = rsvp.getBackwardNode();
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
					break;

				case RP_NO_ROUTE_AVAILABLE:
					if (this.rerouting.equals(ReRouting.SEGMENT)) { // Intermediate
																	// node
																	// re-routing
						// Remove the last visited node from the record route

						String last = rsvp.removeLastVisited();
						if (rsvp.getPathLength() == 0) {
							rsvp.getPath().addNode(last);
						}
						// Put it in the history table
						this.putHistoryTable(rsvp.getFlowLabel(), last);
						// System.out.println("ProcRT: "+id+" last: "+last);
						// Gets the request and update the try counter
						lRequest = (LightpathRequest) rsvp.getObject();
						lRequest.addTry();
						// Verify if we can make another re-routing attempt
						int currentAttempt = this.sizeHistoryTable(rsvp
								.getFlowLabel());
						// System.out.println("Cur: "+currentAttempt+" max: "+this.reroutingAttempts);
						// Gets the number of available neighbors
						int neighbors;
						if (id.equals(lRequest.getSource()))
							neighbors = this.links.size();
						else
							neighbors = this.links.size() - 1;
						// Allow a re-routing if there is allowed attempts and
						// sufficient neighbors
						if ((currentAttempt <= this.reroutingAttempts)
								&& (currentAttempt < neighbors)
								&& (lRequest.getTry() <= this.maxReroutingAttempts)
								&& (packet.getHopLimit() > 0)) {
							// Set as path message
							rsvp.setNextHeader(Packet.Header.RSVP_PATH);
							// Sets the previous label set
							rsvp.setLabelSet(labelSetTable.get(rsvp
									.getFlowLabel()));
							// Set the record route in the RSVP
							rsvp.setRecordRoute();
							// Reset the error
							rsvp.setError(null);
							// Reset the source-destination pair
							rsvp.setSDPair(lRequest.getSource(),
									lRequest.getTarget());
						} else { // Limit exceeded!
							// Set the error
							error = new Error(
									Error.Code.RP_REROUTING_LIMIT_EXCEEDED);
							rsvp.setError(error);
							if (id.equals(lRequest.getSource())) {
								event.setType(Event.Type.LIGHTPATH_PROBLEM);
							} else {
								// Set the backward node
								nextHop = rsvp.getBackwardNode();
								rsvp.addEffectiveHop();
								// Set the next hop in the packet
								rsvp.setNode(nextHop);
								// Set the new time of the event due to
								// transmission
								// time
								event.setTimeStamp(event.getTimeStamp()
										+ links.get(nextHop).getDelay());
								// Remove the entry in the history table
								this.historyTable.remove(rsvp.getFlowLabel());
							}
						}
					} else { // None or end-to-end routing
						if (target.equals(id)) { // RSVP reached the target node
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
						} else { // intermediate nodes
							nextHop = rsvp.getBackwardNode();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
						}
					}
					break;
				case RP_REROUTING_LIMIT_EXCEEDED: // Only for segment re-routing
					// Remove the last visited node from the record route
					String last = rsvp.removeLastVisited();
					// Put it in the history table
					this.putHistoryTable(rsvp.getFlowLabel(), last);
					// System.out.println("ProcRE: "+id+" last: "+last);
					// Gets the request and update the try counter
					lRequest = (LightpathRequest) rsvp.getObject();
					lRequest.addTry();
					// Verify if we can make another re-routing attempt
					int currentAttempt = this.sizeHistoryTable(rsvp
							.getFlowLabel());
					// System.out.println("Cur: "+currentAttempt);
					// Gets the number of available neighbors
					int neighbors;
					if (id.equals(lRequest.getSource()))
						neighbors = this.links.size();
					else
						neighbors = this.links.size() - 1;
					// Allow a re-routing if there is allowed attempts and
					// sufficient neighbors
					if ((currentAttempt <= this.reroutingAttempts)
							&& (currentAttempt < neighbors)
							&& (lRequest.getTry() <= this.maxReroutingAttempts)
							&& (packet.getHopLimit() > 0)) {
						// Set as path message
						rsvp.setNextHeader(Packet.Header.RSVP_PATH);
						// Sets the previous label set
						rsvp.setLabelSet(labelSetTable.get(rsvp.getFlowLabel()));
						// Set the record route in the RSVP
						rsvp.setRecordRoute();
						// Reset the error
						rsvp.setError(null);
						// Reset the source-destination pair
						rsvp.setSDPair(lRequest.getSource(),
								lRequest.getTarget());
					} else { // Limit exceeded!
						if (id.equals(lRequest.getSource())) {
							event.setType(Event.Type.LIGHTPATH_PROBLEM);
							// if (rsvp.getPath().size() == 0)
							// rsvp.setNode(lRequest.getSource());
						} else { // Not in the source node
							// Set the backward node
							nextHop = rsvp.getBackwardNode();
							rsvp.addEffectiveHop();
							// Set the next hop in the packet
							rsvp.setNode(nextHop);
							// Set the new time of the event due to transmission
							// time
							event.setTimeStamp(event.getTimeStamp()
									+ links.get(nextHop).getDelay());
							// Remove the entry in the history table
							this.historyTable.remove(rsvp.getFlowLabel());
						}
					}
					break;
				}
				response = event;
			}
			break;
		case RSVP_RESV:

			rsvp = (CrankRSVP) packet;
			if (verbose)
				System.out.println(id + " - resv request: "
						+ rsvp.getFlowLabel());
			target = rsvp.getTarget();

			// Verify if its a interdomain or intradomain request
			// Check if the target is in the same domain of the current node
			if (getDomain(target).equals(getDomain(id))) {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Intra-domain request from: "
							+ rsvp.getSource() + " to: " + target);
				intra = true;
				inter = false;
			} else {
				if (verbose)
					System.out.println(rsvp.getFlowLabel() + " - " + id
							+ " - Received an Inter-domain request from "
							+ rsvp.getSource() + " to: " + target);
				inter = true;
				intra = false;
			}

			if (intra) // trata como o gustavo
			{
				// TRATAMENTO DA RSVP_RESV PARA INTRADOMINIO
				rsvp = (CrankRSVP) packet;

				// System.out
				// .println(rsvp.getFlowLabel() + " tentando reservar " + id);
				// System.out.println(rsvp.toString());
				/* Update the the wavelength mask of this node. */
				Connection connection = (Connection) rsvp.getObject();
				// Get the forward node
				String fwdId = rsvp.getForwardNode();
				// Get the associated mask
				WavelengthMask linkMask = links.get(fwdId).getMask();
				// See the status of the wavelength
				if (linkMask.testWavelength(connection.getWavelength())) {
					// System.out.println("Adding conn: "+rsvp.getFlowLabel()+" with: "+connection.toString()+" to intermediate node: "+id);
					activeConnections.put(rsvp.getFlowLabel(), connection);
					// Set the wavelength
					linkMask.setWavelength(connection.getWavelength());
					if (this.rerouting.equals(ReRouting.SEGMENT)) {
						// Remove the connection from the history table
						this.historyTable.remove(rsvp.getFlowLabel());
					}
					// Verify if the resv message reached the source node.
					target = rsvp.getTarget();
					if (target.equals(id)) {
						if (verbose)
							System.out.println("RESERVADO!");
						event.setType(Event.Type.LIGHTPATH_ESTABLISHED);
					} else { // Intermediate node
						// See the next hop
						nextHop = rsvp.getBackwardNode();
						// In case of failure
						if (!(CrankControlPlane.hasConnectivity(id, nextHop))) {
							// System.err.println("connectivity:" +
							// rsvp.toString());
							// Send a resvErr msg to the sender
							rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
							rsvp.setSDPair(id, connection.getTarget());
							// Add the error
							error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
							rsvp.setError(error);
							// Change the next hop
							nextHop = rsvp.getForwardNode();
						}
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
				} else { // Contention problem!
					// Send a resvErr msg to the sender
					rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
					rsvp.setSDPair(id, connection.getTarget());
					// Set the error
					error = new Error(Error.Code.ADMISSION_CONTROL_FAILURE);
					rsvp.setError(error);
					// See the next hop
					nextHop = rsvp.getForwardNode();
					// Set the next hop in the packet
					rsvp.setNode(nextHop);
					// Set the new time of the event due to transmission time
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				}
				response = event;
			} else {

				// TRATAMENTO DA RSVP_RESV PARA INTERDOMINO
				if (verbose)
					System.out.println(rsvp.getFlowLabel()
							+ " tentando reservar " + id + " para "
							+ rsvp.getTarget());

				// System.out.println(rsvp.toString());
				/* Update the the wavelength mask of this node. */
				Connection connection = (Connection) rsvp.getObject();
				// Get the forward node
				String fwdId = rsvp.getForwardNode();
				// Get the associated mask
				WavelengthMask linkMask = links.get(fwdId).getMask();
				// See the status of the wavelength
				if (linkMask.testWavelength(connection.getWavelength())) {
					// System.out.println("Adding conn: "+rsvp.getFlowLabel()+" with: "+connection.toString()+" to intermediate node: "+id);
					activeConnections.put(rsvp.getFlowLabel(), connection);
					// Set the wavelength
					linkMask.setWavelength(connection.getWavelength());
					if (this.rerouting.equals(ReRouting.SEGMENT)) {
						// Remove the connection from the history table
						this.historyTable.remove(rsvp.getFlowLabel());
					}
					
					// Verify if the resv message reached the source node.
					target = rsvp.getTarget();
					if (target.equals(id)) {
						System.out.println("RESERVADO!");
						event.setType(Event.Type.LIGHTPATH_ESTABLISHED);

					} else { // Intermediate node
						// See the next hop
						nextHop = rsvp.getBackwardNode();
						// In case of failure
						if (!(AntNetCrankInterControlPlane.hasConnectivity(id,
								nextHop))) {
							if (verbose)
								System.err.println("connectivity:"
										+ rsvp.toString());
							// Send a resvErr msg to the sender
							rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
							rsvp.setSDPair(id, connection.getTarget());
							// Add the error
							error = new Error(Error.Code.RP_NO_ROUTE_AVAILABLE);
							rsvp.setError(error);
							// Change the next hop
							nextHop = rsvp.getForwardNode();
						}
						// Set the next hop in the packet
						rsvp.setNode(nextHop);
						// Set the new time of the event due to transmission
						// time
						event.setTimeStamp(event.getTimeStamp()
								+ links.get(nextHop).getDelay());
					}
				} else { // Contention problem!
					// Send a resvErr msg to the sender
					rsvp.setNextHeader(Packet.Header.RSVP_RESV_ERR);
					rsvp.setSDPair(id, connection.getTarget());
					// Set the error
					error = new Error(Error.Code.ADMISSION_CONTROL_FAILURE);
					rsvp.setError(error);
					// See the next hop
					nextHop = rsvp.getForwardNode();
					// Set the next hop in the packet
					rsvp.setNode(nextHop);
					// Set the new time of the event due to transmission time
					event.setTimeStamp(event.getTimeStamp()
							+ links.get(nextHop).getDelay());
				}
				response = event;
			}

			break;
		case RSVP_RESV_TEAR:
			rsvp = (CrankRSVP) packet;
			/* Update the the wavelength mask of this node. */
			// Get the backward node
			String forwardId = rsvp.getForwardNode();
			// Get the associated mask
			WavelengthMask linkMaskTear = links.get(forwardId).getMask();
			// Clear the wavelength
			int clear = ((Connection) rsvp.getObject()).getWavelength();
			linkMaskTear.clearWavelength(clear);
			// Verify if the RSVP reached the source node.
			if (rsvp.getSource().equals(id)) {
				event.setType(Event.Type.LIGHTPATH_REMOVED);
			} else {
				// See the next hop
				nextHop = rsvp.getBackwardNode();
				rsvp.setNode(nextHop);
			}
			response = event;
			break;

		case RSVP_RESV_ERR:
			rsvp = (CrankRSVP) packet;
			// Remove the connection from the table of connections
			activeConnections.remove(rsvp.getFlowLabel());
			Connection removed_rerr = (Connection) rsvp.getObject();
			target = rsvp.getTarget();
			if (target.equals(id)) { // Now, send a PathErr to the ingress node
				// Convert to pathErr msg
				rsvp.setNextHeader(Packet.Header.RSVP_PATH_ERR);
				// System.out.println("Creating: "+rsvp.toString());
				// Reset the SD pair to the new values
				rsvp.setSDPair(id, removed_rerr.getSource());
			} else {
				nextHop = rsvp.getForwardNode();
				// Get the associated mask
				WavelengthMask linkMaskErr = links.get(nextHop).getMask();
				// Clear the wavelength
				linkMaskErr.clearWavelength(removed_rerr.getWavelength());
				// Set the next hop in the packet
				rsvp.setNode(nextHop);
				// Set the new time of the event due to transmission time
				event.setTimeStamp(event.getTimeStamp()
						+ links.get(nextHop).getDelay());
			}
			response = event;
			break;
		case LINK_FAILURE: // Packet associated with control plane (separated
							// channel)
			// Identify the failure
			Failure failure = (Failure) packet.getPayload();
			// Flooding of the failure
			if (!failureID.contains(failure.getID())) { // First time
				// Mock local update of the topology
				routingTable.updateFromTopology(graph);
				// Broadcast the failure to the neighbors
				String previousHop = packet.getSource();
				Vector<String> neighbors = graph.adjacentNodes(id);
				Vector<Event> broadcast = new Vector<Event>();
				// Adds the flooding information
				for (String neighId : neighbors) {
					if (!neighId.equals(previousHop)) { // not visited
						// Retransmit the packet
						Packet clonedFrom = (Packet) packet.clone();
						clonedFrom.retransmit(id, neighId);
						clonedFrom.setNode(neighId);
						// Set new time stamp
						double transmissionTime = this.links.get(neighId)
								.getDelay();
						double newTimeStamp = event.getTimeStamp()
								+ transmissionTime;
						// Add to the list of broadcast
						broadcast.add(new Event(newTimeStamp,
								Event.Type.PACKET_ARRIVAL, clonedFrom));
					}
				}
				// Add the failure to the list of processed ones.
				failureID.add(failure.getID());
				/* Remove the link states affected by the failure. */
				if (((Edge) failure.getInformation()).getSource().equals(id)) {
					links.remove(((Edge) failure.getInformation())
							.getDestination());
				}
				/* Treatment the failure by the neighbor nodes */
				// Now, if it is the closest node upstream to the failure
				if (id.equals(((Edge) failure.getInformation()).getSource())) {
					// Adds the notification of broken LSP to the event
					for (String activeID : activeConnections.keySet()) {
						// Gets the connection
						Connection active = activeConnections.get(activeID);
						// Locate the failure
						Location location = failure
								.locate(id, active.getPath());
						if (!location.equals(Failure.Location.NOT_APPLICABLE)) {
							// Create the PathErr packet
							CrankRSVP pathErr = new CrankRSVP(active,
									Packet.Header.RSVP_PATH_ERR, id,
									active.getSource());
							// Create the error with path remove flag
							error = new Error(Error.Code.LSP_FAILURE, true);
							pathErr.setError(error);
							pathErr.setFlowLabel(activeID);
							// System.out.println(pathErr.toString());
							double transmissionTime = 0;
							// Not the source node to treat the failure
							if (!id.equals(active.getPath().firstNode())) {
								nextHop = active.getPath().getPreviousNode(id);
								pathErr.setNode(nextHop);
								// Set new time stamp
								transmissionTime = this.links.get(nextHop)
										.getDelay();
							}
							double newTimeStamp = event.getTimeStamp()
									+ transmissionTime + DELTA_TIME;
							broadcast.add(new Event(newTimeStamp,
									Event.Type.PACKET_ARRIVAL, pathErr));
						}
					}
					// Now, if it is the closest node downstream to the failure
				} else if (id.equals(((Edge) failure.getInformation())
						.getDestination())) {
					// Adds the notification of broken LSP to the event
					for (String activeID : activeConnections.keySet()) {
						// Gets the connection
						Connection active = activeConnections.get(activeID);
						// Locate the failure
						Location location = failure
								.locate(id, active.getPath());
						if (!location.equals(Failure.Location.NOT_APPLICABLE)) {
							// Create the PathErr packet
							CrankRSVP pathTear = new CrankRSVP(active,
									Packet.Header.RSVP_PATH_TEAR, id,
									active.getTarget());
							// Create the error
							error = new Error(Error.Code.LSP_FAILURE);
							pathTear.setError(error);
							pathTear.setFlowLabel(activeID);
							// System.out.println(pathTear.toString());
							// Not the target node to treat the failure
							double transmissionTime = 0;
							// Not the last node to tackle the failure
							if (!id.equals(active.getPath().lastNode())) {
								nextHop = active.getPath().getNextNode(id);
								pathTear.setNode(nextHop);
								transmissionTime = this.links.get(nextHop)
										.getDelay();
							}
							// Set new time stamp
							double newTimeStamp = event.getTimeStamp()
									+ transmissionTime + DELTA_TIME;
							// Add to the list of broadcast
							broadcast.add(new Event(newTimeStamp,
									Event.Type.PACKET_ARRIVAL, pathTear));
						}
					}
				}
				// System.out.println(broadcast.toString());
				// Return the multiple packets associated with the failure
				response = new Event(event.getTimeStamp(), Event.Type.MULTIPLE,
						broadcast);
			} else { // Already processed the failure.
				event.setType(Event.Type.IGNORE);
				response = event;
			}
			break;
		}
		return response;
	}

	/**
	 * Returns the most used label set for wavelength assignment.
	 * 
	 * @param usage
	 *            The usage vector.
	 * @return The most used label set for wavelength assignment.
	 */
	protected int[] getMostUsed(long[] usage) {
		int[] order = new int[usage.length];
		for (int i = 0; i < usage.length; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for (int j = 0; j < usage.length; j++) {
				if (usage[j] > max) {
					max = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MIN_VALUE;
		}
		return order;
	}

	protected int[] getMostUsed(int w) {
		// Gets the total usage for all destinations
		WavelengthUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage, 0L);
		for (int i = 0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for (int j = 0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		// Now order from the most used to the least used
		int[] order = new int[w];
		for (int i = 0; i < w; i++) {
			long max = Long.MIN_VALUE;
			int pos = -1;
			for (int j = 0; j < w; j++) {
				if (totalUsage[j] > max) {
					max = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MIN_VALUE;
		}
		return order;
	}

	/**
	 * Returns the least used label set for wavelength assignment.
	 * 
	 * @param usage
	 *            The usage vector.
	 * @return The least used label set for wavelength assignment.
	 */
	protected int[] getLeastUsed(long[] usage) {
		int[] order = new int[usage.length];
		for (int i = 0; i < usage.length; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for (int j = 0; j < usage.length; j++) {
				if (usage[j] < min) {
					min = usage[j];
					pos = j;
				}
			}
			order[i] = pos;
			usage[pos] = Long.MAX_VALUE;
		}
		return order;
	}

	protected int[] getLeastUsed(int w) {
		// Gets the total usage for all destinations
		WavelengthUsage[] table = lambdaTable.getArray();
		long totalUsage[] = new long[w];
		Arrays.fill(totalUsage, 0L);
		for (int i = 0; i < table.length; i++) {
			if (table[i] != null) {
				long usage[] = table[i].getUsage();
				for (int j = 0; j < w; j++) {
					totalUsage[j] = totalUsage[j] + usage[j];
				}
			}
		}
		// Now order from the least used to the most used
		int[] order = new int[w];
		for (int i = 0; i < w; i++) {
			long min = Long.MAX_VALUE;
			int pos = -1;
			for (int j = 0; j < w; j++) {
				if (totalUsage[j] < min) {
					min = totalUsage[j];
					pos = j;
				}
			}
			order[i] = pos;
			totalUsage[pos] = Long.MAX_VALUE;
		}
		return order;
	}

	/*
	 * Obtain the domain of a node Its an alias for OBGPControlPlane.getDomain
	 */
	protected String getDomain(String node) {
		return AntNetCrankInterControlPlane.getDomain(node);
	}
	
	

	protected void updateGraph(Graph graph) {
		this.graph = graph;
	}

	protected void removeLink(String id) {
		this.links.remove(id);
	}

}
