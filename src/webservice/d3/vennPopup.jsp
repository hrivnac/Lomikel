<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<!-- Lomikel Venn -->
<!-- @author Julius.Hrivnac@cern.ch  -->
  
<link href="venn.css?v=vertical-2" rel="stylesheet" type="text/css"/>
<section class="venn-view" aria-label="Overlap analysis">
  <figure class="venn-diagram">
    <div id="vennPopup"></div>
    <figcaption>Area represents the number of objects. The overlap is shared by both sets.</figcaption>
  </figure>
  <div id="vennPopupTxt"></div>
</section>
<input type='button' value='close' onclick='self.close()'>

