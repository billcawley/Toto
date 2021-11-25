<%--
Copyright (C) 2016 Azquo Ltd.

Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:48
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Validate File"/>
<%@ include file="../includes/public_header2.jsp" %>

<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

    function showWorking() {
        var x = document.getElementById("working");
        x.style.display = "block";
    }

</script>

<div id="tabs-with-content">
    <c:if test="${!empty error}">
        <div style="width:100%;font:12px monospace;overflow:auto;padding:10px;max-height: 200px; background-color: #FFEEEE">${error}</div>
    </c:if>
    <div id="working" style="display:none;padding:10px"><h3 class="title is-3">Working... <span
            class="fa fa-spin fa-cog"></span></h3></div>
    <div>
            <div class="box">
                <div>
                    <form action="/api//UserUpload" method="post" enctype="multipart/form-data">
                        <table class="table">
                            <tbody>
                            <tr>
                                <td>
                                    <div class="file has-name is-small" id="file-js-example2">
                                        <label class="file-label is-small">
                                            <input class="file-input is-small" type="file" name="uploadFile">
                                            <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                Manually add file for validation
                                              </span>
                                            </span>
                                            <span class="file-name is-small">
                                            </span>
                                        </label>
                                    </div>
                                </td>
                                <td>
                                    <div class="field is-horizontal">
                                        <div class="field-label is-normal">
                                            <label class="label">Team</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="field">
                                                <input class="input is-small" type="text"
                                                       size="40"
                                                       name="team"></div>
                                        </div>
                                    </div>
                                <td>
                                    <div class="field is-horizontal">
                                        <div class="field-label is-normal">
                                            <label class="label">Database</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="select is-small">
                                                <select name="database" id="uploadDatabase2">
                                                    <c:forEach items="${databases}" var="database">
                                                        <option value="${database.name}" <c:if
                                                                test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                                    </c:forEach>
                                                </select>
                                            </div>
                                        </div>
                                    </div>

                                </td>
                                <td><input type="submit" name="Upload" value="Upload" class="button is-small"/></td>
                            </tr>
                            </tbody>
                        </table>
                    </form>
                </div>
            </div>

            <table class="table is-striped is-fullwidth">
                <thead>
                <tr>
                    <th>Created</th>
                    <th>By user</th>
                    <th>Processed</th>
                    <th>By user</th>
                    <th>
                        <form method="post" action="/api//UserUpload"> File Name <input size="20"
                                                                                              name="pendingUploadSearch">
                        </form>
                    </th>
                    <th>Size</th>
                    <th>Database</th>
                    <th></th>
                    <th></th>
                    <th></th>
                </tr>
                </thead>
                <tbody>

                <c:forEach items="${pendinguploads}" var="pendingupload">
                    <tr>
                        <td>${pendingupload.createdDate}</td>
                        <td>${pendingupload.createdByUserName}</td>
                        <td>${pendingupload.processedDate}</td>
                        <td>${pendingupload.processedByUserName}</td>
                        <td>${pendingupload.fileName}</td>
                        <td>${pendingupload.size}</td>
                        <td>${pendingupload.databaseName}</td>
                        <td>
                            <a href="/api/DownloadFile?pendingUploadId=${pendingupload.id}"
                               class="button is-small" title="Download"><span
                                    class="fa fa-download" title="Download"></span> </a></td>
                        <td>
                            <c:if test="${!pendingupload.loaded}"><a onclick="showWorking();"
                                                                     href="/api/PendingUpload?id=${pendingupload.id}&reject=true"
                                                                     class="button is-small"
                                                                     title="Reject">
                                Reject
                            </a></c:if>
                        </td>
                        <td>
                            <c:if test="${!pendingupload.loaded}"><a onclick="showWorking();"
                                                                     href="/api/PendingUpload?id=${pendingupload.id}"
                                                                     class="button is-small"
                                                                     title="Validate and Load">
                                Validate and Load
                            </a></c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            <div class="container has-text-centered">
                <a href="/api/UserUpload" class="button is-small">Normal View</a>&nbsp;
                <a href="/api/UserUpload?allteams=true" class="button is-small">Show for all teams</a>&nbsp;
                <a href="/api/UserUpload?uploadreports=true" class="button is-small">Show completed uploads</a>
            </div>

    </div>
</div>
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
</body>
</html>