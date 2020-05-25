package com.JHTools.WebService;

// JSP
import javax.servlet.ServletOutputStream;

// Java
import java.util.zip.GZIPOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/** <code>GZipServletOutputStream</code>.
  * <p><font color="#880088">
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
class GZipServletOutputStream extends ServletOutputStream {

  public GZipServletOutputStream(OutputStream output) throws IOException {
    super();
    this.gzipOutputStream = new GZIPOutputStream(output);
    }

  @Override
  public void close() throws IOException {
    this.gzipOutputStream.close();
    }

  @Override
  public void flush() throws IOException {
    this.gzipOutputStream.flush();
    }

  @Override
  public void write(byte b[]) throws IOException {
    this.gzipOutputStream.write(b);
    }

  @Override
  public void write(byte b[], int off, int len) throws IOException {
    this.gzipOutputStream.write(b, off, len);
    }

  @Override
  public void write(int b) throws IOException {
    this.gzipOutputStream.write(b);
    }

  private GZIPOutputStream gzipOutputStream = null;

  }
