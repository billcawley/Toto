<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New User"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit/New Connection</h1>
    <div class="is-danger">${error}</div>
    <form action="/api//ManageDatabaseConnections" method="post">
        <input type="hidden" name="editId" value="${id}"/>
        <!-- no business id -->

        <table class="table">
            <tbody>
            <tr>
                <td width="50%">
                    <div class="field">
                        <label class="label">Name</label>
                        <input class="input is-small" name="name" id="name" value="${name}">
                    </div>
                    <div class="field">
                        <label class="label">Connection String</label>
                        <input class="input is-small" name="connectionString" id="connectionString" value="${connectionString}">
                    </div>
                    <div class="field">
                        <label class="label">User</label>
                        <input class="input is-small" name="user" id="user" value="${user}">
                    </div>
                </td>
                <td width="50%">
                    <div class="field">
                        <label class="label">Password</label>
                        <input class="input is-small" name="password" id="password" value="${password}">
                    </div>
                    <div class="field">
                        <label class="label">Database</label>
                        <input class="input is-small" name="database" id="database" value="${database}">
                    </div>
                </td>
            </tbody>
        </table>

        <div class="centeralign">
            <button type="submit" name="submit" value="save" class="button is-small">Save
            </button>
        </div>
    </form>

</div>

<%@ include file="../includes/admin_footer.jsp" %>
