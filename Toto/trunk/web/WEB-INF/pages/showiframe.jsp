<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<c:set var="title" scope="request" value="View Report" />
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title} - Azquo</title>
    <link rel="stylesheet" href="/quickview/bulma-quickview.min.css">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Roboto&display=swap" rel="stylesheet">

    <link rel="stylesheet" href="/sass/mystyles.css">
    <!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <!-- required for inspect - presumably zap at some point -->
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
    <link href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">

    <script type="text/javascript" src="/js/global.js"></script>
    <style>
        .ui-dialog .ui-tabs-panel{min-height:350px; background:#ECECEC; padding:5px 5px 0px 5px; }
        .ui-dialog .ui-tabs-panel iframe{min-height:350px; background:#FFF;}

        header .nav ul li a.on {background-color:${bannerColor}}
        .ui-widget .ui-widget-header li.ui-state-active {background-color:${bannerColor}}
        a:link {color:${bannerColor}}
        a:visited {color:${bannerColor}}
        .navbar {background-color:${ribbonColor}}

    </style>
</head>
<body>
<nav class="navbar" role="navigation" aria-label="main navigation">
    <div class="navbar-brand">
        <a class="navbar-item" href="/api/Online?reportid=1">
            <img src="${cornerLogo}">
        </a>
    </div>
    <div id="navbarBasicExample" class="navbar-menu">
        <div class="navbar-start">
            <a class="navbar-item is-tab" href="/api/ManageReports" style="color: ${ribbonLinkColor}">Reports</a>

            <c:if test="${showInspect == true}"><a class="navbar-item is-tab" href="#" onclick="return inspectDatabase();" title="Inspect database" style="color: ${ribbonLinkColor}"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>--></c:if>
        </div>
        <div class="navbar-end">
            <c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
                <a class="navbar-item is-tab" href="/api/Login?select=true" style="color: ${ribbonLinkColor}"><i class="fa-solid fa-sitemap"></i></a>
            </c:if>
            <a class="navbar-item is-tab" href="/api/Login?logoff=true" style="color: ${ribbonLinkColor}">Sign Out</a>
        </div>
    </div>
</nav>

<c:if test="${reports != null && sessionScope.test != null}">
    <button class="button" data-show="quickview" data-target="quickviewDefault" style="
	position: fixed;
    top: 6%;
        z-index: 34;
"><i class="fa-solid fa-chevron-right"></i></button>

    <div id="quickviewDefault" class="quickview is-left" style="background-color: ${sideMenuColor}">
        <header class="quickview-header">
            <p style="color:${sideMenuLinkColor}" class="title">Reports</p>
            <span class="delete" data-dismiss="quickview"></span>
        </header>

        <div class="quickview-body">
            <div class="quickview-block">
                <c:forEach items="${reports}" var="report">
                    <c:if test="${report.database != 'None'}">
                        <c:if test="${report.category != ''}">
                            <hr style="height: 0px">
                            &nbsp;&nbsp;<span style="color:${sideMenuLinkColor};text-decoration: underline">${report.category}</span>
                            <hr style="height: 0px">
                        </c:if>
                        <a href="/api/Online?reportid=${report.id}&amp;database=${report.database}"  style="color:${sideMenuLinkColor}">
                            &nbsp;&nbsp;&nbsp;&nbsp;${report.untaggedReportName}<br/>
                        </a>
                    </c:if>
                </c:forEach>

            </div>
        </div>
    </div>
</c:if>


<script type="text/javascript">

    function inspectDatabase(){
        // can be passed database
        $.inspectOverlay("Inspect Azquo").tab("/api/Jstree?op=new", "DB :  ${databaseName}");
        return false;
        // window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }

    function auditDatabase(){
        // can be passed database
        window.open("/api/AuditDatabase", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=100, left=100, width=1600, height=1000")
    }


</script>

    <iframe id="inlineFrame"
            title="Inline Frame"
            width="100%"
            height="100%"
            src="${iframesrc}"
            style="height: calc(100vh - 70px);
display:block;">
    </iframe>

<c:if test="${reports != null && sessionScope.test != null}">
    <script type="text/javascript" src="/quickview/bulma-quickview.min.js"></script>
    <script>
        window.addEventListener('DOMContentLoaded', (event) => {
            var quickviews = bulmaQuickview.attach(); // quickviews now contains an array of all Quickview instances
        });
    </script>
</c:if>

</body>
</html>