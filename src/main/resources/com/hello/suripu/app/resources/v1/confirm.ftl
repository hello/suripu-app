<#-- @ftlvariable name="" type="com.hello.suripu.app.resources.v1.ConfirmView" -->
<html>
<body>
<h3>Hey, ${confirm.userName?html}. <br/>
${confirm.applicationName?html} is requesting the following access to your Sense account: </h3>
<form method="GET" action="${confirm.redirectURI?html}" name="hello_confirmation">
    <input type="input" name="scope" size=50 value="${confirm.scope?html}"><br/>
    <input type="submit">
    <input type="hidden" name="client_request" value="${confirm.clientRequest?html}"><br/>
    <input type="hidden" name="confirmation" value="true"><br/>
</form>
<form method="POST" action="/v1/oauth2/logout">
    <input type="submit" value="This isn't me. Logout.">
</form>

</body>
</html>