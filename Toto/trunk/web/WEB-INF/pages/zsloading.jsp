<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%@taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss" %>
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <title>Azquo report</title>
  <zssjsp:head/>
</head>
<%
  //prevent page cache in browser side
  response.setHeader("Pragma", "no-cache");
  response.setHeader("Cache-Control", "no-store, no-cache");
%>
<body>

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

<div id="wrapper" style="height: 100px;">
  <div class="banner" id="banner">
    <table cellpadding="0" cellspacing="0">
      <tr>
        <td><a href="/api/Online?opcode=loadsheet&reportid=1"><img src="/images/azquo-logo2.png" alt="Azquo logo"/></a></td>
        <td><h1>Loading&nbsp;&nbsp;</h1>
        </td>
        <td width="600px"><div id="serverStatus" style="height:45px;width:100%;font:10px monospace;overflow:auto;"></div></td>
      </tr>
    </table>
  </div>
</div>
</body>
</html>