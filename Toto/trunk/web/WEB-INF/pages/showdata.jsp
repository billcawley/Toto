<%@ page import="com.azquo.memorydb.TreeNode" %>
<%--
  Created by IntelliJ IDEA.
  User: bill
  Date: 05/08/15
  Time: 12:19
  To change this template use File | Settings | File Templates.
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
