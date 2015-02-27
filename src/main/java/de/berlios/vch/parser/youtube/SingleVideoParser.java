package de.berlios.vch.parser.youtube;

import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.OverviewPage;

public class SingleVideoParser extends AbstractVideoListParser {

    public SingleVideoParser(YoutubeParser youtubeParser, LogService logger, Preferences prefs) {
        super(youtubeParser, logger, prefs);
    }

    IVideoPage parseVideo(IWebPage page) throws Exception {
        Map<String, List<String>> params = HttpUtils.parseQuery(page.getUri().getQuery());
        String videoId = params.get("v").get(0);
        String uri = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=" + videoId;
        IOverviewPage opage = new OverviewPage();
        parseVideoList(opage, uri);
        return (IVideoPage) opage.getPages().get(0);
    }
}
