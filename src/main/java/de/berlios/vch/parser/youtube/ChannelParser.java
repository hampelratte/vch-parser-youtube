package de.berlios.vch.parser.youtube;

import static de.berlios.vch.parser.youtube.YoutubeParser.CHARSET;

import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.OverviewPage;

public class ChannelParser {

    private YoutubeParser youtubeParser;

    private IOverviewPage channelPage;

    private JSONObject relatedPlaylists;

    public ChannelParser(YoutubeParser youtubeParser) {
        this.youtubeParser = youtubeParser;
    }

    public IOverviewPage parse(IOverviewPage page) throws Exception {
        this.channelPage = page;

        String channelId = page.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&id=" + channelId;
        String json = HttpUtils.get(uri, youtubeParser.createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONObject item = response.getJSONArray("items").getJSONObject(0);
        relatedPlaylists = item.getJSONObject("contentDetails").getJSONObject("relatedPlaylists");

        // add uploaded videos
        addRelatedPlaylist("uploads", "I18N_VIDEOS");

        // add playlists
        IOverviewPage playlists = new OverviewPage();
        playlists.setParser(youtubeParser.getId());
        playlists.setTitle(youtubeParser.resourceBundle.getString("I18N_PLAYLISTS"));
        playlists.setUri(new URI("yt://playlists/" + channelId));
        channelPage.getPages().add(playlists);

        // add favorites
        addRelatedPlaylist("favorites", "I18N_FAVORITES");

        // add likes
        addRelatedPlaylist("likes", "I18N_LIKES");

        return channelPage;
    }

    private void addRelatedPlaylist(String name, String translation) throws Exception {
        if (relatedPlaylists.has(name)) {
            OverviewPage page = new OverviewPage();
            page.setParser(youtubeParser.getId());
            page.setTitle(youtubeParser.resourceBundle.getString(translation));
            page.setUri(new URI("yt://playlist/" + relatedPlaylists.getString(name)));
            channelPage.getPages().add(page);
        }
    }

    void parseChannelPlaylists(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String channelId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/playlists?part=snippet&maxResults=30&channelId=" + channelId;
        String json = HttpUtils.get(uri, youtubeParser.createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject playlist = items.getJSONObject(i);
            JSONObject snippet = playlist.getJSONObject("snippet");
            IOverviewPage playlistPage = new OverviewPage();
            playlistPage.setParser(youtubeParser.getId());
            playlistPage.setTitle(snippet.getString("title"));
            playlistPage.setUri(new URI("yt://playlist/" + playlist.getString("id")));
            opage.getPages().add(playlistPage);
        }
    }

}
