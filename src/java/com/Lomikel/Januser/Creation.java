package com.Lomikel.Januser;

/** <code>Creation</code> defines the strategie for creation of new {@link Vertex}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public enum Creation {
  
  /** Replace existing {@link Vertex}, if it already exists. */
  REPLACE,
  
  /** Replace existing {@link Vertex}, if it already exists. */
  REUSE,
  
  /** Create a new {@link Vertex}, even if it already exists. */
  DOUBLE;

  public static final Creation[] proper = new Creation[]{REPLACE, REUSE, DOUBLE};

  }
