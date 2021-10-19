<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Login" />
<%@ include file="../includes/basic_header2.jsp" %>
<section class="section">
    <section class="hero ">
        <div class="hero-body">
            <div class="container">
                <div class="columns is-centered">
                    <div class="column is-5-tablet is-4-desktop is-3-widescreen">
                        <div class="box">
                        <h1 class="title">Select a business</h1>
                        <c:forEach items="${users}" var="user">
                            <a href="/api/Login?userid=${user.id}">${user.businessName} - ${user.status}</a><br/>
                        </c:forEach>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</section>
</body>
</html>