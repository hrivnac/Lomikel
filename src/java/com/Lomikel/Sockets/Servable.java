package com.Lomikel.Sockets;

/** <code>Servable</code> can handle communication in the {@link SocketServer}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface Servable { 

  /** Handle request.
    * @param request The request to be answered.
    * @return        The answer to request. */
  public String query(String request);
  
  } 
