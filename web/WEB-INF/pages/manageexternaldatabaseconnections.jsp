<%-- Copyright (C) 2021 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Connections"/>
<%@ include file="../includes/admin_header2.jsp" %>
<div class="box">
    <div class="has-text-danger">${error}</div>
    <table class="table is-striped is-fullwidth">
        <thead>
        <tr>
            <th>Name</th>
            <th>Connection String</th>
            <th>User</th>
            <th>Password</th>
            <th>Database</th>
            <th width="30"></th>
            <th width="30"></th>
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
                <td><a href="/api//ManageDatabaseConnections?deleteId=${connection.id}" title="Delete ${user.name}"
                       onclick="return confirm('Are you sure?')" class="button is-small"><span class="fa fa-trash" title="Delete"></span></a></td>
                <td><a href="/api/ManageDatabaseConnections?editId=${connection.id}" title="Edit ${user.name}"
                       class="button is-small"><span class="fa fa-edit" title="Edit"></span></a></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
    <form action="/api/ManageUsers" method="post">
        <table class="table">
            <tr>
                <td>
                    <a href="/api//ManageDatabaseConnections?editId=0" class="button is-small">Add New Connection</a>&nbsp;
                </td>
            </tr>
        </table>
    </form>

    <div>

    </div>
</div>
<%@ include file="../includes/admin_footer.jsp" %>