<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Validation" />
<%@ include file="../includes/admin_header.jsp" %>
<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

</script>
<div id="dialog" title="" style="display:none;">
    <input type="hidden" name="commentId" id="commentId" value=""/>
<textarea id="comment" name="comment" rows="20" cols="80"></textarea>
    <a href="#" class="button" onclick="saveComment(); $('#dialog').dialog('close'); return false;">Save</a>
    <a href="#" class="button" onclick="$('#dialog').dialog('close'); return false;">Cancel</a>
</div>
    <main>
    <h1>Import Validation for ${filename}</h1>
    <form action="/api/PendingUpload" method="post">
        <input type="hidden" name="id" value="${id}"/>
        <c:choose>
            <c:when test="${dbselect == true}">
                The upload has not been assigned to a database, please select one to continue :
                <select name="databaseId"><c:forEach items="${databases}" var="database">
                    <option value="${database.id}">${database.name}</option>
                </c:forEach></select>
            </c:when>
            <c:otherwise>
                <h2>Database : ${database}</h2>
            </c:otherwise>
        </c:choose>
        <h2>Import Version : ${importversion}</h2>
        <h2>Month : ${month}</h2>
        <div class="error">${error}</div>
        <!-- params passed if they need to be set-->
<c:if test="${setparams == true}">
    <h2>Please confirm parameters :</h2>
</c:if>
        ${maintext}
    <div class="centeralign">
        <input type="submit" name="Ok" value="Ok" class="button"/>
        <a href="#" class="button">Reject</a>
        <a href="/api/ManageDatabases#tab4" class="button">Cancel</a>
    </div>
    </form>
</main>
<%@ include file="../includes/admin_footer.jsp" %>