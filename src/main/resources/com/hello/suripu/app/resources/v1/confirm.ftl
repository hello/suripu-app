<#-- @ftlvariable name="" type="com.hello.suripu.app.resources.v1.ConfirmView" -->
<html>
<head>
    <style>
        input[type="submit"] {
            background: #444;
            color: #FFF;
            font-weight: bold;
        }
        input[type="text"], input[type="scope"], input[type="submit"], .service {
            border: 1px solid transparent;
            border-radius: 4px;
            display: block;
            font-size: 14px;
            height: 42px;
            width: 100%;
        }
        input[type="text"], input[type="scope"] {
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

<form accept-charset="UTF-8" action="${confirm.redirectURI?html}" autocomplete="off" class="" id="login-form" method="GET">
    <input type="hidden" name="client_request" value="${confirm.clientRequest?html}">
    <input type="hidden" name="confirmation" value="true">
    <div class="credentials">
        <label for="scope">Requested Permissions</label>
        <input autocomplete="on" autofocus="autofocus" id="scope" name="scope" value="${confirm.scope?html}" size="30" type="scope">
    </div>

    <div class="clear"></div>

    <input class="button primary" name="commit" type="submit" value="Grant Permissions">
</form>

<form method="POST" action="/v1/oauth2/logout">
    <input class="button primary" name="logout" type="submit" value="Not you? Sign Out">
</form>
</body>
</html>