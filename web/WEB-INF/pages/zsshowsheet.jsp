<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="View Report" />
<c:set var="compact" scope="request" value="compact" />
<c:set var="requirezss" scope="request" value="true" />
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<script type="text/javascript">
// this should be below the header??
    // new post ajax based on Keikai


var autoReloadExternalOn = 0;

    function postAjax(action) {
        //use window.fetch() API
        fetch("/api/ZKSpreadsheetCommand", {
            headers: {
                'Content-Type': 'application/json',
            },
            method: 'POST',
            credentials: 'include',
            // 'myzss' is the id specified on kkjsp tag
            body: JSON.stringify(kkjsp.prepare('myzss', {action: action})) // preparing Keikai's request data
        })
            .then(function (response) {
                return response.json();
            })
            .then(kkjsp.process) // render change generated by SmartUpdateBridge
            .then(handleAjaxResult); //optional post-processing
    }

    // the chosen tree will need to push things through
    function postNameIdForChosenTree(nameId) {
        //use window.fetch() API
        fetch("/api/ZKSpreadsheetCommand", {
            headers: {
                'Content-Type': 'application/json',
            },
            method: 'POST',
            credentials: 'include',
            // 'myzss' is the id specified on kkjsp tag
            body: JSON.stringify(kkjsp.prepare('myzss', {action: "nameIdForChosenTree", nameIdForChosenTree: String(nameId)})) // preparing Keikai's request data, it wants strings
        })
            .then(function (response) {
                return response.json();
            })
            .then(kkjsp.process) // render change generated by SmartUpdateBridge
            .then(handleAjaxResult); //optional post-processing
    }

    //the method to handle ajax result from your servlet
    function handleAjaxResult(result) {
//use your way to hanlde you ajax message or error
        if (result.message) {
            alert(result.message);
        }
//use your way handle your ajax action result
        if (result.action == "check" && result.valid) {
            if (result.form) {
//create a form dynamically to submit the form data
                var field, form = jq("<form action='submitted.jsp' method='post'/>").appendTo('body');
                for (var nm in result.form) {
                    field = jq("<input type='hidden' name='" + nm + "' />").appendTo(form);
                    field.val(result.form[nm]);
                }
                form.submit();
            }
        }
    }

    function inspectDatabase(){
        // can be passed database
        $.inspectOverlay("Inspect Azquo").tab("/api/Jstree?op=new", "DB :  ${databaseName}");
        return false;
        // window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }

    function auditDatabase(){
        // can be passed database
        window.open("/api/AuditDatabase", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=100, left=100, width=1600, height=1000")
    }


    // a more advanced choice option as opposed to the dropdown
    function chosenTree(query){
        // can be passed database
        $.inspectOverlay("Inspect").tab("/api/Jstree?op=new&query=" +  encodeURIComponent(query), "${databaseName}");
        return false;
        // window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }


    function uploadFile(){
        var el = $('<div class="overlay"><iframe src="/api/Online?opcode=upload" width="100%" height="100%" frameborder="0" scrolling="auto"></iframe></div>').hide().appendTo('body');

        el.dialog({
            modal	: 'true',
            width	: 'auto',
            title	: 'Upload File',
            close: refreshReport
        });

        el.show();
        //window.open("/api/Online?opcode=upload", "_blank", "toolbar=no, status=no,scrollbars=no, resizable=no, top=150, left=200, width=300, height=300")
    }

    // could inline?
    function refreshReport(){
        location.reload();
    }


    function mouseIn(elementChosen, e){
        if (e==null) return false;
        var IE = document.all?true:false;
        if (IE) { // grab the x-y pos.s if browser is IE
            var mouseX = e.clientX + document.body.scrollLeft
            var mouseY = e.clientY + document.body.scrollTop
        } else {  // grab the x-y pos.s if browser is NS
            mouseX = e.pageX
            mouseY = e.pageY
        }

        var el=elementChosen;
        for (var lx=0, ly=0;
             el != null;
             lx += el.offsetLeft-el.scrollLeft, ly += el.offsetTop-el.scrollTop, el = el.offsetParent);
        if (mouseX >= lx && mouseY >= ly && mouseX <= lx + elementChosen.offsetWidth && mouseY <= ly + elementChosen.offsetHeight){
            return true;
        }
        return false;
    }
    /*

    var skipSetting = 0;
    var skipMarker = 0;
        // how to stop this hammering? I reckon add a second every time between checks if the data hasn't changed.
    function updateStatus(){
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
    ma
    setInterval(function(){ updateStatus(); }, 1000);*/

function toggleAutoReloadExternal(){
    if (autoReloadExternalOn == 0){
        autoReloadExternalOn = 1;
        document.getElementById("updateButtonText").innerHTML = "Auto Update External Data : ON";
    } else {
        autoReloadExternalOn = 0;
        document.getElementById("updateButtonText").innerHTML = "Auto Update External Data : OFF";
    }
}

setInterval(function () {
    if (autoReloadExternalOn == 1){
        postAjax('ActuallyRELOADEXTERNAL');
    }
}, 30000);

</script>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <span id="lockedResult"><c:if test="${not empty lockedResult}"><textarea class="public" style="height:60px;width:400px;font:10px monospace;overflow:auto;font-family:arial;background:#f58030;color:#fff;font-size:14px;border:0">${lockedResult}</textarea></c:if></span>    <div class="az-topbar">
        <div class="az-searchbar"  style="font-size : 1.5rem; margin: auto">${reportName}
        </div>
        <div class="az-topbar-menu">
            <c:if test="${reloadExternal == true}">
                <div class="az-dropdown">
                <button type="button" aria-haspopup="true" aria-expanded="false" onclick="toggleAutoReloadExternal()">
                    <span id="updateButtonText">Auto Update External Data : OFF</span>
                </button>
                </div>
            </c:if>

            <c:if test="${xml == true}">

                <button class="az-button" type="button" onclick="postAjax('XML'); return false;" >
                    Send XML
                </button>


            </c:if>

            <button id="saveDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="az-button" type="button" onclick="postAjax('Save'); return false;" >
                Save Data
            </button>

            <button id="restoreDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="az-button" type="button" onclick="postAjax('RestoreSavedValues'); return false;" >
                Restore Saved Values
            </button>

            <div class="az-dropdown">
                <div>
                    <button id="headlessui-menu-button-:r0:" type="button" aria-haspopup="true" aria-expanded="false" onclick="window.open('/api/Jstree?op=new&database=${databaseName}')">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
                        <span>Inspect</span>
                    </button>
                </div>
            </div>
            <div class="az-dropdown">
                <div>
                    <button id="headlessui-menu-button-:r1:" type="button" aria-haspopup="true" aria-expanded="false"  onclick="window.location.assign(window.location.href+='&opcode=template')">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path></svg>
                        <span>View Template</span>
                    </button>
                </div>
            </div>
            <button class="az-button" type="button" onclick="postAjax('XLS'); return false;" >
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                     stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round"
                          d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
                Download
            </button>
<!--            <button class="az-button" type="button">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                     stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 9l4-4 4 4m0 6l-4 4-4-4"></path>
                </svg>
                Selections
            </button>-->


        </div>
    </div>
    <main>
        <div class="az-report-view">
            <div class="az-report">
                <kkjsp:spreadsheet id="myzss"
                                   bookProvider="com.azquo.spreadsheet.zk.BookProviderForJSP"
                                   apply="com.azquo.spreadsheet.zk.ZKComposer"
                                   height="100%"
                                   maxVisibleRows="500" maxVisibleColumns="200"
                                   showSheetbar="true" showToolbar="false" showFormulabar="true" showContextMenu="true"/>

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