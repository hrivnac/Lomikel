package com.Lomikel.GroovyServer;

// HTTP Server
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Java
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>RemoteGroovyServer</code> allows .to execute <em>Groovy</em>
  * scripts remotely.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: multi-entry (threads)
// TBD: with request queue
// TBD: timeout
public class RemoteGroovyServer {

  /** Start only on loopback with an operator-supplied secret. */
  public static void main(String[] args) throws Exception {
    HttpServer server = createServer(PORT, System.getenv("LOMIKEL_GROOVY_TOKEN"));
    server.start();    
    log.info("Remote Groovy JSR-223 server started");
    log.info("POST Groovy scripts to: http://localhost:" + PORT + "/eval");
    }

  /** Construct an unstarted server; zero selects an ephemeral port for tests. */
  static HttpServer createServer(int port, String token) throws IOException {
    if (token == null || token.length() < 16) {
      throw new IllegalArgumentException("LOMIKEL_GROOVY_TOKEN must contain at least 16 characters");
      }
    HttpServer server = HttpServer.create(
      new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
    server.createContext("/eval", exchange -> handleEval(exchange, token));
    server.setExecutor(null);
    return server;
    }

  private static void handleEval(HttpExchange exchange, String token) throws IOException {
    try {
      doHandleEval(exchange, token);
      }
    catch (Exception e) {
      log.error("Groovy request failed", e);
      try {
        send(exchange, 500, "Internal server error\n");
        }
      catch (IOException sendError) {
        log.warn("Could not send failure response", sendError);
        }
      }
    finally {
      exchange.close();
      }
    }

  private static void doHandleEval(HttpExchange exchange, String token) throws Exception {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      send(exchange, 405, "Use POST\n");
      return;
      }
    String auth = exchange.getRequestHeaders().getFirst("X-Auth-Token");
    if (auth == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                                               auth.getBytes(StandardCharsets.UTF_8))) {
      send(exchange, 401, "Unauthorized\n");
      return;
      }
    byte[] body = exchange.getRequestBody().readNBytes(MAX_SCRIPT_BYTES + 1);
    if (body.length > MAX_SCRIPT_BYTES) {
      send(exchange, 413, "Script too large\n");
      return;
      }
    String script = new String(body, StandardCharsets.UTF_8);
    Map<String, List<String>> params;
    try {
      params = parseQuery(exchange.getRequestURI().getRawQuery());
      }
    catch (IllegalArgumentException e) {
      send(exchange, 400, "Invalid query\n");
      return;
      }
    Object result = runGroovyScript(script, params);
    send(exchange, 200, String.valueOf(result) + "\n");
    }
  
  private static Object runGroovyScript(String                    script,
                                        Map<String, List<String>> params) throws ScriptException {
    // Create a fresh engine per request for this simple example.
    // That avoids shared mutable state between requests.
    ScriptEngine engine = SCRIPT_ENGINE_MANAGER.getEngineByName("groovy");
    if (engine == null) {
      throw new IllegalStateException("No Groovy JSR-223 engine found. Is groovy-jsr223 on the classpath?");
      }
    Bindings bindings = engine.createBindings();
    // Objects exposed here are visible to the remote Groovy script.
    // This is similar in spirit to exposing Java objects through Py4J.
    bindings.put("service",    new CalculatorService());
    bindings.put("params",     params);
    bindings.put("serverName", "RemoteGroovyServer");    
    return engine.eval(script, bindings);
    }

  private static Map<String, List<String>> parseQuery(String rawQuery) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) {
      return result;
      }
    for (String pair : rawQuery.split("&")) {
      String[] parts = pair.split("=", 2);
      String key = decode(parts[0]);
      String value = parts.length > 1 ? decode(parts[1]) : "";      
      result.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(value);
      } 
    return result;
    }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

  private static void send(HttpExchange exchange,
                           int statusCode,
                           String response) throws IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
      }
    }

  /* Example server-side Java object. */
  public static class CalculatorService {
    public int add(int a, int b) {
      return a + b;
      }    
    public int multiply(int a, int b) {
      return a * b;
      }    
    public String hello(String name) {
      return "Hello, " + name + " from the Java server";
      }
    }
  
  private static final int PORT = 44444;
  private static final int MAX_SCRIPT_BYTES = 65536;

  private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(RemoteGroovyServer.class);
    
  }
