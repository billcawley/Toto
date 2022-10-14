<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit External Data Requests"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit External Data Request</h1>
    <div class="has-text-danger">${error}</div>
    <form action="/api//ManageReports" method="post">
        <input type="hidden" name="editId" value="${reportid}"/>
        <input type="hidden" name="externaldatarequestId" id="externaldatarequestId" value = "${id}" />
        <!-- no business id -->
        <div class="field">
            <label class="label">Report name: ${reportname}</label>
        </div>

        <table class="table">
            <tbody>
            <tr>
                <td>
                    <label class="label">Sheet/Range name</label>
                </td>
                <td>

                    <div class="field">
                        <input class="input is-small" name="sheetRangeName" id="sheetRangeName" value="${sheetRangeName}">
                    </div>
                </td>
            </tr>
            <tr>

                <label>Only fill in below where a database connector is used.  Name and Read SQL only required</label>

            </tr>
            <tr>
                <td>
                    <label class="label">Connector name</label>
                </td>
                <td>

                    <div class="field">
                        <input class="input is-small" name="connectorName" id="connectorName" value="${connectorName}">
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    <label class="label">SQL to load data</label>
                </td>
                <td style="width:600px">
                    <div class="field">
                        <input class="input is-small" name="readSQL" id="readSQL" value="${readSQL}">
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    <label class="label">Key field for save</label>
                </td>
                <td>
                    <div class="field">
                        <input class="input is-small" name="saveKeyfield" id="saveKeyfield" value="${saveKeyfield}">
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    <label class="label">File for save</label>
                </td>
                <td>

                    <div class="field">
                        <input class="input is-small" name="saveFilename" id="saveFilename" value="${saveFilename}">
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    <label class="label">Value for inserted key (0 to autonumber)</label>
                </td>
                <td>
                    <div class="field">
                        <input class="input is-small" name="saveInsertKeyValue" id="saveInsertKeyValue" value="${saveInsertKeyValue}">
                    </div>
                </td>
            </tr>
            <tr>
                <td>
                    <label class="label">Allow Delete (Y/N)</label>
                </td>
                <td>
                    <div class="field">
                        <input class="input is-small" name="saveAllowDelete" id="saveAllowDelete" value="${saveAllowDelete}">
                    </div>
                </td>
            </tr>
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
