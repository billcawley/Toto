<%@ attribute name="node" required="true" type="com.azquo.memorydb.TreeNode" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="azquoTags" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:if test="${!empty node}">
    <div style="position:relative;left:50px">
                ${node.value} <c:if test="${!empty node.heading}"> <b>${node.heading}</b></c:if>
            <c:if test="${!empty node.name}"> ${node.name}</c:if>
            <!-- there was link here, not being used so removed -->
        <c:if test="${!empty node.children}">
            <c:forEach var="child" items="${node.children}">
                <azquoTags:treeNode node="${child}"/>
            </c:forEach>
        </c:if>
   </div>
</c:if>