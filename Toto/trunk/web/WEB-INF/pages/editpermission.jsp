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
  <link rel='stylesheet' id='bootstrap-css'  href='/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap.css?ver=3.9.5' type='text/css' media='all' />
  <link rel='stylesheet' id='bootstrap-responsive-css'  href='https://www.azquo.com/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap-responsive.css?ver=3.9.5' type='text/css' media='all' />
  <title>Edit/New Permission</title>
</head>
<body>
Edit/New Permission<br/>
<form action="/api/ManagePermissions" method="post">
  <input type="hidden" name="editId" value="${id}"/>
  <!-- no business id -->
  <div class="admintable">
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
   </div>
  <div class="error">
    ${error}
    </div>
  <div class="submit">
  <input type="submit" name="submit" value="Submit"/>
    </div>
</form>

</body>
</html>
