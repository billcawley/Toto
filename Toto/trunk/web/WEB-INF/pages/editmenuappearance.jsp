<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit Menu Appearance"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit/Menu Appearance</h1>
    <div class="is-danger">${error}</div>
    <form action="/api//ManageReports" method="post">
        <input type="hidden" name="editId" value="${reportid}"/>
        <input type="hidden" name="menuAppearanceId" id="menuAppearanceId" value = "${id}" />
        <!-- no business id -->
        <div class="field">
             <label class="label">${reportname}</label>
        </div>

        <table class="table">
            <tbody>
            <tr>
                <td width="50%">
                    <div class="field">
                        <label class="label">Role/menu name</label>
                        <input class="input is-small" name="submenuName" id="submenuName" value="${submenuName}">
                    </div>
                    <div class="field">
                        <label class="label">Importance (numerical...high numbers appear higher in the menu)</label>
                        <input class="input is-small" name="importance" id="importance" value="${importance}">
                    </div>
                    <div class="field">
                        <label class="label">OPTIONAL - Shown name(if, for instance, the same report name applies to different databases)</label>
                        <input class="input is-small" name="showname" id="showname" value="${showname}">
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
