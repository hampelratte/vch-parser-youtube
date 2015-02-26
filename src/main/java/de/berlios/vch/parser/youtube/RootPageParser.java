package de.berlios.vch.parser.youtube;

import static de.berlios.vch.parser.youtube.YoutubeParser.CHARSET;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;

import org.json.JSONException;
import org.json.JSONObject;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.OverviewPage;

public class RootPageParser {

    private YoutubeParser youtubeParser;

    private IOverviewPage root;

    private JSONObject relatedPlaylists;

    public RootPageParser(YoutubeParser youtubeParser) {
        this.youtubeParser = youtubeParser;
    }

    public IOverviewPage parseRoot() throws Exception {
        root = new OverviewPage();
        root.setParser(youtubeParser.getId());
        root.setTitle("Youtube");
        root.setUri(new URI("vchpage://localhost/" + youtubeParser.getId()));

        addSubscriptions();

        relatedPlaylists = getRelatedPlaylists();
        addRelatedPlaylist("favorites", "I18N_FAVORITES");
        addRelatedPlaylist("watchLater", "I18N_WATCH_LATER");
        addRelatedPlaylist("likes", "I18N_LIKES");
        addRelatedPlaylist("watchHistory", "I18N_WATCH_HISTORY");

        return root;
    }

    private void addRelatedPlaylist(String name, String translation) throws Exception {
        if (relatedPlaylists.has(name)) {
            OverviewPage page = new OverviewPage();
            page.setParser(youtubeParser.getId());
            page.setTitle(youtubeParser.resourceBundle.getString(translation));
            page.setUri(new URI("yt://playlist/" + relatedPlaylists.getString(name)));
            root.getPages().add(page);
        }
    }

    private JSONObject getRelatedPlaylists() throws UnsupportedEncodingException, IOException, JSONException {
        youtubeParser.checkAndRefreshToken();
        String content = HttpUtils.get("https://www.googleapis.com/youtube/v3/channels?part=contentDetails&mine=true", youtubeParser.createHeaders(), CHARSET);
        JSONObject response = new JSONObject(content);
        JSONObject item = response.getJSONArray("items").getJSONObject(0);
        JSONObject relatedPlaylists = item.getJSONObject("contentDetails").getJSONObject("relatedPlaylists");
        return relatedPlaylists;
    }

    private void addSubscriptions() throws Exception {
        OverviewPage subscriptions = new OverviewPage();
        subscriptions.setParser(youtubeParser.getId());
        subscriptions.setTitle(youtubeParser.resourceBundle.getString("I18N_SUBSCRIPTIONS"));
        subscriptions.setUri(new URI("yt://subscriptions"));
        root.getPages().add(subscriptions);
    }
}
