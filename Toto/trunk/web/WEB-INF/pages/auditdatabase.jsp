<%-- Copyright (C) 2021 Azquo Holdings Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Audit Database" />
<%@ include file="../includes/admin_header.jsp" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="azquoTags"%>
<main>
    <h1>Audit Database</h1><br/>
    <form action="/api/AuditDatabase" method="post">
        <div class="error">${error}</div>
        <table>
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
                    <td><a href="/api/AuditDatabase?datetime=${provenanceForDisplay.displayDate}">${provenanceForDisplay.provenanceCount}</a></td>
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
</main>
<%@ include file="../includes/admin_footer.jsp" %>
