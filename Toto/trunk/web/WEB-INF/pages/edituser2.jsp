<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New User"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit/New User</h1>
    <div class="has-text-danger">${error}</div>
    <form action="/api/ManageUsers" method="post">
        <input type="hidden" name="editId" value="${id}"/>
        <!-- no business id -->

        <table class="table">
            <tbody>
            <tr>
                <td width="33%">
                    <div class="field">
                        <label class="label">Email/logon</label>
                        <input class="input is-small" name="email" id="email" value="${email}">
                    </div>
                    <div class="field">
                        <label class="label">Name</label>
                        <input class="input is-small" name="name" id="name" value="${name}">
                    </div>
                    <div class="field">
                        <label class="label">Status</label>
                        <div class="select is-small">
                            <select name="status" id="status">
                                <option value="ADMINISTRATOR" ${status =="ADMINISTRATOR"?"selected":""}>Administrator
                                </option>
                                <option value="DEVELOPER"  ${status =="DEVELOPER"?"selected":""}>Developer</option>
                                <option value="MASTER"  ${status =="MASTER"?"selected":""}>Master</option>
                                <option value="USER" ${status =="USER"?"selected":""}>User</option>
                            </select>
                        </div>

                    </div>
                </td>
                <td width="33%">
                    <div class="field">
                        <label class="label">End Date</label>
                        <input class="input is-small" name="endDate" id="endDate" value="${endDate}">
                    </div>
                    <div class="field">
                        <label class="label">Database</label>
                        <div class="select is-small">
                            <select name="databaseId">
                                <option value="0">None</option>
                                <c:forEach items="${databases}" var="database">
                                    <option value="${database.id}"<c:if
                                            test="${database.id == user.databaseId}"> selected</c:if>>${database.name}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </div>
                    <div class="field">
                        <label class="label">Report</label>
                        <div class="select is-small">

                            <select name="reportId">
                                <option value="0">None</option>
                                <c:forEach items="${reports}" var="report">
                                    <option value="${report.id}"<c:if
                                            test="${report.id == user.reportId}"> selected</c:if>>${report.reportName}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </div>
                </td>
                <td width="33%">
                    <div class="field">
                        <div>
                            <label class="label">Password</label>
                            <input class="input is-small" type="password" name="password" id="password" type="password">
                        </div>
                    </div>
                    <div class="field">
                        <label class="label">Selections</label>
                        <input class="input is-small" name="selections" id="selections" value="${selections}">
                    </div>
                    <div class="field">
                        <label class="label">Team</label>
                        <input class="input is-small" name="team" id="team" value="${team}">
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
