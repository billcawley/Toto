<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit Menu Appearance"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <h1 class="title">Edit/Menu Appearance</h1>
    <div class="has-text-danger">${error}</div>
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
                <td>
                    <label class="label">Role/menu name</label>
                </td>
                <td>
                    <div class="field">
                        <input class="input is-small" name="submenuName" id="submenuName" value="${submenuName}">
                    </div>
                </td>
            </tr>
            <tr>

                <td>
                    <label class="label">Importance (numerical...high numbers appear higher in the menu)</label>
                </td>
                <td>
                    <div class="field">
                        <input class="input is-small" name="importance" id="importance" value="${importance}">
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
