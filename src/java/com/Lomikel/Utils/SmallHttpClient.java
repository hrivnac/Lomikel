package com.Lomikel.Utils;

// Apache
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.HeaderElement;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClients;

// Java
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.util.zip.GZIPInputStream;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.http.config.Registry;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>SmallHttpClient</code> sends http requests.
  * Supports Get/Post methods with/without GZIP compression.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: JSON/XML should be handked the same way in get/post/put
public class SmallHttpClient {

  /** Make http get call.
    * @param args[0] The http request.
    * @throws LomikelException If anything goes wrong. */
  public static void main(String[] args) throws LomikelException {
    System.out.println(get(args[0]));
    }
  
  // GET -----------------------------------------------------------------------
    
  /** Make http get call.
    * @param question The http request.
    * @return         The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String get(String question) throws LomikelException {
    return get(question, null);
    }
   
  /** Make http get call. It accepts gzipped results.
    * @param question The http request.
    * @param headers  The additional headers.
    * @return         The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String get(String              question,
                           Map<String, String> headers) throws LomikelException {
    String answer = "";
    HttpGet get = new HttpGet(question);
    get.addHeader("Accept-Encoding", "gzip");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        get.addHeader(entry.getKey(), entry.getValue());
        }
      }
    try (CloseableHttpResponse response = execute(get)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answer;
    }
    
  // DELETE --------------------------------------------------------------------
    
  /** Make http delete call.
    * @param question The http request.
    * @return         The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String delete(String question) throws LomikelException {
    return delete(question, null);
    }
   
  /** Make http delete call. It accepts gzipped results.
    * @param question The http request.
    * @param headers  The additional headers.
    * @return         The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String delete(String              question,
                              Map<String, String> headers) throws LomikelException {
    String answer = "";
    HttpDelete delete = new HttpDelete(question);
    delete.addHeader("Accept-Encoding", "gzip");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        delete.addHeader(entry.getKey(), entry.getValue());
        }
      }
    try (CloseableHttpResponse response = execute(delete)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answer;
    }
       
  // POST ----------------------------------------------------------------------  
    
  /** Make http post call.
    * @param url The http url.
    * @param params The request parameters.
    * @return       The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String post(String              question,
                            Map<String, String> params) throws LomikelException {
    return post(question, params, null);
    }

  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param params  The request parameters.
    * @param headers The additional headers.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String post(String              url,
                            Map<String, String> params,
                            Map<String, String> headers) throws LomikelException {
    String answer = "";
    HttpPost post = new HttpPost(url);
    post.addHeader("Accept-Encoding", "gzip");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        post.addHeader(entry.getKey(), entry.getValue());
        }
      }
    List<NameValuePair> nameValuePairs = new ArrayList<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
      }
    try {
      post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
      }
    catch (UnsupportedEncodingException e) {
      log.warn("Cannot encode nameValuePairs", e);
      }      
    try (CloseableHttpResponse response = execute(post)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answer;
    }
    
  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as JSON string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String postJSON(String              url,
                                String              json,
                                Map<String, String> headers,
                                String              header) throws LomikelException {
    StringBuffer answerB = new StringBuffer("");

    HttpPost post = new HttpPost(url);
    post.addHeader("Accept-Encoding", "gzip");
    post.addHeader("Content-Type", "application/json");
    post.addHeader("Accept", "application/json");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        post.addHeader(entry.getKey(), entry.getValue());
        }
      }
    post.setEntity(new StringEntity(json, "UTF-8"));
    try (CloseableHttpResponse response = execute(post)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        if (header != null) {
          for (Header h : response.getHeaders(header)) {
            for (HeaderElement helement : h.getElements()) {
              answerB.append(helement.getName())
                     .append(" = ")
                     .append(helement.getValue())
                     .append("\n");
              }
            }
          //answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answerB = new StringBuffer(getResponseBody(response));   
          }
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answerB.toString();
    }
    
  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as JSON string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String postNDJSON(String              url,
                                  String              json,
                                  Map<String, String> headers,
                                  String              header) throws LomikelException {
    StringBuffer answerB = new StringBuffer("");

    HttpPost post = new HttpPost(url);
    post.addHeader("Accept-Encoding", "gzip");
    post.addHeader("Content-Type", "application/x-ndjson");
    post.addHeader("Accept", "application/json");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        post.addHeader(entry.getKey(), entry.getValue());
        }
      }
    post.setEntity(new StringEntity(json, ContentType.create("application/x-ndjson", "UTF-8")));
    try (CloseableHttpResponse response = execute(post)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        } 
      else {
        if (header != null) {
          for (Header h : response.getHeaders(header)) {
            for (HeaderElement helement : h.getElements()) {
              answerB.append(helement.getName())
                     .append(" = ")
                     .append(helement.getValue())
                     .append("\n");
              }
            }
          //answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answerB = new StringBuffer(getResponseBody(response));   
          }
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answerB.toString();
    }
    
  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as XML string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String postXML(String              url,
                               String              json,
                               Map<String, String> headers,
                               String              header) throws LomikelException {
    StringBuffer answerB = new StringBuffer("");
    HttpPost post = new HttpPost(url);
    post.addHeader("Accept-Encoding", "gzip");
    post.addHeader("Content-Type", "text/xml");
    post.addHeader("Accept", "text/xml");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        post.addHeader(entry.getKey(), entry.getValue());
        }
      }
    try {
      post.setEntity(new StringEntity(json));
      }
    catch (UnsupportedEncodingException e) {
      log.warn("Cannot encode nameValuePairs", e);
      }      
    try (CloseableHttpResponse response = execute(post)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        if (header != null) {
          for (Header h : response.getHeaders(header)) {
            for (HeaderElement helement : h.getElements()) {
              answerB.append(helement.getName())
                     .append(" = ")
                     .append(helement.getValue())
                     .append("\n");
              }
            }
          //answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answerB = new StringBuffer(getResponseBody(response));
          }
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answerB.toString();
    }
    
  // PUT -----------------------------------------------------------------------
  
  /** Make http put call.
    * @param url The http url.
    * @param params The request parameters.
    * @return       The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String put(String              question,
                           Map<String, String> params) throws LomikelException {
    return put(question, params, null);
    }

  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param params  The request parameters.
    * @param headers The additional headers.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String put(String              url,
                           Map<String, String> params,
                           Map<String, String> headers) throws LomikelException {
    String answer = "";
    HttpPut put = new HttpPut(url);
    put.addHeader("Accept-Encoding", "gzip");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        put.addHeader(entry.getKey(), entry.getValue());
        }
      }
    List<NameValuePair> nameValuePairs = new ArrayList<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
      }
    try {
      put.setEntity(new UrlEncodedFormEntity(nameValuePairs));
      }
    catch (UnsupportedEncodingException e) {
      log.warn("Cannot encode nameValuePairs", e);
      }      
    try (CloseableHttpResponse response = execute(put)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answer;
    }
    
  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as JSON string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String putJSON(String              url,
                               String              json,
                               Map<String, String> headers,
                               String              header) throws LomikelException {
    StringBuffer answerB = new StringBuffer("");
    HttpPut put = new HttpPut(url);
    put.addHeader("Accept-Encoding", "gzip");
    put.addHeader("Content-Type", "application/json");
    put.addHeader("Accept", "application/json");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        put.addHeader(entry.getKey(), entry.getValue());
        }
      }
    put.setEntity(new StringEntity(json, "UTF-8"));
    try (CloseableHttpResponse response = execute(put)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        if (header != null) {
          for (Header h : response.getHeaders(header)) {
            for (HeaderElement helement : h.getElements()) {
              answerB.append(helement.getName())
                     .append(" = ")
                     .append(helement.getValue())
                     .append("\n");
              }
            }
          //answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answerB = new StringBuffer(getResponseBody(response));   
          }
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answerB.toString();
    }
    
  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as XML string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws LomikelException If anything goes wrong. */
  public static String putXML(String              url,
                              String              json,
                              Map<String, String> headers,
                              String              header) throws LomikelException {
    StringBuffer answerB = new StringBuffer("");
    HttpPut put = new HttpPut(url);
    put.addHeader("Accept-Encoding", "gzip");
    put.addHeader("Content-Type", "text/xml");
    put.addHeader("Accept", "text/xml");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        put.addHeader(entry.getKey(), entry.getValue());
        }
      }
    try {
      put.setEntity(new StringEntity(json));
      }
    catch (UnsupportedEncodingException e) {
      log.warn("Cannot encode nameValuePairs", e);
      }      
    try (CloseableHttpResponse response = execute(put)) {
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (!isSuccess(statusCode)) {
        throw new LomikelException("HTTP request failed with status " + statusCode);
        }
      else {
        if (header != null) {
          for (Header h : response.getHeaders(header)) {
            for (HeaderElement helement : h.getElements()) {
              answerB.append(helement.getName())
                     .append(" = ")
                     .append(helement.getValue())
                     .append("\n");
              }
            }
          //answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answerB = new StringBuffer(getResponseBody(response));
          }
        }
      }
    catch (LomikelException e) {
      throw e;
      }
    catch (Exception e) {
      // Untrusted exception messages may contain request data or response text.
      throw new LomikelException("HTTP request failed");
      }
    return answerB.toString();
    }
  
  // ---------------------------------------------------------------------------

  private static boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
    }

  /** Isolate cookies/auth context per call while reusing only transport connections. */
  private static CloseableHttpResponse execute(HttpRequestBase request) throws IOException {
    HttpClientContext context = HttpClientContext.create();
    context.setCookieStore(new BasicCookieStore());
    try {
      return getSecureHttpsClient().execute(request, context);
      }
    catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
      throw new IOException("Cannot create HTTP client", e);
      }
    }

  /** Get Response Body. Perform GZIP uncompression if neccessary.
    * @param  response The {@link HttpResponse}.
    * @return          The content of the response.
    * @throws IOException If anything goes wrong. */
  public static String getResponseBody(HttpResponse response) throws IOException {
    if (response.getEntity() == null) {
      return "";
      }
    InputStreamReader isr = null;
    Header[] contentEncoding = response.getHeaders("Content-Encoding");
    if (contentEncoding.length == 1) {
      String acceptEncodingValue = contentEncoding[0].getValue();
      log.debug("Content-encoding: " + acceptEncodingValue);
      if (acceptEncodingValue.indexOf("gzip") != -1) {
        log.debug("Gzipped content");
        GZIPInputStream gis = new GZIPInputStream(response.getEntity().getContent());
        isr = new InputStreamReader(gis, "UTF-8");
        }
      }
    if (isr == null) {
      log.debug("Not Gzipped content");
      isr = new InputStreamReader(response.getEntity().getContent());
      }
    try (BufferedReader reader = new BufferedReader(isr)) {
      StringBuffer buffer = new StringBuffer();
      String dataLine = null;
      while ((dataLine = reader.readLine()) != null) {
        buffer.append(dataLine + "\n");
        }
      return buffer.toString();
      }
    }
    
  /** JVM-owned transport: callers close responses, not this shared client. */
  private static volatile CloseableHttpClient sharedClient;

  /** Initialize once without turning checked TLS setup failures into JVM Errors. */
  private static CloseableHttpClient getSecureHttpsClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    CloseableHttpClient client = sharedClient;
    if (client == null) {
      synchronized (SmallHttpClient.class) {
        client = sharedClient;
        if (client == null) {
          client = buildSecureHttpsClient();
          final CloseableHttpClient toClose = client;
          // Only the JVM owns shutdown: there is no explicit close racing callers.
          try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
              try { toClose.close(); }
              catch (IOException e) { /* JVM is shutting down. */ }
            }, "small-http-client-shutdown"));
            }
          catch (RuntimeException e) {
            try { client.close(); }
            catch (IOException ignored) { /* Preserve the registration failure. */ }
            throw e;
            }
          sharedClient = client;
          }
        }
      }
    return client;
    }

  private static CloseableHttpClient buildSecureHttpsClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
    SSLContext sslContext = SSLContexts.custom()
                                       .loadTrustMaterial(null, acceptingTrustStrategy)
                                       .build();
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);   
    Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                                                                             .register("https", sslsf)
                                                                             .register("http", new PlainConnectionSocketFactory())
                                                                             .build();
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry, null, null, null, 5, TimeUnit.MINUTES);
    connectionManager.setMaxTotal(16);
    connectionManager.setDefaultMaxPerRoute(8);
    connectionManager.setValidateAfterInactivity(1000);
    RequestConfig config = RequestConfig.custom()
                                        .setConnectTimeout(          _timeout * 1000)
                                        .setConnectionRequestTimeout(_timeout * 1000)
                                        .setSocketTimeout(           _timeout * 1000)
                                        .build();                                       
    CloseableHttpClient httpClient = HttpClients.custom()
                                                .setDefaultRequestConfig(config)
                                                .setSSLSocketFactory(sslsf)
                                                .setConnectionManager(connectionManager)
                                                .evictExpiredConnections()
                                                .evictIdleConnections(30, TimeUnit.SECONDS)
                                                .build();
    return httpClient;
    }
    
  private static int _timeout = 60; // 60s
      
  /** Logging . */
  private static Logger log = LogManager.getLogger(SmallHttpClient.class);

  }
