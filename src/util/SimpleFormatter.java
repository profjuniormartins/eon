package util;

import java.io.*;
import java.util.logging.*;

/**
 * Print a brief summary of the LogRecord in a human readable
 * format.  The summary will typically be 1 or 2 lines.
 *
 * @version 1.12, 12/03/01 modified by Gustavo Pavani 20/02/2003
 */

public class SimpleFormatter extends Formatter {

    // Line separator string.  This is the value of the line.separator
    // property at the moment that the SimpleFormatter was created.
 	private String lineSeparator = "\n";

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public synchronized String format(LogRecord record) {
	StringBuilder sb = new StringBuilder();
	sb.append(record.getLevel().getLocalizedName());
	sb.append(": ");
	String message = formatMessage(record);
	sb.append(message);
	sb.append(lineSeparator);
	if (record.getThrown() != null) {
	    try {
	        StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        record.getThrown().printStackTrace(pw);
	        pw.close();
		sb.append(sw.toString());
	    } catch (Exception ex) {
	    }
	}
	return sb.toString();
    }
}
