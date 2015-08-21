<%@ page import="com.azquo.memorydb.TreeNode" %>
<%--
  Created by IntelliJ IDEA.
  User: bill
  Date: 05/08/15
  Time: 12:19
  To change this template use File | Settings | File Templates.

  // edd added paste from stack overflow - this is what we should probably use instead

<%@ attribute name="list" required="true" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="myTags" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:if test="${!empty list}">
    <ul>
    <c:forEach var="folderGroup" items="${list}">
        <li><c:out value="${folderGroup.name}"/></li>
        <myTags:folderGroups list="${folderGroup.subGroups}"/>
    </c:forEach>
    </ul>
</c:if>

The tag calls itself recursively to generate a folder tree.

And inside your JSP, do

<%@ taglib tagdir="/WEB-INF/tags" prefix="myTags" %>
...
<myTags:folderGroups list="${info.folderGroups}"/>


--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title></title>
</head>
<body>
<%!

  public String showNode(TreeNode node){

    StringBuilder output = new StringBuilder();
    String text = node.getValue();
    if (node.getHeading()!=null)  text +=" <b>" + node.getHeading() + "</b>";
    if (node.getName() != null) text += " " + node.getName();

    output.append("<div style=\"position:relative;left:50px\">\n");
    if (node.getLink()!=null && node.getLink().length() > 0){
      output.append("<a href=\"" + node.getLink() + "\">" + text + "</a>");

    }else{
      output.append(text);

    }
    if (node.getChildren()!=null){
      for (TreeNode child:node.getChildren()){
        output.append(showNode(child));
      }
    }
    output.append("</div>");
    return output.toString();


}




%>
<% TreeNode node = (TreeNode)request.getAttribute("node"); %>
    <%=showNode(node)%>

</body>
</html>
