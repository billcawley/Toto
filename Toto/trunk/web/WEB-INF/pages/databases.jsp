<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Databases"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-databases-view">
            <div class="az-section-heading">
                <h3>Databases</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Name</th>
                            <th>Persistence Name</th>
                            <th>Names</th>
                            <th>Values</th>
                            <th>Created</th>
                            <th>Last Audit</th>
                            <th>Auto Backup</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${databases}" var="database">
                            <tr>
                                <!--<td>${database.id}</td>-->
                                <!--<td>${database.businessId}</td> -->
                                <td>${database.name}</td>
                                <td>${database.persistenceName}</td>
                                <td>${database.nameCount}</td>
                                <td>${database.valueCount}</td>
                                <td>${database.created}</td>
                                <td><details>
                                    <summary>${fn:substring(database.lastProvenance, 0, 25)}...</summary>
                                    <p>${database.lastProvenance}</p>
                                </details></td>
                                <td>
                                    <a href="/api/ManageDatabases?toggleAutobackup=${database.id}&ab=${database.autobackup}&newdesign=databases">${database.autobackup}</a><c:if
                                        test="${database.autobackup}">&nbsp;|&nbsp;<a href="/api/ManageDatabaseBackups?databaseId=${database.id}">view</a></c:if>
                                </td>
                                <td>

                                    <div class="az-actions">
                                        <div>

                                            <button id="headlessui-menu-button-:rb:" type="button" aria-haspopup="true"
                                                    aria-expanded="false" onclick="showHideDiv('submenu${database.id}')">
                                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                                                     stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                    <path stroke-linecap="round" stroke-linejoin="round"
                                                          d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z"></path>
                                                </svg>
                                            </button>

                                            <div class="az-actions-items transform opacity-100 scale-100" aria-labelledby="submenu${database.id}" id="submenu${database.id}" role="menu" tabindex="0" style="display: none">
                                                <span role="none"><div id="headlessui-menu-item-:rs:" role="menuitem" tabindex="-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                        <path stroke-linecap="round" stroke-linejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"></path>
                                                    </svg><a href="/api/Jstree?op=new&database=${database.urlEncodedName}" target="_blank">Inspect</a></div>
                                                </span><span role="none">
                                                <div id="headlessui-menu-item-:rt:" role="menuitem" tabindex="-1"><svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                    <path stroke-linecap="round" stroke-linejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                                                </svg>
                                                    <a href="/api/DownloadBackup?id=${database.id}">Download</a></div></span>
                                                <c:if test="${database.loaded}"><span role="none">
                                                <div id role="menuitem" tabindex="-1">
                                                    <a href="/api/ManageDatabases?unloadId=${database.id}&newdesign=databases">Unload</a></div></span></c:if>
                                                <span role="none"><hr role="none"></span><span role="none">
                                                <div id="headlessui-menu-item-:ru:" role="menuitem" tabindex="-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true">
                                                        <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16">
                                                        </path></svg><a href="/api/ManageDatabases?deleteId=${database.id}&newdesign=databases"
                                                                        onclick="return confirm('Are you sure you want to Delete ${database.name}?')">Delete</a></div></span>
                                                <span role="none">
                                                <div role="menuitem" tabindex="-1">
                                                    <a href="/api/ManageDatabases?emptyId=${database.id}&newdesign=databases"
                                                       onclick="return confirm('Are you sure you want to Empty ${database.name}?')">
                                                    Empty</a></div></span>

                                                <c:if test="${database.loaded}">

                                                    <span role="none"><hr role="none"></span><span role="none">
                                                <div role="menuitem" tabindex="-1">
                                                    <a href="/api/ManageDatabases?unloadId=${database.id}">Unload</a></div></span>
                                                    </c:if>
                                            </div>
                                        </div>
                                    </div>

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
