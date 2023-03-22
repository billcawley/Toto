<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Pending Uploads"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div id="working" style="display:none"><h3>Working...</h3></div>
            <div class="az-section-heading">
                <h3>Pending Uploads</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <div>
                        <form onsubmit="document.getElementById('working').style.display = 'block';"
                              action="/api/ManageDatabases" method="post"
                              enctype="multipart/form-data">
                            <input type="hidden" name="newdesign" value="pendinguploads"/>
                            <nav>
                                Manually add file for validation&nbsp;&nbsp;<input class="file-input is-small" type="file"
                                                                     name="uploadFile">
                                Team &nbsp;
                                <input type="text"
                                       size="40"
                                       name="team">
                                &nbsp;
                                Database
                                <select name="database">
                                    <c:forEach items="${databases}" var="database">
                                        <option value="${database.name}" <c:if
                                                test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                    </c:forEach>
                                </select>
                                &nbsp;
                                <button type="submit">Upload</button>
                            </nav>
                        </form>
                    </div>
                </div>
                <div  class="az-table">
                <table>
                    <thead>
                    <tr>
                        <th>Created</th>
                        <th>By user</th>
                        <th>Processed</th>
                        <th>By user</th>
                        <th>
<!--                            <form method="post" action="/api/ManageDatabases#3"> File Name <input size="20"
                                                                                                  name="pendingUploadSearch">
                            </form>-->
                            File Name
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
                                   class="button is-small" title="Download">Download</a></td>
                            <td>




                                <c:if test="${!pendingupload.loaded}">
                                    <button onclick="window.location.assign('/api/PendingUpload?id=${pendingupload.id}&reject=true&opcode=template')">
                                    Reject
                                </button></c:if>
                            </td>
                            <td>
                                <c:if test="${!pendingupload.loaded}">

                                    <button onclick="document.getElementById('working').style.display = 'block';window.location.assign('/api/PendingUpload?id=${pendingupload.id}')">
                                    Validate and Load
                                    </button></c:if>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
                </div>
                <div class="az-table">
                    <nav>
                        <button onclick="window.location.assign('/api/ManageDatabases?newdesign=pendinguploads')"
                                type="button">Normal View</button>
                        <button onclick="window.location.assign('/api/ManageDatabases?newdesign=pendinguploads&allteams=true')"
                                type="button">Show for all teams</button>
                        <button onclick="window.location.assign('/api/ManageDatabases?newdesign=pendinguploads&uploadreports=true')"
                                type="button">Show completed uploads</button>
                    </nav>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
