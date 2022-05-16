<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit External Data Requests"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit/Menu Appearance</h1>
    <div class="is-danger">${error}</div>
    <form action="/api//ManageReports" method="post">
        <input type="hidden" name="editId" value="${reportid}"/>
        <input type="hidden" name="externaldatarequestId" id="externaldatarequestId" value = "${id}" />
        <!-- no business id -->
        <div class="field">
            <label class="label">Report name</label>
            <label class="label">${reportname}</label>
        </div>

        <table class="table">
            <tbody>
            <tr>
                <td width="50%">
                    <div class="field">
                        <label class="label">Sheet/Range name</label>
                        <input class="input is-small" name="sheetRangeName" id="sheetRangeName" value="${sheetRangeName}">
                    </div>
                    <label>Only fill in below where a database connector is used.  Name and Read SQL only required</label>

                    <div class="field">
                        <label class="label">Connector name</label>
                        <input class="input is-small" name="connectorName" id="connectorName" value="${connectorName}">
                    </div>
                    <div class="field">
                        <label class="label">SQL to load data</label>
                        <input class="input is-small" name="readSQL" id="readSQL" value="${readSQL}">
                    </div>
                    <div class="field">
                        <label class="label">Key field for save</label>
                        <input class="input is-small" name="saveKeyfield" id="saveKeyfield" value="${saveKeyfield}">
                    </div>
                    <div class="field">
                        <label class="label">File for save</label>
                        <input class="input is-small" name="saveFilename" id="saveFilename" value="${saveFilename}">
                    </div>
                    <div class="field">
                        <label class="label">Value for inserted key (0 to autonumber)</label>
                        <input class="input is-small" name="saveInsertKeyValue" id="saveInsertKeyValue" value="${saveInsertKeyValue}">
                    </div>
                    <div class="field">
                        <label class="label">Allow Delete (Y/N)</label>
                        <input class="input is-small" name="saveAllowDelete" id="saveAllowDelete" value="${saveAllowDelete}">
                    </div>
                </td>
            </tbody>
        </table>

        <div class="centeralign">
            <button type="submit" name="submit" value="save" class="button is-small">Save</button>
            <button type="submit" name="submit" value="cancel" class="button is-small">Cancel
            </button>
        </div>
    </form>

</div>

<%@ include file="../includes/admin_footer.jsp" %>
