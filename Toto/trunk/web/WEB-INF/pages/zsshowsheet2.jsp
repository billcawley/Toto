<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %><%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>View Report - Azquo</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
    <!-- required for jstree -->
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
    <script type="text/javascript" src="/js/global.js"></script>
</head>
<body>





<nav class="navbar is-black" role="navigation" aria-label="main navigation">
    <div class="navbar-brand">
        <a class="navbar-item" href="https://azquo.com">
            <img src="${logo}" alt="azquo">
        </a>
    </div>
    <div id="navbarBasicExample" class="navbar-menu">
        <div class="navbar-start">
            <a class="navbar-item" href="/api/ManageReports">Reports</a>

                <a class="navbar-item" href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>-->
                <c:if test="${xml == true}"><a class="navbar-item" href="#" onclick="postAjax('XML');return false;">Send XML</a></c:if>
                <c:if test="${xmlzip == true}"><a class="navbar-item" href="#" onclick="postAjax('XMLZIP');return false;">Download XML</a></c:if>
                <c:if test="${showTemplate == true}"><a class="navbar-item" href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">View Template</a></c:if>
                <c:if test="${execute == true}"><a class="navbar-item" href="#" onclick="postAjax('ExecuteSave');window.location.assign(window.location.href+='&opcode=execute')">Execute</a></c:if>
        </div>
        <div class="navbar-end">
            <c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
                <a class="navbar-item" href="/api/Login?select=true">Switch Business</a>
            </c:if>
            <a class="navbar-item" href="/api/Login?logoff=true">Log Off</a>
            <div class="navbar-item has-dropdown is-hoverable">
                <a class="navbar-link is-arrowless" >
                    <span class="fa fa-bars"></span>
                </a>
                <div class="navbar-dropdown is-boxed is-right">
                    <a class="navbar-item"  href="#" onclick="postAjax('XLS'); return false;" title="Download as XLSX (Excel)"><span class="fa fa-file-excel-o"></span>&nbsp;&nbsp;Download as XLSX (Excel)</a>
                    <c:if test="${showTemplate == true}"><a class="navbar-item"   href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;View Template</a></c:if>
                    <a class="navbar-item"  href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Inspect database</a>
                    <a class="navbar-item"  href="#" onclick="return auditDatabase();" title="Audit Database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Audit Database</a>
                    <a class="navbar-item"  href="#" onclick="return uploadFile();" title="Upload file"><span class="fa fa-cloud-upload"></span>&nbsp;&nbsp;Upload file</a>
                    <a class="navbar-item"  href="#" onclick="return postAjax('FREEZE');" title="Upload file"><span class="fa fa-link"></span>&nbsp;&nbsp;Freeze</a>
                    <a class="navbar-item"  href="#" onclick="return postAjax('UNFREEZE');" title="Upload file"><span class="fa fa-unlink"></span>&nbsp;&nbsp;Unfreeze</a>
                    <c:if test="${masterUser == true}">
                        <a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADUSERS" title="Download User List">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download User List</a>
                        <a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADREPORTSCHEDULES" title="Download Report Schedules">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download Report Schedules</a>
                    </c:if>
                    <a class="navbar-item"  href="/api/UserUpload#tab2" title="Upload Data">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Data Validation</a>

                </div>
            </div>
        </div>
    </div>
</nav>

<script type="text/javascript">

    // new post ajax based on Keikai

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

</script>


<div style="height: calc(100% - 70px);">
    <kkjsp:spreadsheet id="myzss"
                       bookProvider="com.azquo.spreadsheet.zk.BookProviderForJSP"
                       apply="com.azquo.spreadsheet.zk.ZKComposer"
                       width="100%" height="100%"
                       maxVisibleRows="500" maxVisibleColumns="200"
                       showSheetbar="true" showToolbar="false" showFormulabar="true" showContextMenu="true"/>

    <!--    <div id="serverStatus"></div> -->
</div>
<style>
    /* remove dash borders of the auto filter */
    [class*="af"]:after {
        border: initial !important;
    }
</style>


</body>
</html>