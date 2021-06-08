<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Login" />
<%@ include file="../includes/basic_header.jsp" %>

<main class="basicDialog">
    <div class="basic-box-container">
        <div class="basic-box">
            <h3>Select a business to delete</h3>
            <c:forEach items="${users}" var="user">
                <a onclick="return confirm('Are you sure you want to Delete ${user.businessName}? All databases and reports for that business will be deleted')" href="/api/DeleteBusiness?userId=${user.id}">${user.businessName}</a><br/>
            </c:forEach>
        </div>
    </div>
</main>

<%@ include file="../includes/basic_footer.jsp" %>