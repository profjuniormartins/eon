package util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Classe para ordenacao de um Mapa (HashMap, por exemplo) com base em seus
 * valores e não se suas chaves. Funciona para qualquer tipo de mapa.
 * 
 * 
 * @author Andre Filipe de M. Batista
 * @version 1.0 - Junho de 2012
 * 
 * 
 * 
 */
public class MapSortter {

	/**
	 * Metodo para ordenacao de um hashMap com base em seus valores. Funciona
	 * para qualquer tipo de mapa.
	 * 
	 * @param unsortMap
	 *            um hashmap a ser sorteado
	 * @return um mapa já ordenado com base nos valores (e nao nas chaves)
	 * @version 1.0 em 11/06/2012.
	 */
	public static Map sortByComparator(Map unsortMap) {

		List list = new LinkedList(unsortMap.entrySet());

		// sort list based on comparator
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o1)).getValue())
						.compareTo(((Map.Entry) (o2)).getValue());
			}
		});

		// put sorted list into map again
		Map sortedMap = new LinkedHashMap();
		for (Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

}
