<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Validation" />
<%@ include file="../includes/admin_header.jsp" %>
<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

</script>
    <main>
    <h1>Import Validation - ${filename}</h1>
    <form>
        <input type="hidden" name="id" value="${id}"/>
        <c:choose>
            <c:when test="${dbselect == true}">
                The upload has not been assigned to a database, please select one to continue :
                <select name="databaseId"><c:forEach items="${databases}" var="database">
                    <option value="${database.id}">${database.name}</option>
                </c:forEach></select>
            </c:when>
            <c:otherwise>
                <h2>Database - ${database}</h2>
            </c:otherwise>
        </c:choose>

        <!-- params passed if they need to be set-->
<c:if test="${setparams == true}">
    The upload does not have sufficient parameters set, please assign them to continue :
    <table>
        <tr>
            <c:forEach items="${params}" var="entry">
                <td>${entry.key}</td>
            </c:forEach>
        </tr>
        <tr>
            <c:forEach items="${params}" var="entry">
                <td><select name="param-${entry.key}"><c:forEach
                        items="${entry.value}"
                        var="listitem">
                    <option value="${listitem}">${listitem}</option>
                </c:forEach></select></td>            </c:forEach>
        </tr>
    </table>
</c:if>
        ${maintext}
    <div class="centeralign">
        <input type="submit" name="Ok" value="Ok" class="button"/>
        <a href="#" class="button">Reject</a>
        <a href="/api/ManageDatabases#tab4" class="button">Cancel</a>
    </div>
    </form>
</main>
<%@ include file="../includes/admin_footer.jsp" %>