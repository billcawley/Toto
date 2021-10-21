<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="title" scope="request" value="Edit Report"/>
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
    <form action="/api/ManageReports" method="post">
        <input type="hidden" name="editId" value="${id}"/>
        <!-- no business id -->

        <table class="table">
            <tbody>
            <tr>
                <td width="50%">
                    <div>
                        <div class="field">
                            <label class="label">File</label>
                            <label class="label">${file}</label>
                        </div>

                        <div class="field">
                            <label class="label">Name</label>
                            <input class="input is-small" name="name" id="name" value="${name}">
                        </div>
                        <div class="field">
                            <label class="label">Category</label>
                            <input class="input is-small" name="category" id="category" value="${category}">
                        </div>
                        <div class="field">
                            <label class="label">Explanation</label>
                            <textarea class="textarea is-small" name="explanation" rows="3" cols="80">${explanation}</textarea>
                        </div>
                    </div>
                </td>
                <td width="50%">
                    <div class="field">
                        <label class="label">Associated Databases</label>
                        <c:forEach items="${databasesSelected}" var="databaseSelected">
                            ${databaseSelected.database.name}&nbsp;<input type="checkbox" name="databaseIdList"
                                                                    value="${databaseSelected.database.id}" <c:if
                                test="${databaseSelected.selected}"> checked</c:if>><br/>
                        </c:forEach>
                    </div>
                </td>
            </tbody>
        </table>
        <div>
            <button type="submit" name="submit" value="save" class="button is-small">Save</button>
        </div>
    </form>

</div>

<%@ include file="../includes/admin_footer.jsp" %>
