package com.Lomikel.DB;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>CellContent</code> contains HBase Cell content.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class CellContent {

  /** Create from {@link String}.
    * @param content The content itself. */
	public CellContent(String content) {
	  _sContent = content;
	  _type = Type.STRING;
	  }
	  
  /** Create from <tt>byte[]</tt>.
    * @param content The content itself.
    * @param         The content {@link Type}. */
	public CellContent(byte[] content,
	                   Type   type) {
	  _bContent = content;
	  _type = type;
	  }
	  
  /** Give {@link String} content.
    * @return The {@link String} content. */
	public String asString() {
	  return _sContent;
	  }
	  
  /** Give <tt>byte[]</tt> content.
    * @return The <tt>byte[]</tt> content. */  
	public byte[] asBytes() {
	  return _bContent;
	  }
	  
  /** Whether the content is a {@link String}.
    * @return Whether the content is a {@link String}. */
	public boolean isString() {
	  return _type == Type.STRING;
	  }
	  
  /** Whether the content is a <tt>byte[]</tt>.
    * @return Whether the content is a <tt>byte[]</tt>. */
	public boolean isBytes() {
	  return !isString();
	  }
	  
  @Override
  public String toString() {
    if (isString()) {
      return "CellContent(String : " + _sContent + ")";
      }
    else {
      return "CellContent(" + _type + ")";
      }
    }
	  
	private Type _type;  
	  
	private String _sContent;
  
	private byte[] _bContent;
	  
  public static enum Type {STRING, FITS};
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(CellContent.class);
    
  }
