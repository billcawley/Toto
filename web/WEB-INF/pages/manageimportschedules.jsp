<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Import Schedules"/>
<%@ include file="../includes/new_header.jsp" %>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">

<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>importSchedules</h3>
            </div>
            <div class="az-section-body">
                <!--                 <div class="az-alert az-alert-warning">${error}</div> -->
                <div>${error}</div>
                <div class="az-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Name</th>
                            <th>Database</th>
                            <th>Author</th>
                            <th> </th>
                            <th></th>
                            <!-- password and salt pointless here -->
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${importschedules}" var="importSchedule">
                            <tr>
                                <td>${importSchedule.name}</td>
                                <td>${importSchedule.database}</td>
                                <td>${importSchedule.user}</td>

                                <td><a href="/api/ManageImportSchedules?editId=${importSchedule.id}&action=setupwizard" title="Test ${importSchedule.name}"class="button is-small"><span class="far fa-paper-plane" title="Test"></span></a>
                                    <a href="/api/ManageImportSchedules?editId=${importSchedule.id}&newdesign=true" title="Edit ${importSchedule.name}"class="button is-small"><span class="fa fa-edit" title="Edit"></span></a>
                                    <a href="/api/ManageImportSchedules?deleteId=${importSchedule.id}&newdesign=true" title="Delete ${importSchedule.name}" class="button is-small"
                                       onclick="return confirm('Are you sure?')"><span class="fa fa-trash" title="Delete"></span> </a></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>

                    <form action="/api/ManageimportSchedules" method="post" enctype="multipart/form-data">
                        <input type="hidden" name="newdesign" value="true"/>
                        <nav>
                            <div>
                                <button onclick="location.href='/api/ManageImportSchedules?editId=0&newdesign=true'" type="button">Add New Import Schedule</button>
                            </div>
                        </nav>
                    </form>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
