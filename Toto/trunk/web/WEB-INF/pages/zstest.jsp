<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>Azquo report</title>
    <style>
        @font-face {
            font-family: 'CCode39';
            src: url('/CCode39.ttf');
        }

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
            z-index:25;
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
         window.open("/api/Online?opcode=upload", "_blank", "toolbar=no, status=no,scrollbars=no, resizable=no, top=150, left=200, width=300, height=300")
    }



    function openTopMenu(){
        var topmenu = document.getElementById("topmenubox");
        if (topmenu.style.display == "block"){
            topmenu.stype.display="none";

        }else {
            topmenu.style.display = "block";
        }
    }



</script>

<div id="wrapper" style="height: 100px;">
    <div class="banner" id="banner">
        <a href="/api/Online?opcode=loadsheet&reportid=1"><img src="/images/azquo-logo2.png" alt="Azquo logo"/></a>
        <a class="menubutton" href="#" onclick="openTopMenu();"><img src="/images/menu.png"></a>
        <div id="topmenubox" class="topmenubox">
            <ul  class="topmenu">
                <li><a href="#" onclick="postAjax('XLS')">Download as XLSX</a></li>
                <li><a href="#" onclick="postAjax('PDF');">Download as PDF</a></li>
                <li><a href="#" onclick="inspectDatabase();">Inspect database</a></li>
                <li><a href="#" onclick="uploadFile();">Upload file</a></li>
               </ul>
        </div>



        <!--        <a class="savedata" href="#" onclick="postAjax('Save')" id="saveData" style="display:none;">Save data</a> -->

<!--
        <button id="xlsButton">XLS</button>
        <button id="pdfButton">PDF</button>
        <button id="inspectButton" onclick="inspectDatabase()">Inspect</button> -->
        <button id="saveData" onclick="postAjax('Save')" <c:if test="${showSave == false}"> style="display:none;" </c:if>>Save Data</button>
        <c:forEach items="${pdfMerges}" var="pdfMerge">
            &nbsp;<button id="${pdfMerge}" onclick="postAjax('PDFMerge${pdfMerge}')">PDF : ${pdfMerge}</button>
        </c:forEach>

    </div>
</div>
<div style="height: calc(100% - 100px);">
    <zssjsp:spreadsheet id="myzss"
                        bookProvider="com.azquo.spreadsheet.view.ZKAzquoBookProvider"
                        apply="com.azquo.spreadsheet.view.ZKComposer"
                        width="100%" height="100%"
                        maxrows="200" maxcolumns="80"
                        showSheetbar="true" showToolbar="true" showFormulabar="true" showContextMenu="true"/>
    <!--    zssjsp:spreadsheet id="myzss"
                            bookProvider="com.azquo.spreadsheet.view.ZKAzquoBookProvider"
                            apply="com.azquo.spreadsheet.view.ZKComposer"
                            width="1850px" height="900px"
                            maxrows="1000" maxcolumns="80"
                            showToolbar="true" showFormulabar="true" showContextMenu="true"/>-->
</div>
</body>
</html>