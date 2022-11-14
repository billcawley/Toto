<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New Connection"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>Edit/New Connection</h3>
            </div>
            <div class="az-table">
                <div>${error}</div>
                <form action="/api//ManageDatabaseConnections" method="post">
                    <input type="hidden" name="editId" value="${id}"/>
                    <table >
                        <tbody>
                        <tr>
                            <td>
                                Name
                            </td>
                            <td>
                                <input name="name" id="name" type="text" value="${name}">
                            </td>
                            <td>Password</td>
                            <td>
                                <input type="password" name="password" id="password"
                                       type="password">
                            </td>
                        </tr>
                        <tr>
                            <td>
                                Connection String
                            </td>
                            <td>
                                <input name="connectionString" id="connectionString" type="text" value="${connectionString}">
                            </td>
                            <td>Database</td>
                            <td><input name="database" id="database" type="text"
                                       value="${database}"></td>
                        </tr>
                        <tr>
                            <td>
                                User
                            </td>
                            <td><input name="user" id="user" type="text"
                                       value="${user}"></td>
                            <td></td>
                            <td></td>
                        </tr>
                    </table>
                    <nav>
                        <div>
                            <button type="submit" name="submit" value="save" class="button is-small">Save
                            </button>
                        </div>
                        <div></div>
                    </nav>
                </form>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>