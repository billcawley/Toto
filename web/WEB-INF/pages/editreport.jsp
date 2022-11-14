<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Users"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>Users</h3>
            </div>
            <div class="az-table">
                <div>${error}</div>
                <form action="/api/ManageReports" method="post" enctype="multipart/form-data">
                    <input type="hidden" name="editId" value="${id}"/>
                    <!-- no business id -->

                    <table>
                        <tbody>
                        <tr>
                            <label class="label">File: ${file}</label>
                        </tr>
                        <tr>
                            <td>
                                <label class="label">Name</label>
                            </td>
                            <td>

                                    <input type="text" name="newReportName" id="newReportName"
                                           value="${newReportName}">
                            </td>
                        </tr>
                        <tr>
                            <td>

                            </td>
                            <td>
                                <div>
                                    <input type="file" name="uploadFile"
                                           id="uploadFile"
                                           multiple>
                                </div>
                                <div><br/></div>

                                <div class="field">
                                    <label >OR: specify an iFrame address</label>
                                    <input type="text" name="iframe" id="iframe" value="${iframe}">
                                </div>
                                <div>
                                    <label class="label">OR <a href="/api/Online?reportid=ADHOC" target="_blank"> design
                                        new report</a></label>
                                </div>

                            </td>
                            <td>
                                    <label class="label">Associated Databases</label>
                                    <c:forEach items="${databasesSelected}" var="databaseSelected">
                                        ${databaseSelected.database.name}&nbsp;<input type="checkbox" name="databaseIdList"
                                        value="${databaseSelected.database.id}" <c:if
                                            test="${databaseSelected.selected}"> checked</c:if>><br/>
                                    </c:forEach>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <label class="label">Optional information</label>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <label class="label">Admin Category</label>

                            </td>
                            <td>
                                <div>
                                    <input type="text" name="category" id="category" value="${category}">
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <label class="label">Explanation</label>
                            </td>
                            <td>
                                <div class="field">
                         <textarea name="explanation" rows="3"
                                   cols="80">${explanation}</textarea>
                                </div>

                            </td>
                        </tr>
                        </tbody>
                    </table>
                    <c:if test="${id > 0}">


                        <div>
                            <label class="label">Appearances in user menus</label>
                        </div>
                        <table>
                            <thead>
                            <tr>
                                <th>Role/Submenu name</th>
                                <th>Importance</th>
                                <th width="30"></th>
                                <th width="30"></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${menuappearances}" var="menuappearance">
                                <tr>
                                    <td>${menuappearance.submenuName}</td>
                                    <td>${menuappearance.importance}</td>
                                    <td>
                                        <a href="/api/ManageReports?menuAppearanceDeleteId=${menuappearance.id}&editId=${id}"
                                           title="Delete ${user.name}"
                                           onclick="return confirm('Are you sure?')"><span
                                                class="fa fa-trash" title="Delete"></span></a></td>
                                    <td><a href="/api/ManageReports?menuAppearanceId=${menuappearance.id}&editId=${id}"
                                           title="Edit ${user.name}"
                                           class="button is-small">Edit</a>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>


                        <table class="table">
                            <tr>
                                <td>
                                    <a href="/api/ManageReports?menuAppearanceId=0&editId=${id}"
                                       class="button">Add New
                                        Menu Appearance</a>&nbsp;
                                </td>
                            </tr>
                        </table>
                        <div>
                            <label class="label">Sheets and ranges filled from external data</label>
                        </div>
                        <table>
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
                                           onclick="return confirm('Are you sure?')" class="button is-small">Delete</a></td>
                                    <td>
                                        <a href="/api/ManageReports?externaldatarequestId=${externaldatarequest.id}&editId=${id}"
                                           title="Edit ${user.name}"
                                           class="button is-small">Edit</a>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>


                        <table class="table">
                            <tr>
                                <td>
                                    <a href="/api/ManageReports?externaldatarequestId=0&editId=${id}">Add
                                        New External Data Request</a>&nbsp;
                                </td>
                            </tr>
                        </table>
                    </c:if>
<nav>
    <div>
        <button type="submit" name="submit" value="save">Save</button>
        <button type="submit" name="submit" value="cancel">Cancel</button>
    </div>
</nav>
                </form>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>