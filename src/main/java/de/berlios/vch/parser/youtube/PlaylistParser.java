package de.berlios.vch.parser.youtube;

import static de.berlios.vch.parser.youtube.YoutubeParser.CHARSET;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.prefs.Preferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;

public class PlaylistParser {

    private YoutubeParser youtubeParser;
    private LogService logger;
    private Preferences prefs;

    public PlaylistParser(YoutubeParser youtubeParser, LogService logger, Preferences prefs) {
        this.youtubeParser = youtubeParser;
        this.logger = logger;
        this.prefs = prefs;
    }

    void parsePlaylist(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String playlistId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=30&playlistId=" + playlistId;
        String json = HttpUtils.get(uri, youtubeParser.createHeaders(), CHARSET);
        JSONObject response = new JSONObject(json);
        JSONArray items = response.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject snippet = item.getJSONObject("snippet");
            YoutubeVideoPageProxy video = new YoutubeVideoPageProxy(logger, prefs);
            video.setParser(youtubeParser.getId());
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
}
