<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Loading"/>
<c:set var="requirezss" scope="request" value="true"/>
<%@ include file="../includes/basic_header.jsp" %>

<%
    // a tweaked copy of zsloading - now I have two copies I really must factor
    // prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>

<script type="text/javascript">
    // edd changed to call every second, no skip maker
    var something = true;

    function updateStatus() {
        if (something) {
            jq.post("/api/SpreadsheetStatus?action=importResult", function (data) {
                if (data.indexOf("true") == 0) { // the sheet should be ready, note indexof not startswith, support for the former better
                    document.getElementById("mainform").submit();
                    //location.reload();
                    something = false;
                    return;
                } else { // possible headline info
                    document.getElementById("headline").innerHTML = "<h1>" + data + "</h1>"
                }
            });
            jq.post("/api/SpreadsheetStatus?action=log", function (data) {
                var objDiv = document.getElementById("serverStatus");
                if (objDiv.innerHTML != data) { // it was updated
                    objDiv.innerHTML = data;
                    objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
                    objDiv.scrollTop = objDiv.scrollHeight;
                    // assume there could be more stuff!
                } else {
                    objDiv.style.backgroundColor = 'white';
//                alert("same data, new skip setting : " + window.skipSetting);
                }
            });
        }
    }

    setInterval(function () {
        updateStatus();
    }, 1000);

</script>
<form action="/api/PendingUpload" method="post" id="mainform">
    <input type="hidden" name="id" value="${id}"/>
    ${paramspassthrough}
</form>
<main class="basicDialog" style="max-width: 800px">
    <div class="basic-box-container">
        <div class="basic-head" style="background-color:${bannerColor}">
            <div class="logo">
                <img src="/images/${logo}" alt="azquo">
            </div>
        </div>
        <div class="basic-box">
            <h3>Running validation... <span class="fa fa-spin fa-cog"></span></h3>
            <div id="headline">
            </div>
            <div id="serverStatus" style="height: 500px"></div>

        </div>
    </div>
</main>

<%@ include file="../includes/basic_footer.jsp" %>