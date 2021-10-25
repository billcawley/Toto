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
<%@ include file="../includes/admin_header2.jsp" %>

<style>
    #tabs-with-content .tabs:not(:last-child) {
        margin-bottom: 0;
    }

    #tabs-with-content .tab-content {
        padding: 1rem;
        display: none;
    }

    #tabs-with-content .tab-content.is-active {
        display: block;
    }
</style>
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
    <div class="tabs">
        <ul>
            <li><a>Direct Uploads</a></li>
            <li><a>Pending Uploads</a></li>
            <li><a>Import Templates</a></li>
        </ul>
    </div>
    <c:if test="${!empty error}">
        <div style="width:100%;font:12px monospace;overflow:auto;padding:10px;max-height: 200px; background-color: #FFEEEE">${error}</div>
    </c:if>
    <div id="working" style="display:none;padding:10px"><h3 class="title is-3">Working... <span
            class="fa fa-spin fa-cog"></span></h3></div>
    <div>
        <section class="tab-content">
            <div class="box">
                <div>
                    <form action="/api/ManageDatabases" method="post" enctype="multipart/form-data"
                          onsubmit="showWorking();">
                        <table class="table">
                            <tr>
                                <td>
                                    <div class="file has-name is-small" id="file-js-example">
                                        <label class="file-label">
                                            <input class="file-input is-small" type="file" name="uploadFile"
                                                   id="uploadFile"
                                                   multiple>
                                            <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                Select Files
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
                                            <label class="label">User&nbsp;Comment</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="field">
                                                <input class="input is-small" type="text"
                                                       id="userComment" size="40"
                                                       name="userComment"></div>
                                        </div>
                                    </div>
                                </td>
                                <td>
                                    <div class="field is-horizontal">
                                        <div class="field-label is-normal">
                                            <label class="label">Database</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="select is-small">
                                                <select name="database" id="uploadDatabase">
                                                    <option value="">None</option>
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
                                <c:if test="${fn:contains(error,'values ')}">
                                    <td><a href="/api/Showdata?chosen=changed" class="button is-small"
                                           title="Download"><span
                                            class="fa fa-search" title="View changed data"></span> </a></td>
                                </c:if>
                            </tr>
                        </table>
                    </form>
                </div>
            </div>
            <!-- Archive List -->
            <table class="table is-striped is-fullwidth">
                <thead>
                <tr>
                    <th><a href="/api/ManageDatabases?sort=${datesort}">Date</a></th>
                    <th><a href="/api/ManageDatabases?sort=${businessnamesort}">Business Name</a></th>
                    <th><a href="/api/ManageDatabases?sort=${dbsort}">Database Name</a></th>
                    <th><a href="/api/ManageDatabases?sort=${usernamesort}">User Name</a></th>
                    <th>
                        <form method="post"> File Name <input size="20" name="fileSearch"></form>
                    </th>
                    <th>Count</th>
                    <th width="15%">Comment</th>
                    <th colspan="4"><a href="/api/ManageDatabases?withautos=true" class="button is-small"
                                       title="Show Automated Uploads">Show Automated Uploads</a></th>
                </tr>
                </thead>
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
                               class="button is-small" data-title="Edit" title="Comment" id="comment${upload.id}">
                            <c:if test="${upload.userComment.length() > 0}">
                                Edit
                            </c:if>
                            <c:if test="${upload.userComment.length() == 0}">
                                Comment
                            </c:if>

                        </a></td>
                        <td><c:if test="${upload.comments.length() > 0}">
                            <a href="/api/ImportResults?urid=${upload.id}" target="new"
                               class="button  is-small" data-title="Import Results"
                               title="View Import Results">Results</a>
                        </c:if></td>

                        <td><c:if test="${upload.downloadable}"><a href="/api/DownloadFile?uploadRecordId=${upload.id}"
                                                                   class="button is-small" title="Download"><span
                                class="fa fa-download" title="Download"></span> </a></c:if></td>
                        <td><a href="/api/ManageDatabases?deleteUploadRecordId=${upload.id}" class="button is-small"
                               title="Delete"><span class="fa fa-trash" title="Delete"></span> </a></td>
                    </tr>
                </c:forEach>
            </table>
        </section>
        <section class="tab-content">
            <div class="box">
                <div>
                    <form action="/api/ManageDatabases#1" method="post" enctype="multipart/form-data">
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
                        <form method="post" action="/api/ManageDatabases#1"> File Name <input size="20"
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
                <a href="/api/ManageDatabases#1" class="button is-small">Normal View</a>&nbsp;
                <a href="/api/ManageDatabases?allteams=true#1" class="button is-small">Show for all teams</a>&nbsp;
                <a href="/api/ManageDatabases?uploadreports=true#1" class="button is-small">Show completed
                    uploads</a>
            </div>

        </section>
        <section class="tab-content">
            <div class="box">
                <div>
                    <form action="/api/ManageDatabases#2" method="post" enctype="multipart/form-data">
                        <input type="hidden" name="template" value="true"/>
                        <table class="table">
                            <tbody>
                            <tr>
                                <td>
                                    <div class="file has-name is-small" id="file-js-example3">
                                        <label class="file-label is-small">
                                            <input class="file-input is-small" type="file" name="uploadFile">
                                            <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                Upload Template
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
                                            <label class="label">Template&nbsp;Comment</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="field">
                                                <input class="input is-small" type="text"
                                                       size="40"
                                                       name="userComment" pattern="(.|\s)*\S(.|\s)*"></div>
                                        </div>
                                    </div>
                                </td>
                                <td>
                                    <div class="field is-horizontal">
                                        <div class="field-label is-normal">
                                            <label class="label">Database</label>
                                        </div>
                                        <div class="field-body">
                                            <div class="select is-small">
                                                <select name="database">
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
                            <a href="/api/ManageDatabases?deleteTemplateId=${template.id}#2"
                               onclick="return confirm('Are you sure you want to delete ${template.templateName}?')"
                               class="button is-small" title="Delete ${template.templateName}"><span
                                    class="fa fa-trash"
                                    title="Delete"></span>
                            </a>
                            <a href="/api/DownloadImportTemplate?importTemplateId=${template.id}#2"
                               class="button is-small" title="Download"><span class="fa fa-download"
                                                                              title="Download"></span> </a>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
            <div class="box">
                <h5 class="title is-5">Assign Templates to Databases</h5>
                <form action="/api/ManageDatabases?templateassign=1#2" method="post">
                    <table class="table">
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

                                    <div class="field-body">
                                        <div class="select is-small">
                                            <select name="templateName-${database.id}">
                                                <option value="">None</option>
                                                <c:forEach items="${importTemplates}" var="template">
                                                    <option value="${template.templateName}" <c:if
                                                            test="${template.templateName == database.importTemplate}"> selected </c:if>>${template.templateName}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                    </div>

                                </td>
                            </tr>
                        </c:forEach>

                        </tbody>
                    </table>
                    <input type="submit" name="Save Changes" value="Save Changes" class="button is-small"/>
                </form>
            </div>
            <div class="box">
                <h5 class="title is-5">Test pre-processor. Select a pre-processor and a zip of data to test</h5>
                <form action="/api/ManageDatabases#4" method="post" enctype="multipart/form-data"
                      onsubmit="showWorking();">
                    <table class="table">
                        <tbody>
                        <tr>
                            <td>
                                <div class="file has-name is-small" id="file-js-example4">
                                    <label class="file-label is-small">
                                        <input class="file-input is-small" id="preprocessorTest"
                                               type="file"
                                               name="preprocessorTest"
                                               multiple>
                                        <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                Select Files
                                              </span>
                                            </span>
                                        <span class="file-name is-small">
                                            </span>

                                    </label>
                                </div>
                            </td>
                            <td>
                                &nbsp;&nbsp;<input
                                    type="submit" name="Upload" value="Upload" class="button is-small"/></td>
                        </tr>
                        </tbody>
                    </table>
                </form>
            </div>
        </section>
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

    let tabsWithContent = (function () {
        let tabs = document.querySelectorAll('.tabs li');
        let tabsContent = document.querySelectorAll('.tab-content');

        let deactvateAllTabs = function () {
            tabs.forEach(function (tab) {
                tab.classList.remove('is-active');
            });
        };

        let hideTabsContent = function () {
            tabsContent.forEach(function (tabContent) {
                tabContent.classList.remove('is-active');
            });
        };

        let activateTabsContent = function (tab) {
            tabsContent[getIndex(tab)].classList.add('is-active');
        };

        let getIndex = function (el) {
            return [...el.parentElement.children].indexOf(el);
        };
        tabs.forEach(function (tab) {
            tab.addEventListener('click', function () {
                deactvateAllTabs();
                hideTabsContent();
                tab.classList.add('is-active');
                activateTabsContent(tab);
            });
        })

        var tabHash = window.location.hash.substr(1);
        if (tabHash) {
            tabs[tabHash].click();
        } else {
            tabs[0].click();
        }

    })();

    const fileInput = document.querySelector('#file-js-example input[type=file]');
    fileInput.onchange = () => {
        if (fileInput.files.length > 0) {
            const fileName = document.querySelector('#file-js-example .file-name');
            if (fileInput.files.length > 1) {
                fileName.textContent = "Multiple files selected";
            } else {
                fileName.textContent = fileInput.files[0].name;
            }
        }
    }

    const fileInput1 = document.querySelector('#file-js-example1 input[type=file]');
    fileInput1.onchange = () => {
        if (fileInput1.files.length > 0) {
            const fileName1 = document.querySelector('#file-js-example1 .file-name');
            if (fileInput1.files.length > 1) {
                fileName1.textContent = "Multiple files selected";
            } else {
                fileName1.textContent = fileInput1.files[0].name;
            }
        }
    }

    const fileinput2 = document.querySelector('#file-js-example2 input[type=file]');
    fileinput2.onchange = () => {
        if (fileinput2.files.length > 0) {
            const fileName2 = document.querySelector('#file-js-example2 .file-name');
            if (fileinput2.files.length > 1) {
                fileName2.textContent = "Multiple files selected";
            } else {
                fileName2.textContent = fileinput2.files[0].name;
            }
        }
    }

    const fileinput3 = document.querySelector('#file-js-example3 input[type=file]');
    fileinput3.onchange = () => {
        if (fileinput3.files.length > 0) {
            const fileName3 = document.querySelector('#file-js-example3 .file-name');
            if (fileinput3.files.length > 1) {
                fileName3.textContent = "Multiple files selected";
            } else {
                fileName3.textContent = fileinput3.files[0].name;
            }
        }
    }

    const fileinput4 = document.querySelector('#file-js-example4 input[type=file]');
    fileinput4.onchange = () => {
        if (fileinput4.files.length > 0) {
            const fileName4 = document.querySelector('#file-js-example4 .file-name');
            if (fileinput4.files.length > 1) {
                fileName4.textContent = "Multiple files selected";
            } else {
                fileName4.textContent = fileinput4.files[0].name;
            }
        }
    }

</script>
</body>
</html>