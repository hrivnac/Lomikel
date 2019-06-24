Customize the interactions with the <b>graph</b>.
<br/>
<input type="button" onclick="clusterByGroups()"  value="Cluster by group type">
<input type="button" onclick="clusterByHubsize()" value="Cluster by group size">
<input type="button" onclick="clusterExpand()"    value="Expand all clusters">
</br>
<input type="checkbox" name="layout"     class="layout"     onclick="switchLayout()"  value="false" title="hierarchical layout"                          >hierarchical</input>
<input type="checkbox" name="zoom"       class="zoom"                                 value="true"  title="clujster by zoom"                      checked>zomm cluster</input>
<input type="checkbox" name="physics"    class="physics"    onclick="switchPhysics()" value="true"  title="activate animation"                    checked>live</input>
<input type="checkbox" name="removeOld"  class="removeOld"                            value="false" title="activate removal of old nodes"               >remove old</input>
<br/>
filter: <input type="text" name="filter" value="" id="filter" title="show only nodes with a string in their label"/>

 
 
 
