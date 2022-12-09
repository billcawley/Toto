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
    <main>
        <div id="mainpage">
            MAINPAGE

        </div>

    </main>
</div>

<script>

    var json = "";
    var reportStart = 0;
    var reportCount = 5;
    var importStart = 0;
    var importCount = 3;
    var metadata = null;
    var openDropdown = "";

    loadTheData();

    async function loadTheData() {
        await getData("page=metadata");
        metadata = json;

        await getData("page=reports");
        var mainHTML = "<div class='az-dashboard-view'>";
        mainHTML += createList(json, "Recently viewed", 0, 3, true, false);
        mainHTML += createList(json, "Reports", 0, reportCount, false, true);
        await getData("page=imports");
        mainHTML +=createList(json, "Imports", 0, importCount, false, true) + "</div>";
        document.getElementById("mainpage").innerHTML = mainHTML;

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


    function createList(json, heading, start, end,  isCard, newButton) {

        var recordsHTML = "";

        for (var page in json) {
            page = page.trim();
            var fieldDefs = metadata.pageSpecs[page].fieldSpecs;
            if (page.length < start + count) {
                count = page.length - start;
            }
            var count = json[page].length;
            for (var recordNo = start; recordNo < end; recordNo++) {
                var record = json[page][recordNo];
                var recordHTML = "";
                if (isCard) {
                    recordHTML = showCard(page, record);
                } else {
                    for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
                        var fieldName = fieldDefs[fieldNo].name;
                        var fieldType = fieldDefs[fieldNo].type;
                        var fieldValue = record[fieldName];
                        var fieldHTML = fieldValue;
                        var fieldTemplate = "";
                        if (fieldType == "select") {
                            fieldTemplate = "reportfieldselecttemplate";
                        }
                        if (fieldType == "badge") {
                            fieldTemplate = "reportfieldbadgetemplate";
                        }
                        if (fieldTemplate > ""){
                            fieldHTML = document.getElementById(fieldTemplate).innerHTML.replace("FIELDNAME", fieldValue).replace(/PAGE/g, page).replace("RECORDID", record.id);
                        }
                        recordHTML += "<td>" + fieldHTML + "</td>";
                    }

                    recordHTML += "<td>" +  getActionsHTML("B",page, record) + "</td>";

                }
                recordsHTML += "<tr>" + recordHTML + "</tr>";
            }
            var recordListHTML = "";

            if (isCard) {
                recordListHTML = "<div class='az-section-heading'><h3>"+heading + "</h3></div><div class='az-section-body'><div class='az-report-cards'>" + recordsHTML + "</div>";


            } else {
                var tableHTML ="<table><thead><tr>";
                for (var fieldNo = 0; fieldNo < fieldDefs.length; fieldNo++) {
                    tableHTML += "<th>" +fieldDefs[fieldNo].heading + "</th>";
                }
                tableHTML += "</tr></thead><tbody>" + recordsHTML + "</tbody></table>";


                if (newButton){
                    newHTML = document.getElementById("newtemplate").innerHTML.replace("PAGE",page);
                }

                recordListHTML = document.getElementById("recordlisttemplate").innerHTML;
                recordListHTML = recordListHTML.replace("TITLE", page).replace("NEWBUTTON", newHTML).replace("STARTRECORD",start).replace("ENDRECORD",end).replace("RECORDCOUNT",count).replace("REPORTTABLE", tableHTML);

            }
            return recordListHTML;

        }
    }

    function getActionsHTML(type,page, record) {
        var actionsHTML = document.getElementById("actionsTemplate").innerHTML;
        var listHTML = "";
        var menu = metadata.pageSpecs[page].menu;

        for (var action in menu) {
            if (menu[action] > "") {
                var actionItemHTML = document.getElementById("actionitemtemplate").innerHTML;
                var iconName = menu[action].toLowerCase();
                listHTML += actionItemHTML.replace(/PAGE/g, page).replace("OPTIONICON", metadata.icons[iconName]).replace(/MENUOPTION/g, menu[action]).replace(/RECORDID/g, record.id);
            } else {
                listHTML += "<hr role='none'>";
            }
        }
        return actionsHTML.replace(/TYPE/g,type).replace(/PAGE/g, page).replace(/RECORDID/g, record.id).replace("ACTIONITEMS", listHTML);

    }

    function showCard(page, record) {
        var cardHTML = document.getElementById("cardtemplate").innerHTML;
        cardHTML = cardHTML.replace("RECORDNAME", record.name).replace("DATABASE", record["database"]).replace("CARDACTIONS", getActionsHTML("A",page, record));
        return cardHTML;

    }

    function toggleDropdown(dropdownId) {

        if (openDropdown > "" && openDropdown!=dropdownId){
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


</script>


<div id="templates" style="display:none">



    <div id="cardtemplate">
        <div class="az-report-card"><a
                href="${rootpath}&page=reports&action=select&id=RECORDID">
            <div class="az-card-data"><h3>RECORDNAME</h3>
                <p>DATABASE</p></div>
            <div class="az-card-icon">
                ${icon.report};
            </div>
        </a>
            <div class="az-card-options">
                <div><a href="${rootpath}&page=reports&action=select&id=RECORDID">
                    ${icon.open}
                    <span>Open</span></a></div>
                <div><a href="${rootpath}&page=reports&action=download&id=RECORDID">
                    ${icon.download}
                    <span>Download</span></a></div>
                <div class="az-card-actions">
                    CARDACTIONS
                </div>
            </div>
        </div>
    </div>

    <div id="recordlisttemplate">
        <div class="az-section-heading"><h3>TITLE</h3>
            <div class="az-section-controls">
                NEWBUTTON
                <div class="az-section-filter">
                    <div>
                        ${icon.search}
                    </div>
                    <input type="text" placeholder="Filter">
                </div>
                <div class="az-section-view">
                                        <span>
                                            <button class="selected" onClick="showCards('TITLE',false);">
                                                ${icon.showrecords}
                                            </button>
                                            <button onClick="showCards=('TITLE', true);">
                                                ${icon.showcards}
                                            </button>
                                        </span>
                </div>
            </div>
        </div>
        <div class="az-section-body">
            <div class="az-table">
                REPORTTABLE
                <nav>
                    <div><p>Showing <strong>STARTRECORD</strong> to ENDRECORD</strong> of
                        <strong>RECORDCOUNT</strong> reports
                    </p></div>
                    <div>
                        <button PREVIOUSDISABLED>Previous
                        </button>
                        <button NEXTDISABLED>Next
                        </button>
                    </div>
                </nav>
            </div>
        </div>

    </div>

    <div id="reportfieldselecttemplate">
        <span class=" full">
            <div>
                <a href="${rootpath}&page=PAGE&action=select&id=RECORDID">
                    ${icon.records}
                    <p>FIELDNAME</p>
                </a>

            </div>
        </span>
    </div>
    <div id="reportfieldbadgetemplate">
        <span class="az-badge">FIELDNAME</span>
    </div>


    <div id="actionsTemplate">
        <div class="az-actions">
            <div>
                <button id="headlessui-menu-button-:TYPE:PAGE:RECORDID" onClick="toggleDropdown('dropdown:TYPE:PAGE:RECORDID')"
                        type="button">
                    ${icon.menu}
                </button>
                <div class="az-actions-items transform opacity-100 scale-100" style="display:none"
                     id="dropdown:TYPE:PAGE:RECORDID" role="menu" tabindex="0">
                    ACTIONITEMS
                </div>
            </div>
        </div>

    </div>


    <div id="newbuttontemplate">
        <div class="section-buttons">
            <button>
                <a href="" ${rootpath}&page=PAGE&action=new">
                    ${icon.upload}
                    New
                </a>
            </button>
        </div>
    </div>


    <div id="actionitemtemplate">
        <span role="none">
            <div id="headlessui-menu-item-:PAGE:RECORDID"
                 role="menuitem" tabindex="-1">
                OPTIONICON
                <a href="${rootpath}&page=PAGE&action=MENUOPTION&id=RECORDID">MENUOPTION</a>
            </div>
        </span>
    </div>

</div>

<div id="newtemplate">
    <div class="az-section-buttons">
        <a href="${rootpath}&page=PAGE&action=new">
            <button>
                ${icon.imports}
                New
            </button>
        </a>
    </div>

</div>


<%@ include file="../includes/new_footer.jsp" %>