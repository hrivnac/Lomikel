package com.Lomikel.Utils;

// Java
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
 
// Log4J
import org.apache.log4j.Logger;

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
    ByteArrayOutputStream bos = null;
    GZIPOutputStream gzipOS = null;
    try {
      bos = new ByteArrayOutputStream(uncompressedData.length);
      gzipOS = new GZIPOutputStream(bos);
      gzipOS.write(uncompressedData);
      gzipOS.close();
      return bos.toByteArray();   
      }
    catch (IOException e) {
      log.error("Cannot zip", e);
      }
    finally {
      try {
        assert gzipOS != null;
        gzipOS.close();
        bos.close();
        }
      catch (Exception ignored) {
        }
      }
    return new byte[]{};
    }
  
  /** Uncompress.
    * @param uncompressedData The compressed <tt>byte[]</tt>.
    * @return                 The uncompressed <tt>byte[]</tt>. */
  public static byte[] unzip(byte[] compressedData) {
    ByteArrayInputStream bis = null;
    ByteArrayOutputStream bos = null;
    GZIPInputStream gzipIS = null;    
    try {
      bis = new ByteArrayInputStream(compressedData);
      bos = new ByteArrayOutputStream();
      gzipIS = new GZIPInputStream(bis);     
      byte[] buffer = new byte[1024];
      int len;
      while((len = gzipIS.read(buffer)) != -1){
        bos.write(buffer, 0, len);
        }
      return bos.toByteArray();
      }
    catch (IOException e) {
      log.error("Cannot unzip", e);
      }
    finally {
      try {
        assert gzipIS != null;
        gzipIS.close();
        bos.close();
        bis.close();
        }
      catch (Exception ignored) {
        }
      }
    return new byte[]{};
    }
      
  /** Is <tt>byte[]</tt> compressed ?
    * @param compressed The <tt>byte[]</tt> to test.
    * @return           Whether the <tt>byte[]</tt> is compressed. */
  public static boolean isZipped(final byte[] compressed) {
    return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(Gzipper.class);
    
  }
