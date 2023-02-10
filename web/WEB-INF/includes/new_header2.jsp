<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%><!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <title>Azquo &gt; ${title}</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0"/>
    <meta name="next-head-count" content="3"/>
    <link rel="icon" href="/favicon.png"/>
    <link rel="stylesheet" href="https://rsms.me/inter/inter.css"/>
    <link rel="preload" href="/newdesign/css/storybook.css" as="style"/>
    <link rel="stylesheet" href="/newdesign/css/storybook.css" data-n-g=""/>
    <noscript data-n-css=""></noscript>

    <script defer="" nomodule="" src="/newdesign/chunks/polyfills-0d1b80a048d4787e.js"></script>
    <script src="/newdesign/chunks/webpack-4dc2921e155e6a75.js" defer=""></script>
    <script src="/newdesign/chunks/framework-4556c45dd113b893.js" defer=""></script>
    <script src="/newdesign/chunks/main-7feab3f544c289f7.js" defer=""></script>
    <script>
        var importWarning = "${results}";
        var importError = "${error}";
    </script>

    <!--    <script src="/newdesign/chunks/pages/_app-6a6814ba84dfd6eb.js" defer=""></script>-->
    <script>${newappjavascript}</script>


    <script src="/newdesign/azoqkzEsPz0JW8-xCH7sF/_buildManifest.js" defer=""></script>
    <script src="/newdesign/azoqkzEsPz0JW8-xCH7sF/_ssgManifest.js" defer=""></script>

    ${extraScripts}
    <c:if test="${requirezss}">
        <kkjsp:head/>
    </c:if>

    <script>
        function showHideDiv(elementId) {
            var x = document.getElementById(elementId);
            if (x.style.display === "none") {
                x.style.display = "block";
            } else {
                x.style.display = "none";
            }
        }

        function hide(elementObj) {
            element.classList.add("az-hidden");
        }

        function show(elementObj) {
            element.classList.remove("az-hidden");
        }
    </script>
</head>
<body>
<div id="__next">
    <div class="az-layout">
        <div class="az-sidebar ${compact}">
            <div>
                <a class="az-sidebar-logo" href="/api/ManageReports/?newdesign=overview"><img
                        src="/images/gbcornerlogo.png"></a>
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
