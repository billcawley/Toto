<%-- Copyright (C) 2016 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Reports"/>
<%@ include file="../includes/admin_header2.jsp" %>
<div class="box">
<div class="container is-fluid">
    <div class="columns is-multiline">

        <c:forEach items="${reports}" var="report">
            <c:if test="${report.category != ''}">
                ${categorybefore} ${report.category} ${categoryafter}
            </c:if>
            <div class="column is-4">
                <div class="card">
                    <header class="card-header">
                        <p class="card-header-title">
                                ${report.untaggedReportName}
                        </p>
                        <button class="card-header-icon" aria-label="more options">
            <span class="icon">
              <a href="/api/ManageReports?editId=${report.id}" title="Edit ${report.reportName}"><i class="fas fa-edit"
                                                                                                    aria-hidden="true"></i></a>
              <a href="/api/ManageReports?deleteId=${report.id}"
                 onclick="return confirm('Are you sure you want to delete ${report.reportName}?')"
                 title="Delete ${report.reportName}"><i class="fas fa-trash" aria-hidden="true"></i></a>
                                  <a href="/api/DownloadTemplate?reportId=${report.id}" title="Download"><i
                                          class="fas fa-download" aria-hidden="true"></i></a>
            </span>
                        </button>
                    </header>
                    <div class="card-content">
                        <div class="content">
                                ${report.explanation}
                            <br>
                            <br>
                            <p>${report.database} - ${report.author}</p>
                        </div>
                    </div>
                    <footer class="card-footer">
                        <c:if test="${report.database != 'None'}"><a
                                href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"
                                class="card-footer-item">Open
                        </a></c:if>
                    </footer>
                </div>
            </div>
        </c:forEach>
    </div>
</div>
</div>
<%@ include file="../includes/admin_footer.jsp" %>
