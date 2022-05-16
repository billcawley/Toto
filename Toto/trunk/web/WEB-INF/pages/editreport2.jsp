<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="title" scope="request" value="Edit Report"/>
<%@ include file="../includes/admin_header2.jsp" %>


<div class="box">
    <h1 class="title">Edit Report Details</h1>
    <div class="is-danger">${error}</div>
    <form action="/api/ManageReports" method="post" enctype="multipart/form-data">
        <input type="hidden" name="editId" value="${id}"/>
        <!-- no business id -->

        <table class="table">
            <tbody>
            <tr>
                <td width="50%">
                    <div>
                        <div class="field">
                            <label class="label">File</label>
                            <label class="label">${file}</label>
                        </div>

                        <div class="field">
                            <label class="label">Name</label>
                            <input class="input is-small" name="newReportName" id="newReportName"
                                   value="${newReportName}">
                        </div>
                        <div>
                            <br/>
                        </div>

                        </div>
                        <div class="file has-name is-small" id="file-js-example">
                            <label class="file-label">
                                <input class="file-input is-small" type="file" name="uploadFile"
                                       id="uploadFile">
                                <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                Select File
                                              </span>
                                            </span>
                                <span class="file-name is-small">
                                            </span>
                            </label>
                        </div>
                        <div><br/></div>

                        <div class="field">
                            <label class="label">OR: specify an iFrame address</label>
                            <input class="input is-small" name="iframe" id="iframe" value="${iframe}">
                        </div>
                        <div class="field">
                            <label class="label">OR <a href="/api/Online?reportid=ADHOC" target="_blank"> design new report</a></label>
                        </div>
                        <div class="field">
                            <label class="label">Admin Category</label>
                            <input class="input is-small" name="category" id="category" value="${category}">
                        </div>
                        <div class="field">
                            <label class="label">Explanation</label>
                            <textarea class="textarea is-small" name="explanation" rows="3"
                                      cols="80">${explanation}</textarea>
                        </div>

                    </div>
                </td>
                <td width="50%">
                    <div class="field">
                        <label class="label">Associated Databases</label>
                        <c:forEach items="${databasesSelected}" var="databaseSelected">
                            ${databaseSelected.database.name}&nbsp;<input type="checkbox" name="databaseIdList"
                            value="${databaseSelected.database.id}" <c:if
                                test="${databaseSelected.selected}"> checked</c:if>><br/>
                        </c:forEach>
                    </div>
                </td>
            </tbody>
        </table>
        <c:if test="${id > 0}">


            <div class="field">
                <label class="label">Appearances in user menus</label>
            </div>
            <table class="table is-striped is-fullwidth">
                <thead>
                <tr>
                    <th>Role/Submenu name</th>
                    <th>Importance</th>
                    <th>Shown name on menu</th>
                    <th width="30"></th>
                    <th width="30"></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${menuappearances}" var="menuappearance">
                    <tr>
                        <td>${menuappearance.submenuName}</td>
                        <td>${menuappearance.importance}</td>
                        <td>${menuappearance.showname}</td>
                        <td><a href="/api/ManageReports?menuAppearanceDeleteId=${menuappearance.id}&editId=${id}"
                               title="Delete ${user.name}"
                               onclick="return confirm('Are you sure?')" class="button is-small"><span
                                class="fa fa-trash" title="Delete"></span></a></td>
                        <td><a href="/api/ManageReports?menuAppearanceId=${menuappearance.id}&editId=${id}"
                               title="Edit ${user.name}"
                               class="button is-small"><span class="fa fa-edit" title="Edit"></span></a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>


            <table class="table">
                <tr>
                    <td>
                        <a href="/api/ManageReports?menuAppearanceId=0&editId=${id}" class="button is-small">Add New
                            Menu Appearance</a>&nbsp;
                    </td>
                </tr>
            </table>
            <div class="field">
                <label class="label">Sheets and ranges filled from external data</label>
            </div>
            <table class="table is-striped is-fullwidth">
                <thead>
                <tr>
                    <th>Sheet/Range name</th>
                    <th>Connector name</th>
                    <th>Read SQL</th>
                    <th>Save keyfield</th>
                    <th>Save filename</th>
                    <th>Save insert key value</th>
                    <th>Allow Delete</th>
                    <th width="30"></th>
                    <th width="30"></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${externaldatarequests}" var="externaldatarequest">
                    <tr>
                        <td>${externaldatarequest.sheetRangeName}</td>
                        <td>${externaldatarequest.connectorName}</td>
                        <td>${externaldatarequest.readSQL}</td>
                        <td>${externaldatarequest.saveKeyfield}</td>
                        <td>${externaldatarequest.saveFilename}</td>
                        <td>${externaldatarequest.saveInsertKeyValue}</td>
                        <td>${externaldatarequest.saveAllowDelete}</td>
                        <td>
                            <a href="/api/ManageReports?externaldatarequestDeleteId=${externaldatarequest.id}&editId=${id}"
                               title="Delete ${user.name}"
                               onclick="return confirm('Are you sure?')" class="button is-small"><span
                                    class="fa fa-trash" title="Delete"></span></a></td>
                        <td><a href="/api/ManageReports?externaldatarequestId=${externaldatarequest.id}&editId=${id}"
                               title="Edit ${user.name}"
                               class="button is-small"><span class="fa fa-edit" title="Edit"></span></a></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>


            <table class="table">
                <tr>
                    <td>
                        <a href="/api/ManageReports?externaldatarequestId=0&editId=${id}" class="button is-small">Add
                            New External Data Request</a>&nbsp;
                    </td>
                </tr>
            </table>
        </c:if>


        <div>
            <button type="submit" name="submit" value="save" class="button is-small">Save</button>
            <button type="submit" name="submit" value="cancel" class="button is-small">Cancel</button>
        </div>
    </form>

</div>
<script>
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

</script>

<%@ include file="../includes/admin_footer.jsp" %>
