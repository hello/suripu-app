<#-- @ftlvariable name="" type="com.hello.suripu.app.resources.v1.LoginView" -->
<html>
<body>
<h1>Please log in to Hello to continue...</h1>
<form method="GET" action="${login.submitURI?html}" name="hello_login">
    Username:<br/>
    <input type="input" name="username"><br/>
    Password:<br/>
    <input type="input" name="password"><br/>
    <input type="submit" value="Login">
    <input type="hidden" name="client_request" value="${login.clientRequest?html}"><br/>
</form>
</body>
</html>