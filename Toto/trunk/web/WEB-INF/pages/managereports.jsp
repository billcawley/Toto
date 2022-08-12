<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Reports"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <div class="az-topbar">
        <button>
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                 stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h8m-8 6h16"></path>
            </svg>
        </button>
        <div class="az-searchbar">
            <form action="#">
                <div>
                    <div>
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
                        </svg>
                    </div>
                    <input placeholder="Search" type="text" value=""></div>
            </form>
        </div>
    </div>

    <main>
        <div class="az-reports-view">
            <div class="az-section-heading"><h3>Reports</h3>
                <div class="az-section-controls">
                    <div class="az-section-filter">
                        <div>
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"
                                 aria-hidden="true">
                                <path fill-rule="evenodd"
                                      d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
                                      clip-rule="evenodd"></path>
                            </svg>
                        </div>
                        <input type="text" placeholder="Filter"></div>
                    <div class="az-section-view"><span><button class="selected"><svg xmlns="http://www.w3.org/2000/svg"
                                                                                     fill="none" viewBox="0 0 24 24"
                                                                                     stroke-width="2"
                                                                                     stroke="currentColor"
                                                                                     aria-hidden="true"><path
                            stroke-linecap="round" stroke-linejoin="round"
                            d="M4 6h16M4 10h16M4 14h16M4 18h16"></path></svg></button><button class=""><svg
                            xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                            stroke="currentColor" aria-hidden="true"><path stroke-linecap="round"
                                                                           stroke-linejoin="round"
                                                                           d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"></path></svg></button></span>
                    </div>
                </div>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Name</th>
                            <th>Database</th>
                            <th>Author</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>

                        <c:forEach items="${reports}" var="report">
                            <!-- <td style="white-space: nowrap;">
                            <a href="/api/ManageReports?editId=${report.id}" title="Edit ${report.reportName}" class="button is-small"><span class="fa fa-edit" title="Edit"></span></a>
                            <a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" title="Delete ${report.reportName}" class="button is-small"><span class="fa fa-trash" title="Delete"></span> </a>
                            <a href="/api/DownloadTemplate?reportId=${report.id}" title="Download" class="button is-small"><span class="fa fa-download" title="Download"></span> </a>
                            </td>-->
                            <tr>
                                <c:if test="${showexplanation}">
                                    <!-- <td>${report.explanation}</td> -->
                                </c:if>

                                <td class="full">
                                    <div>

                                        <c:if test="${report.database != 'None'}"><a
                                            href="/api/Online?reportid=${report.id}&amp;database=${report.database}&newdesign=true"></c:if>
                                        <svg
                                                xmlns="http://www.w3.org/2000/svg"
                                                fill="none"
                                                viewBox="0 0 24 24"
                                                stroke-width="2"
                                                stroke="currentColor"
                                                aria-hidden="true"
                                        >
                                            <path
                                                    stroke-linecap="round"
                                                    stroke-linejoin="round"
                                                    d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2"
                                            ></path>
                                        </svg>
                                        <p>${report.untaggedReportName}</p>
                                        <c:if test="${report.database != 'None'}"></a></c:if>

                                    </div>
                                </td>
                                <td><span class="az-badge">${report.database}</span></td>
                                <td>${report.author}</td>
                                <td>
                                    <div class="az-actions">
                                        <div>
                                            <button id="headlessui-menu-button-:r1h:" type="button" aria-haspopup="true"
                                                    aria-expanded="false">
                                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                     stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                    <path stroke-linecap="round" stroke-linejoin="round"
                                                          d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path>
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>

                        </tbody>
                    </table>
                    <nav>
                        <div><p>Showing <strong>1</strong> to <strong>10</strong> of <strong>11</strong> reports</p>
                        </div>
                        <div>
                            <button disabled="">Previous</button>
                            <button>Next</button>
                        </div>
                    </nav>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
