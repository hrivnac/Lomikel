package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** A small UTF-framed interprocess server. A STOP frame ends one connection. */
public class SocketServer<S extends Servable> implements AutoCloseable {

  /** Listen on loopback by default; remote clients should use a secure tunnel. */
  public SocketServer(S servable, int port) throws LomikelException {
    this(servable, port, ipv4Loopback());
    }

  private static InetAddress ipv4Loopback() {
    try {
      return InetAddress.getByName("127.0.0.1");
      }
    catch (UnknownHostException impossibleForLiteralIp) {
      throw new IllegalStateException("IPv4 loopback unavailable", impossibleForLiteralIp);
      }
    }

  /** Explicit bind address for deployments with their own network security. */
  public SocketServer(S servable, int port, InetAddress bindAddress) throws LomikelException {
    _servable = servable;
    try {
      _server = new ServerSocket(port, 50, bindAddress);
      log.info("Server started on " + _server.getInetAddress() + ":" + _server.getLocalPort());
      }
    catch (IOException e) {
      throw new LomikelException("Cannot create ServerSocket", e);
      }
    }

  /** The bound port, including an ephemeral port selected by passing zero. */
  public int port() {
    return _server.getLocalPort();
    }

  /** The actual listening address. */
  public InetAddress address() {
    return _server.getInetAddress();
    }

  /** Accept and serve one connection until STOP or disconnect. */
  public void accept() throws LomikelException {
    try (Socket socket = _server.accept();
         DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
         DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
      synchronized (this) {
        if (_server.isClosed()) {
          return;
          }
        _activeSockets.add(socket);
        }
      try {
        log.info("Client accepted");
        while (true) {
          String request;
          try {
            request = in.readUTF();
            }
          catch (EOFException e) {
            break; // A peer can disconnect without sending STOP.
            }
          if (request.equals("STOP")) {
            out.writeUTF("");
            break;
            }
          out.writeUTF(_servable.query(request));
          }
        }
      finally {
        synchronized (this) {
          _activeSockets.remove(socket);
          }
        }
      }
    catch (IOException e) {
      if (_server.isClosed()) {
        return; // close() deliberately interrupted accept/readUTF.
        }
      throw new LomikelException("Cannot serve socket connection", e);
      }
    }

  /** Release the listening socket; STOP by itself does not close the server. */
  @Override
  public synchronized void close() throws IOException {
    IOException failure = null;
    try {
      _server.close();
      }
    catch (IOException e) {
      failure = e;
      }
    for (Socket socket : _activeSockets) {
      try {
        socket.close();
        }
      catch (IOException e) {
        if (failure == null) { failure = e; }
        else { failure.addSuppressed(e); }
        }
      }
    if (failure != null) {
      throw failure;
      }
    }

  private final ServerSocket _server;
  private final S _servable;
  private final Set<Socket> _activeSockets = new HashSet<>();
  private static final Logger log = LogManager.getLogger(SocketServer.class);
  }
