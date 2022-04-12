<%-- Copyright (C) 2021 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Validation"/>
<c:if test="${admin == true}">
    <%@ include file="../includes/admin_header2.jsp" %>
</c:if>
<c:if test="${admin == false}">
    <%@ include file="../includes/public_header2.jsp" %>
</c:if>
<!-- required for comments - presumably zap at some point -->
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>



<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

    function doComment(commentId) {
        document.getElementById('comment').value = commentValues[commentId];
        document.getElementById('commentId').value = commentId;
        $('#dialog').dialog();
        $('#dialog').dialog('option', 'width', 800);
        $('#dialog').dialog('option', 'title', commentIds[commentId]);
    }

    function saveComment() {
        $.post("/api/PendingUpload", {
            id: "${id}",
            commentid: commentIds[document.getElementById('commentId').value],
            commentsave: document.getElementById('comment').value
        });
        commentValues[document.getElementById('commentId').value] = document.getElementById('comment').value; // or it will reset when clicked on even if saved
    }

    function dbCheck() {
        if (document.getElementById('finalsubmit') == null) {
            document.getElementById('theForm').submit();
        } else {
            $.post("/api/PendingUpload?id=${id}&dbcheck=true", function (data) {
                if (data.indexOf("ok") != 0) { // the sheet should be ready, note indexof not startswith, support for the former better
                    $('#concurrentWarning').dialog();
                    document.getElementById('mostRecentProvenance').innerHTML = "<b>" + data + "</b>";
                    $('#concurrentWarning').dialog('option', 'height', 'auto');
                } else {
                    document.getElementById('theForm').submit();
                }
                return;
            });
        }
    }

</script>
<div id="dialog" title="" style="display:none;">
    <input type="hidden" name="commentId" id="commentId" value=""/>
    <textarea id="comment" name="comment" rows="15" cols="80"></textarea><br/>
    <a href="#" class="button" onclick="saveComment(); $('#dialog').dialog('close'); return false;">Save</a>
    <a href="#" class="button" onclick="$('#dialog').dialog('close'); return false;">Cancel</a>
</div>
<div class="box" id="concurrentWarning" title="Warning" style="display:none; background-color: #FFFFFF">
    The database has been modified since validation, most recent provenance
    <br/>
    <div id="mostRecentProvenance"></div>
    <br/>
    <a href="#" class="button is-small" onclick="document.getElementById('theForm').submit();return false;">Continue
        Importing</a><br/><br/>
    <a href="#" class="button is-small"
       onclick="document.getElementById('finalsubmit').value = '';document.getElementById('theForm').submit();return false;">Run
        Validation Again</a>
</div>
<div class="box">
    <h5 class="title is-5">Import Validation for ${filename}</h5>
    <form action="/api/PendingUpload" method="post" id="theForm" enctype="multipart/form-data">
        <input type="hidden" name="id" value="${id}"/>
        <c:choose>
            <c:when test="${dbselect == true}">
                The upload has not been assigned to a database, please select one to continue :
            <div class="select is-small">
                <select name="databaseId"><c:forEach items="${databases}" var="database">
                    <option value="${database.id}">${database.name}</option>
                </c:forEach></select>
            </div>
            </c:when>
            <c:otherwise>
        <h5 class="title is-5">Database : ${database}</h5>
            </c:otherwise>
        </c:choose>
        <h5 class="title is-5">Import Version : ${importversion}</h5>
            <h5 class="title is-5">Preprocessor : ${preprocessor}</h5>
                <h5 class="title is-5">Month : ${month}</h5>
        <div class="is-danger">${error}</div>
        <!-- params passed if they need to be set-->
        <c:if test="${runClearExecute == true}">
            <h5 class="title is-5">Clear ${month} data before upload : <input type="checkbox" name="runClearExecute"></h5>
        </c:if>
        <c:if test="${setparams == true}">
        <h5 class="title is-5">Please confirm parameters :</h5>
        </c:if>
        ${maintext}
        <div class="container has-text-centered">
            <a href="#" class="button is-small" onclick="dbCheck();return false;">OK</a>
            <a href="/api/PendingUpload?id=${id}&reject=true" class="button is-small">Reject</a>
            <a href="/api/${cancelUrl}" class="button is-small">Cancel</a>
        </div>
    </form>
</div>
<!-- could ever be a problem if just user?? -->
<%@ include file="../includes/admin_footer.jsp" %>
