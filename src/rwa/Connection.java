/*
 * Connection.java
 *
 * Created on 19 de Maio de 2003, 11:05
 * Modified on March 23, 2004 - Added support for dynamic traffic
 * Modified on Sep 20, 2005 - Modified for the new event-driven model.
 */

package rwa;
import graph.*;

/**
 * A Class-wrapper for defining the attributes of a connection.
 * 
 * @author Gustavo Sousa Pavani 
 * @version 1.2
 */
public class Connection implements java.io.Serializable, Cloneable {
	static final long serialVersionUID = 1L;
	/** The lightpath request associated with this connection. */
	protected LightpathRequest lightpathRequest;
    /** Path from source to target station. */
	protected Path path;
    /** Used wavelength in this lightpath. */
	protected int wavelength;
    /** Unique identification of the flow (lightpath). */
	protected String uniqueID;
    /** The start time of this connection. */
	protected double startTime;
    
    /**
     * Creates new Connection - for clonning purposes.
     */
    protected Connection() {        
    }
    

    /** Creates new Connection.
     * @param aPath Path from source to target station. 
     * @param aWavelength Used wavelength in this lightpath.
     */
    public Connection(Path aPath, int aWavelength) {
        path = aPath;
        wavelength = aWavelength;
    }

    /** Creates new Connection.
     * @param aPath Path from source to target station. 
     * @param aWavelength Used wavelength in this lightpath.
     * @param aUniqueID The unique ID of this connection.
     * @param request The lightpath request associated with this connection.
     */
    public Connection(Path aPath, int aWavelength, String aUniqueID, LightpathRequest request) {
        path = aPath;
        wavelength = aWavelength;
        uniqueID = aUniqueID;
        lightpathRequest = request;
    }
    
    /**
     * Returns the path of this connection.
     * @return The path of this connection.
     */
    public Path getPath() {
        return this.path;
    }
    
    /**
     * Returns the number of hops of this connection.
     * @return The number of hops of this connection.
     */
    public int size() {
    	return (this.path.size() - 1);
    }
    
    /**
     * Returns the wavelength used in this connection.
     * @return The wavelength used in this connection.
     */
    public int getWavelength() {
        return this.wavelength;
    }
        
    /**
     * Returns the source of this connection.
     * @return The source of this connection.
     */    
    public String getSource() {
        return path.firstNode();
    }
        
    /**
     * Returns the target of this connection.
     * @return The target of this connection.
     */
    public String getTarget() {
        return path.lastNode();
    }    

    /**
     * Returns the unique identification of this connection.
     * @return The unique identification of this connection.
     */
    public String getID() {
    	return this.uniqueID;
    }
    
    /**
     * Returns a clone object of this connection.
     * @return A clone object of this connection.
     */
    public Object clone() {
        Connection conn = new Connection();
        conn.path = (Path) this.path.clone();
        conn.wavelength = this.wavelength;
        conn.uniqueID = this.uniqueID;
        conn.lightpathRequest = (LightpathRequest)this.lightpathRequest.clone();
        return (Object)conn;
    }
    
    /**
     * Returns the request associated to this connection.
     * @return The request associated to this connection.
     */
    public LightpathRequest getRequest() {
    	return this.lightpathRequest;
    }
    
    /**
     * Returns a String representation of this object.
     * @return A String representation of this object.
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("UID: ");
        buffer.append(uniqueID);
        buffer.append(", route: ");
        buffer.append(path.toString());
        buffer.append(", wavelength: ");
        buffer.append(wavelength);
        buffer.append(", request: ");
        buffer.append(this.lightpathRequest.toString());
        return buffer.toString();
    }


	/**
	 * Returns the time when this connection started.
	 * @return The time when this connection started.
	 */
	public double getStartTime() {
		return startTime;
	}


	/**
	 * Sets ghe time when this connection started.
	 * @param startTime The start time.
	 */
	public void setStartTime(double startTime) {
		this.startTime = startTime;
	}
    
    
}
