<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Validation"/>
<%@ include file="../includes/new_header.jsp" %>
<!-- required for comments - presumably zap at some point -->
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>

<!-- todo - just user view? will the header sort that? -->

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
<div class="az-content">
    <main>
        <div class="az-users-view">

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
            <div class="az-section-heading">
                <h3>Import Validation for ${filename}</h3>
            </div>

            <form action="/api/PendingUpload" method="post" id="theForm" enctype="multipart/form-data">
                <input type="hidden" name="id" value="${id}"/>
                <c:choose>
                    <c:when test="${dbselect == true}">
                        The upload has not been assigned to a database, please select one to continue :
                            <select name="databaseId"><c:forEach items="${databases}" var="database">
                                <option value="${database.id}">${database.name}</option>
                            </c:forEach></select>
                    </c:when>
                    <c:otherwise>
                        <h4>Database : ${database}</h4>
                    </c:otherwise>
                </c:choose>
                <h4>Import Version : ${importversion}</h4>
                <h4>Preprocessor : ${preprocessor}</h4>
                <h4>Month : ${month}</h4>
                <div>${error}</div>
                <!-- params passed if they need to be set-->
                <c:if test="${runClearExecute == true}">
                    <h4>Clear ${month} data before upload : <input type="checkbox"
                                                                                      name="runClearExecute"></h4>
                </c:if>
                <c:if test="${setparams == true}">
                    <h4>Please confirm parameters :</h4>
                </c:if>
                ${maintext}
                <div class="az-table">
                    <nav>
                        <!-- needs te working thing here?? -->
                        <button  onclick="dbCheck();return false;" type="button">OK</button>
                        <button  onclick="window.location.assign('/api/PendingUpload?id=${id}&reject=true')" type="button">Reject</button>
                        <button  onclick="window.location.assign('/api/${cancelUrl}')" type="button">Cancel</button>
                    </nav>
                </div>
            </form>
        </div>
    </main>
</div>

<!-- could ever be a problem if just user?? -->
<%@ include file="../includes/new_footer.jsp" %>