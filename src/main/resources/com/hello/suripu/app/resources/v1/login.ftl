<#-- @ftlvariable name="" type="com.hello.suripu.app.resources.v1.LoginView" -->
<html>
<head>
    <style>
input[type="submit"] {
    background: #444;
    color: #FFF;
    font-weight: bold;
}
input[type="text"], input[type="email"], input[type="password"], input[type="submit"], .service {
    border: 1px solid transparent;
    border-radius: 4px;
    display: block;
    font-size: 14px;
    height: 42px;
    width: 100%;
}
input[type="text"], input[type="email"], input[type="password"] {
    border-color: #CCC;
    box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.075);
    color: #555;
    margin-bottom: 10px;
    padding: 12px;
}
    </style>
</head>
<body>
<h2>Sign in to Hello</h2>

<form accept-charset="UTF-8" action="${login.submitURI?html}" autocomplete="off" class="" id="login-form" method="GET">
    <input type="hidden" name="client_request" value="${login.clientRequest?html}">
    <div class="credentials">
        <label for="user_email">Email</label>
        <input autocomplete="on" autofocus="autofocus" id="username" name="username" placeholder="Email" size="30" type="email">
        <label for="user_password">Password</label>
        <input autocomplete="off" id="password" name="password" placeholder="Password" size="30" type="password">
    </div>

    <div class="clear"></div>

    <input class="button primary" name="commit" type="submit" value="Sign in">
</form>
</body>
</html>