<%--
Copyright (C) 2016 Azquo Ltd.

Created by IntelliJ IDEA.
  User: cawley
  Date: 09/07/15
  Time: 15:19
  To change this template use File | Settings | File Templates.

  Simple page for user registration.

--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>New Business</title>
</head>
<body>
New Business<br/>

<form action="/api/NewBusiness" method="post">
    <!-- no business id -->
    <div class="admintable">
        <table>
            <tr>
                <td>Business Name</td>
                <td><input name="businessName" value="${businessName}"></td>
            </tr>
            <tr>
                <td>Address 1</td>
                <td><input name="address1" value="${address1}"></td>
            </tr>
            <tr>
                <td>Address 2</td>
                <td><input name="address2" value="${address2}"></td>
            </tr>
            <tr>
                <td>Address 3</td>
                <td><input name="address3" value="${address3}"></td>
            </tr>
            <tr>
                <td>Address 4</td>
                <td><input name="address4" value="${address4}"></td>
            </tr>
            <tr>
                <td>Postcode</td>
                <td><input name="postcode" value="${postcode}"></td>
            </tr>
            <tr>
                <td>Telephone</td>
                <td><input name="telephone" value="${telephone}"></td>
            </tr>
            <tr>
                <td>Fax</td>
                <td><input name="fax" value="${fax}"></td>
            </tr>
            <tr>
                <td>Website</td>
                <td><input name="website" value="${website}"></td>
            </tr>
            <tr>
                <td>Email/Username</td>
                <td><input name="emailUsername" value="${emailUsername}"></td>
            </tr>
            <tr>
                <td>Password</td>
                <td><input name="password" type="password"></td>
            </tr>
            <tr>
                <td>Confirm Password</td>
                <td><input name="confirmPassword" type="password"></td>
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
