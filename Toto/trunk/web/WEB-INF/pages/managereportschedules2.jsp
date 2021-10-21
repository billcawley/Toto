<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Report Schedules"/>
<%@ include file="../includes/admin_header2.jsp" %>
<div class="box">
    <form action="/api/ManageReportSchedules" method="post">
        <div class="is-danger">${error}</div>
        <table class="table is-striped is-fullwidth">
            <thead>
            <tr>
                <!-- <td>Report id</td>
                <td>Business Id</td>-->
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
                        <div class="select is-small">
                            <select name="period${reportSchedule.id}">
                                <option<c:if test="${reportSchedule.period == 'HOURLY'}"> selected</c:if>>HOURLY
                                </option>
                                <option<c:if test="${reportSchedule.period == 'DAILY'}"> selected</c:if>>DAILY</option>
                                <option<c:if test="${reportSchedule.period == 'WEEKLY'}"> selected</c:if>>WEEKLY
                                </option>
                                <option<c:if test="${reportSchedule.period == 'MONTHLY'}"> selected</c:if>>MONTHLY
                                </option>
                            </select>
                        </div>
                    </td>
                    <td><input class="input is-small" name="recipients${reportSchedule.id}"
                               value="${reportSchedule.recipients}"/></td>
                    <td><input class="input is-small" name="nextDue${reportSchedule.id}"
                               value="${reportSchedule.nextDueFormatted}"/></td>
                    <td>
                        <div class="select is-small">
                            <select name="databaseId${reportSchedule.id}">
                                <c:forEach items="${databases}" var="database">
                                    <option value="${database.id}"<c:if
                                            test="${database.id == reportSchedule.databaseId}"> selected</c:if>>${database.name}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </td>
                    <td>
                        <div class="select is-small">
                            <select name="reportId${reportSchedule.id}">
                                <c:forEach items="${reports}" var="report">
                                    <option value="${report.id}"<c:if
                                            test="${report.id == reportSchedule.reportId}"> selected</c:if>>${report.reportName}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </td>
                    <td>
                        <div class="select is-small">
                            <select name="type${reportSchedule.id}">
                                <option<c:if test="${reportSchedule.type == 'PDF'}"> selected</c:if>>PDF</option>
                                <option<c:if test="${reportSchedule.type == 'XLS'}"> selected</c:if>>XLS</option>
                                <option<c:if test="${reportSchedule.type == 'Execute'}"> selected</c:if>>Execute
                                </option>
                            </select>
                        </div>
                    </td>
                    <td><textarea class="textarea is-small" name="parameters${reportSchedule.id}"
                                  rows="3">${reportSchedule.parameters}</textarea></td>
                    <td><a href="/api/ManageReportSchedules?deleteId=${reportSchedule.id}" class="button is-small"><span
                            class="fa fa-trash"></span></a></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <table class="table">
            <tr>
                <td><input type="submit" name="submit" value="Save Changes" class="button is-small"/>
                </td>
                <td><a href="/api/ManageReportSchedules?new=true" class="button is-small">Add new schedule</a>&nbsp;
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
                <td><input type="submit" name="Upload" value="Upload Report Schedule" class="button is-small"/>
                </td>
            </tr>
        </table>
    </form>
</div>
<%@ include file="../includes/admin_footer.jsp" %>
