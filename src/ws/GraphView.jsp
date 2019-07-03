Customize the interactions with the <b>graph</b>.
<br/>
<input type="button" onclick="clusterByGroups()"  value="Cluster by group type">
<input type="button" onclick="clusterByHubsize()" value="Cluster by group size">
<input type="button" onclick="clusterExpand()"    value="Expand all clusters">
</br>
 <input type="checkbox" name="layout"     id="layout"           onclick="switchLayout()"  value="false" title="hierarchical layout"            >hierarchical</input>
(<input type="checkbox" name="layout"     id="layout_direction" onclick="switchLayout()"  value="false" title="up-down or left-right"          >up/lr</input>
 <input type="checkbox" name="layout"     id="layout_method"    onclick="switchLayout()"  value="false" title="ordered by size or hierarchy"   >size/hierarchy</input>)
 <input type="checkbox" name="zoom"       id="zoom"                                value="true"  title="cluster by zoom"                       checked>zoom cluster</input>
(<input type="checkbox" name="stabilize"  id="stabilize"                           value="false" title="stabilize when clustering by zoom"            >stabilize</input>)
 <input type="checkbox" name="physics"    id="physics"   onclick="switchPhysics()" value="true"  title="activate animation"                    checked>live</input>
 <input type="checkbox" name="removeOld"  id="removeOld"                           value="false" title="activate removal of old nodes"               >remove old</input>
<br/>
filter: <input type="text" name="filter" value="" id="filter" title="show only nodes with a string in their label"/>

 
 
 
