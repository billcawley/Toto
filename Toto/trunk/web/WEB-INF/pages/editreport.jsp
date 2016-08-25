<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<c:set var="title" scope="request" value="Edit/New Permission" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
    <h1>Edit Report</h1>
    <form action="/api/ManageReports" method="post">
        <input type="hidden" name="editId" value="${id}"/>
        <!-- no business id -->

        <table class="edit">
            <tbody>
            <tr>
                <td width="50%">
                    <h3>Details</h3>
                    <div class="well">
                        <div>
                            <label >File</label>
                            ${file}
                        </div>
                        <div>
                            <label for="name">Name</label>
                            <input name="name" id="name" value="${name}">
                        </div>
                        <div>
                            <label>Explanation</label>
                            <textarea name="explanation" rows="3">${explanation}</textarea>
                        </div>
                    </div>
                </td>
                <td width="50%">
                    <h3>Associated Databases</h3>
                    <div class="well" align="right">
                                <c:forEach items="${databasesSelected}" var="databaseSelected">
                                        ${databaseSelected.database.name}<input type="checkbox" name="databaseIdList" value="${databaseSelected.database.id}" <c:if test="${databaseSelected.selected}"> checked</c:if>><br/>
                                </c:forEach>
                    </div>
                </td>
            </tbody>
        </table>


        <div class="centeralign">
            <button type="submit" name="submit" value="save" class="button"><span class="fa fa-floppy-o"></span> Save </button>
        </div>
    </form>

</main>

<%@ include file="../includes/admin_footer.jsp" %>
