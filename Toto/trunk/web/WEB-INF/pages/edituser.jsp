<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 28/04/15
  Time: 12:10
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <link rel='stylesheet' id='bootstrap-css'  href='/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap.css?ver=3.9.5' type='text/css' media='all' />
  <link rel='stylesheet' id='bootstrap-responsive-css'  href='https://www.azquo.com/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap-responsive.css?ver=3.9.5' type='text/css' media='all' />
  <title>Edit/New User</title>
</head>
<body>
Edit/New User<br/>
<form action="/api/ManageUsers" method="post">
  <input type="hidden" name="editId" value="${id}"/>
  <!-- no business id -->
 <div class="admintable">
<table>
  <tr>
    <td>Start Date</td>
    <td>${startDate}</td>
  </tr>
  <tr>
    <td>End Date</td>
    <td><input name="endDate" value="${endDate}"></td>
  </tr>
  <tr>
    <td>Email</td>
    <td><input name="email" value="${email}"></td>
  </tr>
  <tr>
    <td>Name</td>
    <td><input name="name" value="${name}"></td>
  </tr>
  <tr>
    <td>Status</td>
    <td><input name="status" value="${status}"></td>
  </tr>
  <tr>
    <td>Password</td>
    <td><input name="password"></td>
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
