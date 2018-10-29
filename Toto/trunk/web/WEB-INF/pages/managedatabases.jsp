<%--
Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT

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


<main class="databases">
    <h1>Manage Databases</h1>
    <div class="error">${error}</div>
    <div class="tabs">
        <ul>
            <li><a href="#tab1">Uploads</a></li>
            <li><a href="#tab2">DB Management</a></li>
            <li><a href="#tab3">Maintenance</a></li>
            <!-- <li><a href="#tab4">Pending Uploads</a></li> -->
        </ul>
        <!-- Uploads -->
        <div id="tab1" style="display:none">
            <h3>Uploads</h3>
            <div class="well">
                <form action="/api/ManageDatabases" method="post" enctype="multipart/form-data">
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
                            <td><label for="useType">Use as setup? (Reload after database cleared)</label> <input
                                    id="useType" type="checkbox" name="setup" value="true"/></td>
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
                    <!--				<td>File Type</td>
                                    <td>Comments</td>-->
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
                        <td><c:if test="${upload.setup}">*SETUP* </c:if>${upload.fileName}</td>
                        <!--<td>${upload.fileType}</td>
				<td>${upload.comments}</td> -->
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
                                            <option value="${databaseServer.id}">${databaseServer.name}
                                                - ${databaseServer.ip}</option>
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
                        <td><a href="/api/DownloadBackup?id=${database.id}" class="button small"
                               title="Download Backup for ${database.name}"><span class="fa fa-download"
                                                                                  title="Backups"></span> </a></td>
                        <td><c:if test="${database.loaded}"><a href="/api/ManageDatabases?unloadId=${database.id}"
                                                               class="button small"
                                                               title="Unload ${database.name}"><span class="fa fa-eject"
                                                                                                     title="Unload"></span></a></c:if>
                        </td>
                        <td><a href="/api/CopyDatabase?databaseId=${database.id}#tab2" class="button small"
                               title="Copy ${database.name}"><span class="fa fa-copy" title="Copy"></span> </a></td>
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
            <form action="/api/ManageDatabases" method="post" enctype="multipart/form-data">
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
            <h3>Memory/CPU report for servers:</h3>
            <div class="well">
                <c:forEach items="${databaseServers}" var="databaseServer">
                    <a href="/api/MemoryReport?serverIp=${databaseServer.ip}" target="new"
                       class="button report">${databaseServer.name}</a>
                </c:forEach>
            </div>
        </div>
        <!-- END Maintenance -->
        <!-- Pending Uploads -->
        <div id="tab4" style="display:none">
            <!-- Archive List -->
            <table>
                <thead>
                <tr>
                    <td>Date</td>
                    <td>Modified On</td>
                    <td>Modified By</td>
                    <td>File Name</td>
                    <td>Source</td>
                    <td>Status</td>
                    <td>Database</td>
                    <c:forEach items="${params}" var="entry">
                        <td>${entry.key}</td>
                    </c:forEach>
                    <td></td>
                    <td></td>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${pendinguploads}" var="pendingupload">
                    <form action="/api/ManageDatabases#tab4">
                        <input type="hidden" name="pendingUploadId" value="${pendingupload.id}">
                        <tr>
                            <td>${pendingupload.date}</td>
                            <td>${pendingupload.statusChangedDate}</td>
                            <td>${pendingupload.userName}</td>
                            <td>${pendingupload.fileName}</td>
                            <td>${pendingupload.source}</td>
                            <td><span <c:if test="${pendingupload.status == 'Rejected'}">style="background-color: #FF8888; color: #000000"</c:if><c:if test="${pendingupload.status == 'Provisionally Loaded'}">style="background-color: #88FF88; color: #000000"</c:if>>
                                    ${pendingupload.status}</span>
                            </td>
                            <td><!-- todo - last selected automatically -->
                                <c:choose>
                                    <c:when test="${pendingupload.status!='Waiting' && pendingupload.status!='Rejected'}">
                                        ${pendingupload.databaseName}
                                    </c:when>
                                    <c:otherwise>
                                        <select name="databaseId"><c:forEach items="${databases}" var="database">
                                            <option value="${database.id}" <c:if test="${pendingupload.databaseName == database.name}">selected</c:if>>${database.name}</option>
                                        </c:forEach></select>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <c:forEach items="${params}" var="entry">
                                <td>

                                    <c:choose>
                                        <c:when test="${pendingupload.status!='Waiting' && pendingupload.status!='Rejected'}">
                                            ${pendingupload.parameters[fn:toLowerCase(entry.key)]}
                                        </c:when>
                                        <c:otherwise>
                                            <select name="pendingupload-${entry.key}"><c:forEach items="${entry.value}"
                                                                                                 var="listitem">
                                                <option value="${listitem}"<c:if test="${listitem == pendingupload.parameters[fn:toLowerCase(entry.key)]}">selected</c:if>>${listitem}</option>
                                            </c:forEach></select>

                                        </c:otherwise>
                                    </c:choose>


                                </td>
                            </c:forEach>
                            <td>
                                <!-- todo allow another load when rejected-->
                                    <c:if test="${pendingupload.importResult.length() > 0}">
                                        <a href="/api/ImportResults?id=${pendingupload.id}" target="new"
                                           class="button inspect small" data-title="Import Results" title="View Import Results">View Import Results</a>
                                    </c:if>
                                <c:if test="${pendingupload.status == 'Waiting'}">
                                    <input type="submit" name="Load" value="Load" class="button small"/>
                                </c:if>
                                <c:if test="${pendingupload.status == 'Rejected'}">
                                    <input type="submit" name="Load" value="Reload" class="button small"/>
                                </c:if>
                            </td>
                            <td><a href="/api/DownloadFile?pendingUploadId=${pendingupload.id}" class="button small"
                                   title="Download"><span class="fa fa-download" titlmae="Download"></span> </a></td>
                        </tr>
                    </form>
                </c:forEach>
                </tbody>
            </table>
<c:if test="${revertlist.length() > 0}">
            <a href="/api/ManageDatabases?revert=true#tab4"
               onclick="return confirm('Are you sure you want to revert${revertlist}')"
               class="button" title="revert">Revert</a>
</c:if>
        </div>
        <!-- END pending Uploads -->
    </div>
</main>

<!-- tabs -->
<script type="text/javascript">
    $(document).ready(function () {
        $('.tabs').tabs();
    });
</script>

<%@ include file="../includes/admin_footer.jsp" %>
