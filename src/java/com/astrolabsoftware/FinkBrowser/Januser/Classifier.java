package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;

/** <code>Classifier</code> classifies sources.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public interface Classifier {
  
  /** Classify <em>source</em> and expand them to alerts (if requested).
    * It should register classes corresponding to specified <tt>objectId</tt>
    * using {@link FinkGremlinRecipies#registerSourcesOfInterest(Classifiers, String, String, double, String, String, boolean, String)}.
    * @param recipies   The {@link FinkGremlinRecipies} caller.
    * @param oid        The <tt>objectId</tt> of source to be added.
    * @param enhance    Whether expand tree under all <em>SourcesOfInterest</em> with alerts
    *                   possibly filled with requested HBase columns.
    * @param columns    The HBase columns to be copied into graph alerts. May be <tt>null</tt>. 
    * @throws LomikelException If anything fails. */
  public abstract void classify(FinkGremlinRecipies recipies,
                                String              oid,
                                boolean             enhance,
                                String              columns) throws LomikelException;
  
  }