<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Setup"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<%@ include file="../includes/new_header.jsp" %>



<div class="az-content" id="setup">
    <div class="az-imports-view">
        <div class="az-section-heading">
            <h3>Import Setup  - file to upload: ${filename}</h3>
        </div>

        <div class="az-table">
            <table>
                <tr>
                    <th>Template name</th>
                    <th>File identification regex</th>
                </tr>
                <tr>
                    <td>
                        <select id="template"  onChange="templateChanged()" >
                            <c:forEach items="${templates}" var="template">
                                <option value="${template}">${template}</option>
                            </c:forEach>
                        </select>
                    </td>
                    <td><input type="text" id="regexShown" onchange="testRegex()"/></td>
                </tr>
                <tr>
                    <td>
                        <input type="text" id="newtemplate" name="newtemplate" value="${newtemplate}" />
                    </td>
                    <td><span id="regexMessage"></span></td>
                </tr>
            </table>

        </div>
        <form method="post" id="import" action="/api/ImportWizard">
            <input type="hidden" name="template" id="hiddenTemplate"/>
            <input type="hidden" name="regex" id="hiddenRegex" />
            <div class="az-import-wizard-pagination" id="submitButton">
                <div>
                    <button class="az-wizard-button-next" onClick="formSubmit()" class="az-wizard-button-next">Next
                    </button>
                </div>
            </div>
        </form>

    </div>
</div>

<script>
    var filename = ${filename};
    filename = filename.toLowerCase();
    var regex =[];
    var templates=[];
    for (var template of ${templates}){
        templates.push("" + template);
    }
    for (var regexTemplate of ${templateRegex}){
        regex.push("" + regexTemplate)
    }
    findMatch()
    testRegex();

    function formSubmit() {
        document.getElementById("hiddenTemplate").value = getTemplateName();
        document.getElementById("hiddenRegex").value = document.getElementById("regexShown").value;
        document.forms("import").submit();

    }

    function getTemplateName(){
        var selectedIndex = document.getElementById("template").selectedIndex;
        var template = templates[selectedIndex];
        if(template!="NEW TEMPLATE"){
            return template;
        }else{
            return document.getElementById("newtemplate").value;
        }


    }

    function templateChanged(){
        var templateIndex =     document.getElementById("template").selectedIndex;
        var template = templates[templateIndex];
        if(template=="NEW TEMPLATE"){
            document.getElementById("newtemplate").type="text";
        }else{
            document.getElementById("newtemplate").type="hidden";
        }
        document.getElementById("regexShown").value = regex[templateIndex];
        testRegex();




    }

    function findMatch(){

        for (var i=0;i<regex.length;i++){
            if (filename.match(regex[i])){
                document.getElementById('template').value="'" + templates[i] + "'";
                document.getElementById("regexShown").value = regex[i];
                return;
            }
        }
        var spacePos = filename.indexOf(" ");
        if (spacePos < 0){
            spacePos = filename.indexOf(".");
        }
        document.getElementById("regexShown").value = filename.substring(0,spacePos);

        document.getElementById("newtemplate").value = filename.substring(0,spacePos);
        testRegex();
    }

    function testRegex(){
        var regex1 = document.getElementById("regexShown").value;
        if (regex1==""){
            ok=null;
        }else{
            var ok = filename.match(regex1);
        }
        if (getTemplateName()==""){
            ok = null;
        }

        if (ok!=null){
            document.getElementById("regexMessage").innerHTML = "regex matches";
            document.getElementById("submitButton").style.display = "block";
        }else{
            document.getElementById("regexMessage").innerHTML = "regex does not match";
            document.getElementById("submitButton").style.display = "none";

        }

    }

</script>



<%@ include file="../includes/new_footer.jsp" %>
