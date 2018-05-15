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
             function createJson() {
                 debugger;
                 var json = '{"headings":["'
                 var headingNo = 0;
                while (document.getElementById("heading" + headingNo).value > "") {

                    var headingNameId = document.getElementById("heading" + headingNo + "id").value;
                    var headingType = document.getElementById("heading" + headingNo + "select").value;
                    json += '{"id":"' + headingNameId + '","type":"' + headingType + '"},';
                    headingNo++;
                }
                json =json.substring(0,json.length-1) + "]}";
                alert("json: " + json);
                return false;
            }


            function chooseHeading(heading){
                var headingVal = document.getElementById(heading).value;
                //var w = $['inspectOverlay']("Inspect").tab("/api/Jstree?op=new&database=" + document.getElementById("database").value, "inspect");
                var w = window.open("/api/Jstree?op=new&mode=choosename&database=" + document.getElementById("database").value, "Choose a name","height=400, width=600, left=100,menubar=no, status=no,titlebar=no");
                w.onbeforeunload = function() {
                    var itemChosen = localStorage.getItem("itemsChosen");
                      var colonPos = itemChosen.indexOf(":");
                    document.getElementById(heading).value = itemChosen.substring(colonPos + 1);
                    document.getElementById(heading + "id").value = itemChosen.substring(0,colonPos);
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
                                    <input type="text" name="${heading.id}" id="${heading.id}" value="${heading.name}"/>
                                    <input type="hidden" name="${heading.id}id" id="${heading.id}id" value="">
                                    <a onclick="chooseHeading('${heading.id}')" class="button" title="Choose ${heading.id}"><span class="fa fa-edit" title="Unload"></span></a></td>


                                </td>
                                <td>
                                    <select name="${heading.id}select" id="${heading.id}select">
                                        <option value="name" <c:if test="${heading.type=='name'}">selected</c:if>>name</option>
                                        <option value="value" <c:if test="${heading.type=='value'}">selected</c:if>>value</option>
                                        <option value="children" <c:if test="${heading.type=='children'}">selected</c:if>>children</option>
                                        <option value="grandchildren" <c:if test="${heading.type=='grandchildren'}">selected</c:if>>grandchildren</option>
                                        <option value="detailed" <c:if test="${heading.type=='detailed'}">selected</c:if>>detailed</option>
                                    </select>




                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                     <a href="#" onclick="createJson()">Create report" </a>
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
