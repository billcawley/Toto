<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Databases" />
<%@ include file="../includes/public_header.jsp" %>

<style>
    td {width:100px}
</style>


<div id="dialog" title="Basic dialog" class="basicDialog">
    (test data)
    <div class="context">
        <span class="contextDate">01/01/2017 11:00</span>
        by <span class="contextUser"> Bill Cawley</span> in the rest of the context
    </div>
    <div class="contextValues">
        <p>This is the default dialog which is useful for displaying information.The dialog window can be moved, resized and closed with the 'x' icon.</p>
    </div>
</div>


<div id="content-main">
    <div class="padding">
        <div id="message"> </div>
    </div>
</div>

<script>

    var $op="${op}";


    var $sheetName="${sheetname}";
    var $region="${region}";
    var $regionrow = "${regionrow}";
    var $regioncol="${regioncol}";
    var $database = "${database}";
    var $sessionId = "${sessionid}";
    var $audit = ${audit};

    if ($op=="audit") {
        showAudit();
    }
    if ($op=="inspect") {
        inspectDatabase($database);
    }

    function showAudit(){

        var html = "";
        if ($audit.function != null) {
            html = "<div class='function'>$audit.function</div>";
        }
        for (var afd = 0; afd < $audit.auditForDisplayList.length; afd++){
            var auditForDisplay = $audit.auditForDisplayList[afd];
            html += "<div class='context'><span class='contextDate'>" + new Date(auditForDisplay.date) + "</span> by <span class='contextUser'>" + auditForDisplay.user + "</span> : " + auditForDisplay.method + " : " + auditForDisplay.name + " : " + auditForDisplay.context  + "</div>";
            html +="<table>";

            for (var v = 0; v < auditForDisplay.valuesWithIdsAndNames.length; v++){
                var valuesEtc = auditForDisplay.valuesWithIdsAndNames[v];
                html += "<tr>";
                var count = 0;
                var valsList = String(valuesEtc.second);
                var values = valsList.split(",");
                for (var v2=0; v2 < values.length;v2++){
                    if (count==0){
                        html+="<td><b>" + values[v2] + "</b></td>";
                    }else{
                        html+="<td>" + values[v2] + "</td>"
                    }
                    count++;
                }
                html+="</tr>"
            }
            html+="</table>"

        }
        document.getElementById("dialog").innerHTML = html;
        return;
    }




    function inspectDatabase() {
        // can be passed database
        $['inspectOverlay']("Inspect").tab("Jstree?op=new&sessionId=" + $sessionId, "inspect");
        return false;
    }
</script>


<%@ include file="../includes/public_footer.jsp" %>
