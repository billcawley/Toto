<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Overview"/>
<c:set var="extraScripts" scope="request" value="<script src=\"/newdesign/chunks/385-0c9862a9f5582a25.js\" defer=\"\"></script><script src=\"/newdesign/chunks/pages/index-4dbb0f3e88b59b50.js\" defer=\"\"></script>" />
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-reports-view">
            <div class="az-section-heading">
                <h3>Overview</h3>
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
                    <div class="az-section-view">
                                        <span
                                        ><button class="selected">
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
                                                            d="M4 6h16M4 10h16M4 14h16M4 18h16"
                                                    ></path>
                                                </svg></button
                                        ><button class="">
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
                                                            d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
                                                    ></path>
                                                </svg></button
                                        ></span>
                    </div>
                </div>
            </div>
            <div class="az-section-body">
            </div>
            <%@ include file="../includes/new_footer.jsp" %>
