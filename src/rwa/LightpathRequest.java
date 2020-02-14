/*
 * Created on Sep 20, 2005.
 */
package rwa;

import ops.Payload;

/**
 * Encapsulates the attributes of a ligthpath request.  
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class LightpathRequest implements Payload {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The source node. */
	protected String source;
	/** The target node. */
	protected String target;
	/** The duration of this lightpath. */
	protected double duration;
	/** The number of alternative tries allowed to establish the request.*/
	protected int tries;
	/** The current try. */
	protected int currentTry;
	/** The number of try - not erasable. */
	protected int triesHistory;
	
	/**
	 * Creates a new LightpathRequest object.
	 * 
	 * @param sourceId The source node.
	 * @param targetId The target node.
	 * @param aDuration The duration of this lightpath.
	 */
	public LightpathRequest(String sourceId, String targetId, double aDuration) {
		source = sourceId;
		target = targetId;
		duration = aDuration;
		tries = 0;
		currentTry = 0;
		triesHistory = 0;
	}

	/**
	 * Creates a new LightpathRequest object.
	 * 
	 * @param sourceId The source node.
	 * @param targetId The target node.
	 * @param aDuration The duration of this lightpath.
	 * @param maxTries The maximum number of alternative tries that this request should try.
	 */
	public LightpathRequest(String sourceId, String targetId, double aDuration, int maxTries) {
		source = sourceId;
		target = targetId;
		duration = aDuration;
		tries = maxTries;
		currentTry = 0;
		triesHistory = 0;
	}
	
	/**
	 * For cloning purposes.
	 */
	protected LightpathRequest() {
		
	}
	/**
	 * Get the source node of this request.
	 * @return The source node of this request.
	 */
	public String getSource() {
		return source;
	}
	
	/**
	 * Get the target node of this request.
	 * @return The target node of this request.
	 */
	public String getTarget() {
		return target;
	}
	
	/**
	 * Gets the duration of this lightpath. 	
	 */
	public double getDuration() {
		return this.duration;
	}
	
	/**
	 * Set the new residual duration of this request.
	 * @param aDuration The new duration.
	 */
	public void setDuration(double aDuration) {
		this.duration = aDuration;
	}
	
	/**
	 * Verify if a new connection establishment should be tried. 
	 * 
	 * @return True, if a new connection establishment should be tried.
	 */
	public boolean tryAgain() {
		return (tries >= currentTry && triesHistory <=1);
	}
	
	/**
	 * Add a tentative of connection establishment.
	 */
	public void addTry() {
	    currentTry = currentTry + 1;	
	    triesHistory +=1;
	}
	
	public void setMaxTries(int newTries) {
		this.tries = newTries;
	}
	
	/**
	 * Returns the current try.
	 * @return
	 */
	public int getTry() {
		return this.currentTry;
	}

	/** 
	 * Resets the number of tentatives.
	 */
	public void resetTry() {
		this.currentTry = 0;
	}
	
	public int getTriesHistory() {
		return triesHistory;
	}

	public void setTriesHistory(int triesHistory) {
		this.triesHistory = triesHistory;
	}

	/**
	 * Returns a String representation of this request.
	 * @return A String representation of this request.
	 */
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("src: ");
		buf.append(source);
		buf.append(", tg: ");
		buf.append(target);
		buf.append(", duration: ");
		buf.append(duration);
		buf.append(", try ");
		buf.append(currentTry);
		buf.append(" of ");
		buf.append(this.tries);
		return buf.toString();
	}
	
	/**
	 * Returns a clone of this object.
	 */
	public Object clone() {
		LightpathRequest cloned = new LightpathRequest();
		cloned.currentTry = this.currentTry;
		cloned.duration = this.duration;
		cloned.tries = this.tries;
		cloned.source = new String(this.source);
		cloned.target = new String(this.target);
		return cloned;
	}
	
}
