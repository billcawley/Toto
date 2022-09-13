<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Report Schedules"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>Manage Report Schedules</h3>
            </div>
            <div class="az-section-body">
                <form action="/api/ManageReportSchedules" method="post">

                    <div>${error}</div>
                    <div class="az-table">
                        <table>
                            <thead>
                            <tr>
                                <th>Period</th>
                                <th>Recipients</th>
                                <th>Next Due</th>
                                <th>Database</th>
                                <th>Report</th>
                                <th>Type</th>
                                <th>Parameters</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${reportSchedules}" var="reportSchedule">
                                <tr>
                                    <td>
                                            <select name="period${reportSchedule.id}">
                                                <option<c:if
                                                        test="${reportSchedule.period == 'HOURLY'}"> selected</c:if>>
                                                    HOURLY
                                                </option>
                                                <option<c:if
                                                        test="${reportSchedule.period == 'DAILY'}"> selected</c:if>>
                                                    DAILY
                                                </option>
                                                <option<c:if
                                                        test="${reportSchedule.period == 'WEEKLY'}"> selected</c:if>>
                                                    WEEKLY
                                                </option>
                                                <option<c:if
                                                        test="${reportSchedule.period == 'MONTHLY'}"> selected</c:if>>
                                                    MONTHLY
                                                </option>
                                            </select>
                                    </td>
                                    <td><input type="text" name="recipients${reportSchedule.id}"
                                               value="${reportSchedule.recipients}"/></td>
                                    <td><input type="text" class="input is-small" name="nextDue${reportSchedule.id}"
                                               value="${reportSchedule.nextDueFormatted}"/></td>
                                    <td>
                                            <select name="databaseId${reportSchedule.id}">
                                                <c:forEach items="${databases}" var="database">
                                                    <option value="${database.id}"<c:if
                                                            test="${database.id == reportSchedule.databaseId}"> selected</c:if>>${database.name}</option>
                                                </c:forEach>
                                            </select>
                                    </td>
                                    <td>
                                            <select name="reportId${reportSchedule.id}">
                                                <c:forEach items="${reports}" var="report">
                                                    <option value="${report.id}"<c:if
                                                            test="${report.id == reportSchedule.reportId}"> selected</c:if>>${report.reportName}</option>
                                                </c:forEach>
                                            </select>
                                    </td>
                                    <td>
                                            <select name="type${reportSchedule.id}">
                                                <option<c:if test="${reportSchedule.type == 'PDF'}"> selected</c:if>>
                                                    PDF
                                                </option>
                                                <option<c:if test="${reportSchedule.type == 'XLS'}"> selected</c:if>>
                                                    XLS
                                                </option>
                                                <option<c:if
                                                        test="${reportSchedule.type == 'Execute'}"> selected</c:if>>
                                                    Execute
                                                </option>
                                            </select>
                                    </td>
                                    <td><input type="text" name="parameters${reportSchedule.id}"
                                                  rows="3">${reportSchedule.parameters}</input></td>
                                    <td><a href="/api/ManageReportSchedules?deleteId=${reportSchedule.id}"
                                           class="button is-small"><span
                                            class="fa fa-trash"></span></a></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                        <nav>
                            <div>
                            <button onclick="this.form.submit()">Save Changes</button>
                            <button onclick="location.href='/api/ManageReportSchedules?new=true'">Add new
                                schedule</button>&nbsp;
                            </div><div>
                                            <input class="file-input is-small" type="file" name="uploadFile"
                                                   id="uploadFile"
                                                   multiple>

                            <button onclick="this.form.submit()">Upload Report Schedule</button></div>
                        </nav>
                    </div>
                </form>

            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
