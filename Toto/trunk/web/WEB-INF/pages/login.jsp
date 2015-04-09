<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 08/04/15
  Time: 15:41
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Azquo Login</title>
</head>
<body>

<form>


  User/Email : <input name="user" value="${userEmail}"/><br/>
  Password : <input name="password" type="password"/><br/>${error}

<input type="submit" name="Submit" value="Submit"/>


</form>


</body>
</html>
