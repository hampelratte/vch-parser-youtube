<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${TITLE}</h1>

${I18N_PERMISSION_DESC}
<br/>
<br/>
<a href="https://accounts.google.com/o/oauth2/auth?device_id=1&device_name=vdr&client_id=714957467037-ve0fnv569lspqbm7q26dn95rs2i373f4.apps.googleusercontent.com&redirect_uri=${CALLBACK_URI?url}&scope=https://www.googleapis.com/auth/youtube&response_type=code&access_type=offline">${I18N_GRANT_PERMISSION}</a>
<br/>
<br/>
<a href="https://security.google.com/settings/security/permissions">${I18N_REVOKE_PERMISSION}</a>
<br/>
<br/>
<fieldset>
    <legend>${I18N_TECHNICAL_DETAILS}:</legend>
    <ul>
        <li>Access Token: ${ACCESS_TOKEN}</li>
        <li>${I18N_EXPIRES}: ${EXPIRES}</li>
        <li>Refresh Token: ${REFRESH_TOKEN}</li>
    </ul>
</fieldset>
<#include "footer.ftl">