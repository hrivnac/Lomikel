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
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

  /** Start the server on defaulr port and with default security. */
  public static void main(String[] args) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);   
    server.createContext("/eval", RemoteGroovyServer::handleEval);    
    server.setExecutor(null);
    server.start();    
    log.info("Remote Groovy JSR-223 server started");
    log.info("POST Groovy scripts to: http://localhost:" + PORT + "/eval");
    }

  private static void handleEval(HttpExchange exchange) throws IOException {
    try {
      doHandleEval(exchange);
      }
    catch (Throwable t) {
      t.printStackTrace();
      String response = "Fatal server error:\n" + t + "\n";    
      try {
        send(exchange, 500, response);
        }
      catch (Throwable sendError) {
        sendError.printStackTrace();
        }
      }
    }

  private static void doHandleEval(HttpExchange exchange) throws Exception {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      send(exchange, 405, "Use POST\n");
      return;
      }
    String auth = exchange.getRequestHeaders().getFirst("X-Auth-Token");
    if (!TOKEN.equals(auth)) {
      send(exchange, 401, "Unauthorized\n");
      return;
      }
    String script = new String(exchange.getRequestBody().readAllBytes(),
                               StandardCharsets.UTF_8);
    Map<String, List<String>> params = parseQuery(exchange.getRequestURI().getRawQuery());
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

  private static final String TOKEN = "secret";

  private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(RemoteGroovyServer.class);
    
  }
