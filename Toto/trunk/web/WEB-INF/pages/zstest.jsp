<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>Edds book provider</title>
    <zssjsp:head/>
</head>
<%
    //prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>
<body>
<div>
    <zssjsp:spreadsheet id="myzss"
                        bookProvider="com.azquo.view.ZKAzquoBookProvider"
                        apply="com.azquo.view.ZKComposer"
                        width="100%" height="900px"
                        maxrows="200" maxcolumns="80"
                        showToolbar="true" showFormulabar="true" showContextMenu="true" showSheetbar="true"/>
<!--    zssjsp:spreadsheet id="myzss"
                        bookProvider="com.azquo.view.ZKAzquoBookProvider"
                        apply="com.azquo.view.ZKComposer"
                        width="1850px" height="900px"
                        maxrows="1000" maxcolumns="80"
                        showToolbar="true" showFormulabar="true" showContextMenu="true"/>-->
</div>
</body>
</html>