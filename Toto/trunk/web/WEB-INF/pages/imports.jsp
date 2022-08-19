<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Imports"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-imports-view">
            <div class="az-section-heading">
                <h3>Imports</h3>
                <div class="az-section-controls">
                    <div class="az-section-buttons">
                        <button>
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
                                        d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                                ></path>
                            </svg>
                            New
                        </button>
                    </div>
                    <div class="az-section-filter">
                        <div>
                            <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    viewBox="0 0 20 20"
                                    fill="currentColor"
                                    aria-hidden="true"
                            >
                                <path
                                        fill-rule="evenodd"
                                        d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
                                        clip-rule="evenodd"
                                ></path>
                            </svg>
                        </div>
                        <input type="text" placeholder="Filter"/>
                    </div>
                    <div class="az-section-view">
                    <span><button class="selected">
                    <svg xmlns="http://www.w3.org/2000/svg"
                         fill="none"
                         viewBox="0 0 24 24"
                         stroke-width="2"
                         stroke="currentColor"
                         aria-hidden="true">
                    <path stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M4 6h16M4 10h16M4 14h16M4 18h16"></path>
                    </svg></button><button class="">
                    <svg xmlns="http://www.w3.org/2000/svg"
                         fill="none"
                         viewBox="0 0 24 24"
                         stroke-width="2"
                         stroke="currentColor"
                         aria-hidden="true">
                        <path stroke-linecap="round"
                              stroke-linejoin="round"
                              d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"></path>
                    </svg></button></span>
                    </div>
                </div>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>File Name</th>
                            <th>Database</th>
                            <th>User</th>
                            <th>Date</th>
                            <th></th>
                        </tr>

                        </thead>
                        <tbody>
                        <c:forEach items="${uploads}" var="upload">
                            <tr>
                            <tr>
                                <td class="full">
                                    <div>
                                        <a href="/imports/1/"
                                        >
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
                                                        d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
                                                ></path>
                                            </svg>
                                            <p>${upload.fileName}</p></a
                                        >
                                    </div>
                                </td>
                                <td><span class="az-badge">${upload.databaseName}</span></td>
                                <td>${upload.userName}</td>
                                <td>${upload.formattedDate}</td>
                                <td>
                                    <div class="az-actions">
                                        <div>
                                            <button
                                                    id="headlessui-menu-button-:R1kcml6:"
                                                    type="button"
                                                    aria-haspopup="true"
                                                    aria-expanded="false"
                                                    onclick="showHideDiv('submenu${upload.id}')"
                                            >
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
                                                            d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"
                                                    ></path>
                                                </svg>
                                            </button>
                                            <div class="az-actions-items transform opacity-100 scale-100"
                                                 style="display: none"
                                                 aria-labelledby="headlessui-menu-button-:rcp:"
                                                 id="submenu${upload.id}" role="menu" tabindex="0">
                                                <c:if test="${upload.comments.length() > 0}">
                                                <span
                                                        role="none"><div id="headlessui-menu-item-:rg0:" role="menuitem"
                                                                         tabindex="-1"><svg
                                                        xmlns="http://www.w3.org/2000/svg" fill="none"
                                                        viewBox="0 0 24 24"
                                                        stroke-width="2" stroke="currentColor" aria-hidden="true"><path
                                                        stroke-linecap="round" stroke-linejoin="round"
                                                        d="M3 19v-8.93a2 2 0 01.89-1.664l7-4.666a2 2 0 012.22 0l7 4.666A2 2 0 0121 10.07V19M3 19a2 2 0 002 2h14a2 2 0 002-2M3 19l6.75-4.5M21 19l-6.75-4.5M3 10l6.75 4.5M21 10l-6.75 4.5m0 0l-1.14.76a2 2 0 01-2.22 0l-1.14-.76"></path></svg><a
                                                        href="/api/ImportResults?urid=${upload.id}"
                                                        target="new">Results</a></div></span>
                                                </c:if>
                                                <c:if test="${upload.downloadable}"><span role="none"><div
                                                        id="headlessui-menu-item-:rg1:" role="menuitem" tabindex="-1"><svg
                                                        xmlns="http://www.w3.org/2000/svg" fill="none"
                                                        viewBox="0 0 24 24"
                                                        stroke-width="2" stroke="currentColor" aria-hidden="true"><path
                                                        stroke-linecap="round" stroke-linejoin="round"
                                                        d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path></svg><a
                                                        href="/api/DownloadFile?uploadRecordId=${upload.id}">Download</a></div></span></c:if>
                                                <!--<span
                                                    role="none"><div id="headlessui-menu-item-:rg2:" role="menuitem"
                                                                     tabindex="-1"><svg
                                                    xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                    stroke-width="2" stroke="currentColor" aria-hidden="true"><path
                                                    stroke-linecap="round" stroke-linejoin="round"
                                                    d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path></svg><a
                                                    href="/imports/#">Comment</a></div></span>--><span role="none"><hr
                                                    role="none"></span><span role="none"><div
                                                    id="headlessui-menu-item-:rg3:" role="menuitem" tabindex="-1"><svg
                                                    xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                    stroke-width="2" stroke="currentColor" aria-hidden="true"><path
                                                    stroke-linecap="round" stroke-linejoin="round"
                                                    d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg><a
                                                    href="/api/ManageDatabases?deleteUploadRecordId=${upload.id}"
                                                    &newdesign=imports>Delete</a></div></span></div>

                                        </div>
                                    </div>
                                </td>
                            </tr>


                            <!-- <td>${upload.businessName}</td>
                            <td><a href="/api/ManageDatabases?fileSearch=${upload.fileName}">${upload.count}</a>
                            <td>${upload.userComment}</td>
                            <td><a href="/api/UploadRecordComment?urid=${upload.id}" target="new"
                            class="button is-small" data-title="Edit" title="Comment"
                            id="comment${upload.id}">
                            <c:if test="${upload.userComment.length() > 0}">
                                Edit
                            </c:if>
                            <c:if test="${upload.userComment.length() == 0}">
                                Comment
                            </c:if>

                            </a></td>
                            <td><c:if test="${upload.comments.length() > 0}">
                            <a href="/api/ImportResults?urid=${upload.id}" target="new"
                            class="button is-small" data-title="Import Results"
                            title="View Import Results">Results</a>
                        </c:if></td>

                            <td><c:if test="${upload.downloadable}"><a
                            href="/api/DownloadFile?uploadRecordId=${upload.id}"
                            class="button is-small" title="Download"><span
                            class="fa fa-download" title="Download"></span> </a></c:if></td>
                            <td><a href="/api/ManageDatabases?deleteUploadRecordId=${upload.id}"
                            class="button is-small"
                            title="Delete"><span class="fa fa-trash" title="Delete"></span> </a></td>
                            -->
                        </c:forEach>
                        </tbody>
                    </table>
                    <br/>
                    <br/>
                    <br/>
                    <br/>
                    <br/>
                    <br/>
                    <br/>
                    <br/>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
