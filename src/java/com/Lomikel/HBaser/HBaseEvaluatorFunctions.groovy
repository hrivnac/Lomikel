import com.Lomikel.HBaser.HBaseEvaluatorFunctions;

/** <code>HBaseEvaluatorFuctions</code> provide static functions available to
  * {@link HBaseEvaluator}. They interpreted within Evaluator process so that
  * have access to all its variables and functions.
  * In most cases, it just provides interface to {@link HBaseEvaluatorFunctions}
  * class.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
  
/** Do-nothing Demo. */
boolean demo() {
  return HBaseEvaluatorFunctions.demo();
  }
   
/** Evaluate, if <tt>ra,dec</tt> are within specified limits.
  * @param raMin  The minimal value of <tt>ra</tt> (in deg).
  * @param raMax  The maximal value of <tt>ra</tt> (in deg).
  * @param decMin The minimal value of <tt>dec</tt> (in deg).
  * @param decMax The maximal value of <tt>dec</tt> (in deg).
  * @return       Whether <tt>ra,dec</tt> from the database are within specified limits. */
boolean isWithinGeoLimits(double raMin,
                          double raMax,
                          double decMin,
                          double decMax) {
  return HBaseEvaluatorFunctions.isWithinGeoLimits(ra, dec, raMin, raMax, decMin, decMax);
  }

/** Evaluate, if <tt>ra,dec</tt> are within specified angular from concrete direction.
  * @param ra0   The central value of <tt>ra</tt> (in deg).
  * @param dec0  The central value of <tt>dec</tt> (in deg).
  * @param delta The maximal angular distance from the central direction (in deg).
  * @return      Whether <tt>ra,dec</tt> from the database are within specified argular. */
boolean isNear(double ra0,
               double dec0,
               double delta) {
  return HBaseEvaluatorFunctions.isNear(ra, dec, ra0, dec0, delta);
  }


  
