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
                                <td>
                                    <div class="az-table" id="fieldtable"> </div>
                                </td>
                                <td>
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



    async function changed(query, nodeId) {
        let params = "op=searchdatabase&sessionid=${pageContext.session.id}";
        params += "&query=" + encodeURIComponent(query)+"&id=" + nodeId;
        let data = await azquoSend(params);
        json = await data.json();
        if(json.error > ""){
            var errorDiv = document.getElementById("error");
            errorDiv.innerText = json.error;
            return;
        }
        if (json.queryresult > ""){
            handleQueryresult(json.queryresult);
        }
        if (json.details > ""){
            handleDetails(json);
        }
    }

    function handleQueryResult(jsonItem){
        var itemsHTML = "";
        for (var topNameId =0;topNameId < jsonItem.length;topNameId++){
            var element = jsonItem[topNameId];
            itemsHTML += showSet(element.topName,element.found, true);
        }
        document.getElementById("fieldtable").innerHTML = itemsHTML;

    }


    function handleDetails(json) {


        var itemsHTML = showSet("Parents", json.parents, false);
        itemsHTML = showSet(json.name, json.details);


        itemsHTML += showSet("Children", json.children, false);
        document.getElementById("searchitem").innerHTML = itemsHTML;
    }

    function showSet(setName, setElements){
        itemsHTML += "<div class='az-heading'>" + setName + "</div>\n";
        for (var element of setElements){
            if (element.id > ""){
                itemsHTML += "<div class='az-founditem' onClick='itemSelected(" + element.id + ")'>"+element.name + "</div>\n";
            }else{
                itemsHTML +="<div class='az-attribute'>" + element.name + ":" + element.value + "</div>\n";
            }

        }

    }


</script>


<%@ include file="../includes/new_footer.jsp" %>
