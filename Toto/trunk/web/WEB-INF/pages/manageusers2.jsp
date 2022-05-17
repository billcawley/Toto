<%-- Copyright (C) 2021 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage users"/>
<%@ include file="../includes/admin_header2.jsp" %>
<div class="box">
    <a href="/api/ManageUsers?downloadRecentActivity=true">Download Recent Activity Summary</a>
    <div class="has-text-danger">${error}</div>
    <table class="table is-striped is-fullwidth">
        <thead>
        <tr>
            <th>User Email</th>
            <th>Name</th>
            <th>End Date</th>
            <!--<td>Business Id</td>-->

            <th>Status</th>
            <th>Start Menu</th>
            <th>Database</th>
            <th>Selections</th>
            <th>Recent Activity</th>
            <th width="30"></th>
            <th width="30"></th>
            <!-- password and salt pointless here -->
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${users}" var="user">
            <tr>
                <td>${user.email}</td>
                <td>${user.name}</td>

                <td>${user.endDate}</td>
                <!--<td>${user.businessId}</td>-->

                <td>${user.status}</td>
                <td>${user.reportName}</td>
                <td>${user.databaseName}</td>
                <td>${user.selections}</td>
                <td><a href="/api/ManageUsers?recentId=${user.id}">${user.recentActivity}</a></td>
                <td><a href="/api/ManageUsers?deleteId=${user.id}" title="Delete ${user.name}"
                       onclick="return confirm('Are you sure?')" class="button is-small"><span class="fa fa-trash" title="Delete"></span></a></td>
                <td><a href="/api/ManageUsers?editId=${user.id}" title="Edit ${user.name}"
                       class="button is-small"><span class="fa fa-edit" title="Edit"></span></a></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    <form action="/api/ManageUsers" method="post" enctype="multipart/form-data">
        <table class="table">
            <tr>
                <td>
                    <a href="/api/ManageUsers?editId=0" class="button is-small">Add New User</a>&nbsp;
                </td>
                <td>
                    <a href="/api/CreateExcelForDownload?action=DOWNLOADUSERS" class="button is-small">Download Users as
                        Excel</a> &nbsp;
                </td>
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
                                                Select File
                                              </span>
                                            </span>
                            <span class="file-name is-small">
                                            </span>
                        </label>
                    </div>

                </td>
                <td>

                    <input type="submit" name="Upload" value="Upload User List" class="button  is-small"/>&nbsp;
                </td>
            </tr>
        </table>
    </form>

    <div>

    </div>
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