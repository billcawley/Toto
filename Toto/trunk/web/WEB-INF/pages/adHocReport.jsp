<%--
  Created by IntelliJ IDEA.
  User: Bill
  Date: 27/03/2018
  Time: 14:22
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title><%--
Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT

Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:48
  To change this template use File | Settings | File Templates.
--%>
        <%@ page contentType="text/html;charset=UTF-8" language="java" %>
        <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
        <c:set var="title" scope="request" value="Azquo Adhoc Report" />
        <%@ include file="../includes/admin_header.jsp" %>

        <script  type="text/javascript">
            function chooseHeading(heading){
                var headingVal = document.getElementById(heading).value;
                //var w = $['inspectOverlay']("Inspect").tab("/api/Jstree?op=new&database=" + document.getElementById("database").value, "inspect");
                var w = window.open("/api/Jstree?op=new&database=" + document.getElementById("database").value, "Choose a name","height=400, width=600, left=100,menubar=no, status=no,titlebar=no");
                w.onbeforeunload = function() {
                    var itemChosen = localStorage.getItem("itemsChosen");
                    document.getElementById(heading).value = itemChosen;
                    alert("window closed");
                }
                return false;
            }

        </script>
        <main class="databases">
        <main class="databases">
            <h1>Ad-hoc Report</h1>
            <div class="error">${error}</div>
            <!-- Uploads -->
                 <form action="/api/AdhocReport" method="post">
                    <div class="well">
                        <table>
                            <tr>
                                <td>
                                    <label for="database">Database:</label>
                                    <select name="database" id="database">
                                        <option value="">None</option>
                                        <c:forEach items="${databases}" var="database">
                                            <option value="${database}" <c:if test="${database == reportDatabase}"> selected </c:if>>${database}</option>
                                        </c:forEach>
                                    </select>
                                </td>
                                <td><label for="reportname">Report Name:</label> <input name="reportname" id="reportname" value = "${reportname}"/></td>
                                <td>
                                    <input type="submit" name="Create Report" value="Create Report" class="button"/>
                                </td>
                            </tr>
                        </table>
                    </div>
                    <!-- Headings list -->
                    <table>
                        <thead>
                        <tr>
                            <td>Heading</td>
                            <td>Type</td>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${headings}" var="heading">
                            <tr>
                                <td>
                                    <input type="text" name="${heading.id}" id="${heading.id}" value=""${heading.name}"/>
                                    <a onclick="chooseHeading('${heading.id}')" class="button small" title="Choose ${heading.id}"><span class="fa fa-edit" title="Unload"></span></a></td>


                                </td>
                                <td>

                                    <input type="radio" name="${heading.type}" value="Name" <c:if test="${heading.type== 'name'}">CHECKED</c:if>/> Name
                                    <input type="radio" name="${heading.type}" value="Value" <c:if test="${heading.type== 'value'}">CHECKED</c:if>/> Value
                                    <c:if test="${heading.isset}">
                                        <input type="radio" name="${heading.type}" value="Detailed" <c:if test="${heading.type== 'detailed'}">CHECKED</c:if>/> Detailed
                                    </c:if>
                                    <c:if test="${heading.hasGrandchildren}">
                                        <input type="radio" name="${heading.type}" value="Children" <c:if test="${heading.type== 'children'}">CHECKED</c:if>/> Value
                                        <input type="radio" name="${heading.type}" value="Grandchildren" <c:if test="${heading.type== 'grandchildren'}">CHECKED</c:if>/> Value
                                    </c:if>



                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </form>
                <!-- Headings List -->
         </main>

        <!-- tabs -->
        <script type="text/javascript">
            $(document).ready(function(){
                $('.tabs').tabs();
            });
        </script>

        <%@ include file="../includes/admin_footer.jsp" %>
    </title>
</head>
<body>

</body>
</html>
