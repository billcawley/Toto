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
                <form action="/api/ManageUsers" method="post">
                    <input type="hidden" name="editId" value="${id}"/>
                    <input type="hidden" name="newdesign" value="true"/>
                    <!-- no business id -->

                    <table>
                        <tbody>
                        <tr>
                            <td>
                                Email/logon
                            </td>
                            <td>
                                <input name="email" id="email" type="text" value="${email}">
                            </td>
                            <td>
                                End Date
                            </td>
                            <td>
                                <input name="endDate" id="endDate" type="text"  value="${endDate}">
                            </td>

                            <td>Password</td>
                            <td>
                                <input type="password" name="password" id="password"
                                       type="password">
                            </td>
                        </tr>
                        <tr>
                            <td>
                                Name
                            </td>
                            <td>
                                <input name="name" id="name" type="text" value="${name}">
                            </td>
                            <td>Database</td>
                            <td>
                                <select name="databaseId">
                                    <option value="0">None</option>
                                    <c:forEach items="${databases}" var="database">
                                        <option value="${database.id}"<c:if
                                                test="${database.id == user.databaseId}"> selected</c:if>>${database.name}</option>
                                    </c:forEach>
                                </select>
                            </td>
                            <td>Selections</td>
                            <td><input name="selections" id="selections" type="text"
                                       value="${selections}"></td>
                        </tr>
                        <tr>
                            <td>
                                Status
                            </td>
                            <td>
                                <select name="status" id="status">
                                    <option value="ADMINISTRATOR" ${status =="ADMINISTRATOR"?"selected":""}>
                                        Administrator
                                    </option>
                                    <option value="DEVELOPER"  ${status =="DEVELOPER"?"selected":""}>Developer
                                    </option>
                                    <option value="MASTER"  ${status =="MASTER"?"selected":""}>Master</option>
                                    <option value="USER" ${status =="USER"?"selected":""}>User</option>
                                </select>
                            </td>
                            <td>Report</td>
                            <td><select name="reportId">
                                <option value="0">None</option>
                                <c:forEach items="${reports}" var="report">
                                    <option value="${report.id}"<c:if
                                            test="${report.id == user.reportId}"> selected</c:if>>${report.reportName}</option>
                                </c:forEach>
                            </select></td>
                            <td>Role</td>
                            <td><input name="team" id="team" type="text" value="${team}"></td>
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
