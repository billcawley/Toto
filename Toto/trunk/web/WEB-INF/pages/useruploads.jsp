<%--
Copyright (C) 2019 Azquo Ltd.

Stripped down managed databases
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="User Uploads"/>
<%@ include file="../includes/public_header.jsp" %>
<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

    //this is jquery
    function showWorking() {
        $('html,body').scrollTop(0);
        $('#working').show();
        setTimeout('checkStatus()', 1000);
    }

    function checkStatus() {
        $.ajax({
            url: "/api/SpreadsheetStatus?action=working",
            type: "GET",
            dataType: 'json',
            success: function (data) {
                //$('#statusmessage').html(data.message);
                if (data.status == "working")
                    setTimeout('checkStatus()', 1000);
                else
                    $('#working').hide();
            }
        });
    }

    $(window).on("load", function () {
        $('html,body').scrollTop(0); // hitting a specific tab can make the screen be half way down, we don't want that
    })

</script>

<main class="databases">
    <h1>User Uploads</h1>
    <div class="error">${error}</div>
    <div id="working" class="loading" style="display:none"><h3>Working...</h3>
        <div class="loader"><span class="fa fa-spin fa-cog"></span></div>
    </div>
    <div class="tabs">
        <ul>
            <li><a href="#tab1">Direct Upload</a></li>
            <li><a href="#tab2">Pending Uploads</a></li>
            <li><a href="#tab3">Import Templates</a></li>
        </ul>
        <!-- Uploads -->
        <div id="tab1" style="display:none">
            <h3>Direct Upload</h3>
            <div class="well">
                <form action="/api/UserUpload" method="post" enctype="multipart/form-data"
                      onsubmit="$('#working').show();">
                    <table>
                        <tbody>
                        <tr>
                            <td><label for="uploadFile">Upload File:</label> <input id="uploadFile" type="file"
                                                                                    name="uploadFile"></td>
                            <td>
                                <label for="uploadDatabase">Database:</label>
                                <select name="database" id="uploadDatabase">
                                    <option value="">None</option>
                                    <c:forEach items="${databases}" var="database">
                                        <option value="${database.name}" <c:if
                                                test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                    </c:forEach>
                                </select>
                            </td>
                            <td><input type="submit" name="Upload" value="Upload" class="button "/></td>
                            <c:if test="${fn:contains(error,'values ')}">
                                <td><a href="/api/Showdata?chosen=changed" class="button small" title="Download"><span
                                        class="fa fa-search" title="View changed data"></span> </a></td>
                            </c:if>
                        </tr>
                        </tbody>
                    </table>
                </form>
            </div>
            <!-- Archive List -->
            <table>
                <thead>
                <tr>
                    <td><a href="/api/UserUpload?sort=${datesort}">Date</a></td>
                    <td><a href="/api/UserUpload?sort=${businessnamesort}">Business Name</a></td>
                    <td><a href="/api/UserUpload?sort=${dbsort}">Database Name</a></td>
                    <td><a href="/api/UserUpload?sort=${usernamesort}">User Name</a></td>
                    <td>
File Name<!-- no search here for the mo -->
                    </td>
                    <td>File Type</td>
                    <td></td>
                    <td></td>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${uploads}" var="upload">
                    <tr>
                        <td>${upload.formattedDate}</td>
                        <td>${upload.businessName}</td>
                        <td>${upload.databaseName}</td>
                        <td>${upload.userName}</td>
                        <td>${upload.fileName}</td>
                        <td>${upload.fileType}</td>
                        <td><c:if test="${upload.comments.length() > 0}">
                            <a href="/api/ImportResults?urid=${upload.id}" target="new"
                               class="button inspect small" data-title="Import Results" title="View Import Results">Results</a>
                        </c:if></td>

                        <td><c:if test="${upload.downloadable}"><a href="/api/DownloadFile?uploadRecordId=${upload.id}"
                                                                   class="button small" title="Download"><span
                                class="fa fa-download" title="Download"></span> </a></c:if></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>

        </div>
        <!-- END Uploads -->
        <!-- Pending Uploads -->
        <div id="tab2" style="display:none">
            <form action="/api/UserUpload#tab2" method="post" enctype="multipart/form-data">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Manually add file for validation : </label> <input type="file" name="uploadFile"></td>
                        <td>Team <input type="text" name="team"/></td>
                        <td>
                            <label for="uploadDatabase2">Database:</label>
                            <select name="database" id="uploadDatabase2">
                                <c:forEach items="${databases}" var="database">
                                    <option value="${database.name}" <c:if
                                            test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                </c:forEach>
                            </select>
                        </td>
                        <td><input type="submit" name="Upload" value="Upload" class="button"/></td>
                    </tr>
                    </tbody>
                </table>
            </form>

            <table>
                <thead>
                <tr>
                    <td>Created</td>
                    <td>By user</td>
                    <td>Processed</td>
                    <td>By user</td>
                    <td>
                        <form method="post" action="/api/UserUpload#tab2"> File Name <input size="20"
                                                                                                 name="pendingUploadSearch">
                        </form>
                    </td>
                    <td>Size</td>
                    <td>Database</td>
                    <td></td>
                    <td></td>
                    <td></td>
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
                               class="button small" title="Download"><span
                                    class="fa fa-download" title="Download"></span> </a></td>
                        <td>
                            <c:if test="${!pendingupload.loaded}"><a onclick="showHideDiv('working');"
                                                                     href="/api/PendingUpload?id=${pendingupload.id}&reject=true"
                                                                     class="button"
                                                                     title="Reject">
                                Reject
                            </a></c:if>
                        </td>
                        <td>
                            <c:if test="${!pendingupload.loaded}"><a onclick="showHideDiv('working');"
                                                                     href="/api/PendingUpload?id=${pendingupload.id}"
                                                                     class="button"
                                                                     title="Validate and Load">
                                Validate and Load
                            </a></c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            <div class="centeralign">
                <a href="/api/UserUpload#tab2" class="button">Normal View</a>&nbsp;
                <a href="/api/UserUpload?allteams=true#tab2" class="button">Show for all teams</a>&nbsp;
                <a href="/api/UserUpload?uploadreports=true#tab2" class="button">Show completed uploads</a>
            </div>
        </div>
        <!-- END pending Uploads -->
        <!-- Import Templates -->
        <div id="tab3" style="display:none">
            <h3>Import Templates</h3>
            <form action="/api/UserUpload#tab3" method="post" enctype="multipart/form-data">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Upload Template:</label> <input type="file" name="uploadFile">
                        </td>
                        <td>
                            <label for="uploadDatabase1">Database:</label>
                            <select name="database" id="uploadDatabase1">
                                <option value="">None</option>
                                <c:forEach items="${databases}" var="database">
                                    <option value="${database.name}" <c:if
                                            test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                </c:forEach>
                            </select>
                        </td>
                        <td><input type="hidden" name="template" value="true"/></td>
                        <td><input type="submit" name="Upload" value="Upload" class="button "/></td>
                    </tr>
                    </tbody>
                </table>
                <table>
                    <thead>
                    <tr>
                        <td>Uploader</td>
                        <td>Template Name</td>
                        <td>Date Uploaded</td>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${importTemplates}" var="template">
                        <tr>
                            <td>${template.user}</td>
                            <td>${template.templateName}</td>
                            <td>${template.dateCreated}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </form>
        </div>
        <!-- END Uploads -->
    </div>
</main>
<!-- tabs -->
<script type="text/javascript">
    $(document).ready(function () {
        $('.tabs').tabs();
    });
</script>
<%@ include file="../includes/public_footer.jsp" %>