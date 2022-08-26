<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Wizard"/>
<%@ include file="../includes/new_header.jsp" %>


<div class="az-content">
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
                        <div id="dataparentline" class="az-alert" style="display:none">
                            For this stage you need to specify a 'parent' name for the data you select
                            <input type="text" id="dataparent" aria-expanded="false" tabindex="0"
                                   aria-labelledby="headlessui-combobox-label-:r14:">


                        </div>
                        <div class="az-table" id="fieldtable">
                        </div>
                    </div>
                    <div class="az-import-wizard-pagination">
                        <div>
                            <button class="az-wizard-button-back" onClick="loadLastStage()">Back
                            </button
                            >
                            <button class="az-wizard-button-next" onClick="loadNextStage()">Next</button>
                            <form method="post" id="import" action="/api/ImportWizard">
                                <input type="hidden" name="submit" value="import"/>
                                <button style="display:none" id="importnow" class="az-wizard-button-next"
                                        onClick="submit()">Import now!
                                </button>
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
    changed("", "");
    var fieldcols = [];
    var itemTemplate = "";


    function loadNextStage() {
        nextStage = stage + 1;
        changed(null, null);
    }

    function loadLastStage() {
        nextStage = stage - 1;
        changed(null, null);
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

        document.getElementById("dataparentline").style.display = "none";
        document.getElementById("suggestions").innerHTML = "";
        document.getElementById("suggestionDiv").style.display = "none";
        let params = "op=importwizard&datachosen=&sessionid=${pageContext.session.id}";
        params += "&fields=" + encodeURIComponent(getFieldInfoAsString());
        if (chosenId != null) {
            params += "&chosenfield=" + encodeURIComponent(chosenId) + "&chosenvalue=" + encodeURIComponent(selection);
        }
        params += "&stage=" + stage + "&nextstage=" + nextStage;
        if (stage == 3) {
            params += "&dataparent=" + document.getElementById("dataparent").value;
        }

        let data = await azquoSend(params);
        let json = await data.json();
        fields = [];
        itemTemplate = document.getElementById("stage-template").innerHTML;
        fillHTML(json, "stage");
        itemTemplate = "<tr>";
        var fieldcount = 0;
        for (var i = 0; i < fieldcols.length; i++) {
            itemTemplate += "<td>VALUE" + (i + 1) + "</td>";
        }
        itemTemplate += "</tr>";
        fillHTML(json, "field");
        stage = nextStage;
        if (stage == 3) {
            document.getElementById("dataparentline").style.display = "block";

        }
        if (document.getElementById("suggestions").innerHTML > "") {
            document.getElementById("suggestionDiv").style.display = "block";
        }
        if (stage == 4) {
            document.getElementById("importnow").style.display = "block"
        }
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

                if (itemFact == "imported name") {
                    fields.push(itemValue);
                }
                console.log(itemFact + ":" + itemValue);
                if (!isArray(itemValue)) {
                    if (itemValue == "tick") {
                        itemHTML = itemHTML.replace(itemFact.toUpperCase(), document.getElementById("az-tick").innerHTML);
                    } else {
                        var fieldpos = elementOf(fieldcols, itemFact);
                        if (fieldpos > 0) {
                            var replacement = itemValue;
                            if (itemFact == "textEntry") {
                                replacement = "<input type=\"text\" value=\"" + replacement + "\">";
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
                            headingHTML += "<th>" + heading + "</th>"
                        }
                        document.getElementById("fieldtable").innerHTML = headingHTML + "</tr></thead><tbody id=\"fields\"></tbody></table>";
                    }
                    if (itemFact == "dataParent" && itemValue > "") {
                        document.getElementById("dataparent").value = itemValue;
                    }
                    if (itemValue > "") {
                        rangeElement = document.getElementById(itemFact);
                        if (rangeElement != null) {
                            var itemVal = decodeURIComponent(itemValue);
                            rangeElement.innerHTML = decodeURIComponent(itemVal);
                        }
                    }

                } else {
                    var onchange = "";
                    var selectHTML = "";
                    if (itemFact == "valuesFound") {
                        onchange = " onchange=selectionChanged(this)";
                    }
                    if (nextStage == 4) {
                        onchange = "onchange=changed(null,null)";
                    }
                    if (itemValue.length == 1) {
                        selectHTML = itemValue[0];
                    } else {

                        selectHTML = "<select id=\"" + oneItem.fieldName + "-" + itemFact + "\"" + onchange + ">";
                        var selectCount = 0;
                        for (var selectItem of itemValue) {
                            if (selectCount++ > 100) break;
                            var selectOption = selectItem;
                            var selected = "";
                            if (selectItem.endsWith(" selected")) {
                                selectOption = selectItem.substring(0, selectItem.length - 9);
                                selected = " selected";
                            }
                            selectHTML += "\n<option value = \"" + selectOption + "\"" + selected + ">" + selectOption + "<\option>";
                        }
                    }
                    var fieldpos = elementOf(fieldcols, itemFact);
                    if (fieldpos > 0) {
                        itemHTML = itemHTML.replaceAll("VALUE" + fieldpos, selectHTML);
                    } else {
                        itemHTML = itemHTML.replaceAll(itemFact.toUpperCase(), selectHTML);
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

    function submit() {
        changed(null, null);//to store away any changes
        document.getElementById("import").submit();
    }

</script>


<%@ include file="../includes/new_footer.jsp" %>
