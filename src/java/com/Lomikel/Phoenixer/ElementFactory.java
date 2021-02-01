package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.sql.Date;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.log4j.Logger;

/** <code>ElementFactory</code> creates {@link Element}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface ElementFactory {

  /** Create prototype {@link Element} from its name.
    * @param name The prototype {@link Element} name.
    * @return The created prototype {@link Element} .
    * @throws LomikelException If anything goes wrong. */
  public Element	 element(String name) throws LomikelException;
    
  }
