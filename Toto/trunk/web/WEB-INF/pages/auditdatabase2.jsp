<%-- Copyright (C) 2021 Azquo Holdings Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Audit Database"/>
<%@ include file="../includes/public_header2.jsp" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="azquoTags" %>
<div class="box">
    <table class="table is-striped is-fullwidth">
        <tr>
            <td>Audit Database</td>
            <td text-align:right>
                <c:if test="${date==null}">
                    <a onclick="window.close()"><span class="fa fa-close" title="Back"></span></a>
                </c:if>
                <c:if test="${date.length() == 8}">
                    <a href="/api/AuditDatabase?datetime=back"><span class="fa fa-arrow-left" title="Back"></span></a>
                </c:if>
                <c:if test="${date.length() > 8}">
                    <a href="/api/AuditDatabase?datetime=${date.substring(0,8)}"><span class="fa fa-arrow-left" title="Back"></span></a>
                </c:if>
            </td>
        </tr>
    </Table>

    <form action="/api/AuditDatabase" method="post">
        <div class="has-text-danger">${error}</div>
        <table class="table is-striped is-fullwidth">
            <thead>
            <tr>
                <!-- <td>Report id</td>
                <td>Business Id</td>-->
                <td>Date/Time</td>
                <td>User(s)</td>
                <td>Method</td>
                <td>Name</td>
                <td>Context</td>
                <td>Instance count</td>
                <td>Names changed</td>
                <td>Values changed</td>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${provenanceForDisplays}" var="provenanceForDisplay">
                <tr>
                    <td>${provenanceForDisplay.displayDate}</td>
                    <td>${provenanceForDisplay.user}</td>
                    <td>${provenanceForDisplay.method}</td>
                    <td>${provenanceForDisplay.name}</td>
                    <td>${provenanceForDisplay.context}</td>
                    <td>
                        <a href="/api/AuditDatabase?datetime=${provenanceForDisplay.displayDate}">${provenanceForDisplay.provenanceCount}</a>
                    </td>
                    <td>${provenanceForDisplay.nameCount} </td>
                    <td>${provenanceForDisplay.valueCount}</td>
                </tr>
            </c:forEach>
            </tbody>
        </table>

        <azquoTags:treeNode node="${node}"/>
    </form>
    <!---
    <div class="centeralign">
        <form action="/api/ManageprovenanceForDisplays" method="post" enctype="multipart/form-data"><input type="submit" name="Upload" value="Upload provenanceForDisplay" class="button "/>&nbsp;
            <input id="uploadFile" type="file" name="uploadFile"></form>
    </div>
    --->
</div>
<%@ include file="../includes/public_footer.jsp" %>