package com.Lomikel.Utils;

// Java
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
 
// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Gzipper</code> handles <em>gzip</em> compression and decompression.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Gzipper {
  
  /** Compress.
    * @param uncompressedData The uncompressed <tt>byte[]</tt>.
    * @return                 The compressed <tt>byte[]</tt>. */
  public static byte[] zip(byte[] uncompressedData) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length)) {
      try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
        gzipOS.write(uncompressedData);
        }
      return bos.toByteArray();
      }
    catch (IOException e) {
      log.error("Cannot zip", e);
      return new byte[]{};
      }
    }
  
  /** Uncompress.
    * @param uncompressedData The compressed <tt>byte[]</tt>.
    * @return                 The uncompressed <tt>byte[]</tt>. */
  public static byte[] unzip(byte[] compressedData) {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
         ByteArrayOutputStream bos = new ByteArrayOutputStream();
         GZIPInputStream gzipIS = new GZIPInputStream(bis)) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = gzipIS.read(buffer)) != -1) {
        bos.write(buffer, 0, len);
        }
      return bos.toByteArray();
      }
    catch (IOException e) {
      log.error("Cannot unzip", e);
      return new byte[]{};
      }
    }
      
  /** Is <tt>byte[]</tt> compressed ?
    * @param compressed The <tt>byte[]</tt> to test.
    * @return           Whether the <tt>byte[]</tt> is compressed. */
  public static boolean isZipped(final byte[] compressed) {
    return compressed.length >= 2 &&
           (compressed[0] == (byte) GZIPInputStream.GZIP_MAGIC) &&
           (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(Gzipper.class);
    
  }
