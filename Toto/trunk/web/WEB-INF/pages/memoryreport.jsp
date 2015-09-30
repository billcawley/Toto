<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 30/09/15
  Time: 09:36
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <meta http-equiv="refresh" content="1; url=/api/MemoryReport?serverIp=${serverIp}">
  <title></title>
</head>
<body>
<a href="/api/MemoryReport?serverIp=${serverIp}&gc=true">GC</a></br>
${memoryReport}
</body>
</html>
