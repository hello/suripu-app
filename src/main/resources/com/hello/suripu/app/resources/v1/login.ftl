<#-- @ftlvariable name="" type="com.hello.suripu.app.resources.v1.LoginView" -->
<html>
<body>
<form method="post" action="/v1/oauth2/login">
    <input type="input" name="username" value="josef@sayhello.com"><br/>
    <input type="input" name="password" value="josefdev"><br/>
    <input type="input" name="client_id" value="${login.clientId?html}"><br/>
    <input type="input" name="state" value="${login.state?html}"><br/>
    <input type="input" name="scope" value="${login.scope?html}"><br/>
    <input type="submit">
</form>
<h1>Hello! Welcome to ${login.name?html}!</h1>
</body>
</html>