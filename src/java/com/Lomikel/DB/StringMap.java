package com.Lomikel.DB;

// Java
import java.util.Map;

/** <code>StringMap</code> simplifies intefaces of database clients. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: is should not be necessary
public interface StringMap extends Map<String, String> {}
