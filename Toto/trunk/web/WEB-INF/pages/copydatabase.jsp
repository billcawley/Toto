<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 24/10/16
  Time: 15:51
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups" />
<%@ include file="../includes/admin_header.jsp" %>


<main class="databases">
    <h1>Copy database ${database}</h1>
    <div class="error">${error}</div>
    <form><label for="copyName">New Database Name:</label> <input name="copyName" id="copyName"/><input type="hidden" name="databaseId" value="${databaseId}"/>
        <input type="submit" name="Copy" value="Copy" class="button"/></form>

</main>
<%@ include file="../includes/admin_footer.jsp" %>