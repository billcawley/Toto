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
    table.az-search, td {

        border-color:#333333;
        vertical-align:top;
        min-width:400px;
        font-weight: normal;
    }
    table.az-search, th {
        border-color:#333333;
        vertical-align:top;
        min-width:400px;
        font-weight: bold;
    }
    .az-searchitem {
        margin-left: 0.5rem;
        margin-right: 0.5rem;
        line-height: 1.25rem;
        --tw-text-opacity: 1;
        text-decoration: underline;
    }

    .az-sortheading{
        color:#004444;
    }


    .az-foundlist {
        color: rgb(55 65 81 / var(--tw-text-opacity));
        font-size:11px;
    }

    .itemselected {
        color: #880000;
        margin: 5px;
        font-size:20px;
        border:0px;
    }

    .az-attributetable .action
    {
        width:auto;
        text-align:right;
        white-space: nowrap;
        font-size:11px;
    }
    .az-attributetable   {
        border-collapse:collapse;
        border-spacing:0;
        width:1%;
        font-size:11px;
    }




</style>

<div class="az-content" id="setup">
    <div class="az-topbar">
        <div class="az-searchbar">
            <div>
                <input name="query" id="query" placeholder="Search" type="text" value=""/>
            </div>
        </div>
    </div>
    <main>
        <table class="az-search">
            <tr>
                <th>
                    Search results
                </th>
                <th>Chosen item
                </th>
            </tr>
            <tr>

                <td>
                    <div class="az-list" id="fieldtable"> </div>
                </td>
                <td>
                    <div class="itemselected" id="itemselected"></div>
                    <div class="az-list" id="children"></div>
                </td>
            </tr>
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
        document.getElementById("itemselected").innerHTML = "";
        document.getElementById("children").innerHTML = "";

    }


    function handleDetails(json) {
        document.getElementById("itemselected").innerHTML = showDetails(json);
        document.getElementById("children").innerHTML = showSet("", json.children.children, false);
    }



    function showDetails(json){

        var jsonDetails = json.details;
        var name = jsonDetails.attributes.DEFAULT_DISPLAY_NAME;
        if (name.indexOf("\n")>0){
            name = name.replaceAll("\n","<br/>");
        }

        var itemHTML = "<div>" + name +"</div><table class='az-attributetable'>\n";
        itemHTML+="<div class='namedetailsinner'><div style='font-size:11px;line-height=15px'>";

        if (json.parents.children.length > 0) {
            for (var parent of json.parents.children) {
                if (parent.parentNameId > 0) {
                    itemHTML += oneLine("az-parent", parent.parentName, parent.text, parent.nameId);
                }
            }
        }


        for (var att in jsonDetails.attributes){
            if (att!="DEFAULT_DISPLAY_NAME"){
                itemHTML += oneLine("az-attribute",att,jsonDetails.attributes[att], 0);

            }
        }
        for (var info in jsonDetails){
            if (info != "attributes" && info != "id" && info != "name"){
                itemHTML += oneLine("az-attribute",info, jsonDetails[info], 0);
            }
        }
        itemHTML += "</table></div>\n";
        return itemHTML;



    }

    function oneLine(type, name,value, nameId){
        if (nameId==0){
            return "<tr class='" + type + "'><td>" + name + "</td><td>" + value + "</td></tr>";

        }
        return "<tr><td class='" + type + "'>" + name + "</td><td class='az-searchitem' onClick='itemSelected(" + nameId + ")' >"+value + "</td></tr>\n";

    }

    function showSet(setName, setElements, truncate){
        var itemHTML = "<div class='az-foundlist'>\n";
        if (setName > "")
            itemHTML += "<h2 style='font-size:18px'>" +  setName + "</h2>\n";
        if (setElements.length == 0){
            return "";
        }
        for (var element of setElements){
            var eol = element.text.indexOf("\n");
            var text = element.text;
            if (truncate){
                if (eol > 0){
                    text = text.substring(0,eol);
                }
            }
            if (element.sortName> ""){
                itemHTML +="<div class='az-sortheading'>" + element.sortName + "</div>";
            }
            itemHTML += "<div class='az-searchitem' onClick='itemSelected(" + element.nameId + ")' >"+text.replaceAll("\n","<br/>") + "</div>\n";
        }
        return itemHTML + "</div>";

    }


</script>


<%@ include file="../includes/new_footer.jsp" %>
