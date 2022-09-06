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
            <div class="az-section-body">
                <!--                 <div class="az-alert az-alert-warning">${error}</div> -->
                <div>${error}</div>
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>User Email</th>
                            <th>Name</th>
                            <th>End Date</th>
                            <!--<td>Business Id</td>-->

                            <th>Status</th>
<!--                            <th>Start Menu</th>
                            <th>Database</th>
                            <th>Selections</th>-->
                            <th>Recent Activity</th>
                            <th></th>
                            <th></th>
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
<!--                                <td>${user.reportName}</td>
                                <td>${user.databaseName}</td>
                                <td>${user.selections}</td>-->
                                <td><a href="/api/ManageUsers?recentId=${user.id}&newdesign=true">${user.recentActivity}</a></td>
                                <td><a href="/api/ManageUsers?deleteId=${user.id}&newdesign=true" title="Delete ${user.name}"
                                       onclick="return confirm('Are you sure?')">Delete</a></td>
                                <td><a href="/api/ManageUsers?editId=${user.id}&newdesign=true" title="Edit ${user.name}"
                                       >Edit</a></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>

                    <form action="/api/ManageUsers" method="post" enctype="multipart/form-data">
                        <input type="hidden" name="newdesign" value="true"/>
                        <nav>
                            <div>
                            <button onclick="location.href='/api/ManageUsers?downloadRecentActivity=true'">Download Recent Activity Summary</button>
                                    <button onclick="location.href='/api/ManageUsers?editId=0&newdesign=true'">Add New User</button>
                                    <button onclick="location.href='/api/CreateExcelForDownload?action=DOWNLOADUSERS'">Download Users as
                                        Excel</button> &nbsp;
                            </div>
                            <div>
                                            <input type="file" name="uploadFile"
                                                   id="uploadFile"
                                                   multiple>

                                    <button onclick="this.form.submit()">Upload User List</button> &nbsp;
                            </div>
                        </nav>
                    </form>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
