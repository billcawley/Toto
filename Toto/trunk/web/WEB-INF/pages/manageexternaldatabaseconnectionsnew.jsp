<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database COnnections"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>Manage Database Connections</h3>
            </div>
            <div class="az-section-body">
                <!--                 <div class="az-alert az-alert-warning">${error}</div> -->
                <div>${error}</div>
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Name</th>
                            <th>Connection String</th>
                            <th>User</th>
                            <th>Password</th>
                            <th>Database</th>
                            <th></th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${connections}" var="connection">
                            <tr>
                                <td>${connection.name}</td>
                                <td>${connection.connectionString}</td>
                                <td>${connection.user}</td>
                                <td>${connection.password}</td>
                                <td>${connection.database}</td>

                                <td><a href="/api/ManageDatabaseConnections?deleteId=${connection.id}" title="Delete" onclick="return confirm('Are you sure?')">Delete</a></td>
                                <td><a href="/api/ManageDatabaseConnections?editId=${connection.id}" title="Edit">Edit</a></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                    <nav>
                        <div>
                    <button onclick="location.href='/api//ManageDatabaseConnections?editId=0'" type="button">Add New Connection</button>
                        </div>
                    </nav>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
