package com.Lomikel.GroovyServer;

// Java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
    RemoteGroovyClient remote = new RemoteGroovyClient("http://localhost:44444/eval",
                                                      System.getenv("LOMIKEL_GROOVY_TOKEN"));
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
    URI uri = URI.create(serverUrl);
    String host = uri.getHost();
    boolean local = "localhost".equalsIgnoreCase(host) ||
                    "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
    if (!("https".equalsIgnoreCase(uri.getScheme()) ||
          ("http".equalsIgnoreCase(uri.getScheme()) && local)) ||
        uri.getRawUserInfo() != null || host == null) {
      throw new IllegalArgumentException("Use HTTPS or a loopback HTTP tunnel for Groovy scripts");
      }
    if (token == null || token.length() < 16) {
      throw new IllegalArgumentException("LOMIKEL_GROOVY_TOKEN must contain at least 16 characters");
      }
    // The server binds IPv4 explicitly. Resolve its HTTP localhost alias the
    // same way even when this JVM prefers ::1; retain the rest of the URI raw.
    if ("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(host)) {
      int start = serverUrl.indexOf("://") + 3;
      _serverUrl = serverUrl.substring(0, start) + "127.0.0.1" +
                   serverUrl.substring(start + host.length());
      }
    else {
      _serverUrl = serverUrl;
      }
    _token = token;
    _client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build();
    }

  public String eval(String script) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(_serverUrl))
                                     .timeout(Duration.ofSeconds(30))
                                     .header("X-Auth-Token", _token)
                                     .header("Content-Type", "text/plain; charset=utf-8")
                                     .POST(HttpRequest.BodyPublishers.ofString(script))
                                     .build();
    HttpResponse<String> response = _client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("Groovy server returned HTTP " + response.statusCode());
      }
    return response.body();
    }

  private final String _serverUrl;
  
  private final String _token;
  
  private final HttpClient _client;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(RemoteGroovyClient.class);

  }