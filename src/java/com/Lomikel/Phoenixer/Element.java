package com.Lomikel.Phoenixer;

import com.Lomikel.Januser.JanusClient;
import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.Map;

/** <code>Element</code> represents stored object.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: flag for real Elements (not Prototypes)
public interface Element {
  
  /** Give the Element schema.
    * @return The Element schema as <tt>name-type</tt>. */
  public Map<String, String> schema();
  
  /** Give the key columns.
    * @return The column names constituting the key. */
  public String[] keyColumns();
  
  /** Give the <em>Phoenix</em> table keeping this Element.
    * @return The <em>Phoenix</em> table keeping this Element.
    *         Can be <tt>null</tt>. */
  public String phoenixTable();
    
  /** Merge attributes with attributes of another {@link Element}.
    * @param other The {@link Element} to merge attributes from. */
  public void merge(Element other);
  
  // Content -------------------------------------------------------------------
  
  /** Give the content.
    * @return The content. Corresponds to <em>Phoenicx</em> databease. */
  public Map<String, Object> content();
  
  /** Set (or replace) an attribute from the <tt>SCHEMA</tt>.
    * @param name  The name of the attribute.
    * @param objet The attribute value. Ignored, if <tt>null</tt>.
    * @return      This {@link Element} itself, to be reused. */
  // BUG: cannot be updated (in Phoenix)
  public <E> Element set(String name,
                         E      object);
     
  /** Give an attribute from the <tt>SCHEMA</tt>.
    * @param name  The name of the attribute.
    * @param objet The emty attribute value. It will be filled with real object.
    * @return      The attribute value, <tt>null</tt> if not set. */
  public <E> E get(String name,
                   E      object);

  /** Give an attribute from the <tt>SCHEMA</tt> as {@link String}.
    * @param name  The name of the attribute.
    * @return      The attribute value, <tt>null</tt> if not set. */
  public <E> E get(String name);
      
  // Graph ---------------------------------------------------------------------
  
  /** Get associated {@link Vertex}.
    * @return The associated {@link Vertex}.
    *         It will be crerated if needed.
    * @throws LomikelException If anything goes wrong. */
  public Vertex vertex() throws LomikelException;
    
  // ---------------------------------------------------------------------------
  
  /** Give an empty clone of this.
    * @return The empty clone of this. */
  public <E extends Element> E emptyClone();
  
  /** Give the key.
    * @return The key value, <tt>null</tt> if not known. */
  public String[] key();
  
  /** Set the key.
    * @param key The key to be set to proper column variables. */
  public void setKey(String[] key);
  
  /** Reset all values except key constituents.
    * @return The Element clone with all fields cleared, except key fields. */
  public <E extends Element> E onlyKey();
  
  /** Give detailed content.
    * @param format The output {@link Format}. 
    * @return The detailed content. */
  public String toString(Format format);
    
  public static enum Format {TXT};
  
  }
