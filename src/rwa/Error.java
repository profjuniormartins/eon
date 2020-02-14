/*
 * Created on Jan 28, 2006.
 */
package rwa;

import java.io.Serializable;

/**
 * Represents an error on RSVP-TE. This class can be seen as the
 * ERROR_SPEC object with additional information.
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class Error implements Serializable {
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;
	/** The error codes for RSVP-TE. */
	public enum Code{
		/** Routing problem / Label Set */ RP_LABEL_SET,
		/** Routing problem / Re-routing limit Exceeded. */ RP_REROUTING_LIMIT_EXCEEDED,
		/** Routing problem / No route available toward destination. */ RP_NO_ROUTE_AVAILABLE, 
		/** Admission control Failure. */ ADMISSION_CONTROL_FAILURE,
		/** Service preempted / no available resource. */ SERVICE_PREEMPTED,
		/** LSP failure. */ LSP_FAILURE,
		/** Inter LSP ReRouting **/ RP_INTER_REROUTING
	}
	/**
	 * Flag to free resources when a failure occurs. To be used with PathErr and ResvErr. */
	protected boolean pathStateRemoveFlag = false;
	/** The actual error code of this error. */
	protected Code value;
	
	/**
	 * Creates a new Error object.
	 * @param aValue The value of the error code.
	 */
	public Error(Code aValue) {
		this.value = aValue;
	}

	/**
	 * Creates a new Error object.
	 * @param aValue The value of the error code.
	 * @param aRemoveFlag The value of the remove flag.
	 */
	public Error(Code aValue, boolean aRemoveFlag) {
		this.value = aValue;
		this.pathStateRemoveFlag = aRemoveFlag;
	}

	/**
	 * Returns the error code value of this object.
	 * @return The error code value of this object.
	 */
	public Code getErrorCode() {
		return this.value;
	}

	/**
	 * Gets the value of the "Path_State_remove_flag".
	 * @return The value of the "Path_State_remove_flag".
	 */
	public boolean getRemoveFlag() {
		return this.pathStateRemoveFlag;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Code: ");
		builder.append(value.toString());
		if (this.pathStateRemoveFlag) 
			builder.append(" [PATH_REMOVE_FLAG]");
		return builder.toString();
	}
}
