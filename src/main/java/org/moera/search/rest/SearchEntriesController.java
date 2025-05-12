package org.moera.search.rest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.SearchEntryType;
import org.moera.lib.node.types.SearchHashtagFilter;
import org.moera.lib.node.types.SearchHashtagSliceInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.search.data.Database;
import org.moera.search.data.EntryRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.util.SafeInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/search/entries")
@NoCache
public class SearchEntriesController {

    private static final Logger log = LoggerFactory.getLogger(SearchEntriesController.class);

    private static final int MAX_ENTRIES_PER_REQUEST = 50;

    @Inject
    private Database database;

    @Inject
    private EntryRepository entryRepository;

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
        if (filter.getLimit() == null || filter.getLimit() > MAX_ENTRIES_PER_REQUEST) {
            filter.setLimit(MAX_ENTRIES_PER_REQUEST);
        }
        if (filter.getBefore() == null && filter.getAfter() == null) {
            filter.setBefore(SafeInteger.MAX_VALUE);
        }

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

}
