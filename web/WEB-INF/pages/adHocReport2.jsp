<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Azquo Adhoc Report"/>
<%@ include file="../includes/admin_header2.jsp" %>
<script type="text/javascript">
    function createJson() {
        debugger;
        var json = '{"headings":["'
        var headingNo = 0;
        while (headingNo < 10 && document.getElementById("heading" + headingNo).value > "") {

            var headingNameId = document.getElementById("heading" + headingNo + "id").value;
            var headingType = document.getElementById("heading" + headingNo + "select").value;
            json += '{"id":"' + headingNameId + '","type":"' + headingType + '"},';
            headingNo++;
        }
        json = json.substring(0, json.length - 1) + "]}";
        document.getElementById("reportid").value = json;
        return false;
    }


    function chooseHeading(heading) {
        var headingVal = document.getElementById(heading).value;
        //var w = $['inspectOverlay']("Inspect").tab("/api/Jstree?op=new&database=" + document.getElementById("database").value, "inspect");
        var w = window.open("/api/Jstree?op=new&mode=choosename&database=" + document.getElementById("database").value, "Choose a name", "height=400, width=600, left=100,menubar=no, status=no,titlebar=no");
        w.onbeforeunload = function () {
            var itemChosen = localStorage.getItem("itemsChosen");
            var colonPos = itemChosen.indexOf(":");
            document.getElementById(heading).value = itemChosen.substring(colonPos + 1);
            document.getElementById(heading + "id").value = itemChosen.substring(0, colonPos);
        }
        return false;
    }


</script>
<div class="box">
    Ad-hoc Report
    <div class="has-text-danger">${error}</div>
    <form action="/api/Online?opcode=adhocreport" method="post">
        <input type="hidden" name="reportid" value="">
        <div>
            <table class="table">
                <tr>
                    <td>
                        <label class="label">Database</label>
                        <div class="select is-small">

                            <select name="database" id="database">
                                <option value="">None</option>
                                <c:forEach items="${databases}" var="database">
                                    <option value="${database}" <c:if
                                            test="${database == reportDatabase}"> selected </c:if>>${database}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </td>
                    <td><label class="label">Report Name</label> <input class="input is-small" name="reportname" id="reportname"
                                                                         value="${reportname}"/></td>
                    <td>
                        <label class="label">&nbsp;</label>
                        <input type="submit" onClick="CreateJson()" value="Create Report" class="button is-small"/>
                    </td>
                </tr>
            </table>
        </div>
        <!-- Headings list -->
        <table class="table is-striped is-fullwidth">
            <thead>
            <tr>
                <th>Heading</th>
                <th>Type</th>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${headings}" var="heading">
                <tr>
                    <td>
                        <input type="text" name="${heading.id}" id="${heading.id}" value="${heading.name}"/>
                        <input type="hidden" name="${heading.id}id" id="${heading.id}id" value="">
                        <a onclick="chooseHeading('${heading.id}')" class="button is-small" title="Choose ${heading.id}"><span
                                class="fa fa-edit" title="Unload"></span></a></td>
                    <td>
                        <div class="select is-small">
                            <select name="${heading.id}select" id="${heading.id}select">
                                <option value="name" <c:if test="${heading.type=='name'}">selected</c:if>>name</option>
                                <option value="value" <c:if test="${heading.type=='value'}">selected</c:if>>value
                                </option>
                                <option value="children" <c:if test="${heading.type=='children'}">selected</c:if>>
                                    children
                                </option>
                                <option value="grandchildren"
                                        <c:if test="${heading.type=='grandchildren'}">selected</c:if>>
                                    grandchildren
                                </option>
                                <option value="detailed" <c:if test="${heading.type=='detailed'}">selected</c:if>>
                                    detailed
                                </option>
                            </select>
                        </div>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </form>
    <!-- Headings List -->
</div>
<%@ include file="../includes/admin_footer.jsp" %>
