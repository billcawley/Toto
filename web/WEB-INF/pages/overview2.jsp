<%@ include file="../includes/new_header2.jsp" %>
<div class="az-content">
    <div class="az-topbar">
        <button>
            ${icon.search}
        </button>
        <div class="az-searchbar">
            <form action="#">
                <div>
                    <div>
                        ${icon.search}
                    </div>
                    <input placeholder="Search" type="text" value=""></div>
            </form>
        </div>
    </div>
    <main id="mainpage">
    </main>
</div>
<div id="az-new">

</div>


<script>

    var pendingChange = "";
    var changeComplete = false;
    var json = "";
    var metadata = null;
    var sheets = null;//newer version of metadata taken from uploaded 'metadata' workbook
    var templateMap = new Map();
    var icons = new Map();
    Map
    var openDropdown = "";
    loadTheData();
    setInterval(function () {spotChange();}, 500);


    async function loadTheData() {
        var page = "overview";
        await getData("page=" + page);
        metadata = json;
        sheets = metadata.pagedata.sheets;
        icons = extractIcons(sheets["Icons"]);
        templateMap = bufferTemplates(sheets["HTML Templates"]);
        for (var table in metadata.tableSpecs) {
            var displayInfo = metadata.tableSpecs[table];
            displayInfo["records"] = json[table];
            displayInfo["startRecord"] = 0;
            displayInfo["startRecord"] = 0;
        }
        //menus created elsewhere so that they appear when spreacsheets are loaded
        //createMenus(page);
        showPage();

    }


    function createMenus(page) {
        createMenu("primary", metadata.mainmenu, page);
        createMenu("secondary", metadata.secondarymenu, page);
    }

    function createMenu(idName, menuItems, page) {
        var menuItemsHTML = "";
        for (var itemNo = 0; itemNo < menuItems.length; itemNo++) {
            var menuItem = menuItems[itemNo];
            var itemHTML = templateMap.get("menuitemtemplate");//document.getElementById("menuitemtemplate").innerHTML;
            var active = "";
            if (menuItem.name.toLowerCase() == page) {
                active = " active";
            }
            itemHTML = itemHTML.replace("NAME", menuItem.name).replace("ICON", menuItem.icon).replace("HREF", menuItem.href).replace("ACTIVE", active);
            menuItemsHTML += itemHTML;
        }
        document.getElementById("az-" + idName + "menu").innerHTML = menuItemsHTML;
    }


    function spotChange() {
        if (!changeComplete) {//this should not be necessary.....
            for (var table in metadata.tables) {
                var filterEntry = document.getElementById("filter" + table.replace(" ", ""));
                if (filterEntry != null) {
                    if (filterEntry.value != metadata.tables[table]["filterString"]) {
                        filter(table, filterEntry.value);
                    } else {
                        if (pendingChange == table) {
                            reshow(table);
                        }
                    }
                }
            }
        }
    }


    function showPage() {
        var mainHTML = "<div class='az-dashboard-view'>";
        for (var table in metadata.tables) {
            mainHTML += createList(table, false);

        }
        document.getElementById("mainpage").innerHTML = mainHTML;
    }


    function reshow(table) {
        changeComplete = true;
        createList(table, true);

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


    async function getData(additionalparams) {

        let params = "op=storybook&sessionid=${pageContext.session.id}&" + additionalparams;
        let data = await azquoSend(params);
        json = await data.json();
        if (json.error > "") {
            var errorDiv = document.getElementById("error");
            errorDiv.innerText = json.error;
            return;
        }
    }


    function createList(table, reshow) {

        var fieldDefs = metadata.tableSpecs[table].fieldSpecs;
        var recordInfo = metadata.tables[table];
        var recordsToShow = recordInfo['recordsToShow'];
        if (recordsToShow == 0) {
            return "";
        }
        var records = recordInfo["records"];
        var filterString = recordInfo['filterString'];
        if (filterString > "") {
            for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
                var fieldDef = fieldDefs[fieldNo];
                if (fieldDef.heading.toLowerCase() == "name") {
                    records = records.filter((record) => record[fieldDef.name]?.toLowerCase().includes(filterString?.toLowerCase()))
                    break;
                }
            }

        }


        var startRecord = recordInfo["startRecord"];
        var recordCount = records.length;
        var lastToShow = startRecord + recordsToShow;

        table = table.trim();
        if (lastToShow > recordCount) {
            lastToShow = recordCount;
        }
        var recordsHTML = createRecords(table, records, startRecord, lastToShow, recordInfo["recordClass"]);


        if (recordInfo["recordsOnly"]) {
            return showHeading(table,"") + "<div class='az-section-body'><div class='az-record-cards'>" + recordsHTML + "</div></div>";


        } else {
            if (recordInfo["recordClass"] == "az-table") {
                var recordsHTML = createRecordTable(table, recordsHTML, startRecord, lastToShow, records.length);
            }

            var controlsHTML = "";

            if (!recordInfo["recordsOnly"]) {
                var newHTML = "";
                if (recordInfo["allowNew"]) {
                    newHTML = templateMap.get("newtemplate");// document.getElementById("newtemplate").innerHTML.replace("TABLE", table);
                }
                var controlsHTML = templateMap.get("controlstemplate");
                //var controlsHTML = document.getElementById("controlstemplate").innerHTML;
                controlsHTML = controlsHTML.replace("NEWBUTTON", newHTML).replace(/TITLE/g, table).replace("FILTER", table.replace(" ", ""));
            }
            var recordListHTML = "<div id='recordList" + table + "' class='az-section-body'><div class='" + recordInfo["recordClass"] + "'>" + recordsHTML + "</div></div>"
            if (reshow) {
                document.getElementById("recordList" + table).outerHTML = recordListHTML;
                return;


            }


            return showHeading(table,controlsHTML) + recordListHTML;

        }

        function showHeading(table, controlsHTML){
            return "<div class='az-section-heading'><h3>" + table + "</h3>" + controlsHTML + "</div>"
        }


    }

    function createRecords(table, records, startRecord, lastToShow, recordClass) {
        var fieldDefs = metadata.tableSpecs[table]["fieldSpecs"];
        var recordsHTML = "";
        for (var recordNo = startRecord; recordNo < lastToShow; recordNo++) {
            var record = records[recordNo];
            var recordHTML = "";
            var menu = metadata.tableSpecs[table].menu;

            if (recordClass == "az-record-cards") {
                recordsHTML += showCard(table, record);
            } else {
                for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
                    var fieldName = fieldDefs[fieldNo].name;
                    var fieldType = fieldDefs[fieldNo].type;
                    var fieldValue = record[fieldName];
                    if (fieldType=="date"){
                        fieldValue = fieldValue.day + "/" + fieldValue.month + "/" + fieldValue.year;
                    }
                    var fieldHTML = fieldValue;
                    var fieldTemplate = "";
                    if (fieldType == "select") {
                        fieldTemplate = templateMap.get("fieldselecttemplate");// document.getElementById("fieldselecttemplate").innerHTML
                        fieldTemplate = fieldTemplate.replace("HREF", composeHref(menu,"Open",record));
                    }
                    if (fieldType == "badge") {
                        fieldTemplate = templateMap.get("fieldbadgetemplate");//document.getElementById("fieldbadgetemplate").innerHTML;
                    }
                    if (fieldTemplate > "") {
                        fieldHTML = fieldTemplate.replace("FIELDNAME", fieldValue).replace(/TABLE/g, table).replace("RECORDID", record.id);
                    }
                    recordHTML += "<td>" + fieldHTML + "</td>";
                }

                recordHTML += "<td>" + getActionsHTML("B", table, record) + "</td>";
                recordsHTML += "<tr>" + recordHTML + "</tr>";

            }
        }
        return recordsHTML;

    }

    function createTable(table, recordsHTML) {
        var tableHTML = "<table><thead><tr>";
        var fieldDefs = metadata.tableSpecs[table].fieldSpecs;
        for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
            tableHTML += "<th>" + fieldDefs[fieldNo].heading + "</th>";
        }
        tableHTML += "</tr></thead><tbody>" + recordsHTML + "</tbody></table>";
        return tableHTML

    }

    function createRecordTable(table, records, startRecord, lastToShow, recordCount) {
        var tableHTML = createTable(table, records);
        var buttonHTML = '<button onClick="previous(\'' + table + '\')"';
        if (startRecord == 0) {
            buttonHTML += " disabled";
        }
        buttonHTML += '>Previous</button><button onClick="next(\'' + table + '\')"';
        if (lastToShow == recordCount) {
            buttonHTML += " disabled";
        }

        var recordtableHTML = templateMap.get("recordtabletemplate"); //document.getElementById("recordtabletemplate").innerHTML;
        recordtableHTML = recordtableHTML.replace("PREVIOUSANDNEXTBUTTONS", buttonHTML + ">Next</button>").replace("STARTRECORD", (startRecord + 1)).replace("ENDRECORD", lastToShow).replace("RECORDCOUNT", recordCount).replace("RECORDS", tableHTML);
        return recordtableHTML;
    }

    function composeHref(menu, action, record){
        for (var item in menu){
            if (menu[item].name==action) {
                return menu[item].href.replace(/DATABASE/g, record["database"]).replace(/ID/g, record.id)
            }
        }
    }

    function getActionsHTML(type, table, record) {
        var actionsHTML = templateMap.get("actionstemplate");//document.getElementById("actionsTemplate").innerHTML;
        var listHTML = "";
        var menu = metadata.tableSpecs[table].menu;

        for (var action in menu) {
            if (menu[action].name > "") {
                var actionItemHTML = templateMap.get("actionitemtemplate");//document.getElementById("actionitemtemplate").innerHTML;
                listHTML += actionItemHTML.replace("OPTIONICON", menu[action].icon).replace(/MENUOPTION/g, menu[action].name).replace(/TABLE/g, table).replace("HREF", composeHref(menu,menu[action].name, record));
            } else {
                listHTML += "<hr role='none'>";
            }
        }
        return actionsHTML.replace(/TYPE/g, type).replace(/TABLE/g, table).replace(/RECORDID/g, record.id).replace("ACTIONITEMS", listHTML);

    }

    function showCard(table, record) {
        var cardHTML = templateMap.get("cardtemplate");//document.getElementById("cardtemplate").innerHTML;
        var iconName = table.toLowerCase();
        if (iconName.endsWith("s")) {
            iconName = iconName.substring(0, iconName.length - 1);
        }
        var menu = metadata.tableSpecs[table].menu;

        cardHTML = cardHTML.replace("HREFSELECT",composeHref(menu,"Open", record)).replace("HREFDOWNLOAD", composeHref(menu,"Download,record"));
        cardHTML = cardHTML.replace("RECORDNAME", record.name).replace("DATABASE", record["database"]).replace("CARDACTIONS", getActionsHTML("A", table, record)).replace(/TABLE/g, table).replace("ICON", metadata.icons[iconName]);
        return cardHTML;

    }

    function toggleDropdown(dropdownId) {

        if (openDropdown > "" && openDropdown != dropdownId) {
            document.getElementById(openDropdown).style.display = 'none';
        }
        var dropdown = document.getElementById(dropdownId);
        if (dropdown.style.display == 'none') {
            dropdown.style.display = 'block';
            openDropdown = dropdownId;
        } else {
            dropdown.style.display = 'none';
        }
        return false;
    }

    function filter(table, newValue) {
        var recordInfo = metadata.tables[table];
        recordInfo["filterString"] = newValue;
        recordInfo["startRecord"] = 0;
        pendingChange = table;
    }

    function previous(table) {
        var recordInfo = metadata.tables[table];
        var count = recordInfo["recordsToShow"];
        recordInfo["startRecord"] -= count;
        reshow(table);
    }

    function next(table) {
        var recordInfo = metadata.tables[table];
        var count = recordInfo["recordsToShow"];
        recordInfo["startRecord"] += count;
        reshow(table);
    }

    function clearChange() {
        changeComplete = false;
    }

    function showClass(table, recordClass) {
        metadata.tables[table]["recordClass"] = recordClass;
        reshow(table);
    }


    function uploadNew(table) {
        var newHTML = "";
        var dbHTML = "";
        if (table == "database") {
            newHTML = "";
            dbHTML = "<input id='newdatabase' type='text'/>"
        } else {
            newHTML = '<div class="az-section-controls">' + templateMap.get("newtemplate").replace("TABLE", "database") + "</div>";
            dbHTML = '<div class="az-select">' + setDBlist() + "</div>";
        }
        var newUploadHTML = templateMap.get("newuploadtemplate");//document.getElementById("newuploadtemplate").innerHTML;
        newUploadHTML = newUploadHTML.replace("NEWBUTTON", newHTML).replace("DATABASESELECT", dbHTML).replace("DROPDOWNICON", metadata.icons["dropdown"]);
        var newPage = document.getElementById("az-new")
        newPage.innerHTML = newUploadHTML;

    }

    function testclosenew() {
        var target = event.target;
        if (!target.closest('#az-newbox')) {
            document.getElementById("az-new").innerHTML = "";
        }
    }


    function setDBlist() {
        var selectHTML = "";

        selectHTML = "<select style=\"width:-webkit-fill-available\" id=\"databaseChosen\">";
        var selectCount = 0;
        for (var selectItem of metadata.tables["Databases"]["records"]) {
            if (selectCount++ > 100) break;
            var selected = "";
            if (selectItem.name == metadata.lastdatabase) {
                selected = " selected";
            }
            selectHTML += "\n<option value = \"" + selectItem.name + "\"" + selected + ">" + selectItem.name + "</option>";
        }

        selectHTML += "</select>";
        return selectHTML;
    }

    function showRecord(table){
        var fieldDefs = metadata.tableSpecs[table]["fieldSpecs"];
        var recordHTML = "<div class='az-record-view'>" +  showHeading(table,"");
        recordHTML +="<div class='az-table'>";
        var record = records[0];

        for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
            var fieldName = fieldDefs[fieldNo].name;
            var fieldType = fieldDefs[fieldNo].type;
            var fieldValue = record[fieldName];
            if (fieldType=="date"){
                fieldValue = fieldValue.day + "/" + fieldValue.month + "/" + fieldValue.year;
            }
            var fieldHTML = fieldValue;
            var fieldTemplate = "";
            if (fieldType == "select") {
                fieldTemplate = templateMap.get("fieldselecttemplate").replace("HREF", composeHref(menu,"Open",record));
            }
            if (fieldType == "badge") {
                fieldTemplate = templateMap.get("fieldbadgetemplate");//document.getElementById("fieldbadgetemplate").innerHTML;
            }
            if (fieldTemplate > "") {
                fieldHTML = fieldTemplate.replace("FIELDNAME", fieldValue).replace(/TABLE/g, table).replace("RECORDID", record.id);
            }
            recordHTML += "<tr>" + fieldHTML + "</tr>";
        }

    }

    function bufferTemplates(templateSheet){
        var templateMap = new Map();
        var templateName = "";
        var templateHTML = "";
        var rowNo = 0;
        while (rowNo < templateSheet.length){
            var row = templateSheet[rowNo];
            if (row[0]>"") {
                templateName = row[0];
                while (rowNo < templateSheet.length) {
                    row = templateSheet[rowNo];
                    if (row.length < 2 || row[1] == "") {
                        break;
                    }
                    templateHTML += row[1];
                    rowNo++;
                }
                templateMap.set(templateName, replaceIcons(templateHTML.replace(/\"/g,'"')));
                templateHTML = "";
            }
            rowNo++;
        }
        return templateMap;
    }

    function replaceIcons(text){
        var iconPos = text.indexOf("$");
        while (iconPos > 0){
            var endpos = text.indexOf("}", iconPos)
            text = text.substring(0,iconPos) + icons.get(text.substring(iconPos + 7, endpos)) + text.substring(endpos + 1);
            iconPos = text.indexOf("$");
        }
        return text;
    }

    function extractIcons(sheet){
        var icons = new Map();
        for (var rowNo = 1;rowNo < sheet.length; rowNo++){
            var row = sheet[rowNo];
            if (row[0] > ""){
                var iconVal = "";
                for (var col=1;col < row.length;col++){
                    var bit = row[col];
                    if (bit==""){
                        break;
                    }
                    iconVal += bit;
                }
                icons.set(row[0], iconVal);
            }
        }
        return icons;
    }


</script>



<%@ include file="../includes/new_footer.jsp" %>


<div id="headlessui-dialog-panel-:r70:" class="opacity-100 translate-y-0 scale-100">
</div>