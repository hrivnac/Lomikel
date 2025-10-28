package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;

// Java
import java.util.Map;
import java.util.HashMap;

/** <code>ZTFClassifier</code> classifies <em>ZTF</em> <em>objects</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class ZTFClassifier extends Classifier {
         
  @Override
  public String survey() {
    return "ZTF";
    }

  }
