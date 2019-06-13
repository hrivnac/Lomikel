package com.JHTools.Utils;

// Apache
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.entity.StringEntity;

// Java
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.ArrayList;

// Log4J
import org.apache.log4j.Logger;

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
    * @throws CommonException If anything goes wrong. */
  public static void main(String[] args) throws CommonException {
    System.out.println(get(args[0]));
    }
  
  // GET -----------------------------------------------------------------------
    
  /** Make http get call.
    * @param question The http request.
    * @return         The answer.
    * @throws CommonException If anything goes wrong. */
  public static String get(String question) throws CommonException {
    return get(question, null);
    }
   
  /** Make http get call. It accepts gzipped results.
    * @param question The http request.
    * @param headers  The additional headers.
    * @return         The answer.
    * @throws CommonException If anything goes wrong. */
  public static String get(String              question,
                           Map<String, String> headers) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
    HttpGet get = new HttpGet(question);
    get.addHeader("Accept-Encoding", "gzip");
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        get.addHeader(entry.getKey(), entry.getValue());
        }
      }
    try {
      HttpResponse response = client.execute(get);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Call to " + question + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (Exception e) {
      throw new CommonException("Call to " + question + " failed", e);
      }
    finally {
      get.releaseConnection();
      }  
    return answer;
    }
       
  // POST ----------------------------------------------------------------------  
    
  /** Make http post call.
    * @param url The http url.
    * @param params The request parameters.
    * @return       The answer.
    * @throws CommonException If anything goes wrong. */
  public static String post(String              question,
                            Map<String, String> params) throws CommonException {
    return post(question, params, null);
    }

  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param params  The request parameters.
    * @param headers The additional headers.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  public static String post(String              url,
                            Map<String, String> params,
                            Map<String, String> headers) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
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
    try {
      HttpResponse response = client.execute(post);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Post to " + url + " " + params + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (Exception e) {
      throw new CommonException("Post to " + url + " " + params + " failed", e);
      }
    finally {
      post.releaseConnection();
      }  
    return answer;
    }
    
  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as JSON string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  // TBD: header should be more generic
  public static String postJSON(String              url,
                                String              json,
                                Map<String, String> headers,
                                String              header) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
    HttpPost post = new HttpPost(url);
    post.addHeader("Accept-Encoding", "gzip");
    post.addHeader("Content-Type", "application/json");
    post.addHeader("Accept", "application/json");
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
    try {
      HttpResponse response = client.execute(post);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Post to " + url + " " + json + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        if (header != null) {
          answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answer = getResponseBody(response);   
          }
        }
      }
    catch (Exception e) {
      throw new CommonException("Post to " + url + " " + json + " failed", e);
      }
    finally {
      post.releaseConnection();
      }  
    return answer;
    }
    
  /** Make http post call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as XML string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  // TBD: header should be more generic
  public static String postXML(String              url,
                               String              json,
                               Map<String, String> headers,
                               String              header) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
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
    try {
      HttpResponse response = client.execute(post);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Post to " + url + " " + json + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        if (header != null) {
          answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answer = getResponseBody(response);
          }
        }
      }
    catch (Exception e) {
      throw new CommonException("Post to " + url + " " + json + " failed", e);
      }
    finally {
      post.releaseConnection();
      }  
    return answer;
    }
    
  // PUT -----------------------------------------------------------------------
  
  /** Make http put call.
    * @param url The http url.
    * @param params The request parameters.
    * @return       The answer.
    * @throws CommonException If anything goes wrong. */
  public static String put(String              question,
                           Map<String, String> params) throws CommonException {
    return put(question, params, null);
    }

  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param params  The request parameters.
    * @param headers The additional headers.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  public static String put(String              url,
                           Map<String, String> params,
                           Map<String, String> headers) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
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
    try {
      HttpResponse response = client.execute(put);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Put to " + url + " " + params + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        answer = getResponseBody(response);   
        }
      }
    catch (Exception e) {
      throw new CommonException("Put to " + url + " " + params + " failed", e);
      }
    finally {
      put.releaseConnection();
      }  
    return answer;
    }
    
  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as JSON string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  // TBD: header should be more generic
  public static String putJSON(String              url,
                               String              json,
                               Map<String, String> headers,
                               String              header) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
    HttpPut put = new HttpPut(url);
    put.addHeader("Accept-Encoding", "gzip");
    put.addHeader("Content-Type", "application/json");
    put.addHeader("Accept", "application/json");
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
    try {
      HttpResponse response = client.execute(put);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Put to " + url + " " + json + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        if (header != null) {
          answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answer = getResponseBody(response);   
          }
        }
      }
    catch (Exception e) {
      throw new CommonException("Put to " + url + " " + json + " failed", e);
      }
    finally {
      put.releaseConnection();
      }  
    return answer;
    }
    
  /** Make http put call. It accepts gzipped results.
    * @param url     The http url.
    * @param json    The request parameters as XML string.
    * @param headers The additional headers. May be <code>null</code>.
    * @param header  The requested header (instead of answer body). May be <code>null</code>.
    * @return        The answer.
    * @throws CommonException If anything goes wrong. */
  // TBD: header should be more generic
  public static String putXML(String              url,
                              String              json,
                              Map<String, String> headers,
                              String              header) throws CommonException {
    String answer = "";
    DefaultHttpClient client = new DefaultHttpClient();
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
    try {
      HttpResponse response = client.execute(put);
      StatusLine statusLine = response.getStatusLine();
      int statusCode = statusLine.getStatusCode();
      if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
        throw new CommonException("Put to " + url + " " + json + " failed: " + statusLine.getReasonPhrase());
        }
      else {
        if (header != null) {
          answer = response.getHeaders(header)[0].getElements()[0].getName();
          }
        else {
          answer = getResponseBody(response);
          }
        }
      }
    catch (Exception e) {
      throw new CommonException("Put to " + url + " " + json + " failed", e);
      }
    finally {
      put.releaseConnection();
      }  
    return answer;
    }
  
  // ---------------------------------------------------------------------------

  /** Get Response Body. Perform GZIP uncompression if neccessary.
    * @param  response The {@link HttpResponse}.
    * @return          The content of the response.
    * @throws IOException If anything goes wrong. */
  public static String getResponseBody(HttpResponse response) throws IOException {
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
    BufferedReader reader = new BufferedReader(isr);
    StringBuffer buffer = new StringBuffer();
    String dataLine = null;
    while ((dataLine = reader.readLine()) != null) {
      buffer.append(dataLine + "\n");
      }
    reader.close();
    return buffer.toString();
    }
      
  /** Logging . */
  private static Logger log = Logger.getLogger(SmallHttpClient.class);

  }
