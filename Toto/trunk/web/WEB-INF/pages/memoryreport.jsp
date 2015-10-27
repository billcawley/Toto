<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<c:choose>
	<c:when test="${param.gc == true}">
		<a href="/api/MemoryReport?serverIp=${serverIp}&gc=false">Exclude GC</a></br>
	</c:when>
	<c:otherwise>
		<a href="/api/MemoryReport?serverIp=${serverIp}&gc=true">Include GC</a></br>
	</c:otherwise>
</c:choose>

<pre>
${memoryReport}
</pre>