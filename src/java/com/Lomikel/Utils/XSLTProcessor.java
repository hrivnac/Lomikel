package com.Lomikel.Utils;

// XML
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;

// Java
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.StringReader;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>XSLTProcessor</code> converts XML fragments
  * using XSLT stylesheets.
  * <p><font color="#880088">
  * $Id: XSLTProcessor.java 740562 2016-04-15 12:46:38Z hrivnac $
  * <pre>
  * $Date: 2016-04-15 14:46:38 +0200 (Fri, 15 Apr 2016) $
  * $Revision: 740562 $
  * $Author: hrivnac $
  * $HeadURL: svn+ssh://svn.cern.ch/reps/atlasoff/Database/TAGHadoop/TagConvertor/trunk/src/net/hep/atlas/Database/EIHadoop/Util/XSLTProcessor.java $
  * </pre>
  * </font></p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @version $Id: XSLTProcessor.java 740562 2016-04-15 12:46:38Z hrivnac $
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class XSLTProcessor {

// (new XSLTProcessor(parameters)).transform(result, new StringResource(xslt).toString());

  /** Create. */
  public XSLTProcessor() {}

  /** Create and initialise transformation parameters.
    * @param parameters The {@link Map} of parameters to be used to parametrise
  . * the transformation. */
  public XSLTProcessor(Map<String, String> parameters) {
    _parameters = parameters;
    }

  /** Transform XML String into result file using XSLT stylesheet.
    * @param fileName   The filename for result file.
    * @param xmlString  The XML string to be converted.
    *                   All text before XML text is ignored.
    * @param xsltString The conversion XSLT stylesheet.
    *                   If <code>null</code>, standard stylesheet is used.
    * @return           The file with HTML result.
    * @throws IOException                       if result file can't be written.
    * @throws TransformerException              if transformation can't be performed.
    * @throws TransformerConfigurationException if {@link Transformer} can't be created.
    * @throws LomikelException if transformation can't be performed. */
  public File transform(String fileName,
                        String xmlString,
                        String xsltString) throws LomikelException {
    try {
      xmlString = xmlString.substring(xmlString.indexOf("<?xml"));
      Transformer transformer = transformer(xsltString);
      Source xml = new StreamSource(new StringReader(xmlString));
      File file = new File(fileName.replaceAll("/", "_"));
      Result result = new StreamResult(new FileWriter(file));
      transformer.transform(xml, result);
      return file;
      }
    catch (Exception e) {
      throw new LomikelException("Can't process XML fragment:\n\n" + xmlString, e);
      }
    }

  /** Transform XML String into result String using XSLT stylesheet.
    * @param xmlString  The XML string to be converted.
    *                   All text before XML text is ignored.
    * @param xsltString The conversion XSLT stylesheet.
    *                   If <code>null</code>, standard stylesheet is used.
    * @return           The HTML result.
    * @throws LomikelException if transformation can't be performed. */
  public String transform(String xmlString,
                          String xsltString) throws LomikelException {
    xmlString = xmlString.substring(xmlString.indexOf("<?xml"));
    try {
      Transformer transformer = transformer(xsltString);
      Source xml = new StreamSource(new StringReader(xmlString));
      StringWriter string = new StringWriter();
      Result result = new StreamResult(string);
      transformer.transform(xml, result);
      return string.toString();
      }
    catch (Exception e) {
      if (e instanceof LomikelException) {
        throw (LomikelException)e;
        }
      else {
        throw new LomikelException("Can't process XML fragment:\n\n" + xmlString, e);
        }
      }
    }

  /** Get {@link Transformer} from XSLT string.
    * @param xsltString The String containing XSLT stylesheet.
    * @return           The corresponding {@link Transformer}.
    * @throws LomikelException if {@link Transformer} can't be created.*/
  private Transformer transformer(String xsltString) throws LomikelException {
    try {
      TransformerFactory factory = TransformerFactory.newInstance();
      Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(xsltString)));
      for (String parameter : _parameters.keySet()) {
        transformer.setParameter(parameter, _parameters.get(parameter));
        }
      return transformer;
      }
    catch (Exception e) {
      throw new LomikelException("Can't create Transformer:\n\n" + e.toString() + "\n\n" + xsltString, e);
      }
    }

  private Map<String, String> _parameters = new HashMap<>();
                                       
  /** Logging . */
  private static Logger log = Logger.getLogger(XSLTProcessor.class);
                                                
  }
