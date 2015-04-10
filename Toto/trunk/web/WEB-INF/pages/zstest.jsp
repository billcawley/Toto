<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>Azquo report</title>
    <style>


        .menubutton{
            position:absolute;
            right:0;
            top:0;
            height:20px;
            width:30px;
            overflow:hidden;
            border:1px solid #888888;
            background-color: #cccccc;
            text-align:center;
        }

        .topmenubox{
            position:absolute;
            right:0;
            top:20px;
            border:1px solid silver;
            width:250px;
            display:none;
            z-index:3;
            background-color: white;
            height:400px

        }


        .topmenu{

            list-style-type:none;
            margin:0;
            padding:0;
            font-family:Arial;
            font-size:12px;

        }

        .topmenu li {
            position:relative;
            display:block;
            padding:10px;
            color:#444444;


        }

        .topmenu li ul{
            display:none;
            position:absolute;
            line-height:5px;
            z-index:1;
            border:1px solid #cccccc;
            padding:10px;
            background-color:white;
        }

        .topmenu li:hover ul{
            display:block;

        }

        .topmenu li ul li{
            clear:both;

        }

        .topmenu a{
            text-decoration:none;
            color:#444444;


        }

        .savedata{
            position:absolute;
            left:300px;
            top:20px;
            display:none;
            font-family:Arial;
            font-size:24px;
            text-decoration:none;
            color:#444444;

        }

        .savedata{
            position:absolute;
            left:300px;
            top:20px;
            display:none;
            font-family:Arial;
            font-size:24px;
            text-decoration:none;
            color:#444444;

        }



        .highlightoptions {
            display:none;
            width: 160px;
            height: 200px;
            position: absolute;
            z-index: 4;
            padding: 0px 10px 10px 10px;
            background-color: #eeeeee;
            border: 1px solid #444444;
            top: 100px;
            left: 100px;
            line-height: 8px;
            text-align: center;
        }

        .highlightoptions a{
            text-decoration:none;
            font-weight: bold;
            color:#444444;
        }

        .button{
            height:20px;
            width:60px;
            background: transparent url("/images/button.png") no-repeat left top;
            text-align:center;
            vertical-align: middle;
            position:absolute;
            left:0;
            top:0;
            padding:2px;
            z-index:5;
        }

        .button a{
            text-decoration:none;
            color:#ffffff;
        }



    </style>
    <zssjsp:head/>
</head>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<body>

<script type="text/javascript">

    /*
    //jq is jquery name in zk, which version is 1.6.4 in sparedsheet 3.0.0 (zk 6.5.3 and later)
    jq(document).ready(function () {
//register client event on button by jquery api
        jq("#xlsButton").click(function () {
            postAjax("XLS");
        });
        jq("#pdfButton").click(function () {
            postAjax("PDF");
        });
    });
    */
    // example functions modified
    function postAjax(action) {
//get the necessary zk ids form zssjsp[component_id]
//'myzss' is the sparedhseet id that you gaved in sparedsheet tag
        var desktopId = zssjsp['myzss'].desktopId;
        var zssUuid = zssjsp['myzss'].uuid;
//use jquery api to post ajax to your servlet (in this demo, it is AjaxBookServlet),
//provide desktop id and spreadsheet uuid to access zk component data in your servlet
        jq.ajax({url: "/api/ZKSpreadsheetCommand",//the servlet url to handle ajax request
            data: {desktopId: desktopId, zssUuid: zssUuid, action: action},
            type: 'POST', dataType: 'json'}).done(handleAjaxResult);
    }
    //the method to handle ajax result from your servlet
    function handleAjaxResult(result) {
//process the json result that contains zk client update information
        zssjsp.processJson(result);
//use your way to hanlde you ajax message or error
        if (result.message) {
            alert(result.message);
        }
        ;
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
        // deliberately leaving the database blank for the mo, it's in ${databaseChosen}
        window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }


    function uploadFile(){
         window.open("/api/Online?op=upload", "_blank", "toolbar=no, status=no,scrollbars=no, resizable=no, top=150, left=200, width=300, height=300")
    }



    function showHighlight() {
        document.getElementById("highlightoptions").style.display = "block";
        document.getElementById(dataRegionMenu).style.display = "none";
    }

    function highlight(days){
        document.getElementById("editedName").value = "highlight";
        document.getElementById("editedValue").value = days + "";
        document.getElementById("opcode").value="setchosen";
        document.azquoform.submit();
    }

    function openTopMenu(){
        document.getElementById("topmenubox").style.display="block";
        topMenuOpening = true;
    }



</script>

<div id="wrapper">
    <div class="banner" id="banner">
        <a href="/api/Online"><img src="/images/azquo-logo2.png" alt="Azquo logo"/></a>
        <a class="menubutton" href="#" onclick="openTopMenu();"><img src="/images/menu.png"></a>
        <div id="topmenubox" class="topmenubox">
            <ul  class="topmenu">
                <li><a href="#" onclick="postAjax('XLS')">Download as XLSX</a></li>
                <li><a href="#" onclick="postAjax('PDF');">Download as PDF</a></li>
                <li><a href="#" onclick="inspectDatabase();">Inspect database</a></li>
                <li><a href="#" onclick="uploadFile();">Upload file</a></li>
                <li>
                    <a href="#"  onclick="showHighlight();">Highlight changed data
                        (currently $hdays days)
                    </a>
                </li>
                <li><a href="#" onclick="tryNewSheet()">Show new version</a></li>
            </ul>
        </div>
        <a class="savedata" href="#" onclick="saveData()" id="saveData" style="display:none;">Save data</a>
        <div id="highlightoptions" class="highlightoptions">
            <p>Highlight data changed recently</p>
            <div class="highlightoption"><a href="#" onclick="highlight(0)">None</a></div>
            <div class="highlightoption"><a href="#" onclick="highlight(1)">1 day</a></div>
            <div class="highlightoption"><a href="#" onclick="highlight(7)">7 days</a></div>
            <div class="highlightoption"><a href="#" onclick="highlight(30)">30 days</a></div>
            <div class="highlightoption"><a href="#" onclick="highlight(90)">90 days</a></div>
            <div class="highlightoption"><a href="#" onclick="highlight(365)">1 year</a></div>

        </div>




<!---
        <button id="xlsButton">XLS</button>
        <button id="pdfButton">PDF</button>
        <button id="inspectButton" onclick="inspectDatabase()">Inspect</button> --->
    </div>
</div>
<div>
    <zssjsp:spreadsheet id="myzss"
                        bookProvider="com.azquo.view.ZKAzquoBookProvider"
                        apply="com.azquo.view.ZKComposer"
                        width="100%" height="900px"
                        maxrows="200" maxcolumns="80"
                        showSheetbar="true" showToolbar="true" showFormulabar="true" showContextMenu="true"/>
    <!--    zssjsp:spreadsheet id="myzss"
                            bookProvider="com.azquo.view.ZKAzquoBookProvider"
                            apply="com.azquo.view.ZKComposer"
                            width="1850px" height="900px"
                            maxrows="1000" maxcolumns="80"
                            showToolbar="true" showFormulabar="true" showContextMenu="true"/>-->
</div>
</body>
</html>