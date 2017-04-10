<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 07/04/17
  Time: 15:05
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups" />
<%@ include file="../includes/admin_header.jsp" %>


<main class="databases">
    <h1>Copy Business</h1>
    <div class="error">${error}</div>
    <form>
        <label for="a">New business name:</label> <input name="businessName" id="a"/><br/>
        <label for="b">New admin user email :</label> <input name="userEmail" id="b"/><br/>
        <label for="c">Password :</label> <input name="password" type="password" id="c"/><br/>
        <input type="submit" name="Copy" value="Copy" class="button"/></form>

</main>
<%@ include file="../includes/admin_footer.jsp" %>