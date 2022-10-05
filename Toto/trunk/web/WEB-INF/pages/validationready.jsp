<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Running Validation"/>
<c:set var="compact" scope="request" value="compact"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<c:set var="extraScripts" scope="request"
       value="<script type=\"text/javascript\" src=\"https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js\"></script><script type=\"text/javascript\" src=\"https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js\"></script>"/>

<%@ include file="../includes/new_header.jsp" %>
<script type="text/javascript">
    // edd changed to call every second, no skip maker
    var something = true;

    function updateStatus() {
        if (something) {
            jQuery.post("/api/SpreadsheetStatus?action=importResult", function (data) {
                if (data.indexOf("true") == 0) { // the sheet should be ready, note indexof not startswith, support for the former better
                    document.getElementById("mainform").submit();
                    //location.reload();
                    something = false;
                    return;
                } else { // possible headline info
                    // efc there's no headline . . .
                    //document.getElementById("headline").innerHTML = "<h1>" + data + "</h1>"
                }
            });
            jQuery.post("/api/SpreadsheetStatus?action=log", function (data) {
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

<div class="az-content">
    <div class="az-fileupload-modal-container">
        <div>
            <div id="headlessui-dialog-panel-:r6c:" class="opacity-100 translate-y-0 scale-100">
                <div>
                    <div>
                        <div class="az-file-upload-control loading"> <!-- add logging? -->
                            <div class="az-loading">
                                <div class="az-loading-info"><img class="az-spinner"
                                                                  src="https://cherrett-digital.s3.amazonaws.com/spinner.gif"><!-- <span>82%</span> -- no % as we don't know it!-->
                                </div>
                                <div class="az-logging-info">
                                    <div id="showdetail"  style="display: block">
                                        <button onclick="showHideDiv('showdetail');showHideDiv('hidedetail');showHideDiv('serverStatus')">
                                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                 stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                <path stroke-linecap="round" stroke-linejoin="round"
                                                      d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                            </svg>
                                            Show detail
                                        </button>
                                    </div>

                                    <div id="hidedetail" style="display: none">
                                        <button onclick="showHideDiv('showdetail');showHideDiv('hidedetail');showHideDiv('serverStatus')">
                                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                 stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                <path stroke-linecap="round" stroke-linejoin="round"
                                                      d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                            </svg>
                                            Hide detail
                                        </button>
                                    </div>
                                    <div id="serverStatus" style="height:145px; width:100%;font:10px monospace;overflow:auto;display: none"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

</div>
<style>
    /* remove dash borders of the auto filter */
    [class*="af"]:after {
        border: initial !important;
    }
</style>
<%@ include file="../includes/new_footer.jsp" %>