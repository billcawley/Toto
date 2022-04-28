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
    </style>
</head>
<body>
<nav class="navbar is-black" role="navigation" aria-label="main navigation">
    <div class="navbar-brand">
        <a class="navbar-item" href="https://azquo.com">
            <img src="${logo}" alt="azquo">
        </a>
    </div>
    <div id="navbarBasicExample" class="navbar-menu">
        <div class="navbar-start">
            <a class="navbar-item" href="/api/ManageReports">Reports</a>

            <c:if test="${showInspect == true}"><a class="navbar-item" href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>--></c:if>
        </div>
        <div class="navbar-end">
            <c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
                <a class="navbar-item" href="/api/Login?select=true"><i class="fa-solid fa-sitemap"></i></a>
            </c:if>
            <a class="navbar-item" href="/api/Login?logoff=true">Sign Out</a>
        </div>
    </div>
</nav>

<c:if test="${reports != null && param.testmenu==true}">
    <button class="button" data-show="quickview" data-target="quickviewDefault" style="
	position: fixed;
    top: 5%;
        z-index: 34;
"><i class="fa-solid fa-chevron-right"></i></button>

    <div id="quickviewDefault" class="quickview is-left">
        <header class="quickview-header">
            <p class="title">Reports</p>
            <span class="delete" data-dismiss="quickview"></span>
        </header>

        <div class="quickview-body">
            <div class="quickview-block">
                <c:forEach items="${reports}" var="report">
                    <c:if test="${report.database != 'None'}">
                        <c:if test="${report.category != ''}">
                            <hr>
                            &nbsp;&nbsp;${report.category}
                            <hr>
                        </c:if>
                        <a href="/api/Online?reportid=${report.id}&amp;database=${report.database}&amp;permissionid=${report.untaggedReportName}">
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

<c:if test="${reports != null && param.testmenu==true}">
    <script type="text/javascript" src="/quickview/bulma-quickview.min.js"></script>
    <script>
        var quickviews = bulmaQuickview.attach(); // quickviews now contains an array of all Quickview instances
    </script>
</c:if>

</body>
</html>