<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit Import Schedule"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-importSchedules-view">
            <div class="az-section-heading">
                <h3>Edit Import Schedule</h3>
            </div>
            <div class="az-table">
                <div class="az-alert-danger" id="error">${error}</div>
                <form id="edit" action="/api/ManageImportSchedules" method="post"  enctype="multipart/form-data">
                    <input type="hidden" name="editId" value="${id}"/>
                    <input type="hidden" name="newdesign" value="true"/>
                    <!-- no business id -->

                    <table>
                        <tbody>
                        <tr>
                            <td>
                                Name
                            </td>
                            <td>
                                <input name="name" id="name" type="text" value="${name}">
                            </td>
                        </tr>
                        <tr>
                            <td>Database</td>
                            <td>
                                <select id="databaseid" name="databaseid" onChange="submitForm()" >
                                    <option value="0">none</option>
                                    <c:forEach items="${databases}" var="d">
                                        <option value="${d.id}"<c:if
                                                test="${d.id == databaseid}"> selected</c:if>>${d.name}</option>
                                    </c:forEach>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td>Count</td>
                            <td>
                                <input name="count" id="count" type="text" value="${count}">
                            </td>
                        </tr>
                        <tr>
                            <td>
                                Frequency
                            </td>
                            <td>
                                <select name="frequency">
                                    <c:forEach items="${periods}" var="period">
                                        <option value="${period}"<c:if
                                                test="${frequency == period}"> selected</c:if>>${period}</option>
                                    </c:forEach>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                Next Scheduled Date
                            </td>
                            <td>
                                <input name="nextdate" id="nextdate" type="text"  value="${nextdate}">
                            </td>
                        </tr>
                        <tr>

                            <td>Connector</td>
                            <td>
                                <select id="connectorid" name="connectorid" onChange="connectorChanged()">
                                    <option value="0">from file</option>
                                    <c:forEach items="${connectors}" var="c">
                                        <option value="${c.id}"<c:if
                                                test="${c.id == connectorid}"> selected</c:if>>${c.name}</option>
                                    </c:forEach>
                                </select>
                            </td>
                        </tr>
                        <tr id="sqlline">
                            <td>
                                SQL
                            </td>

                            <td>
                                <input name="sql" id="sql" type="text" value="${sql}"/>
                            </td>
                        </tr>
                        <tr id="fileuploadline">
                            <td>Regex to identify:
                            </td>

                            <td>  <input name="regex" id="regex" type="text" value="${regex}">
                            </td>
                        </tr>

                        <tr>
                            <td>Import Template</td>
                            <td>
                                <select id="template" name="template" onChange="templateChanged()">
                                    <c:forEach items="${templates}" var="templatechoice">
                                        <option value="${templatechoice}"<c:if
                                                test="${templatechoice == template}"> selected</c:if>>${templatechoice}</option>
                                    </c:forEach>
                                </select>

                            </td>
                            <td id="newtemplate1">
                                Name <input name="newtemplate" id="newtemplate" value="${newtemplate}" type="text"/>
                            </td>
                        </tr>
                        <tr>
                            <td>Output Connector</td>
                            <td>
                                <select id="output" name="outputconnectorid">
                                    <option value="0">none</option>
                                    <c:forEach items="${outputconnectors}" var="oc">
                                        <option value="${oc.id}"<c:if
                                                test="${oc.id == outputconnectorid}"> selected</c:if>>${oc.name}</option>
                                    </c:forEach>
                                </select>

                            </td>
                        </tr>
                        <tr>
                            <td>Notes</td>
                            <td><input name="notes" id="notes" type="text"
                                       value="${notes}"></td>
                        </tr>
                    </table>
                    <nav>
                        <div>
                            <input type="hidden" name="action" id="action"/>
                            <table>
                                <tr>
                                    <td><button type="button" onClick="saveButton()" class="az-wizard-button-next">Save </button> </td>
                                    <td id="fileupload"> File to test: <input type="file", id="uploadFile" name="uploadFile"/></td>
                                    <td id="testbutton"> <button  type="button" onClick="testButton()" class="az-wizard-button-next">Test import</button> </td>
                                </tr>
                            </table>
                        </div>
                        <div></div>
                    </nav>
                </form>
            </div>
        </div>
    </main>
</div>

<script>

    window.addEventListener('keydown',function(e){if(e.keyIdentifier=='U+000A'||e.keyIdentifier=='Enter'||e.keyCode==13){if(e.target.nodeName=='INPUT'&&e.target.type=='text'){e.preventDefault();return false;}}},true);
    templateChanged();
    connectorChanged();

    function testButton() {
        if (testFormFilled) {
            document.getElementById("action").value = "test";
            if (document.getElementById("connectorid").value == "0" && document.getElementById("uploadFile").files.length == 0) {
                document.getElementById("error").innerHTML = "Please select a file to test";
                return;
            }
        }
        submitForm();
    }



    function testFormFilled(){
        var error = "";
        if (document.getElementById("name").value == ""){
            error +="name needed;";
        }
        if (document.getElementById("nextdate").value==""){
            error += "next scheduled date needed;"
        }
        if (document.getElementById("connectorid").value==0){
            if (document.getElementById("regex").value==""){
                error += "regex needed";
            }
        }else{
            if (document.getElementById("sql").value=="" ){
                error += "SQL needed";
            }
        }
        if (document.getElementById("template").value == "NEW TEMPLATE" && document.getElementById("newtemplate").value == ""){
            error += "template name needed;"
        }
        if (document.getElementById("databaseid").value == "0" && document.getElementById("output").value == "0"){
            error += "either database or output connector needed;"
        }
        document.getElementById("testbutton").style.display="none";
        document.getElementById("fileupload").style.display = "none";
        if (error>""){
            document.getElementById("error").innerHTML = error;
            return false;
        }else{
            document.getElementById("testbutton").style.display="block";
            if (document.getElementById("connectorid").value == "0"){
                document.getElementById("fileupload").style.display = "table-cell";
            }

        }
        return true;

    }

    function saveButton() {
        if(testFormFilled()) {
            document.getElementById("action").value = "save";
            submitForm();
        }
    }

    function submitForm(){
        var template = document.getElementById("template").value;
        if (template!="NEW TEMPLATE"){
            document.getElementById("newtemplate").value = template;
        }
        document.getElementById("edit").submit();
    }


    function templateChanged(){
        var template = document.getElementById("template").value;
        if (template=="NEW TEMPLATE"){
            document.getElementById("newtemplate1").style.display="block";
        }else{
            document.getElementById("newtemplate1").style.display="none";

        }

    }

    function connectorChanged(){
        var connectorid = document.getElementById("connectorid").value;
        if (connectorid == "0"){
            document.getElementById("sqlline").style.display = "none";
            document.getElementById("fileuploadline").style.display="";
        }else{
            document.getElementById("sqlline").style.display = "";
            document.getElementById("fileuploadline").style.display="none";

        }
        testFormFilled();

    }


</script>



<%@ include file="../includes/new_footer.jsp" %>
