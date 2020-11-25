package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;

// Java 
import java.net.Socket; 
import java.net.ServerSocket; 
import java.io.DataInputStream; 
import java.io.DataOutputStream; 
import java.io.BufferedInputStream; 
import java.io.EOFException; 
import java.io.IOException; 

// Log4J
import org.apache.log4j.Logger;

/** <code>SocketServer</code> is a simple client for interprocess communication.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class SocketServer<S extends Servable> { 

  /** Start the Server and wait for connection.
    * @param servable The {@link Servable} to handle comminucation.
    * @param port     The Server port.
    * @throws LomikelException When cannot be created. */
	public SocketServer(S   servable,
	                    int port) throws LomikelException { 
	  _servable = servable;
	  try {
	    _server = new ServerSocket(port); 
  	  log.info("Server started, stop by Ctrl-C"); 
	    }
	  catch (IOException e) {
	    log.error("Cannot create ServerSocket", e);
	    }	   
	  }
	  
  /** Wait for connection.
    * Server will be stopped after receiving <tt>STOP</tt>.
    * Then it can be called again.
    * @throws LomikelException When cannot be created. */
	public void accept() throws LomikelException {  
		try { 
			log.info("Waiting for a client ..."); 
			Socket socket = _server.accept(); 
			log.info("Client accepted"); 
			DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream())); 
			DataOutputStream out = new DataOutputStream(socket.getOutputStream()); 
			String line = ""; 
			while (!line.equals("STOP")) { 
				try	{ 
					line = in.readUTF(); 
					if (line.equals("STOP")) {
					  out.writeUTF("");
					  }
					else {
				  	out.writeUTF(_servable.query(line));
				  	log.info(line); 
				    }
		  		} 
				catch(EOFException e)	{ 
					log.error("End of stream", e);
					break;
				  }
				catch(IOException e)	{ 
					log.error("Failed to receive message", e); 
				  } 
			  } 
			log.info("Closing connection"); 
			socket.close(); 
			in.close(); 
	  	} 
		catch(IOException e) { 
			throw new LomikelException("Cannot create", e); 
		  } 
	  } 
	  
	private ServerSocket _server;  
	  
	private S _servable;  

  /** Logging . */
  private static Logger log = Logger.getLogger(SocketServer.class);

  } 
