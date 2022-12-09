<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  Created by IntelliJ IDEA.
  User: Bill
  Date: 07/12/2022
  Time: 09:03
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <meta charset="utf-8">
    <title>Azquo &gt; Dashboard</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0">
    <meta name="next-head-count" content="3">
    <link rel="icon" href="/favicon.png">
    <link rel="stylesheet" href="https://rsms.me/inter/inter.css">
    <link rel="preload"
          href="/newdesign/css/storybook.css"
          as="style">
    <link rel="stylesheet"
          href="/newdesign/css/storybook.css"
          data-n-g="">
    <noscript data-n-css=""></noscript>
<body>
<script>
    var showCards = false;
</script>
<div id="__next">
    <div class="az-layout">
        <div class="az-sidebar">
            <div><a class="az-sidebar-logo" href="/"><img
                    src="https://cherrett-digital.s3.eu-west-2.amazonaws.com/assets/images/logo_dark_bg.png"></a>
                <nav>
                    <div class="az-sidebar-primary">
                        <c:forEach items="${mainmenu}" var="menuItem">

                            <a class="group <c:if test="${selected==menuItem.name}"> active</c:if> rel="noopener noreferrer" href="${menuItem.href}">
                            ${menuItem.icon}
                            <span>${menuItem.name}</span>
                            </a>
                        </c:forEach>
                    </div>
                    <div class="az-sidebar-secondary">
                        <c:forEach items="${secondarymenu}" var="menuItem">
                            <a class="group  <c:if test="${selected==menuItem.name}"> active</c:if>" rel="noopener noreferrer" href="${menuItem.href}/">
                                    ${icon[menuItem.name]}"
                                <span>{menuItem.name}</span>
                            </a>
                        </c:forEach>
                    </div>
                </nav>
            </div>
        </div>
