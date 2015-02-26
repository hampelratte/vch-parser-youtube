<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${TITLE}</h1>

VCH benötigt Deine Zustimmung, um auf Dein Youtube-Konto zugreifen zu können.
<br/>
<br/>
<a href="https://accounts.google.com/o/oauth2/auth?device_id=1&device_name=vdr&client_id=714957467037-ve0fnv569lspqbm7q26dn95rs2i373f4.apps.googleusercontent.com&redirect_uri=${CALLBACK_URI?url}&scope=https://www.googleapis.com/auth/youtube&response_type=code&access_type=offline">Berechtigung erteilen</a>
<br/>
<br/>
<a href="https://security.google.com/settings/security/permissions">Berechtigung widerrufen</a>
<br/>
<br/>
<fieldset>
    <legend>Technische Details:</legend>
    <ul>
        <li>Access Token: ${ACCESS_TOKEN}</li>
        <li>Läuft ab: ${EXPIRES}</li>
        <li>Refresh Token: ${REFRESH_TOKEN}</li>
    </ul>
</fieldset>
<#include "footer.ftl">