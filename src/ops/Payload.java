/*
 * Created on 26/01/2006.
 */
package ops;

/**
 * Interface to define the general caractheristics of the 
 * payload.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public interface Payload extends Cloneable, java.io.Serializable {

	/**
	 * Returns a clone object of this payload.
	 * @return
	 */
	public Object clone();
}
