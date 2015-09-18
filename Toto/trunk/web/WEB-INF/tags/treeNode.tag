<%@ attribute name="node" required="true" type="com.azquo.memorydb.TreeNode" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="azquoTags" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:if test="${!empty node}">
    <div style="position:relative;left:50px">
        <c:if test="${!empty node.link}"><a href="${node.link}"></c:if>
                ${node.value} <c:if test="${!empty node.heading}"> <b>${node.heading}</b></c:if>
            <c:if test="${!empty node.name}"> ${node.name}</c:if>
        <c:if test="${!empty node.link}"></a></c:if>
        <c:if test="${!empty node.children}">
            <c:forEach var="child" items="${node.children}">
                <azquoTags:treeNode node="${child}"/>
            </c:forEach>
        </c:if>
   </div>
</c:if>