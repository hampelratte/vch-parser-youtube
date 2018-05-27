package de.berlios.vch.parser.youtube;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import de.berlios.vch.config.ConfigService;
import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.i18n.ResourceBundleLoader;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.web.menu.IWebMenuEntry;
import de.berlios.vch.web.menu.WebMenuEntry;

@Component
@Provides
public class YoutubeParser implements IWebParser, ResourceBundleProvider {

    // oauth constants
    static final String GOOGLE_OAUTH_URI = "https://accounts.google.com/o/oauth2/token";
    static final String CHARSET = "UTF-8";
    static final String CLIENT_ID = "714957467037-ve0fnv569lspqbm7q26dn95rs2i373f4.apps.googleusercontent.com";
    static final String CLIENT_SECRET = "2xnXB34wJAbpj2vza1qmnYCA";

    @Requires
    private ConfigService cs;
    Preferences prefs;

    @Requires
    LogService logger;

    private BundleContext ctx;

    ResourceBundle resourceBundle;

    private ServiceRegistration<IWebMenuEntry> menuReg;

    public YoutubeParser(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String getId() {
        return YoutubeParser.class.getName();
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        return new RootPageParser(this).parseRoot();
    }

    @Override
    public String getTitle() {
        return "Youtube";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        checkAndRefreshToken();

        String uri = page.getUri().toString();
        if ("yt://subscriptions".equals(uri)) {
            parseSubscriptions(page);
        } else if (uri.startsWith("yt://channel/")) {
            IOverviewPage opage = (IOverviewPage) page;
            page = new ChannelParser(this).parse(opage);
        } else if (uri.startsWith("yt://playlist/")) {
            new PlaylistParser(this, logger, prefs).parsePlaylist(page);
        } else if (uri.startsWith("yt://playlists/")) {
            new ChannelParser(this).parseChannelPlaylists(page);
        } else if (uri.startsWith("https://www.youtube.com/watch?v=")) {
            page = new SingleVideoParser(this, logger, prefs).parseVideo(page);
        } else if (uri.contains("/search?")) {
            new SearchParser(this, logger, prefs).parseSearch(page);
        }
        return page;
    }

    private void parseSubscriptions(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        int maxresults = 50;
        int fetched = 0;
        int total = 0;
        String baseUri = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults="+maxresults+"&order=alphabetical";
        String uri = baseUri;
        do {
            String json = HttpUtils.get(uri, createHeaders(), CHARSET);
            JSONObject response = new JSONObject(json);
            JSONObject pageInfo = response.getJSONObject("pageInfo");
            total = pageInfo.getInt("totalResults");
            if(response.has("nextPageToken") && !response.isNull("nextPageToken")) {
                uri = baseUri + "&pageToken=" + response.getString("nextPageToken");
            }
            JSONArray items = response.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject sub = items.getJSONObject(i);
                JSONObject snippet = sub.getJSONObject("snippet");
                IOverviewPage channelPage = new OverviewPage();
                channelPage.setParser(getId());
                channelPage.setTitle(snippet.getString("title"));
                channelPage.setUri(new URI("yt://channel/" + snippet.getJSONObject("resourceId").getString("channelId")));
                opage.getPages().add(channelPage);
            }
            fetched += maxresults;
        } while(fetched < total);
    }

    Map<String, String> createHeaders() {
        String accessToken = prefs.get("oauth.token.access", "");
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }

    void checkAndRefreshToken() throws UnsupportedEncodingException, IOException, JSONException {
        if (tokenNeedsRefresh()) {
            logger.log(LogService.LOG_DEBUG, "Access token needs a refresh");
            refreshToken();
        }
    }

    /**
     * Checks, if the access token is about to expire
     *
     * @return true, if the access token expires in less than 1 minute
     */
    private boolean tokenNeedsRefresh() {
        long now = System.currentTimeMillis();
        long expirationTime = prefs.getLong("oauth.token.expires", 0);
        return (expirationTime - now) < TimeUnit.MINUTES.toMillis(1);
    }

    /**
     * Uses the user's refresh token to obtain a new access token, which then is valid for about an hour
     *
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws JSONException
     */
    private void refreshToken() throws UnsupportedEncodingException, IOException, JSONException {
        String refreshToken = prefs.get("oauth.token.refresh", "");
        if (refreshToken.isEmpty()) {
            throw new RuntimeException("Please configure the youtube parser first");
        }

        // @formatter:off
        String params = "client_id=" + CLIENT_ID + "&" +
                "client_secret=" + CLIENT_SECRET + "&" +
                "refresh_token=" + refreshToken + "&" +
                "grant_type=refresh_token";
        // @formatter:on

        String response = HttpUtils.post(GOOGLE_OAUTH_URI, null, params.getBytes(CHARSET), CHARSET);
        JSONObject json = new JSONObject(response);
        String accessToken = json.getString("access_token");
        int expiresIn = json.getInt("expires_in");
        long expirationTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn);
        prefs.put("oauth.token.access", accessToken);
        prefs.putLong("oauth.token.expires", expirationTime);
    }

    @Validate
    public void start() {
        prefs = cs.getUserPreferences(ctx.getBundle().getSymbolicName());
        registerMenu();
    }

    private void registerMenu() {
        // register web interface menu
        IWebMenuEntry menu = new WebMenuEntry(getResourceBundle().getString("I18N_BROWSE"));
        menu.setPreferredPosition(Integer.MIN_VALUE + 1);
        menu.setLinkUri("#");
        SortedSet<IWebMenuEntry> childs = new TreeSet<IWebMenuEntry>();
        IWebMenuEntry entry = new WebMenuEntry();
        entry.setTitle(getTitle());
        entry.setLinkUri("/parser?id=" + getClass().getName());
        childs.add(entry);
        menu.setChilds(childs);
        childs = new TreeSet<IWebMenuEntry>();
        IWebMenuEntry open = new WebMenuEntry();
        open.setTitle(getResourceBundle().getString("I18N_OPEN"));
        open.setLinkUri(entry.getLinkUri());
        childs.add(open);
        entry.setChilds(childs);
        menuReg = ctx.registerService(IWebMenuEntry.class, menu, null);
    }

    @Invalidate
    public void stop() {
        prefs = null;

        // unregister the web menu
        if (menuReg != null) {
            menuReg.unregister();
        }
    }

    @Override
    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            try {
                logger.log(LogService.LOG_DEBUG, "Loading resource bundle for " + getClass().getSimpleName());
                resourceBundle = ResourceBundleLoader.load(ctx, Locale.getDefault());
            } catch (IOException e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load resource bundle", e);
            }
        }
        return resourceBundle;
    }
}
