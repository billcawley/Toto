<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Login, Azquo build 11-10-18" />
<%@ include file="../includes/basic_header.jsp" %>

<main class="basicDialog">
    <div class="basic-box-container">
        <div class="basic-head" style="background-color:#ed1b24">
            <div class="logo">
                <img src="/images/edbroking_logo.jpg" alt="azquo">
            </div>
        </div>
        <div class="basic-box">
            <h3>Logon to the Azquo system below</h3>
            <div class="error">${error}</div>
            <p>All Azquo reports can be viewed online &#8211; use the same log on details as you use in your spreadsheets.</p>

            <form method="post" name="loginform" action="/api/Login">
                <input type="hidden" name="online" value="true">


                <div>
                    <label for="user">Username</label>
                    <input type="text" id="az_Logon" value="${userEmail}" name="user" placeholder="Please enter your username"/>
                </div>

                <div>
                    <label for="password">Password</label>
                    <input type="password" id="az_Password" name="password" value=""  placeholder="Please enter your password"/>
                </div>
                <div class="centeralign">
                    <button class="button" style="background-color:#ed1b24">Login <span class="fa fa-sign-in"></span></button>
                </div>
            </form>
        </div>
    </div>
</main>
