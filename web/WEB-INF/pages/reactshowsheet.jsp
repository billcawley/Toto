<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="View Report"/>
<c:set var="compact" scope="request" value="compact"/>
<c:set var="requirezss" scope="request" value="true"/>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%><!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <title>Azquo &gt; ${title}</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0"/>
    <meta name="next-head-count" content="3"/>
    <link rel="icon" href="/favicon.png"/>

    <link rel="stylesheet" href="/newdesign/css/keikai-iframe.css" data-n-g=""/>

    <kkjsp:head/>

</head>
<body>
<div id="__next">
    <div class="az-layout">


        <div class="az-content">
            <span id="lockedResult"></span>
            <div class="az-topbar-menu" style=""display:none">

            <div id="saveDataButton">
            </div>

            <div id="restoreDataButton" >
            </div>

            <!--            <button class="az-button" type="button">
                   <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                        stroke="currentColor" aria-hidden="true">
                       <path stroke-linecap="round" stroke-linejoin="round" d="M8 9l4-4 4 4m0 6l-4 4-4-4"></path>
                   </svg>
                   Selections
               </button>-->


        </div>
        <main>
            <div class="az-report-view">
                <div class="az-report">
                    <kkjsp:spreadsheet id="myzss"
                                       bookProvider="com.azquo.spreadsheet.zk.BookProviderForJSP"
                                       apply="com.azquo.spreadsheet.zk.ZKComposer"
                                       height="100%"
                                       maxVisibleRows="500" maxVisibleColumns="200"
                                       showSheetbar="true" showToolbar="false" showFormulabar="true"
                                       showContextMenu="true"/>

                </div>
            </div>
        </main>
    </div>
    <style>
        /* remove dash borders of the auto filter */
        [class*="af"]:after {
            border: initial !important;
        }
    </style>
</div>
</div>
<script src="/js/spreadsheet.js"/>
<!-- todo - switch page so menus are highlighted -->
</body>
</html>
