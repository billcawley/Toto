<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="View Report"/>
<c:set var="compact" scope="request" value="compact"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <iframe id="inlineFrame"
            title="Inline Frame"
            width="100%"
            height="100%"
            src="${iframesrc}"
            style="height: calc(100vh - 70px);
display:block;">
    </iframe>
</div>
<style>
    /* remove dash borders of the auto filter */
    [class*="af"]:after {
        border: initial !important;
    }
</style>
<%@ include file="../includes/new_footer.jsp" %>