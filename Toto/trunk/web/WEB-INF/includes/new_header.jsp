<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

    <title>Azquo &gt; Dashboard</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0">
    <meta name="next-head-count" content="3">
    <link rel="icon" href="https://prototype.azquo.cherrett.digital/favicon.png">
    <link rel="stylesheet" href="https://rsms.me/inter/inter.css">
    <link rel="preload" href="/newdesign/8e4ed02343a08963.css" as="style">
    <link rel="stylesheet" href="/newdesign/8e4ed02343a08963.css" data-n-g="">
    <noscript data-n-css=""></noscript>
    <script defer="" nomodule="" src="/newdesign/polyfills-0d1b80a048d4787e.js"></script>
    <script src="/newdesign/webpack-4dc2921e155e6a75.js" defer=""></script>
    <script src="/newdesign/framework-4556c45dd113b893.js" defer=""></script>
    <script src="/newdesign/main-7feab3f544c289f7.js" defer=""></script>
    <script src="/newdesign/_app-7ab67057484a7399.js" defer=""></script>
    <script src="/newdesign/385-0c9862a9f5582a25.js" defer=""></script>
<!--    <script src="/newdesign/index-4dbb0f3e88b59b50.js" defer=""></script> -->
    <script src="/newdesign/_buildManifest.js" defer=""></script>
    <script src="/newdesign/_ssgManifest.js" defer=""></script>

    <c:if test="${requirezss}">
        <kkjsp:head/>
    </c:if>
<!--    <link as="script" rel="prefetch" href="/newdesign/reports-9b7761ddc0ac6ae2.js">
    <link as="script" rel="prefetch" href="/newdesign/imports-8757a456a399af16.js">
    <link as="script" rel="prefetch" href="/newdesign/databases-618567b8dfa1aad2.js">
    <link as="script" rel="prefetch" href="/newdesign/users-f79e8872aa959588.js">
    <link as="script" rel="prefetch" href="/newdesign/[id]-7e48ef916c83f6a3.js">
    <link as="script" rel="prefetch" href="/newdesign/[id]-81a2ba0ddd08557c.js"> -->
</head>
<body>
<div id="__next">
    <div class="az-layout">
        <div class="az-sidebar">
            <div><a class="az-sidebar-logo" href="https://prototype.azquo.cherrett.digital/"><img
                    src="/newdesign/logo_dark_bg.png"></a>
                <nav>
                    <div class="az-sidebar-primary"><a class="group" rel="noopener noreferrer"
                                                       href="https://prototype.azquo.cherrett.digital/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"></path>
                        </svg>
                        <span>Overview</span></a><a class="group active" rel="noopener noreferrer"
                                                    href="https://prototype.azquo.cherrett.digital/reports/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2"></path>
                        </svg>
                        <span>Reports</span></a><a class="group" rel="noopener noreferrer"
                                                   href="https://prototype.azquo.cherrett.digital/imports/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
                        </svg>
                        <span>Imports</span></a><a class="group" rel="noopener noreferrer"
                                                   href="https://prototype.azquo.cherrett.digital/databases/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"></path>
                        </svg>
                        <span>Databases</span></a><a class="group" rel="noopener noreferrer"
                                                     href="https://prototype.azquo.cherrett.digital/users/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path>
                        </svg>
                        <span>Users</span></a><a class="group" rel="noopener noreferrer"
                                                 href="https://prototype.azquo.cherrett.digital/schedules/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                        <span>Schedules</span></a></div>
                    <div class="az-sidebar-secondary"><a class="group" rel="noopener noreferrer"
                                                         href="https://prototype.azquo.cherrett.digital/settings/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path>
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                        </svg>
                        <span>Settings</span></a><a class="group" target="_blank" rel="noopener noreferrer"
                                                    href="http://storybook.azquo.cherrett.digital/">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2"
                             stroke="currentColor" aria-hidden="true">
                            <path stroke-linecap="round" stroke-linejoin="round"
                                  d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                        </svg>
                        <span>Help</span></a></div>
                </nav>
            </div>
        </div>
