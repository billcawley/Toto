<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Loading"/>
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
//                    location.replace("/api/${targetController}" + data.substring(4));// nothing added in most cases - EFC note this was to move people to the right tab
                    location.replace("/api/${targetController}?newdesign=imports");// nothing added in most cases
                    something = false;
                    return;
                } else { // possible headline info
                    //document.getElementById("headline").innerHTML = "<h1>" + data + "</h1>" // that would have broken before also . . . hmmm
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
<div class="az-content">
    <!--    <div class="az-topbar">
            <button>
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                     stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h8m-8 6h16"></path>
                </svg>
            </button>
            <div class="az-searchbar">
                <form action="#">
                    <div>
                        <div>
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                                 stroke="currentColor" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round"
                                      d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
                            </svg>
                        </div>
                        <input placeholder="Search" type="text" value=""></div>
                </form>
            </div>
            <div class="az-topbar-menu">
                <div class="az-dropdown">
                    <div>
                        <button id="headlessui-menu-button-:r0:" type="button" aria-haspopup="true" aria-expanded="false">
                            <span>File</span>
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                                 stroke="currentColor" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"></path>
                            </svg>
                        </button>
                    </div>
                </div>
                <div class="az-dropdown">
                    <div>
                        <button id="headlessui-menu-button-:r1:" type="button" aria-haspopup="true" aria-expanded="false">
                            <span>Database</span>
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                                 stroke="currentColor" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"></path>
                            </svg>
                        </button>
                    </div>
                </div>
                <div class="az-dropdown">
                    <div>
                        <button id="headlessui-menu-button-:r2:" type="button" aria-haspopup="true" aria-expanded="false">
                            <span>Template</span>
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                                 stroke="currentColor" aria-hidden="true">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"></path>
                            </svg>
                        </button>
                    </div>
                </div>
                <button class="az-button" type="button">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                         stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round"
                              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                    </svg>
                    Download
                </button>
                <button class="az-button" type="button">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                         stroke="currentColor" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M8 9l4-4 4 4m0 6l-4 4-4-4"></path>
                    </svg>
                    Selections
                </button>
            </div>
        </div>-->


    <div class="az-fileupload-modal-container">
        <div>
            <div id="headlessui-dialog-panel-:r6c:" class="opacity-100 translate-y-0 scale-100">
                <div class="az-fileupload">
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