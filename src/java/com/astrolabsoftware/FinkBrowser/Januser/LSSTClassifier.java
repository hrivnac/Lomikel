package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

/** <code>ZTFClassifier</code> classifies <em>LSST</em> <em>objects</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class LSSTClassifier extends Classifier {
  
  @Override
  public String survey() {
    return "LSST";
    }
    
  @Override
  public FPC fpc() throws LomikelException {
    if (_fpc == null) {
      _fpc = new FPC(survey());
      }
    return _fpc;
    }
    
  private static FPC _fpc;
  
  }
