package org.moera.search.rest;

import java.util.Collections;
import java.util.List;
import jakarta.inject.Inject;

import org.moera.lib.node.types.RecommendedPostingInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/recommendations/postings")
@NoCache
public class RecommendationPostingController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationPostingController.class);

    private static final int DEFAULT_POSTINGS_PER_REQUEST = 20;
    private static final int MAX_POSTINGS_PER_REQUEST = 200;

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @GetMapping
    public List<RecommendedPostingInfo> popular(@RequestParam(required = false) Integer limit) {
        log.info("GET /recommendations/postings (limit = {})", LogUtil.format(limit));

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.read(() -> postingRepository.findPopular(size));
    }

    @GetMapping("/reading")
    public List<RecommendedPostingInfo> popularForReading(@RequestParam(required = false) Integer limit) {
        log.info("GET /recommendations/postings/reading (limit = {})", LogUtil.format(limit));

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.read(() -> postingRepository.findReadPopular(size));
    }

    @GetMapping("/commenting")
    public List<RecommendedPostingInfo> popularForCommenting(@RequestParam(required = false) Integer limit) {
        log.info("GET /recommendations/postings/commenting (limit = {})", LogUtil.format(limit));

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.read(() -> postingRepository.findCommentPopular(size));
    }

}
