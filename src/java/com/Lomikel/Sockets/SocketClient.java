package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;

// Java
import java.net.Socket; 
import java.io.DataInputStream; 
import java.io.DataOutputStream; 
import java.io.BufferedInputStream; 
import java.io.IOException; 

// Log4J
import org.apache.log4j.Logger;

/** <code>SocketClient</code> is a simple client for interprocess communication.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class SocketClient {

  /** Create the Client and connect to {@link SocketServer}..
    * @param address The Server ip address.
    * @param port    The Server port.
    * @throws LomikelException When cannot be created. */
	public SocketClient(String address,
	                    int    port) throws LomikelException { 
		try { 
			_socket = new Socket(address, port); 
			_in  = new DataInputStream(new BufferedInputStream(_socket.getInputStream())); 
			_out = new DataOutputStream(_socket.getOutputStream()); 
			log.info("Connected to " + address + ":" + port); 
	  	} 
		catch(IOException e) { 
			throw new LomikelException("Cannot create", e);
		  } 
	  } 
	  
	/** Send message to {@link SocketServer}.
	  * @param  msg The message to be send.
	  * @return     The result.
    * @throws LomikelException When msg cannot be send. */
	public String send(String msg) throws LomikelException {
	  String line = "";
		try { 
			_out.writeUTF(msg);
			line = _in.readUTF();
		  } 
		catch(IOException e) { 
			throw new LomikelException("Cannot send message: " + msg,  e); 
		  }
		return line;
	  }
	  
	/** Close the connection to the {@link SocketServer}. */
	public void close() {
		try { 
  	  send("STOP");
			_out.close(); 
			_socket.close(); 
		  } 
		catch(LomikelException | IOException e) { 
			log.warn("Could not close", e); 
		  } 
	  }

	private Socket _socket = null; 
	
	private DataInputStream  _in  = null;
	
	private DataOutputStream _out	 = null; 

  /** Logging . */
  private static Logger log = Logger.getLogger(SocketClient.class);

  } 
