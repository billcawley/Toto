<%-- Copyright (C) 2021 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Login"/>
<%@ include file="../includes/basic_header2.jsp" %>
<section class="section">
    <section class="hero ">
        <div class="hero-body">
            <div class="container">
                <div class="columns is-centered">
                    <div class="column is-5-tablet is-4-desktop is-3-widescreen">
                        <div class="box" style="background-color:${logoncolour}">
                            <img src="/images/edbroking_logo.jpg" alt="azquo">
                        </div>
                        <div class="box">
                            <form action="/api/Login" method="post">
                                <div class="field">
                                    <label class="label">Email/Username</label>
                                    <div class="control has-icons-left">
                                        <input type="text" class="input" value="${userEmail}" name="user" required>
                                        <span class="icon is-small is-left">
                  <i class="fa fa-envelope"></i>
                </span>
                                    </div>
                                </div>
                                <div class="field">
                                    <label class="label">Password</label>
                                    <div class="control has-icons-left">
                                        <input type="password" placeholder="" class="input" name="password" required>
                                        <span class="icon is-small is-left">
                  <i class="fa fa-lock"></i>
                </span>
                                    </div>
                                </div>
                                <c:if test="${not empty error}">
                                    <p class="help is-danger">${error}</p><br/>
                                </c:if>
                                <div class="field">
                                    <button class="button is-success"  style="background-color:#ed1b24">
                                        Login${logonmessage}
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</section>


</body>
</html>