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

<div class="az-content" id="setup">
    <div class="az-topbar">
        <div class="az-searchbar">
            <div>
                <input name="query" id="query" placeholder="Search" type="text" value=""/>
            </div>
        </div>
    </div>
    <main>
        <div class="az-import-wizard-view">
            <div class="az-import-wizard">
                <div class="az-import-wizard-main">
                    <div class="az-section-body">
                        <table>
                            <tr>
                                <td style="min-width:400px;vertical-align:top">
                                    <div class="az-table" id="fieldtable"> </div>
                                </td>
                                <td style="min-width:400px;vertical-align:top">
                                    <div class="az-table" id="searchitem"></div>
                                </td>
                            </tr>
                        </table>
                    </div>
                </div>
            </div>
        </div>
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
    setInterval(function() { spotChange(); }, 100);
    var lastValue = "";


    function spotChange(){
        if(document.getElementById("query").value!=lastValue){
            lastValue = document.getElementById("query").value;
            if (lastValue > ""){
                changed(lastValue,0);
            }
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


    function itemSelected(nameId){
        changed("",nameId);
    }

    async function changed(query, nodeId) {
        let params = "op=searchdatabase&sessionid=${pageContext.session.id}";
        params += "&query=" + encodeURIComponent(query)+"&nameId=" + nodeId;
        let data = await azquoSend(params);
        json = await data.json();
        if(json.error > ""){
            var errorDiv = document.getElementById("error");
            errorDiv.innerText = json.error;
            return;
        }
        if (query > ""){
            handleQueryResult(json);
        }
        if (nodeId > 0){
            handleDetails(json);
        }
    }

    function handleQueryResult(jsonItem){
        var itemsHTML = "";
        for (var topName in jsonItem){
            var element = jsonItem[topName];
            itemsHTML += showSet(topName,element.children, true);
        }
        document.getElementById("fieldtable").innerHTML = itemsHTML;
        document.getElementById("searchitem").innerHTML = "";

    }


    function handleDetails(json) {



        var itemsHTML ="<div class='az-report-card'>" + showSet("Parents", json.parents.children, false);
        itemsHTML += showDetails(json.details);
        itemsHTML += showSet("Children", json.children.children, false);
        document.getElementById("searchitem").innerHTML = itemsHTML + "</div>";
    }



    function showDetails(jsonDetails){
        var name = jsonDetails.attributes.DEFAULT_DISPLAY_NAME;
        if (name.indexOf("\n")>0){
            name = name.replaceAll("\n","<br/>");
        }
        var itemHTML = "<div class='az-card-data'>" + name +"</div>\n";

        for (var att in jsonDetails.attributes){
            if (att!="DEFAULT_DISPLAY_NAME"){
                itemHTML +="<div class='az-attribute'>" + att + ":" + jsonDetails.attributes[att] + "</div>\n";

            }
        }
        for (var info in jsonDetails){
            if (info != "attributes" && info != "id" && info != "name"){
                itemHTML += "<div class='az-extraInfo'>" + info + ":" + jsonDetails[info] + "</div>";
            }
        }
        return itemHTML;



    }

    function showSet(setName, setElements){
        var itemHTML = "<div class='az-searchpanel-search-results-list'>\n";
        if (setName > "")
            itemHTML += "<h2>" +  setName + "</h2>\n";
        if (setElements.length == 0){
            return "";
        }
        for (var element of setElements){
            itemHTML += "<div class='active' onClick='itemSelected(" + element.nameId + ")'>"+element.text.replaceAll("\n","<br/>") + "</div>\n";

        }
        return itemHTML + "</div>";

    }


</script>


<%@ include file="../includes/new_footer.jsp" %>
