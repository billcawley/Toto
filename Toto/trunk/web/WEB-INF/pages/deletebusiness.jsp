<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 11/04/17
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups" />
<%@ include file="../includes/admin_header.jsp" %>


<main class="databases">
    <h1>Delete Business</h1>
    <div class="error">${error}</div>
    <form method="post">
        <input type="hidden" name="confirm" value="${businessname}">
        <input type="submit" name="Delete" value="Delete - do not press unless you are sure" class="button"/></form>

</main>
<%@ include file="../includes/admin_footer.jsp" %>