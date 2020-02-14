/**
 * 
 */

package rwa.crankback.obgp;



import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Vector;

/**
 * This class represents an OBGP Routing Table All nodes, from all domain, will
 * have access to it in order to process an interdomain path request and update
 * it with OBGP UPDATE messages
 * 
 * @author andre
 * 
 */
public class OBGPRoutingTable {
	
	protected static Vector<LinkedHashMap<String, Vector<String>>> teste = new Vector<LinkedHashMap<String, Vector<String>>>();
	
	
	
	
	/**
	 * Ideia da CrankRoutes
	 * 
	 * Sera uma nova tabela de roteamento que faz uso do crackback. 
	 * A mesma usara a mesma string de identificacao para reconhecer  o par SD
	 * E com isto, ira devolver um arraylist contendo todos os K possiveis caminhos
	 * interdominio
	 * 
	 * Para que esta possa funcionar corretamente, tenho que implementar multiplos
	 * caminhos interdominio.
	 * Logo, tenho que colocar esta informacao no XML e gerar novamente esta tabela de roteamento 
	 */
	protected static LinkedHashMap<String, ArrayList<Vector<String>>> crankRoutes;
	
	
	
	
	

	protected static LinkedHashMap<String, Vector<String>> routes;

	public OBGPRoutingTable() {
		//routes = new LinkedHashMap<String, Vector<String>>();
		crankRoutes = new  LinkedHashMap<String, ArrayList<Vector<String>>>();
		
		

	}

	public OBGPRoutingTable(LinkedHashMap<String, ArrayList<Vector<String>>> route) {
		this.crankRoutes = route;

	}

	public static void putEntry(String key, ArrayList<Vector<String>> paths) {
		
		crankRoutes.put(key, paths);
	//	System.out.println("Adicionando"  + key  +   paths.toString());
	}

	

	public void removeEntry(String source, String dest) {
		String key = source + "-" + dest;
		crankRoutes.remove(key);
	}

	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (String key : crankRoutes.keySet()) {
			buffer.append(key+":");
			buffer.append("\n");
			buffer.append(crankRoutes.get(key).toString()+"\n");
		}

		return buffer.toString();
	}
	
	public static String getNextHop(String source, String target, int trying) {
		if(source.contains(":")) {
			source = source.split(":")[0];
		}
		if(target.contains(":")) {
			target = target.split(":")[0];
		}
		String key = source+"-"+target;
		//System.out.println("Me pediram o nexthop, estou devolvendo: " + crankRoutes.get(key).get(trying).firstElement());
		return crankRoutes.get(key).get(trying).firstElement();
	}

	
	public static Vector<String> getASPath(String source, String target, int trying) {
		if(source.contains(":")) {
			source = source.split(":")[0];
		}
		if(target.contains(":")) {
			target = target.split(":")[0];
		}
		String key = source+"-"+target;
		return crankRoutes.get(key).get(trying);
	}
	


}
