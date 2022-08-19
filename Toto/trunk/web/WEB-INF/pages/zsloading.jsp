<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Loading" />
<c:set var="compact" scope="request" value="compact" />
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<c:set var="extraScripts" scope="request" value="<script type=\"text/javascript\" src=\"https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js\"></script><script type=\"text/javascript\" src=\"https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js\"></script>" />

<%@ include file="../includes/new_header.jsp" %>
<script type="text/javascript">
    var skipSetting = 0;
    var skipMarker = 5;
    // how to stop this hammering? I reckon add a second every time between checks if the data hasn't changed.
    function updateStatus(){
        jQuery.post("/api/SpreadsheetStatus?action=sheetReady&reportid=${reportid}", function(data){
            var objDiv = document.getElementById("serverStatus");
            if ("true" == data){ // the sheet should be ready
                location.reload();
                return;
            }
        });


        if (window.skipMarker <= 0){
            jQuery.post("/api/SpreadsheetStatus?action=log", function(data){
                var objDiv = document.getElementById("serverStatus");
                if (objDiv.innerHTML != data){ // it was updated
                    objDiv.innerHTML = data;
                    objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
                    objDiv.scrollTop = objDiv.scrollHeight;
                    // assume there could be more stuff!
                    window.skipSetting = 0;
                    window.skipMarker = 0;
                    document.getElementById("chosen").innerHTML = extractChoices(data);
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

    function extractChoices(data){
        var choices = new Map();
        const dataLines = data.split("<br>");
        var firstline = true;
        for (var dataLine of dataLines) {
            if (firstline) {
                firstline = false;//first line may be chopped
            } else {
                var choice = dataLine.split(" : ");
                if (choice.length > 1 && choice[0].indexOf(" finishing") < 0) {
                    choices.set(choice[0].trim(), choice[1].trim());
                }
            }
        }
        var toReturn = "";
        for (let ch of choices.keys()){
            toReturn+= ch + " = " + choices.get(ch) + "<br/>";
        }
        return toReturn;
    }

    setInterval(function(){ updateStatus(); }, 1000);


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
    <main>
        <div class="az-report-view">
            <div class="az-report">
                <div class="az-loading">




























                    <div class="az-loading-info">
                        <img
                                class="az-spinner"
                                src="https://cherrett-digital.s3.amazonaws.com/spinner.gif"
                        /><br/>
                        <div id="chosen" style="height:45px; width:100%;font:10px monospace;overflow:auto;display: none"></div>
                        <div id="serverStatus" style="height:145px; width:100%;font:10px monospace;overflow:auto;display: none"></div>
                        <!-- <div class="container has-text-centered" style="padding:10px;"><a href="javascript:void(0)" id="abort" onclick='jq.post("/api/SpreadsheetStatus?action=stop", null)'>Abort Load</a></div>-->
                    </div>
                </div>
            </div>
        </div>
    </main>
</div>
<style>
    /* remove dash borders of the auto filter */
    [class*="af"]:after {
        border: initial !important;
    }
</style>
<%@ include file="../includes/new_footer.jsp" %>