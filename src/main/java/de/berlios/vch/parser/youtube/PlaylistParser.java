package de.berlios.vch.parser.youtube;

import java.util.prefs.Preferences;

import org.osgi.service.log.LogService;

import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;

public class PlaylistParser extends AbstractVideoListParser {

    public PlaylistParser(YoutubeParser youtubeParser, LogService logger, Preferences prefs) {
        super(youtubeParser, logger, prefs);
    }

    void parsePlaylist(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        String playlistId = opage.getUri().getPath().substring(1);
        String uri = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=30&playlistId=" + playlistId;
        parseVideoList(opage, uri);
    }
}
