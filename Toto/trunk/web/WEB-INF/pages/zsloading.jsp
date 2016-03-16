<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Loading" />
<c:set var="requirezss" scope="request" value="true" />
<%@ include file="../includes/basic_header.jsp" %>

<%
  //prevent page cache in browser side
  response.setHeader("Pragma", "no-cache");
  response.setHeader("Cache-Control", "no-store, no-cache");
%>

<script type="text/javascript">

  var skipSetting = 0;
  var skipMarker = 5;
  // how to stop this hammering? I reckon add a second every time between checks if the data hasn't changed.
  function updateStatus(){
    jq.post("/api/SpreadsheetStatus?action=sheetReady&reportid=${reportid}", function(data){
      var objDiv = document.getElementById("serverStatus");
      if ("true" == data){ // the sheet should be ready
        location.reload();
        return;
      }
    });


    if (window.skipMarker <= 0){
      jq.post("/api/SpreadsheetStatus?action=log", function(data){
        var objDiv = document.getElementById("serverStatus");
        if (objDiv.innerHTML != data){ // it was updated
          objDiv.innerHTML = data;
          objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
          objDiv.scrollTop = objDiv.scrollHeight;
          // assume there could be more stuff!
          window.skipSetting = 0;
          window.skipMarker = 0;
        } else {
          objDiv.style.backgroundColor = 'white';
          if (window.skipSetting == 0){
            window.skipSetting = 1;
          } else {
            window.skipSetting *= 2;
          }
//                alert("same data, new skip setting : " + window.skipSetting);
          window.skipMarker = window.skipSetting;
        }
      });
    } else {
      window.skipMarker--;
    }
  }

  setInterval(function(){ updateStatus(); }, 1000);

</script>


<main class="basicDialog">
	<div class="basic-box-container">
		<div class="basic-head">
			<div class="logo">
				<a href="/api/Online?opcode=loadsheet&reportid=1"><img src="/images/logo_alt.png" alt="azquo"></a>
			</div>
		</div>
		<div class="basic-box">
			<h3>Loading Data...</h3>
			<div class="loader">
				<span class="fa fa-spin fa-cog"></span>
			</div>
			<div id="serverStatus"></div>
            <a href="javascript:void(0)" id="abort" onclick='jq.post("/api/SpreadsheetStatus?action=stop", null)' class="button alt small"><span class="fa fa-times-circle"></span> Abort Load</a>

		</div>
	</div>
</main>

<%@ include file="../includes/basic_footer.jsp" %>