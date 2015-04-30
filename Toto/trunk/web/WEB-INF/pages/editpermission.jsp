<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 29/04/15
  Time: 11:49
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <title>Edit/New Permission</title>
</head>
<body>
Edit/New Permission<br/>
<form action="/api/ManagePermissions" method="post">
  <input type="hidden" name="editId" value="${id}"/>
  <!-- no business id -->
  <table>
    <tr>
      <td>Database</td>
      <td><select name="databaseId">
        <c:forEach items="${databases}" var="database">
          <option value="${database.id}"<c:if test="${database.id = databaseId}"> selected</c:if>>${database.name}</option>
        </c:forEach>
        </select>
      </td>
    </tr>
    <tr>
      <td>User</td>
      <td><select name="userId">
        <c:forEach items="${users}" var="user">
          <option value="${user.id}"<c:if test="${user.id = userId}"> selected</c:if>>${user.email}</option>
        </c:forEach>
      </select>
      </td>
    </tr>
    <tr>
      <td>Start Date</td>
      <td><input name="startDate" value="${startDate}"></td>
    </tr>
    <tr>
      <td>End Date</td>
      <td><input name="endDate" value="${endDate}"></td>
    </tr>
    <tr>
      <td>Read List</td>
      <td><input name="readList" value="${readList}"></td>
    </tr>
    <tr>
      <td>Write List</td>
      <td><input name="writeList" value="${writeList}"></td>
    </tr>
  </table>
  ${error}
  <input type="submit" name="submit" value="Submit"/>
</form>

</body>
</html>
