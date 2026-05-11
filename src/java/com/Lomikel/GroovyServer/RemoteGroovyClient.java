package com.Lomikel.GroovyServer;

// Java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>RemoteGroovyClient</code> sends <em>Groovy</em>
  * scripts to {@link RemoteGroovyServer }.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class RemoteGroovyClient {

  /** Selftest. */
  public static void main(String[] args) throws IOException, InterruptedException {
    RemoteGroovyClient remote = new RemoteGroovyClient("http://localhost:44444/eval", "secret");
    String script = """
                    def a = 6
                    def b = 7
                    return service.multiply(a, b)
                    """;
    String result = remote.eval(script);
    log.info("Remote result: " + result);
    }

  public RemoteGroovyClient(String serverUrl,
                            String token) {
    _serverUrl = serverUrl;
    _token = token;
    _client = HttpClient.newHttpClient();
    }

  public String eval(String script) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(_serverUrl))
                                     .header("X-Auth-Token", _token)
                                     .header("Content-Type", "text/plain; charset=utf-8")
                                     .POST(HttpRequest.BodyPublishers.ofString(script))
                                     .build();
    HttpResponse<String> response = _client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Server returned HTTP " + response.statusCode() + ":\n" + response.body());
      }
    return response.body();
    }

  private final String _serverUrl;
  
  private final String _token;
  
  private final HttpClient _client;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(RemoteGroovyServer.class);

  }