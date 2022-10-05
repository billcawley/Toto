<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Templates"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div id="working" style="display:none"><h3>Working...</h3></div>
            <div class="az-section-heading">
                <h3>Import Templates</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <div>
                        <form onsubmit="document.getElementById('working').style.display = 'block';"
                              action="/api/ManageDatabases" method="post"
                              enctype="multipart/form-data">
                            <input type="hidden" name="newdesign" value="pendinguploads"/>
                            <nav>
                                Upload Template&nbsp;&nbsp;<input class="file-input is-small" type="file"
                                                                  name="uploadFile">
                                Template Comment &nbsp;
                                <input type="text"
                                       size="40"
                                       name="userComment" pattern="(.|\s)*\S(.|\s)*"> &nbsp;
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
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Uploader</th>
                            <th>Template Name</th>
                            <th>Notes</th>
                            <th>Date Uploaded</th>
                            <th></th>
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
                                    <a href="/api/ManageDatabases?deleteTemplateId=${template.id}#4"
                                       onclick="return confirm('Are you sure you want to delete ${template.templateName}?')"
                                       class="button is-small" title="Delete ${template.templateName}"><span
                                            class="fa fa-trash"
                                            title="Delete"></span>
                                    </a>
                                    <a href="/api/DownloadImportTemplate?importTemplateId=${template.id}#4"
                                       class="button is-small" title="Download"><span class="fa fa-download"
                                                                                      title="Download"></span> </a>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>
            <div class="az-section-heading">
                <h3>Assign Templates to Databases</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <form action="/api/ManageDatabases?templateassign=1&newdesign=importtemplates" method="post">
                        <table>
                            <thead>
                            <tr>
                                <th>Database</th>
                                <th>Template</th>
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
                        <nav><button type="submit">Save Changes</button></nav>
                    </form>
                </div>
            </div>
            <div class="az-section-heading">
                <h3>Test pre-processor. Select a pre-processor and a zip of data to test</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <form action="/api/ManageDatabases?newdesign=importtemplates" method="post" enctype="multipart/form-data"
                          onsubmit="document.getElementById('working').style.display = 'block';">
                        <table class="table">
                            <tbody>
                            <tr>
                                <td>
                                            <input id="preprocessorTest"
                                                   type="file"
                                                   name="preprocessorTest"
                                                   multiple>
                                </td>
                                <td><nav><button type="submit">Upload</button></nav>
                                    </td>
                            </tr>
                            </tbody>
                        </table>
                    </form>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
