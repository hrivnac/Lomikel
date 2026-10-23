package com.Lomikel.Utils;

import java.nio.file.Files;
import java.nio.file.Path;

/** Offline regression checks; run with javac/java, without external services. */
public class XSLTProcessorTest {
  private static final String TEXT_XSL = "<?xml version=\"1.0\"?>"
      + "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
      + "<xsl:output method=\"text\"/><xsl:template match=\"/\"><xsl:value-of select=\"/root/value\"/>"
      + "</xsl:template></xsl:stylesheet>";

  public static void main(String[] args) throws Exception {
    String result = new XSLTProcessor().transform("<root><value>fragment</value></root>", TEXT_XSL);
    if (!"fragment".equals(result)) throw new AssertionError("XML without declaration: " + result);
    System.out.println("XML fragment without declaration: PASS");
    Path secret = Files.createTempFile("xslt-secret-", ".xml");
    try {
      Files.writeString(secret, "<secret>DO_NOT_READ</secret>");
      String xsl = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
          + "<xsl:template match=\"/\"><xsl:value-of select=\"document('"
          + secret.toUri() + "')/secret\"/></xsl:template></xsl:stylesheet>";
      try {
        String exposed = new XSLTProcessor().transform("<root/>", xsl);
        throw new AssertionError("External document was accessed: " + exposed);
      } catch (LomikelException expected) {
        if (expected.getMessage().contains("DO_NOT_READ")) throw new AssertionError("Leaked secret");
      }
      System.out.println("External document blocked: PASS");
      Files.writeString(secret, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
          + "<xsl:template match=\"/\">INCLUDED</xsl:template></xsl:stylesheet>");
      String include = "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
          + "<xsl:include href=\"" + secret.toUri() + "\"/></xsl:stylesheet>";
      try {
        new XSLTProcessor().transform("<root/>", include);
        throw new AssertionError("External stylesheet was accessed");
      } catch (LomikelException expected) {
        // An external xsl:include is forbidden, regardless of its contents.
      }
      System.out.println("External stylesheet blocked: PASS");
      String entity = "<!DOCTYPE root [<!ENTITY external SYSTEM \"" + secret.toUri()
          + "\">]><root>&external;</root>";
      try {
        String exposed = new XSLTProcessor().transform(entity, null);
        throw new AssertionError("External XML entity was accessed: " + exposed);
      } catch (LomikelException expected) {
        // DTD access must not resolve an external entity from input XML.
      }
      System.out.println("External XML entity blocked: PASS");
    } finally {
      Files.deleteIfExists(secret);
    }
    String identity = new XSLTProcessor().transform("<root><value>identity</value></root>", null);
    if (!identity.contains("<value>identity</value>")) throw new AssertionError("Null stylesheet should copy XML: " + identity);
    System.out.println("Null stylesheet identity: PASS");
    try {
      new XSLTProcessor().transform("<root>PRIVATE_XML_CONTENT<", TEXT_XSL);
      throw new AssertionError("Malformed XML accepted");
    } catch (LomikelException expected) {
      if (expected.getMessage().contains("PRIVATE_XML_CONTENT")) throw new AssertionError("XML leaked in exception");
    }
    System.out.println("Exception does not echo XML: PASS");
    try {
      new XSLTProcessor().transform("<root/>", "<PRIVATE_STYLESHEET_CONTENT/>");
      throw new AssertionError("Malformed stylesheet accepted");
    } catch (LomikelException expected) {
      if (expected.getMessage().contains("PRIVATE_STYLESHEET_CONTENT")) throw new AssertionError("Stylesheet leaked in exception");
    }
    System.out.println("Exception does not echo stylesheet: PASS");
    String prefixed = new XSLTProcessor().transform("prefix ignored\n<?xml version=\"1.0\"?><root><value>declared</value></root>", TEXT_XSL);
    if (!"declared".equals(prefixed)) throw new AssertionError("Declaration preamble: " + prefixed);
    System.out.println("Declaration preamble: PASS");
    Path output = Path.of("xslt-output-" + System.nanoTime() + ".txt");
    try {
      new XSLTProcessor().transform(output.toString(), "<root><value>file</value></root>", TEXT_XSL);
      if (!"file".equals(Files.readString(output))) throw new AssertionError("File output differs");
      System.out.println("File output without declaration: PASS");
    } finally {
      Files.deleteIfExists(output);
    }
  }
}
