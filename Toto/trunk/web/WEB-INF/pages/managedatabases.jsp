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
<c:set var="title" scope="request" value="Manage Databases"/>
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
    <h1>Manage Databases</h1>
    <div class="error">${error}</div>
    <div id="working" class="loading" style="display:none"><h3>Working...</h3>
        <div class="loader"><span class="fa fa-spin fa-cog"></span></div>
    </div>
    <div class="tabs">
        <ul>
            <li><a href="#tab1">Uploads</a></li>
            <li><a href="#tab2">DB Management</a></li>
            <li><a href="#tab3">Restore Backup</a></li>
            <li><a href="#tab4">Pending Uploads</a></li>
            <li><a href="#tab5">Import Templates</a></li>
        </ul>
        <!-- Uploads -->
        <div id="tab1" style="display:none">
            <h3>Uploads</h3>
            <div class="well">
                <form action="/api/ManageDatabases" method="post" enctype="multipart/form-data"
                      onsubmit="$('#working').show();">
                    <table>
                        <tbody>
                        <tr>
                            <td><label for="uploadFile">Upload Files:</label> <input id="uploadFile" type="file" name="uploadFile" multiple></td>
                            <td><label for="userComment">Comment:</label><input id="userComment" size="40" name="userComment" required pattern="(.|\s)*\S(.|\s)*"></td>
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
                    <td><a href="/api/ManageDatabases?sort=${datesort}">Date</a></td>
                    <td><a href="/api/ManageDatabases?sort=${businessnamesort}">Business Name</a></td>
                    <td><a href="/api/ManageDatabases?sort=${dbsort}">Database Name</a></td>
                    <td><a href="/api/ManageDatabases?sort=${usernamesort}">User Name</a></td>
                    <td>
                        <form method="post"> File Name <input size="20" name="fileSearch"></form>
                    </td>
                    <td>Count</td>
                    <td width= "30%">Comment</td>
                    <td colspan="4"><a href="/api/ManageDatabases?withautos=true" class="button small" title="Show Automated Uploads">Show Automated Uploads</a></td>
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
                        <td><a href="/api/ManageDatabases?fileSearch=${upload.fileName}">${upload.count}</a></td>
                        <td>${upload.userComment}</td>
                        <td><a href="/api/UploadRecordComment?urid=${upload.id}" target="new"
                               class="button inspect small" data-title="Edit" title="Comment" id="comment${upload.id}">
                            <c:if test="${upload.userComment.length() > 0}">
                                Edit
                            </c:if>
                            <c:if test="${upload.userComment.length() == 0}">
                                Comment
                            </c:if>

                        </a></td>
                        <td><c:if test="${upload.comments.length() > 0}">
                            <a href="/api/ImportResults?urid=${upload.id}" target="new"
                               class="button inspect small" data-title="Import Results" title="View Import Results">Results</a>
                        </c:if></td>

                        <td><c:if test="${upload.downloadable}"><a href="/api/DownloadFile?uploadRecordId=${upload.id}"
                                                                   class="button small" title="Download"><span
                                class="fa fa-download" title="Download"></span> </a></c:if></td>
                        <td><a href="/api/ManageDatabases?deleteUploadRecordId=${upload.id}" class="button small"
                               title="Delete"><span class="fa fa-trash" title="Delete"></span> </a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>


        </div>
        <!-- END Uploads -->
        <!-- DB Management -->
        <div id="tab2" style="display:none">
            <h3>Create new database:</h3>
            <div class="well">
                <form action="/api/ManageDatabases" method="post">
                    <table>
                        <tr>
                            <td><label for="createDatabase">Database Name:</label> <input name="createDatabase"
                                                                                          id="createDatabase"/></td>
                            <td>
                                <!-- <label for="databaseType">Database Type:</label> <input name="databaseType" id="databaseType"/>--></td>
                            <td>
                                <c:if test="${serverList == true}">
                                    <label for="databaseServerId">Select Server:</label>
                                    <select name="databaseServerId" id="databaseServerId">
                                        <c:forEach items="${databaseServers}" var="databaseServer">
                                            <option value="${databaseServer.id}">${databaseServer.name}</option>
                                        </c:forEach>
                                    </select>
                                </c:if>
                            </td>
                            <td>
                                <input type="submit" name="Create Database" value="Create Database" class="button"/>
                            </td>
                        </tr>
                    </table>
                </form>
            </div>
            <!-- Database Options Table -->
            <table>
                <thead>
                <tr>
                    <!--<td>${database.id}</td> -->
                    <!--<td>${database.businessId}</td>-->
                    <td>Name</td>
                    <td>Persistence Name</td>
                    <td>Name Count</td>
                    <td>Value Count</td>
                    <td>Created</td>
                    <td>Last Audit</td>
                    <td>Auto backup</td>
                    <td></td>
                    <td></td>
                    <td></td>
                    <td></td>
                    <td></td>
                    <td></td>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${databases}" var="database">
                    <tr>
                        <!--<td>${database.id}</td>-->
                        <!--<td>${database.businessId}</td> -->
                        <td>${database.name}</td>
                        <td>${database.persistenceName}</td>
                        <td>${database.nameCount}</td>
                        <td>${database.valueCount}</td>
                        <td>${database.created}</td>
                        <td>${database.lastProvenance}</td>
                        <td>
                            <a href="/api/ManageDatabases?toggleAutobackup=${database.id}&ab=${database.autobackup}#tab2">${database.autobackup}</a><c:if
                                test="${database.autobackup}">&nbsp;|&nbsp;<a href="/api/ManageDatabaseBackups?databaseId=${database.id}">view</a></c:if>
                        </td>
                        <td><a href="/api/Jstree?op=new&database=${database.urlEncodedName}"
                               data-title="${database.urlEncodedName}" class="button small inspect"
                               title="Inspect"><span class="fa fa-eye" title="Inspect ${database.name}"></span></a></td>
                        <td><a href="/api/ManageDatabases?emptyId=${database.id}#tab2"
                               onclick="return confirm('Are you sure you want to Empty ${database.name}?')"
                               class="button small" title="Empty ${database.name}"><span class="fa fa-bomb"
                                                                                         title="Empty"></span></a></td>
                        <td><a href="/api/ManageDatabases?deleteId=${database.id}#tab2"
                               onclick="return confirm('Are you sure you want to Delete ${database.name}?')"
                               class="button small" title="Delete ${database.name}"><span class="fa fa-trash"
                                                                                          title="Delete"></span> </a>
                        </td>
                        <td><a onclick="showWorking();" href="/api/DownloadBackup?id=${database.id}"
                               class="button small"
                               title="Download Backup for ${database.name}"><span class="fa fa-download"
                                                                                  title="Download Backup"></span> </a>
                        </td>
                        <td><c:if test="${database.loaded}"><a href="/api/ManageDatabases?unloadId=${database.id}"
                                                               class="button small"
                                                               title="Unload ${database.name}"><span class="fa fa-eject"
                                                                                                     title="Unload"></span></a></c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <!-- END DB Management -->
        <!-- Maintenance -->
        <div id="tab3" style="display:none">
            <h3>Restore Backup.</h3>
            WARNING : the database specified internally by the zip or "Database" here will zap a database and associated
            reports and auto backups if they exist before it restores the file contents.
            <form onsubmit="$('#working').show();" action="/api/ManageDatabases" method="post"
                  enctype="multipart/form-data">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Upload Backup File:</label> <input type="file" name="uploadFile">
                        </td>
                        <td><input type="hidden" name="backup" value="true"/></td>
                        <td>Database <input type="text" name="database" value=""/></td>
                        <td><input type="submit" name="Upload" value="Upload" class="button "/></td>
                    </tr>
                    </tbody>
                </table>
            </form>
            <h3>Download Custom Backup.</h3>
            For advanced users - specify a subset of a database to download.
            <form onsubmit="$('#working').show();" action="/api//DownloadBackup" method="get">
                <table>
                    <tbody>
                    <tr>
                        <td>Database:                                 <select name="id" >
                            <c:forEach items="${databases}" var="database">
                                <option value="${database.id}" <c:if
                                        test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                            </c:forEach>
                        </select>

                        </td>
                        <td>Name subset <input type="text" name="namesubset" value=""/></td>
                        <td><input type="submit" name="Download" value="Download" class="button "/></td>
                    </tr>
                    </tbody>
                </table>
            </form>
            <h3>Memory/CPU report for servers:</h3>
            <div class="well">
                <c:forEach items="${databaseServers}" var="databaseServer">
                    <a href="/api/MemoryReport?serverIp=${databaseServer.ip}" target="new"
                       class="button report">${databaseServer.name}</a>
                </c:forEach>
            </div>
            <div class="well">
                    <a href="/api/UserLog"
                       class="button" target="new">User Log</a>
            </div>

        </div>
        <!-- END Maintenance -->
        <!-- Pending Uploads -->
        <div id="tab4" style="display:none">
            <form action="/api/ManageDatabases#tab4" method="post" enctype="multipart/form-data">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Manually add file for validation : </label> <input type="file"
                                                                                                       name="uploadFile">
                        </td>
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
                        <form method="post" action="/api/ManageDatabases#tab4"> File Name <input size="20"
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
                <a href="/api/ManageDatabases#tab4" class="button">Normal View</a>&nbsp;
                <a href="/api/ManageDatabases?allteams=true#tab4" class="button">Show for all teams</a>&nbsp;
                <a href="/api/ManageDatabases?uploadreports=true#tab4" class="button">Show completed uploads</a>
            </div>
        </div>
        <!-- END pending Uploads -->
        <!-- Import Templates -->
        <div id="tab5" style="display:none">
            <h3>Import Templates</h3>
            <form action="/api/ManageDatabases#tab5" method="post" enctype="multipart/form-data">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Upload Template:</label> <input type="file" name="uploadFile">
                        </td>
                        <td><label for="templateComment">Comment:</label><input id="templateComment" size="40" name="userComment" required pattern="(.|\s)*\S(.|\s)*"></td>
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
                        <td>Notes</td>
                        <td>Date Uploaded</td>
                        <td></td>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${importTemplates}" var="template">
                        <tr>
                            <td>${template.user}</td>
                            <td>${template.templateName}</td>
                            <td>${template.notes}</td>
                            <td>${template.dateCreated}</td>
                            <td>
                                <a href="/api/ManageDatabases?deleteTemplateId=${template.id}#tab5"
                                   onclick="return confirm('Are you sure you want to delete ${template.templateName}?')"
                                   class="button small" title="Delete ${template.templateName}"><span
                                        class="fa fa-trash"
                                        title="Delete"></span>
                                </a>
                                <a href="/api/DownloadImportTemplate?importTemplateId=${template.id}#tab5"
                                   class="button small" title="Download"><span class="fa fa-download"
                                                                               title="Download"></span> </a>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </form>
            <br/>
            <br/>
            <h3>Assign Templates to Databases</h3>
            <form action="/api/ManageDatabases?templateassign=1#tab5" method="post">
                <table>
                    <thead>
                    <tr>
                        <td>Database</td>
                        <td>Template</td>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${databases}" var="database">
                        <tr>
                            <td>${database.name}</td>
                            <td>
                                <select name="templateName-${database.id}">
                                    <option value="">None</option>
                                    <c:forEach items="${importTemplates}" var="template">
                                        <option value="${template.templateName}" <c:if
                                                test="${template.templateName == database.importTemplate}"> selected </c:if>>${template.templateName}</option>
                                    </c:forEach>
                                </select>
                            </td>
                        </tr>
                    </c:forEach>

                    </tbody>
                </table>
                <input type="submit" name="Save Changes" value="Save Changes" class="button "/>
            </form>

            <br/>
            <br/>
            <h3>Test pre-processor. Select a pre-processor and a zip of data to test</h3>
            <form action="/api/ManageDatabases#tab5" method="post" enctype="multipart/form-data" onsubmit="$('#working').show();">
                <table>
                    <tbody>
                    <tr>
                        <td><label for="uploadFile">Upload Files:</label> <input id="preprocessorTest" type="file" name="preprocessorTest" multiple>&nbsp;&nbsp;<input type="submit" name="Upload" value="Upload" class="button "/></td>
                    </tr>
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

<%@ include file="../includes/admin_footer.jsp" %>
