package com.Lomikel.Apps;

// Java
import java.io.Serializable;

// Log4J
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.appender.AbstractAppender;

/** <code>ConsoleAppender</code> appends Log4J messages
  * to the {@link Console}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class BSConsoleAppender extends AbstractAppender {

  protected BSConsoleAppender(String                         name,
                              Filter                         filter,
                              Layout<? extends Serializable> layout,
                              boolean                        ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }
    
  @Override
	public void append(LogEvent event) {
    String loggerName = event.getLoggerName();
    loggerName = loggerName.substring(loggerName.lastIndexOf(".") + 1, loggerName.length());
    if (event.getLevel().equals(Level.INFO)) {
      BSConsole.setText(loggerName + ": " + event.getMessage().toString());
      }
    else if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
      BSConsole.setError(loggerName + ": " + event.getMessage().toString());
      }
    }
    
  public static BSConsoleAppender createAppender(String                         name, 
                                                 Filter                         filter,
                                                 Layout<? extends Serializable> layout,
                                                 boolean                        ignoreExceptions) {
        return new BSConsoleAppender(name, filter, layout, ignoreExceptions);
    } 
  }

