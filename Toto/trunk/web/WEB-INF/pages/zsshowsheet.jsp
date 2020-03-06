<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="View Report" />
<c:set var="requirezss" scope="request" value="true" />
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<%@ include file="../includes/public_header.jsp" %>

<script type="text/javascript">
    // example functions modified
    function postAjax(action) {
        // on any of these we may want to see the log . . .
        window.skipSetting = 0;
        window.skipMarker = 0;
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
    	$.inspectOverlay("Inspect").tab("/api/Jstree?op=new", "${databaseName}");
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
    <zssjsp:spreadsheet id="myzss"
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
<%@ include file="../includes/public_footer.jsp" %>