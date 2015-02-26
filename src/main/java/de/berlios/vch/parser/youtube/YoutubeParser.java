package de.berlios.vch.parser.youtube;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.StringTokenizer;
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
    private Preferences prefs;

    @Requires
    private LogService logger;

    private BundleContext ctx;

    private ResourceBundle resourceBundle;

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
        OverviewPage root = new OverviewPage();
        root.setParser(getId());
        root.setTitle("Youtube");
        root.setUri(new URI("vchpage://localhost/" + getId()));

        OverviewPage subscriptions = new OverviewPage();
        subscriptions.setParser(getId());
        subscriptions.setTitle("Subscriptions");
        subscriptions.setUri(new URI("yt://subscriptions"));
        root.getPages().add(subscriptions);

        return root;
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
            parseChannel(page);
        } else if (uri.startsWith("yt://playlist/")) {
            parsePlaylist(page);
        } else if (uri.startsWith("yt://playlists/")) {
            parseChannelPlaylists(page);
        }
        return page;
    }

    private void parseChannelPlaylists(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String channelId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/playlists?part=snippet&maxResults=30&channelId=" + channelId;
        String json = HttpUtils.get(uri, createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject playlist = items.getJSONObject(i);
            JSONObject snippet = playlist.getJSONObject("snippet");
            IOverviewPage playlistPage = new OverviewPage();
            playlistPage.setParser(getId());
            playlistPage.setTitle(snippet.getString("title"));
            playlistPage.setUri(new URI("yt://playlist/" + playlist.getString("id")));
            opage.getPages().add(playlistPage);
        }
    }

    private void parsePlaylist(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String playlistId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=30&playlistId=" + playlistId;
        String json = HttpUtils.get(uri, createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject snippet = item.getJSONObject("snippet");
            YoutubeVideoPageProxy video = new YoutubeVideoPageProxy(logger, prefs);
            video.setParser(getId());
            video.setTitle(snippet.getString("title"));
            video.setDescription(snippet.getString("description"));
            video.setPublishDate(parsePublishDate(snippet));
            video.setThumbnail(parseThumbnail(snippet));
            String videoId = snippet.getJSONObject("resourceId").getString("videoId");
            video.setUri(new URI("https://www.youtube.com/watch?v=" + videoId));
            video.setVideoUri(new URI("yt://video/" + videoId));
            opage.getPages().add(video);
        }
    }

    private Calendar parsePublishDate(JSONObject snippet) {
        if (snippet.has("publishedAt")) {
            try {
                String dateString = snippet.getString("publishedAt");
                Date publishedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.000Z'").parse(dateString);
                Calendar pubDate = Calendar.getInstance();
                pubDate.setTime(publishedAt);
                return pubDate;
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
            }
        }
        return null;
    }

    private URI parseThumbnail(JSONObject snippet) {
        if (snippet.has("thumbnails")) {
            try {
                JSONObject thumbs = snippet.getJSONObject("thumbnails");
                String size = thumbs.has("medium") ? "medium" : "default";
                JSONObject thumb = thumbs.getJSONObject(size);
                return new URI(thumb.getString("url"));
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't parse thumbnail", e);
            }
        }
        return null;
    }

    private void parseChannel(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String channelId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&id=" + channelId;
        String json = HttpUtils.get(uri, createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONObject item = response.getJSONArray("items").getJSONObject(0);
        JSONObject relatedPlaylists = item.getJSONObject("contentDetails").getJSONObject("relatedPlaylists");

        IOverviewPage uploadsPage = new OverviewPage();
        uploadsPage.setParser(getId());
        uploadsPage.setTitle("Videos");
        uploadsPage.setUri(new URI("yt://playlist/" + relatedPlaylists.getString("uploads")));
        opage.getPages().add(uploadsPage);

        // add playlists
        IOverviewPage playlists = new OverviewPage();
        playlists.setParser(getId());
        playlists.setTitle("Playlists");
        playlists.setUri(new URI("yt://playlists/" + channelId));
        parseChannelPlaylists(playlists);
        opage.getPages().add(playlists);

        if (relatedPlaylists.has("favorites")) {
            IOverviewPage favoritesPage = new OverviewPage();
            favoritesPage.setParser(getId());
            favoritesPage.setTitle("Favorites");
            favoritesPage.setUri(new URI("yt://playlist/" + relatedPlaylists.getString("favorites")));
            opage.getPages().add(favoritesPage);
        }

        if (relatedPlaylists.has("likes")) {
            IOverviewPage likedPage = new OverviewPage();
            likedPage.setParser(getId());
            likedPage.setTitle("Likes");
            likedPage.setUri(new URI("yt://playlist/" + relatedPlaylists.getString("likes")));
            opage.getPages().add(likedPage);
        }
    }

    private void parseSubscriptions(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String uri = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=30&order=alphabetical";
        String json = HttpUtils.get(uri, createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
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
    }

    private Map<String, String> createHeaders() {
        String accessToken = prefs.get("oauth.token.access", "");
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }

    private void checkAndRefreshToken() throws UnsupportedEncodingException, IOException, JSONException {
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

    public Map<String, Object> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (query != null) {
            StringTokenizer st = new StringTokenizer(query, "&");
            while (st.hasMoreTokens()) {
                String keyValue = st.nextToken();
                StringTokenizer st2 = new StringTokenizer(keyValue, "=");
                String key = null;
                String value = "";
                if (st2.hasMoreTokens()) {
                    key = st2.nextToken();
                    key = URLDecoder.decode(key, "UTF-8");
                }

                if (st2.hasMoreTokens()) {
                    value = st2.nextToken();
                    value = URLDecoder.decode(value, "UTF-8");
                }

                logger.log(LogService.LOG_DEBUG, "Found key value pair: " + key + "," + value);
                if (parameters.containsKey(key)) {
                    logger.log(LogService.LOG_DEBUG, "Key already exists. Assuming array of values. Will bes tored in a list");
                    Object o = parameters.get(key);
                    if (o instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> values = (List<String>) o;
                        values.add(value);
                    } else if (o instanceof String) {
                        List<String> values = new ArrayList<String>();
                        values.add((String) o);
                        values.add(value);
                        parameters.put(key, values);
                    }
                } else {
                    parameters.put(key, value);
                }
            }
        }
        return parameters;
    }

}
