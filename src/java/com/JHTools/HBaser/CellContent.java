package com.JHTools.HBaser;

// Log4J
import org.apache.log4j.Logger;

/** <code>CellContent</code> contains HBase Cell content.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: better !
public class CellContent {

  /** TBD */
	public CellContent(String content) {
	  _sContent = content;
	  _type = Type.STRING;
	  }
	  
  /** TBD */
	public CellContent(byte[] content) {
	  _bContent = content;
	  _type = Type.BYTES;
	  }
	  
  /** TBD */
	public String asString() {
	  return _sContent;
	  }
	  
  /** TBD */
	public byte[] asBytes() {
	  return _bContent;
	  }

  /** TBD */
	public boolean isString() {
	  return _type == Type.STRING;
	  }
	  
  /** TBD */
	public boolean isBytes() {
	  return _type == Type.BYTES;
	  }
	  
	/** TBD */
  public String toString() {
    if (isString()) {
      return "CellContent(String : " + _sContent + ")";
      }
    else if (isBytes()) {
      return "CellContent(bytes[] : " + _bContent + ")";
      }
    return "CellContent()";
    }
	  
	private Type _type;  
	  
	private String _sContent;
  
	private byte[] _bContent;
	  
  public static enum Type {STRING, BYTES};
  
  /** Logging . */
  private static Logger log = Logger.getLogger(CellContent.class);
    
  }
