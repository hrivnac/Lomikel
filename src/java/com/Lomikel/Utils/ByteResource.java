package com.Lomikel.Utils;

// Java
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/** <code>ByteResource</code> gives Resource as Byte Stream.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ByteResource {

  /** Create.
    * @param resource The resource path. */
  public ByteResource(String resource) {
    InputStream input = ByteResource.class.getClassLoader().getResourceAsStream(resource);
    if (input == null) {
      throw new IllegalArgumentException("Resource " + resource + " cannot be found !");
      }
    try (InputStream is = input;
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = is.read(buffer)) != -1) {
        baos.write(buffer, 0, count);
        }
      _content = baos.toByteArray();
      }
    catch (IOException e) {
      // Construction fails before toFile can write an incomplete resource.
      throw new UncheckedIOException("Resource " + resource + " cannot be read !", e);
      }
    }

  /** Write resource to a file.
    * @param fn The name of the file to write the resource to. */
  public void toFile(String fn) {
    try (FileOutputStream fos = new FileOutputStream(fn)) {
      fos.write(_content);
      }
    catch (IOException e) {
      System.err.println("Cannot write to file " + fn + " !\n" + e);
      }
    }

  /** Show the content using the platform default charset, as before. */
  public String toString() {
    return new String(_content, Charset.defaultCharset());
    }

  private final byte[] _content;

  }
