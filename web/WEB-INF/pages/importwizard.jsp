<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Wizard"/>
<c:set var="compact" scope="request" value="compact"/>
<c:set var="requirezss" scope="request" value="true"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<script type="text/javascript">
    // this should be below the header??
    // new post ajax based on Keikai
    var cancelledTemplate="";

    function postAjax(action) {
        //use window.fetch() API
        fetch("/api/ZKSpreadsheetCommand", {
            headers: {
                'Content-Type': 'application/json',
            },
            method: 'POST',
            credentials: 'include',
            // 'myzss' is the id specified on kkjsp tag
            body: JSON.stringify(kkjsp.prepare('myzss', {action: action})) // preparing Keikai's request data
        })
            .then(function (response) {
                return response.json();
            })
            .then(kkjsp.process) // render change generated by SmartUpdateBridge
            .then(handleAjaxResult); //optional post-processing
    }

    // the chosen tree will need to push things through
    function postNameIdForChosenTree(nameId) {
        //use window.fetch() API
        fetch("/api/ZKSpreadsheetCommand", {
            headers: {
                'Content-Type': 'application/json',
            },
            method: 'POST',
            credentials: 'include',
            // 'myzss' is the id specified on kkjsp tag
            body: JSON.stringify(kkjsp.prepare('myzss', {
                action: "nameIdForChosenTree",
                nameIdForChosenTree: String(nameId)
            })) // preparing Keikai's request data, it wants strings
        })
            .then(function (response) {
                return response.json();
            })
            .then(kkjsp.process) // render change generated by SmartUpdateBridge
            .then(handleAjaxResult); //optional post-processing
    }

    //the method to handle ajax result from your servlet
    function handleAjaxResult(result) {
//use your way to hanlde you ajax message or error
        if (result.message) {
            alert(result.message);
        }
//use your way handle your ajax action result
        if (result.action == "check" && result.valid) {
            if (result.form) {
//create a form dynamically to submit the form data
                var field, form = jq("<form action='submitted.jsp' method='post'/>").appendTo('body');
                for (var nm in result.form) {
                    field = jq("<input type='hidden' name='" + nm + "' />").appendTo(form);
                    field.val(result.form[nm]);
                }
                form.submit();
            }
        }
    }

    function inspectDatabase() {
        // can be passed database
        $.inspectOverlay("Inspect Azquo").tab("/api/Jstree?op=new", "DB :  ${databaseName}");
        return false;
        // window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }

    function auditDatabase() {
        // can be passed database
        window.open("/api/AuditDatabase", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=100, left=100, width=1600, height=1000")
    }


    // a more advanced choice option as opposed to the dropdown
    function chosenTree(query) {
        // can be passed database
        $.inspectOverlay("Inspect").tab("/api/Jstree?op=new&query=" + encodeURIComponent(query), "${databaseName}");
        return false;
        // window.open("/api/Jstree?op=new", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
    }


    function uploadFile() {
        var el = $('<div class="overlay"><iframe src="/api/Online?opcode=upload" width="100%" height="100%" frameborder="0" scrolling="auto"></iframe></div>').hide().appendTo('body');

        el.dialog({
            modal: 'true',
            width: 'auto',
            title: 'Upload File',
            close: refreshReport
        });

        el.show();
        //window.open("/api/Online?opcode=upload", "_blank", "toolbar=no, status=no,scrollbars=no, resizable=no, top=150, left=200, width=300, height=300")
    }

    // could inline?
    function refreshReport() {
        location.reload();
    }


    function mouseIn(elementChosen, e) {
        if (e == null) return false;
        var IE = document.all ? true : false;
        if (IE) { // grab the x-y pos.s if browser is IE
            var mouseX = e.clientX + document.body.scrollLeft
            var mouseY = e.clientY + document.body.scrollTop
        } else {  // grab the x-y pos.s if browser is NS
            mouseX = e.pageX
            mouseY = e.pageY
        }

        var el = elementChosen;
        for (var lx = 0, ly = 0;
             el != null;
             lx += el.offsetLeft - el.scrollLeft, ly += el.offsetTop - el.scrollTop, el = el.offsetParent) ;
        if (mouseX >= lx && mouseY >= ly && mouseX <= lx + elementChosen.offsetWidth && mouseY <= ly + elementChosen.offsetHeight) {
            return true;
        }
        return false;
    }

    /*

    var skipSetting = 0;
    var skipMarker = 0;
        // how to stop this hammering? I reckon add a second every time between checks if the data hasn't changed.
    function updateStatus(){
        if (window.skipMarker <= 0){
            jq.post("/api/SpreadsheetStatus?action=log", function(data){
                var objDiv = document.getElementById("serverStatus");
                if (objDiv.innerHTML != data){ // it was updated
                    objDiv.innerHTML = data;
                    objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
                    objDiv.scrollTop = objDiv.scrollHeight;
                    // assume there could be more stuff!
                    window.skipSetting = 0;
                    window.skipMarker = 0;
                } else {
                    objDiv.style.backgroundColor = 'white';
                    if (window.skipSetting == 0){
                        window.skipSetting = 1;
                    } else {
                        window.skipSetting *= 2;
                    }
    //                alert("same data, new skip setting : " + window.skipSetting);
                    window.skipMarker = window.skipSetting;
                }
            });
        } else {
            window.skipMarker--;
        }
    }
    ma
    setInterval(function(){ updateStatus(); }, 1000);*/

</script>
<%@ include file="../includes/new_header.jsp" %>


<div class="az-content" id="sampleData">
    <div id="dataTable"></div>
    <div class="az-import-wizard-pagination">
        <div>
            <button id="hideDataButton" class="az-wizard-button-back"
                    onClick="showSetup()">Revert
            </button>
        </div>
    </div>

</div>
<div class="az-content" id="setup">
    <div class="az-topbar">
        <button>
            <svg
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke-width="2"
                    stroke="currentColor"
                    aria-hidden="true"
            >
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h8m-8 6h16"></path>
            </svg>
        </button>
        <div class="az-searchbar">
            <form action="#">
                <div>
                    <div>
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
                                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            ></path>
                        </svg>
                    </div>
                    <input placeholder="Search" type="text" value=""/>
                </div>
            </form>
        </div>
    </div>
    <main>
        <div class="az-import-wizard-view">
            <div class="az-import-wizard">
                <nav class="az-import-wizard-progress">
                    <ol id="stages">
                    </ol>
                </nav>
                <div class="az-import-wizard-main">
                    <div class="az-section-heading">
                        <h3 id="title"></h3>
                        <div class="az-section-controls">
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
                        </div>
                    </div>
                    <div class="az-section-body">
                        <div class="az-alert az-alert-warning">
                            <div>
                                <div></div>
                                <div></div>
                                <div>
                                    <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                            aria-hidden="true"
                                    >
                                        <path
                                                fill-rule="evenodd"
                                                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                                                clip-rule="evenodd"
                                        ></path>
                                    </svg>
                                </div>
                                <div></div>
                                <div>
                                    <div id="instructions">
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div id="suggestionDiv" class="az-alert az-alert-info">
                            <div>
                                <div>
                                    <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                            aria-hidden="true"
                                    >
                                        <path
                                                fill-rule="evenodd"
                                                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z"
                                                clip-rule="evenodd"
                                        ></path>
                                    </svg>
                                </div>
                                <div></div>
                                <div></div>
                                <div></div>
                                <div>
                                    <div id="suggestions"></div>
                                </div>
                            </div>
                        </div>
                        <div class="az-table" id="fieldtable">
                        </div>
                    </div>
                    <div class="az-import-wizard-pagination" style="position:fixed;bottom:0px">
                        <div id="error" class="az-alert az-alert-info">${error}</div>
                        <div>
                            <button class="az-wizard-button-back" onClick="loadLastStage()">Back </button>
                            <button class="az-wizard-button-next" id="nextButton" onClick="loadNextStage()">Next </button>
                            <button id="showdataButton" class="az-wizard-button-back"  onClick="showData()">Show Sample Output </button>
                            <form method="post" id="import" action="/api/ImportWizard">
                                <input type="hidden" name="importbutton" value="import"/>
                                <button style="display:none" id="importnow" class="az-wizard-button-next"  onClick="formSubmit()">Import now!</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </main>
</div>
<div id="templates" style="display:none">
    <span id="az-tick">
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
                                                                                d="M5 13l4 4L19 7"
                                                                        ></path></svg>
    </span>
    <div id="stage-template">
        <li>
            <div class="STAGECLASS">
                <div>
                                                    <span></span
                                                    ><span
                ><span
                ><span><span>STAGENUMBER</span></span></span
                ><span
                ><span>STAGENAME</span
                ><span>STAGECOMMENT</span></span
                ></span
                >
                </div>
                <div class="step-seperator">
                    <svg viewBox="0 0 12 82" fill="none" preserveAspectRatio="none">
                        <path
                                d="M0.5 0V31L10.5 41L0.5 51V82"
                                stroke="currentcolor"
                                vector-effect="non-scaling-stroke"
                        ></path>
                    </svg>
                </div>
            </div>
        </li>

    </div>
</div>

<div id="preprocessor">
    <div class="az-report">
        <kkjsp:spreadsheet id="myzss"
                           bookProvider="com.azquo.spreadsheet.zk.BookProviderForJSP"
                           height="95%"
                           maxVisibleRows="100" maxVisibleColumns="100"
                           showSheetbar="true" showToolbar="false" showFormulabar="true" showContextMenu="true"/>

    </div>
    <div id="spreadsheetButtons" class="az-import-wizard-pagination" style="position:fixed;bottom:0px">
        <div>
            <button class="az-wizard-button-back"
                    onClick="savePreprocessorChanges()">Standard view
            </button>
        </div>
    </div>

</div>


<script>


    /*

rowHeadingsSource: string[][],
columnHeadingsSource: string[][],
columnHeadings: string[][],
rowHeadings: string[][],
context: string[][],
data: string[][],
highlight: boolean[][],
comments: string[][]
options: string,
lockresult: string
     */

    var stage = 1;
    var nextStage = 1
    var fields = [];
    var hidden = [];
    var fieldcols = [];
    var itemTemplate = "";
    var json = null;
    var spreadsheetShown = false;
    changed(null,null);


    function loadNextStage() {
        document.getElementById("error").innerHTML = "";
        if (stage == 0) {//
            changed();
            showSpreadsheet();
        } else {
            nextStage = stage + 1;
            changed(null, null);
        }
    }

    function loadLastStage() {
        document.getElementById("error").innerHTML = "";
        nextStage = stage - 1;
        changed(null, null);
    }


    function checkDeleteTemplate(){
        try{
            var templateCell = document.getElementById("template-templatename");
            if (templateCell.selectedIndex>0){
                document.getElementById("deletetemplate").style.display = "block";
                return;
            }
        }catch(e){

        }
        document.getElementById("deletetemplate").style.display = "none";
    }

    async function azquoSend(params) {
        var host = sessionStorage.getItem("host");
        try {
            let data = await fetch('/api/Excel/', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: params
            });
            //console.log(data)
            return data;
        } catch (e) {
            console.log(e)
        }
    }

    function getFieldInfoAsString() {
        var fieldInfo = "";
        var fieldTable = document.getElementById("fields");
        if (fieldTable == null) {
            return "";
        }
        for (i = 0; i < fieldTable.rows.length; i++) {

            // GET THE CELLS COLLECTION OF THE CURRENT ROW.
            var objCells = fieldTable.rows.item(i).cells;

            // LOOP THROUGH EACH CELL OF THE CURENT ROW TO READ CELL VALUES.
            var tab = "";
            for (var j = 0; j < objCells.length; j++) {
                var fieldVal = "";
                var cell = objCells.item(j).lastChild;
                var isOption = false;
                if (cell != null) {
                    try {
                        if (cell.options != null) {
                            fieldVal = cell.options[cell.selectedIndex].text;
                            isOption = true;
                        }
                    } catch (error) {

                    }
                    if (!isOption) {
                        if (cell.tagName == "INPUT") {
                            if (cell.type == "checkbox") {
                                fieldVal = cell.checked;
                            } else {
                                fieldVal = cell.value;
                            }
                        } else {
                            fieldVal += objCells.item(j).innerText;

                        }
                    }
                }
                fieldInfo += tab + fieldVal;
                tab = "\t";
            }
            fieldInfo += "\n";     // ADD A BREAK (TAG).
        }
        return fieldInfo;
    }

    function selectionChanged(sel) {
        var chosenValue = sel.options[sel.selectedIndex].text;
        var chosenField = sel.id.substring(0, sel.id.indexOf("-valuesFound"));
        changed(chosenField, chosenValue);

    }

    async function changed(chosenId, selection) {
        /*
       the message to Azquo tells it what values are currently selected.  The return will be a json array of fieldname,fieldvalues[]  (e.g changing the data value will set up rows, columns and templates)

        */
        showSetup();
        try {
            postAjax("submit");
        }catch(e){

        }

        let params = "op=importwizard&datachosen=&sessionid=${pageContext.session.id}";
        params += "&fields=" + encodeURIComponent(getFieldInfoAsString());
        if (chosenId != null) {
            params += "&chosenfield=" + encodeURIComponent(chosenId) + "&chosenvalue=" + encodeURIComponent(selection);
        }
        params += "&stage=" + stage + "&nextstage=" + nextStage;
        var templateCell = document.getElementById("template-templatename");
        if (templateCell!=null){
            params +="&template=" + templateCell.options[templateCell.selectedIndex].text + "&canceltemplate=" + cancelledTemplate;
        }
        let data = await azquoSend(params);
        json = await data.json();
        if(json.error > ""){
            var errorDiv = document.getElementById("error");
            errorDiv.innerText = json.error;
            return;
        }
        try{
            postAjax("reset");//fill in the new formulae...
        }catch(e){

        }
        if (nextStage == 7) {
            showSpreadsheet();
            return;
        }
        fields = [];
        itemTemplate = document.getElementById("stage-template").innerHTML;
        if (json.stage.length == 1) {
            stage = 0;//MATCHSTAGE
            nextStage = 0;
        }
        fillHTML(json, "stage");
        itemTemplate = "<tr>";
        var fieldcount = 0;
        for (var i = 0; i < fieldcols.length; i++) {
            itemTemplate += "<td>VALUE" + (i + 1) + "</td>";
        }
        itemTemplate += "</tr>";
        fillHTML(json, "field");
        stage = nextStage;
        if (stage==0){
            //document.getElementById("template").style.display = "block";
            document.getElementById("nextButton").innerHTML = "Spreadsheet View";
        }
        if (stage==0 || stage==4){
            document.getElementById("importnow").style.display = "block";
        }
        if (document.getElementById("suggestions").innerHTML > "") {
            document.getElementById("suggestionDiv").style.display = "block";
        }
        if (stage == 4) {
            document.getElementById("showdataButton").style.display = "none";

        }else{
            document.getElementById("showdataButton").style.display = "block";

        }
        checkDeleteTemplate();
    }

    function isArray(what) {
        return Object.prototype.toString.call(what) === '[object Array]';
    }


    function fillHTML(json, jsonItem) {

        var itemsHTML = "";

        for (var jsonArrayItem = 0; jsonArrayItem < json[jsonItem].length; jsonArrayItem++) {
            var oneItem = json[jsonItem][jsonArrayItem];
            var itemHTML = itemTemplate;
            for (var itemFact in oneItem) {
                var itemValue = oneItem[itemFact];
                if (itemValue != null) {


                    if (itemFact == "imported name") {
                        fields.push(itemValue);
                    }
                    //console.log(itemFact + ":" + itemValue);
                    if (!isArray(itemValue)) {
                        if (itemValue == "tick") {
                            itemHTML = itemHTML.replace(itemFact.toUpperCase(), document.getElementById("az-tick").innerHTML);
                        } else {
                            var fieldpos = elementOf(fieldcols, itemFact);
                            if (fieldpos > 0) {
                                var replacement = itemValue;
                                if (itemFact == "textEntry") {
                                    var onchange = "";
                                    if (nextStage == 5) {
                                        onchange = ' onChange="changed(null,null)"';
                                    }
                                    replacement = '<input type="text" value=\'' + replacement + '\'' + onchange + '>';
                                }

                                itemHTML = itemHTML.replaceAll("VALUE" + fieldpos, replacement);
                            } else {
                                itemHTML = itemHTML.replaceAll(itemFact.toUpperCase(), itemValue);
                            }
                        }
                        if (itemFact == "fields" && itemValue > "") {
                            fieldcols = itemValue.split(",");
                        }
                        if (itemFact == "fieldHeadings" && itemValue > "") {
                            var headings = itemValue.split(",");
                            var headingHTML = "<table><thead><tr>";
                            for (var heading of headings) {
                                if (heading.toUpperCase().trim() != "MATCHED NAME"){
                                    headingHTML += "<th style=\"min-width:150px\">" + heading + "</th>"
                                }else{
                                    headingHTML += setChoice("<th style=\"min-width:150px\">" + heading + " - template: TEMPLATENAME</th><th> <button id='deletetemplate' style='display:none' class='az-wizard-button-back' onClick='cancelTemplate()'>Cancel Template</button></th>", "template", "templatename", json.template);

                                }
                            }
                            document.getElementById("fieldtable").innerHTML = headingHTML + "</tr></thead><tbody id=\"fields\"></tbody></table>";
                        }
                        if (itemValue > "") {
                            rangeElement = document.getElementById(itemFact);
                            if (rangeElement != null) {
                                var itemVal = decodeURIComponent(itemValue);
                                rangeElement.innerHTML = decodeURIComponent(itemVal);
                            }
                        }

                    } else {
                        itemHTML = setChoice(itemHTML, oneItem.fieldName, itemFact, itemValue);
                    }
                }
            }
            itemsHTML += itemHTML;
        }
        if (nextStage == 1 && jsonItem == "field") {
            itemsHTML += "<tr><td><input id=\"newfield\" type=\"text\"></td><td>you can add fields here</td><td><input id=\"newname\" type=\"text\"></td></tr>";
        }

        document.getElementById(jsonItem + "s").innerHTML = itemsHTML;

    }

    function setChoice(itemHTML, fieldName,itemFact,itemValue){
        var onchange = "";
        var selectHTML = "";
        if (itemFact == "valuesFound") {
            onchange = " onchange=selectionChanged(this)";
        } else {
            if (nextStage == 0) {//MATCHSTAGE
                onchange = "onchange=changed(null,null)";
            }
        }
        if (nextStage == 4) {
            onchange = "onchange=changed(null,null)";
        }

        if (itemValue.length == 1) {
            selectHTML = itemValue[0];
        } else {

            selectHTML = "<select id=\"" + fieldName + "-" + itemFact + "\"" + onchange + ">";
            var selectCount = 0;
            for (var selectItem of itemValue) {
                if (selectCount++ > 100) break;
                var selectOption = selectItem;
                var selected = "";
                if (selectItem.endsWith(" selected")) {
                    selected = " selected";
                    selectOption = selectItem.substring(0, selectItem.indexOf(" selected"));
                }
                selectHTML += "\n<option value = \"" + selectOption + "\"" + selected + ">" + selectOption + "</option>";
            }
        }
        selectHTML +="</select>";
        var fieldpos = elementOf(fieldcols, itemFact);
        if (fieldpos > 0) {
            itemHTML = itemHTML.replaceAll("VALUE" + fieldpos, selectHTML);
        } else {
            itemHTML = itemHTML.replaceAll(itemFact.toUpperCase(), selectHTML);
        }
        return itemHTML;

    }

    function elementOf(set, element) {
        count = 1;
        for (var testItem of set) {
            if (testItem.trim() == element) {
                return count;
            }
            count++;
        }
        return 0;
    }

    function checkChanged(fieldNo) {
        let val = document.getElementById("check1-" + fieldNo).checked;
        if (val) {
            document.getElementById("check2-" + fieldNo).style.display = "none";
            document.getElementById("check2-" + fieldNo).checked = false;
        } else {
            document.getElementById("check2-" + fieldNo).style.display = "block";
        }
    }

    function formSubmit() {
        changed(null, null);//to store away any changes
        document.getElementById("import").submit();
    }

    function showSpreadsheetData(){
        nextStage = 7;
        changed(null,null);//generate the data
        showData();
    }

    function showData() {
        var dataHTML = "";
        var selectList = json.field;
        for (var line = 0; line < 100; line++) {
            var lineHTML = "<tr>";
            for (var oneField of selectList) {

                if (line == 0) {
                    lineHTML += "<td>" + oneField.fieldName + "</td>";
                } else {
                    var lineValue = "";
                    if (oneField.valuesFound!=null && line < oneField.valuesFound.length) {
                        lineValue = oneField.valuesFound[line - 1];
                    }
                    if (lineValue.endsWith(" selected")) {
                        lineValue = lineValue.substring(0, lineValue.indexOf(" selected"));
                    }
                    lineHTML += "<td>" + lineValue + "</td>"
                }
            }
            dataHTML += lineHTML + "</tr>";
        }
        document.getElementById("dataTable").innerHTML = "<table>" + dataHTML + "</table>";
        document.getElementById("setup").style.display = "none";
        document.getElementById("sampleData").style.display = "block";
    }



    function showSpreadsheet() {
        document.getElementById("preprocessor").style.height = "";
        document.getElementById("setup").style.display = "none";
        spreadsheetShown = true;
        document.getElementById("spreadsheetButtons").style.display = "block";

    }

    async function savePreprocessorChanges(){
        await changed("spreadsheet calculate",null);
    }

    function showSetup() {
        document.getElementById("dataTable").innerHTML = "";
        document.getElementById("sampleData").style.display = "none";
        document.getElementById("setup").style.display = "block";
        document.getElementById("preprocessor").style.height = "1px";
        document.getElementById("suggestions").innerHTML = "";
        document.getElementById("suggestionDiv").style.display = "none";
        document.getElementById("spreadsheetButtons").style.display = "none";

    }

    function cancelTemplate(){
        var templateCell = document.getElementById("template-templatename");
        cancelledTemplate = templateCell.options[templateCell.selectedIndex].text;
        templateCell.selectedIndex = 0;
        changed(null,null);


    }

</script>


<%@ include file="../includes/new_footer.jsp" %>
h