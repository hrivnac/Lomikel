package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;

// Java
import java.net.Socket; 
import java.io.DataInputStream; 
import java.io.DataOutputStream; 
import java.io.BufferedInputStream; 
import java.io.IOException; 

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
	  _address = address;
	  _port    = port;
	  connect();
	  } 
	  
	/** Reconnect to the {@link Socket}.
    * @throws LomikelException When cannot be reconnected. */
  public void reconnect() throws LomikelException {
    log.info("Reconnecting");
    close();
    connect();
    }
	  
	/** Connect to the {@link Socket}.
    * @throws LomikelException When cannot be connected. */
  private void connect() throws LomikelException {
    Socket socket = null;
    try {
      if ("localhost".equalsIgnoreCase(_address)) {
        try {
          socket = new Socket("127.0.0.1", _port);
          }
        catch (IOException ipv4Failure) {
          // Prefer explicit IPv6 loopback next: "localhost" may still choose
          // IPv4 on IPv4-preferred JVMs even for an IPv6-only listener.
          try { socket = new Socket(java.net.InetAddress.getByName("::1"), _port); }
          catch (IOException ipv6Failure) {
            // Retain the original hostname resolution as a last resort.
            try { socket = new Socket(_address, _port); }
            catch (IOException e) {
              e.addSuppressed(ipv4Failure);
              e.addSuppressed(ipv6Failure);
              throw e;
              }
            }
          }
        }
      else {
        socket = new Socket(_address, _port);
        }
      DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      synchronized (this) {
        _socket = socket;
        _in = in;
        _out = out;
        }
      log.info("Connected to " + _address + ":" + _port);
      }
    catch (IOException e) {
      if (socket != null) {
        try { socket.close(); }
        catch (IOException closeError) { e.addSuppressed(closeError); }
        }
      throw new LomikelException("Cannot connect Socket", e);
      }
    }
	  
	/** Send message to {@link SocketServer}.
	  * @param  msg The message to be send.
	  * @return     The result. <tt>null</tt> if message cannot be send. */
  public String send(String msg) {
    synchronized (_requestLock) {
      Socket socket;
      DataInputStream in;
      DataOutputStream out;
      synchronized (this) {
        socket = _socket;
        in = _in;
        out = _out;
        if (socket == null || socket.isClosed() || in == null || out == null) {
          return null;
          }
        _requestActive = true;
        }
      try {
        out.writeUTF(msg);
        return in.readUTF();
        }
      catch (IOException e) {
        return null;
        }
      finally {
        synchronized (this) { _requestActive = false; }
        }
      }
    }
	  
  /** Close the connection without waiting indefinitely for a STOP reply. */
  public void close() {
    Socket socket;
    DataInputStream in;
    DataOutputStream out;
    boolean active;
    synchronized (this) {
      socket = _socket;
      in = _in;
      out = _out;
      active = _requestActive;
      _socket = null;
      _in = null;
      _out = null;
      }
    if (socket == null) {
      return;
      }
    try {
      if (!active && in != null && out != null) {
        // Do not steal a response from an in-flight send.
        socket.setSoTimeout(500);
        out.writeUTF("STOP");
        in.readUTF();
        }
      }
    catch (IOException e) {
      log.warn("Could not signal STOP", e);
      }
    finally {
      try {
        socket.close();
        }
      catch (IOException e) {
        log.warn("Could not close", e);
        }
      }
    }
	  
	private Socket _socket = null; 
	
	private DataInputStream  _in  = null;
	
	private DataOutputStream _out	 = null; 
	
	private String _address;
	
	private int _port;

  private final Object _requestLock = new Object();
  private boolean _requestActive;

  /** Logging . */
  private static Logger log = LogManager.getLogger(SocketClient.class);

  } 
