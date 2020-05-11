package com.JHTools.Utils;

// Java
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
 
// TBD
public class Gzipper {
  
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
      e.printStackTrace();
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
      e.printStackTrace();
      }
    finally {
      try {
        assert gzipIS != null;
        gzipIS.close();
        bos.close();
        bis.close();
        }
      catch (Exception e) {
        e.printStackTrace();
        }
      }
    return new byte[]{};
    }
      
  public static boolean isZipped(final byte[] compressed) {
    return (compressed[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (compressed[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
    }
    
  }