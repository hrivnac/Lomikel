package com.Lomikel.Sockets;

import com.Lomikel.Utils.LomikelException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Offline loopback-only socket lifecycle and error-path regression. */
public final class SocketOfflineTest {
  private static void require(boolean yes, String message) {
    if (!yes) { throw new AssertionError(message); }
  }

  public static void main(String[] args) throws Exception {
    try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      try {
        new SocketServer<>(request -> request, occupied.getLocalPort());
        throw new AssertionError("A bind failure must fail construction");
      }
      catch (LomikelException expected) { /* occupied port */ }
    }

    SocketServer<Servable> server = new SocketServer<>(request -> "reply:" + request, 0);
    require(server.address().isLoopbackAddress(), "Default listener must be loopback only");
    AtomicReference<Throwable> workerError = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      try { server.accept(); server.accept(); server.accept(); }
      catch (Throwable error) { workerError.set(error); }
    }, "socket-offline-test");
    worker.setDaemon(true);
    worker.start();
    try {
      SocketClient client = new SocketClient("127.0.0.1", server.port());
      require("reply:hello".equals(client.send("hello")), "First request");
      client.reconnect();
      require("reply:again".equals(client.send("again")), "Request after reconnect");
      java.lang.reflect.Field output = SocketClient.class.getDeclaredField("_out");
      output.setAccessible(true);
      Object stream = output.get(client);
      output.set(client, null); // Simulate a close between socket and stream reads.
      try {
        require(client.send("during-close") == null, "A missing stream must not throw");
      }
      finally { output.set(client, stream); }
      client.close();
      require(client.send("after-close") == null, "send on closed client must return null");
      SocketClient localhost = new SocketClient("localhost", server.port());
      require("reply:local".equals(localhost.send("local")),
              "localhost client must reach the IPv4 loopback listener");
      localhost.close();
      worker.join(3000);
      require(!worker.isAlive(), "Server did not finish handling both connections");
      require(workerError.get() == null, "Server failed: " + workerError.get());
    }
    finally { server.close(); }

    try (ServerSocket ipv6Server = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
      AtomicReference<Throwable> ipv6Error = new AtomicReference<>();
      Thread ipv6Worker = new Thread(() -> {
        try (Socket peer = ipv6Server.accept()) {
          peer.setSoTimeout(2000);
          DataInputStream in = new DataInputStream(peer.getInputStream());
          DataOutputStream out = new DataOutputStream(peer.getOutputStream());
          require("ipv6".equals(in.readUTF()), "IPv6-only listener received wrong request");
          out.writeUTF("reply:ipv6");
          require("STOP".equals(in.readUTF()), "IPv6-only listener missed STOP");
          out.writeUTF("");
        }
        catch (Throwable error) { ipv6Error.set(error); }
      }, "ipv6-only-socket-worker");
      ipv6Worker.setDaemon(true);
      ipv6Worker.start();
      SocketClient ipv6Client = new SocketClient("localhost", ipv6Server.getLocalPort());
      try {
        require("reply:ipv6".equals(ipv6Client.send("ipv6")),
                "localhost client must reach an IPv6-only loopback listener");
      }
      finally { ipv6Client.close(); }
      ipv6Worker.join(3000);
      require(!ipv6Worker.isAlive() && ipv6Error.get() == null,
              "IPv6-only worker did not finish: " + ipv6Error.get());
    }

    SocketServer<Servable> idleServer = new SocketServer<>(request -> request, 0);
    AtomicReference<Throwable> idleError = new AtomicReference<>();
    Thread idleWorker = new Thread(() -> {
      try { idleServer.accept(); }
      catch (Throwable error) { idleError.set(error); }
    }, "idle-socket-worker");
    idleWorker.setDaemon(true);
    idleWorker.start();
    try (Socket idlePeer = new Socket("127.0.0.1", idleServer.port())) {
      idlePeer.setSoTimeout(2000);
      new DataOutputStream(idlePeer.getOutputStream()).writeUTF("ready");
      require("ready".equals(new DataInputStream(idlePeer.getInputStream()).readUTF()),
              "Idle peer did not enter the server's active connection");
      idleServer.close();
      idleWorker.join(800);
      boolean hung = idleWorker.isAlive();
      idlePeer.close(); // Always unblock a failing implementation.
      idleWorker.join(3000);
      require(!hung && !idleWorker.isAlive(), "closing server must release an idle accepted peer");
      require(idleError.get() == null, "Server close failed: " + idleError.get());
    }
    finally { idleServer.close(); }

    try (ServerSocket silentServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      java.util.concurrent.CountDownLatch accepted = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
      AtomicReference<Socket> peerSocket = new AtomicReference<>();
      Thread peer = new Thread(() -> {
        try (Socket socket = silentServer.accept()) {
          peerSocket.set(socket);
          accepted.countDown();
          new DataInputStream(socket.getInputStream()).readUTF(); // Consume STOP, never acknowledge it.
          release.await(3, java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (Exception error) { workerError.set(error); }
      }, "silent-socket-peer");
      peer.setDaemon(true);
      peer.start();
      SocketClient slowClient = new SocketClient("127.0.0.1", silentServer.getLocalPort());
      require(accepted.await(1, java.util.concurrent.TimeUnit.SECONDS), "Silent peer never accepted");
      Thread closer = new Thread(slowClient::close, "socket-close-test");
      closer.setDaemon(true); // A broken close must fail the assertion, not keep the JVM alive.
      closer.start();
      closer.join(1800);
      boolean hung = closer.isAlive();
      if (hung && peerSocket.get() != null) {
        peerSocket.get().close(); // Unblock even an implementation that never sends STOP.
      }
      release.countDown();
      closer.join(3000);
      peer.join(3000);
      require(!hung, "close must not wait indefinitely for STOP acknowledgement");
      require(!closer.isAlive() && !peer.isAlive(), "Offline test leaked a thread");
      require(workerError.get() == null, "Silent peer failed: " + workerError.get());
    }
    try (ServerSocket concurrentServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      java.util.concurrent.CountDownLatch requestSeen = new java.util.concurrent.CountDownLatch(1);
      AtomicReference<Throwable> concurrentError = new AtomicReference<>();
      Thread peer = new Thread(() -> {
        try (Socket socket = concurrentServer.accept()) {
          new DataInputStream(socket.getInputStream()).readUTF();
          requestSeen.countDown();
          socket.getInputStream().read(); // Wait until close aborts the in-flight request.
        }
        catch (Exception e) { concurrentError.set(e); }
      }, "concurrent-socket-peer");
      peer.setDaemon(true);
      peer.start();
      SocketClient concurrentClient = new SocketClient("127.0.0.1", concurrentServer.getLocalPort());
      AtomicReference<Throwable> sendError = new AtomicReference<>();
      Thread sender = new Thread(() -> {
        try { require(concurrentClient.send("blocked") == null,
                      "A request interrupted by close must return null"); }
        catch (Throwable error) { sendError.set(error); }
      }, "concurrent-socket-sender");
      sender.setDaemon(true);
      sender.start();
      try {
        require(requestSeen.await(1, java.util.concurrent.TimeUnit.SECONDS),
                "Concurrent peer did not receive the request");
        concurrentClient.close();
        sender.join(2000);
        peer.join(2000);
        require(!sender.isAlive() && !peer.isAlive(), "Concurrent close leaked a thread");
        require(sendError.get() == null, "Concurrent send failed: " + sendError.get());
        require(concurrentError.get() == null, "Concurrent peer failed: " + concurrentError.get());
      }
      finally { concurrentClient.close(); }
    }
    System.out.println("SocketOfflineTest PASS");
  }
}
