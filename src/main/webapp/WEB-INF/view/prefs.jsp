<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!-- заимпортим спринговскую библиотеку форм, умеет работать с models -->
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<head>
    <title>TraderGhost preferences</title>
</head>
<body>
<h2>VITTACH</h2>

<form:form method="POST" action="stopClotho">
    <input type="submit" value="Stop"/>
</form:form>

<form:form method="POST" modelAttribute="prefModel" action="updatePref">
    <input type="submit" value="Apply"/>
    <table>
        <tr>
            <td>Telegram chatId:</td>
            <td><form:input path="telegramChatId"/></td>
        </tr>
        <tr>
            <td>Telegram Token:</td>
            <td><form:input path="telegramToken" size="50"/></td>
        </tr>
        <tr><td><br></td></tr>
        <tr>
            <td>Api key:</td>
            <td><form:input path="apiKey" size="25"/></td>
        </tr>
        <tr>
            <td>Secret key:</td>
            <td><form:input path="secretKey" size="50"/></td>
        </tr>
        <tr>
            <td>Url:</td>
            <td><form:input path="url"/></td>
        </tr>
        <tr><td><br></td></tr>
        <tr>
            <td>Price sensitive:</td>
            <td><form:input path="priceSensitive"/></td>
        </tr>
        <tr>
            <td>Count of orders:</td>
            <td><form:input path="countOfOrders"/></td>
        </tr>
        <tr>
            <td>Order volume:</td>
            <td><form:input path="orderVol"/></td>
        </tr>
        <tr><td><br></td></tr>
        <tr>
            <td>Price offset:</td>
            <td><form:input path="priceOffset"/></td>
        </tr>
        <tr>
            <td>Price step:</td>
            <td><form:input path="priceStep"/></td>
        </tr>
        <tr><td><br></td></tr>
        <tr>
            <td>Stop price bias:</td>
            <td><form:input path="stopPriceBias"/></td>
        </tr>
        <tr>
            <td>Stop price step:</td>
            <td><form:input path="stopPriceStep"/></td>
        </tr>
        <tr>
            <td>Stop price offset:</td>
            <td><form:input path="stopPxOffset"/></td>
        </tr>
    </table>
</form:form>
</body>
</html>