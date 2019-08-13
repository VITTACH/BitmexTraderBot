<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!-- заимпортим спринговскую библиотеку форм, умеет работать с models -->
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<head>
    <title>TraderGhost preferences</title>
</head>
<body>
<form:form method="POST" modelAttribute="userModel" action="checkLogin">
    <table>
        <tr>
            <td>UserName:</td>
            <td><form:input path="userName"/></td>
        </tr>
        <tr>
            <td>Password:</td>
            <td><form:password path="password"/></td>
        </tr>
        <tr>
            <td>
                <input type="submit" value="Login"/>
            </td>
        </tr>
    </table>
</form:form>
</body>
</html>