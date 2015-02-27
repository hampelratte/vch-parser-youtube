package de.berlios.vch.parser.youtube;

import java.util.prefs.Preferences;

import org.osgi.service.log.LogService;

import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;

public class SearchParser extends AbstractVideoListParser {

    public SearchParser(YoutubeParser youtubeParser, LogService logger, Preferences prefs) {
        super(youtubeParser, logger, prefs);
    }

    void parseSearch(IWebPage page) throws Exception {
        IOverviewPage opage = (IOverviewPage) page;
        parseVideoList(opage, page.getUri().toString());
    }
}
