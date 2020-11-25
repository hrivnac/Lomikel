package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;

// Log4J
import org.apache.log4j.Logger;

/** <code>SocketTest</code> tests the communication
  * between {@link SocketClient} and {@link SocketServer}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class SocketTest {

  /** Start the {@link SocketServer}, send msg from {@link SocketClient}
    * and close the connection.
    * @throws LomikelException When anything fails. */
  public static void main(String[] args) throws LomikelException {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          TestServable servable = new TestServable();
          SocketServer server = new SocketServer<TestServable>(servable, _port);
          while (true) {
            server.accept();
            }
          }
        catch (LomikelException e) {
          log.error("Cannot create SocketServer", e);
          }
        }
      };
    thread.start();
		SocketClient client = new SocketClient(_ip, _port);
		client.send("Pokus 1");
		client.close();
		client = new SocketClient(_ip, _port);
		client.send("Pokus 2");
		client.close();
	  }
	  
	private static String _ip = "127.0.0.1";
	
	private static int    _port = 5000;
	  
  /** Logging . */
  private static Logger log = Logger.getLogger(SocketTest.class);
	  
  } 
