package de.berlios.vch.parser.youtube;

import static de.berlios.vch.parser.youtube.YoutubeParser.CHARSET;
import static de.berlios.vch.parser.youtube.YoutubeParser.CLIENT_ID;
import static de.berlios.vch.parser.youtube.YoutubeParser.CLIENT_SECRET;
import static de.berlios.vch.parser.youtube.YoutubeParser.GOOGLE_OAUTH_URI;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;

import de.berlios.vch.config.ConfigService;
import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.web.TemplateLoader;
import de.berlios.vch.web.servlets.VchHttpServlet;

@Component
public class OauthServlet extends VchHttpServlet {

    public static String PATH = "/config/parser/youtube/oauth";

    @Requires(filter = "(instance.name=vch.parser.youtube)")
    private IWebParser parser;

    @Requires(filter = "(instance.name=vch.parser.youtube)")
    private ResourceBundleProvider rbp;

    @Requires
    private LogService logger;

    @Requires
    private TemplateLoader templateLoader;

    @Requires
    private HttpService httpService;

    @Requires
    private ConfigService cs;
    private Preferences prefs;

    private BundleContext ctx;

    public OauthServlet(BundleContext ctx) {
        this.ctx = ctx;
    }

    public OauthServlet(YoutubeParser parser, Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    protected void get(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, Object> params = new HashMap<String, Object>();

        String singleUseAuthCode = req.getParameter("code");
        if (singleUseAuthCode == null) {
            params.put("OAUTH_RESULT", rbp.getResourceBundle().getString("I18N_LOGIN_FAILED"));
        } else {
            try {
                requestAccessToken(req.getLocalAddr(), req.getLocalPort(), singleUseAuthCode);
                params.put("OAUTH_RESULT", rbp.getResourceBundle().getString("I18N_LOGIN_SUCCESS"));
            } catch (Exception e) {
                logger.log(LogService.LOG_ERROR, "Couldn't obtain an access token", e);
                params.put("OAUTH_RESULT", rbp.getResourceBundle().getString("I18N_LOGIN_FAILED"));
            }
        }

        params.put("TITLE", rbp.getResourceBundle().getString("I18N_YOUTUBE_CONFIG"));
        params.put("NOTIFY_MESSAGES", getNotifyMessages(req));

        String page = templateLoader.loadTemplate("oauthYoutube.ftl", params);
        resp.getWriter().print(page);
    }

    private void requestAccessToken(String ipAddress, int port, String singleUseAuthCode) throws UnsupportedEncodingException, IOException, JSONException {
        // @formatter:off
        String params = "code=" + singleUseAuthCode + "&" + 
                "client_id=" + CLIENT_ID + "&" + 
                "client_secret=" + CLIENT_SECRET + "&" + 
                "redirect_uri=http://" + ipAddress + ":" + port + PATH + "&" + 
                "grant_type=authorization_code";
        // @formatter:on

        String response = HttpUtils.post(GOOGLE_OAUTH_URI, null, params.getBytes(CHARSET), CHARSET);
        JSONObject json = new JSONObject(response);
        String accessToken = json.getString("access_token");
        String refreshToken = json.getString("refresh_token");
        int expiresIn = json.getInt("expires_in");
        long expirationTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn);
        prefs.put("oauth.token.access", accessToken);
        prefs.put("oauth.token.refresh", refreshToken);
        prefs.putLong("oauth.token.expires", expirationTime);

        logger.log(LogService.LOG_INFO, accessToken + " " + refreshToken + " " + expiresIn);
    }

    @Override
    protected void post(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        get(req, resp);
    }

    @Validate
    public void start() throws ServletException, NamespaceException {
        prefs = cs.getUserPreferences(ctx.getBundle().getSymbolicName());

        registerServlet();
    }

    private void registerServlet() throws ServletException, NamespaceException {
        // register the servlet
        httpService.registerServlet(PATH, this, null, null);
    }

    @Invalidate
    public void stop() {
        prefs = null;

        // unregister the config servlet
        if (httpService != null) {
            httpService.unregister(PATH);
        }
    }
}
