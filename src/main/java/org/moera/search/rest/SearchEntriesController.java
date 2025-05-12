package org.moera.search.rest;

import java.util.Collections;
import java.util.List;
import jakarta.inject.Inject;

import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SearchEntryInfo;
import org.moera.lib.node.types.SearchEntryType;
import org.moera.lib.node.types.SearchHashtagFilter;
import org.moera.lib.node.types.SearchHashtagSliceInfo;
import org.moera.lib.node.types.SearchTextFilter;
import org.moera.lib.node.types.SearchTextPageInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.EntryRepository;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.index.Index;
import org.moera.search.scanner.UpdateQueue;
import org.moera.search.scanner.updates.SheriffScanUpdate;
import org.moera.search.util.SafeInteger;
import org.moera.search.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/search/entries")
@NoCache
public class SearchEntriesController {

    private static final Logger log = LoggerFactory.getLogger(SearchEntriesController.class);

    private static final int DEFAULT_ENTRIES_PER_REQUEST = 20;
    private static final int MAX_ENTRIES_PER_REQUEST = 200;

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private EntryRepository entryRepository;

    @Inject
    private Index index;

    @Inject
    private UpdateQueue updateQueue;

    @PostMapping("/by-hashtag")
    public SearchHashtagSliceInfo byHashtag(@RequestBody SearchHashtagFilter filter) {
        log.info("POST /search/entries/by-hashtag");

        filter.validate();

        ValidationUtil.assertion(
            filter.getBefore() == null || filter.getAfter() == null,
            "search.before-after-exclusive"
        );

        if (filter.getEntryType() == null) {
            filter.setEntryType(SearchEntryType.ALL);
        }
        canonicalizeHashtags(filter.getHashtags());
        if (filter.getLimit() == null) {
            filter.setLimit(DEFAULT_ENTRIES_PER_REQUEST);
        }
        if (filter.getLimit() > MAX_ENTRIES_PER_REQUEST) {
            filter.setLimit(MAX_ENTRIES_PER_REQUEST);
        }
        if (filter.getBefore() == null && filter.getAfter() == null) {
            filter.setBefore(SafeInteger.MAX_VALUE);
        }
        activateSheriff(filter.getSheriffName());

        var slice = new SearchHashtagSliceInfo();

        slice.setEntries(
            database.read(() ->
                entryRepository.findEntriesByHashtag(
                    filter.getEntryType(),
                    filter.getHashtags(),
                    filter.getPublisherName(),
                    Boolean.TRUE.equals(filter.getInNewsfeed()),
                    filter.getMinImageCount(),
                    filter.getMaxImageCount(),
                    filter.getVideoPresent(),
                    filter.getSheriffName(),
                    requestContext.hasClientScope(Scope.VIEW_CONTENT),
                    filter.getBefore(),
                    filter.getAfter(),
                    filter.getLimit()
                )
            )
        );

        if (filter.getAfter() == null) {
            slice.setBefore(filter.getBefore());
            if (slice.getEntries().size() < filter.getLimit()) {
                slice.setAfter(SafeInteger.MIN_VALUE);
            } else {
                slice.setAfter(slice.getEntries().get(slice.getEntries().size() - 1).getMoment() - 1);
            }
        } else {
            slice.setAfter(filter.getAfter());
            if (slice.getEntries().size() < filter.getLimit()) {
                slice.setBefore(SafeInteger.MAX_VALUE);
            } else {
                slice.setBefore(slice.getEntries().get(slice.getEntries().size() - 1).getMoment());
            }
        }

        return slice;
    }

    @PostMapping("/by-text")
    public SearchTextPageInfo byText(@RequestBody SearchTextFilter filter) {
        log.info("POST /search/entries/by-text");

        filter.validate();

        if (filter.getEntryType() == null) {
            filter.setEntryType(SearchEntryType.ALL);
        }
        canonicalizeHashtags(filter.getHashtags());
        if (filter.getPage() == null) {
            filter.setPage(0);
        }
        if (filter.getLimit() == null) {
            filter.setLimit(DEFAULT_ENTRIES_PER_REQUEST);
        }
        if (filter.getLimit() > MAX_ENTRIES_PER_REQUEST) {
            filter.setLimit(MAX_ENTRIES_PER_REQUEST);
        }
        activateSheriff(filter.getSheriffName());

        var searchResult = index.search(
            filter.getEntryType(),
            filter.getText(),
            filter.getHashtags(),
            filter.getPublisherName(),
            Boolean.TRUE.equals(filter.getInNewsfeed()),
            filter.getOwners(),
            filter.getRepliedTo(),
            filter.getMinImageCount(),
            filter.getMaxImageCount(),
            filter.getVideoPresent(),
            Util.toTimestamp(filter.getCreatedAfter()),
            Util.toTimestamp(filter.getCreatedBefore()),
            requestContext.hasClientScope(Scope.VIEW_CONTENT),
            filter.getPage(),
            filter.getLimit()
        );
        var entries = searchResult.total() > 0
            ? database.read(
                () -> entryRepository.findDocuments(
                    filter.getEntryType(), searchResult.documentIds(), filter.getSheriffName()
                )
            )
            : Collections.<SearchEntryInfo>emptyList();

        var page = new SearchTextPageInfo();
        page.setTotal(searchResult.total());
        page.setEntries(entries);
        return page;
    }

    private static void canonicalizeHashtags(List<String> filter) {
        if (filter != null) {
            for (int i = 0; i < filter.size(); i++) {
                String hashtag = filter.get(i);
                if (!hashtag.startsWith("#")) {
                    filter.set(i, "#" + hashtag);
                }
            }
        }
    }

    private void activateSheriff(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return;
        }
        var exists = database.read(() -> nodeRepository.exists(name));
        if (!exists) {
            return;
        }
        var scannedSheriff = database.read(() -> nodeRepository.isScannedSheriff(name));
        if (!scannedSheriff) {
            log.info("Sheriff {} is referred for the first time, starting scanning", name);
            updateQueue.offer(new SheriffScanUpdate(name));
        }
    }

}
