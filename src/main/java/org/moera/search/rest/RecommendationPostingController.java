package org.moera.search.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.node.types.RecommendedPostingInfo;
import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.auth.AuthenticationException;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.CachePopularPostingsRepository;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.global.RequestCounter;
import org.moera.search.scanner.ingest.SheriffIngest;
import org.moera.search.util.PostingLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private RequestCounter requestCounter;

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    @Inject
    private CachePopularPostingsRepository cachePopularPostingsRepository;

    @Inject
    private SheriffIngest sheriffIngest;

    @GetMapping
    public List<RecommendedPostingInfo> popular(
        @RequestParam(required = false) String sheriff,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings (sheriff = {}, limit = {})",
            LogUtil.format(sheriff), LogUtil.format(limit)
        );

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");
        sheriffIngest.activate(sheriff);

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.write(() -> {
            String clientName = requestContext.getClientName(Scope.IDENTIFY);
            if (clientName == null) {
                var cached = cachePopularPostingsRepository.getPopular(sheriff);
                if (cached == null) {
                    cached = postingRepository.findPopular(sheriff, MAX_POSTINGS_PER_REQUEST);
                    cachePopularPostingsRepository.setPopular(sheriff, cached);
                }
                return cached.subList(0, Math.min(cached.size(), size));
            } else {
                var recommended = postingRepository.findRecommended(clientName, sheriff, size);
                if (recommended.size() >= size) {
                    return recommended;
                }

                var list = new ArrayList<>(recommended);
                var used = recommended.stream()
                    .map(r -> new PostingLocation(r.getNodeName(), r.getPostingId()))
                    .collect(Collectors.toSet());
                var other = postingRepository.findRecommendedByNobody(clientName, sheriff, size);
                int i = 0;
                while (list.size() < size && i < other.size()) {
                    var item = other.get(i);
                    var key = new PostingLocation(item.getNodeName(), item.getPostingId());
                    if (!used.contains(key)) {
                        list.add(item);
                    }
                    i++;
                }
                return list;
            }
        });
    }

    @GetMapping("/reading")
    public List<RecommendedPostingInfo> popularForReading(
        @RequestParam(required = false) String sheriff,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings/reading (sheriff = {}, limit = {})",
            LogUtil.format(sheriff), LogUtil.format(limit)
        );

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");
        sheriffIngest.activate(sheriff);

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.write(() -> {
            var cached = cachePopularPostingsRepository.getPopularReading(sheriff);
            if (cached == null) {
                cached = postingRepository.findReadPopular(sheriff, MAX_POSTINGS_PER_REQUEST);
                cachePopularPostingsRepository.setPopularReading(sheriff, cached);
            }
            return cached.subList(0, Math.min(cached.size(), size));
        });
    }

    @GetMapping("/commenting")
    public List<RecommendedPostingInfo> popularForCommenting(
        @RequestParam(required = false) String sheriff,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings/commenting (sheriff = {}, limit = {})",
            LogUtil.format(sheriff), LogUtil.format(limit)
        );

        if (limit == null) {
            limit = DEFAULT_POSTINGS_PER_REQUEST;
        }
        if (limit > MAX_POSTINGS_PER_REQUEST) {
            limit = MAX_POSTINGS_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");
        sheriffIngest.activate(sheriff);

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.write(() -> {
            var cached = cachePopularPostingsRepository.getPopularCommenting(sheriff);
            if (cached == null) {
                cached = postingRepository.findCommentPopular(sheriff, MAX_POSTINGS_PER_REQUEST);
                cachePopularPostingsRepository.setPopularCommenting(sheriff, cached);
            }
            return cached.subList(0, Math.min(cached.size(), size));
        });
    }

    @PostMapping("/accepted/{nodeName}/{postingId}")
    public Result acceptRecommendation(@PathVariable String nodeName, @PathVariable String postingId) {
        log.info(
            "POST /recommendations/postings/accepted/{nodeName}/{postingId} (nodeName = {}, postingId = {})",
            LogUtil.format(nodeName), LogUtil.format(postingId)
        );

        var clientName = requestContext.getClientName(Scope.UPDATE_FEEDS);
        if (clientName == null) {
            throw new AuthenticationException();
        }

        var exists = database.read(() -> postingRepository.exists(nodeName, postingId));
        if (!exists) {
            throw new ObjectNotFoundFailure("not-found");
        }

        database.writeNoResult(() -> {
            postingRepository.clearRecommendationAcceptance(clientName, nodeName, postingId);
            postingRepository.acceptRecommendation(clientName, nodeName, postingId);
        });

        return Result.OK;
    }

    @PostMapping("/rejected/{nodeName}/{postingId}")
    public Result rejectRecommendation(@PathVariable String nodeName, @PathVariable String postingId) {
        log.info(
            "POST /recommendations/postings/rejected/{nodeName}/{postingId} (nodeName = {}, postingId = {})",
            LogUtil.format(nodeName), LogUtil.format(postingId)
        );

        var clientName = requestContext.getClientName(Scope.UPDATE_FEEDS);
        if (clientName == null) {
            throw new AuthenticationException();
        }

        var exists = database.read(() -> postingRepository.exists(nodeName, postingId));
        if (!exists) {
            throw new ObjectNotFoundFailure("not-found");
        }

        database.writeNoResult(() -> {
            postingRepository.clearRecommendationAcceptance(clientName, nodeName, postingId);
            postingRepository.rejectRecommendation(clientName, nodeName, postingId);
        });

        return Result.OK;
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void purgeExpiredCache() {
        if (!database.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                log.debug("Deleting expired popular postings cache");
                database.writeNoResult(() -> cachePopularPostingsRepository.deleteExpired());
            }
        }
    }

}
