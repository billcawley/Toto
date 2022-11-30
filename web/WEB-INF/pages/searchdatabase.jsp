<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ include file="../includes/new_header.jsp" %>
<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Search Database"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>

<style>

    .az-attname {
        width:100%;
    }

    .az-foundlist table td {
        padding:2px;
    }


    .az-list{
        white-space:normal;

    }
    .az-searchitem {
        margin-left: 0.5rem;
        margin-right: 0.5rem;
        line-height: 1.25rem;
        --tw-text-opacity: 1;
        text-decoration: underline;
    }

    .az-sortheading {
        color: #004444;
    }



    .az-itemfound {
        border-bottom:2px;
        height:400px;
        width:500px;
        white-space:normal;
        overflow:auto;
    }


    .az-attributetable .action {
        width: auto;
        text-align: right;
        white-space: nowrap;
        font-size: 11px;
    }

    .az-attributetable {
        border-collapse: collapse;
        border-spacing: 0;
        width: 1%;
        font-size: 11px;
    }


</style>

<div class="az-content" id="setup">
    <div class="az-topbar">
        <div class="az-searchpanel-search">
            <table>
                <tr>
                    <td>
                        <div>
                            <input style="border-color:transparent" name="query" id="query" placeholder="Search" type="text" value=""/>
                        </div>
                    </td>
                    <td>
                        Filters: <span id="az-filters"></span>
                    </td>
                </tr>
            </table>
        </div>
    </div>
    <main>
        <table class="az-table">
            <thead>
            <tr>
                <th style="min-width:500px">
                    Search results
                </th>
                <th>Chosen item
                </th>
            </tr>
            </thead>
            <tbody>
            <tr class="az-foundlist">

                <td class="az-foundlist">
                    <div id="fieldtable"></div>
                </td>
                <td class="az-foundlist">
                    <div  id="itemselected" style="display:block"></div>
                    <div class="az-list" id="children"></div>
                </td>
            </tr>
            </tbody>
        </table>
        <div id="error">${error}</div>
    </main>
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

    var json = null;
    var quote = "`";
    setInterval(function () {
        spotChange();
    }, 100);
    var lastValue = "";
    var filters = [];
    var filterHTML = '&nbsp&nbsp<span class="close"><span class="fa fa-close" onClick="removeFilter(\'FILTER\')">FILTERSHOWN</span>'
    changed("", 0);


    function spotChange() {
        if (document.getElementById("query").value != lastValue) {
            lastValue = document.getElementById("query").value;
            changed(lastValue, 0);
        }
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


    function itemSelected(nameId) {
        changed("", nameId);
    }

    async function changed(query, nodeId) {
        //enclose all values in quotes to avoid confusion
        query = quote + query + quote;
        if (filters.length > 0) {
            for (var filter of filters) {
                var colonPos = filter.indexOf(":");
                query += "&" + quote + filter.substring(0,colonPos) + quote + "=" + quote + filter.substring(colopPos + 1) + quote;
            }
        }
        let params = "op=searchdatabase&sessionid=${pageContext.session.id}";
        params += "&query=" + encodeURIComponent(query) + "&nameId=" + nodeId;
        let data = await azquoSend(params);
        json = await data.json();
        if (json.error > "") {
            var errorDiv = document.getElementById("error");
            errorDiv.innerText = json.error;
            return;
        }
        if (nodeId == 0) {
            handleQueryResult(json);
        } else {
            handleDetails(json);
        }
        showFilters();
    }

    function showFilters(){

        filters.sort();
        var filtersHTML = "";
        var filterVar = "";
        var filterVal = "";
        for (var filter of filters) {
            filterVal = filter.substring(filter.indexOf(":") + 1);
            var filtershown = filter;
            if (filter.substring(0,filter.indexOf(":"))!=filterVar){
                filtershown = filterVal;

            }
            filterVar = filter.substring(0,filter.indexOf(":"));

            filtersHTML += filterHTML.replace("FILTERSHOWN", filter).replaceAll("FILTER", filter);
        }
        document.getElementById("az-filters").innerHTML = filtersHTML;

    }

    function handleQueryResult(jsonItem) {
        var itemsHTML = "<div>";
        for (var topName in jsonItem) {
            var element = jsonItem[topName];
            if (element!=null){

                itemsHTML +=  showFoundSet(topName, element.children) ;
            }
        }
        document.getElementById("fieldtable").innerHTML = itemsHTML + "</div>";
        document.getElementById("itemselected").innerHTML = "";
        document.getElementById("children").innerHTML = "";

    }


    function handleDetails(json) {
        document.getElementById("itemselected").innerHTML = showDetails(json);
        document.getElementById("children").innerHTML = "<div>" + showSet("", json.children.children)+"</div>";
    }


    function showDetails(json) {

        var jsonDetails = json.details;
        var name = jsonDetails.attributes.DEFAULT_DISPLAY_NAME;
        if (name.indexOf("\n") > 0) {
            name = name.replaceAll("\n", "<br/>");
        }

        var itemHTML = "<div class='az-itemfound az-alert az-alert-info'><b>" + name + "</b></div><table class='az-attributetable'>\n";
        itemHTML += "<div style='font-size:11px;line-height=15px'>";

        if (json.parents.children.length > 0) {
            for (var parent of json.parents.children) {
                if (parent.parentNameId > 0) {
                    itemHTML += oneLine("az-parent", parent.parentName, parent.text, parent.nameId);
                }
            }
        }


        for (var att in jsonDetails.attributes) {
            if (att != "DEFAULT_DISPLAY_NAME") {
                itemHTML += oneLine("az-attribute", att, jsonDetails.attributes[att], 0);

            }
        }
        for (var info in jsonDetails) {
            if (info != "attributes" && info != "id" && info != "name") {
                itemHTML += oneLine("az-attribute", info, jsonDetails[info], 0);
            }
        }
        itemHTML += "</table>\n";
        return itemHTML;


    }

    function oneLine(type, name, value, nameId) {
        if (nameId == 0) {
            return "<tr class='" + type + "'><td>" + name + "</td><td class='az-attname'>" + value + "</td></tr>";

        }
        return "<tr class='az-searchitem' onClick='itemSelected(" + nameId + ")'><td>" + name + "</td><td class='az-attname'>" + value + "</td></tr>\n";

    }

    function newFilter(name, value) {
        var filterString = name + ":" + value;
        const index = filters.indexOf(filterString);
        if (index >=0){
            return;
        }
        filters.push(filterString);
        showFilters();
        changed(lastValue,0);
    }

    function removeFilter(name){
        const index = filters.indexOf(name);
        if (index > -1) { // only splice array when item is found
            filters.splice(index, 1); // 2nd parameter means remove one item only
        }
        showFilters();
        changed(lastValue,0);
    }


    function showFoundSet(setName, setElements) {
        var itemHTML = "";
        itemHTML += "<table><thead><tr><th>" + setName + "</th></tr><thead>\n";
        if (setElements.length == 0) {
            return "";
        }
        for (var element of setElements) {
            var eol = element.text.indexOf("\n");
            var text = element.text;
            text = text.replaceAll("\n", " ").replaceAll("\r", "");
            if (text.length > 60) {
                var foundPos = text.toLowerCase().indexOf(lastValue.toLowerCase());
                var startPos = 0;
                if (foundPos > 30) {
                    startPos = text.indexOf(" ", foundPos - 30);
                }
                var endPos = text.length;
                if (endPos > startPos + 60) {
                    endPos = startPos + 60;
                }
                text = text.substring(startPos, endPos);
                if (startPos > 0) {
                    text = "..." + text;
                }
                if (endPos < text.length) {
                    text = text + "...";
                }
            }
            itemHTML += "<tr><td><span onClick='itemSelected(" + element.nameId + ")' >" + text.replaceAll("\n", "<br/>") + "</span></td></tr>\n";
        }
        return itemHTML + "</table>";

    }


    function showSet(setName, setElements) {
        var itemHTML = "";
        if (setName > "")
            itemHTML += "<thead><tr><th>" + setName + "</th></tr><thead>/>\n";
        if (setElements.length == 0) {
            return "";
        }
        for (var element of setElements) {
            var eol = element.text.indexOf("\n");
            var text = element.text;
            if (element.sortName > "") {
                itemHTML += "<div class='az-sortheading'>" + element.sortName + "</div>";
            }
            itemHTML += "<p onClick='itemSelected(" + element.nameId + ")' >" + text.replaceAll("\n", "<br/>") + "</p>\n";
        }
        return itemHTML + "<br>";

    }


</script>


<%@ include file="../includes/new_footer.jsp" %>
