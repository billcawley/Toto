<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Login" />
<%@ include file="../includes/basic_header.jsp" %>

<main class="basicDialog">
	<div class="basic-box-container">
		<div class="basic-box">
			<h3>Please select a business</h3>
			<c:forEach items="${users}" var="user">
				<a href="/api/Login?userid=${user.id}">${user.businessName} - ${user.status}</a><br/>
			</c:forEach>
		</div>
	</div>
</main>

<%@ include file="../includes/basic_footer.jsp" %>