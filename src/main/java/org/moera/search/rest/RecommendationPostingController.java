package org.moera.search.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.node.types.RecommendedPostingInfo;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.util.PostingLocation;
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
    public List<RecommendedPostingInfo> popular(
        @RequestParam(required = false) String sheriffName,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings (sheriffName = {}, limit = {})",
            LogUtil.format(sheriffName), LogUtil.format(limit)
        );

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

        return database.read(() -> {
            String clientName = requestContext.getClientName(Scope.IDENTIFY);
            if (clientName == null) {
                return postingRepository.findPopular(sheriffName, size);
            } else {
                var recommended = postingRepository.findRecommended(clientName, sheriffName, size);
                if (recommended.size() >= size) {
                    return recommended;
                }

                var list = new ArrayList<>(recommended);
                var used = recommended.stream()
                    .map(r -> new PostingLocation(r.getNodeName(), r.getPostingId()))
                    .collect(Collectors.toSet());
                var other = postingRepository.findRecommendedByNobody(clientName, sheriffName, size);
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
        @RequestParam(required = false) String sheriffName,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings/reading (sheriffName = {}, limit = {})",
            LogUtil.format(sheriffName), LogUtil.format(limit)
        );

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

        return database.read(() -> postingRepository.findReadPopular(sheriffName, size));
    }

    @GetMapping("/commenting")
    public List<RecommendedPostingInfo> popularForCommenting(
        @RequestParam(required = false) String sheriffName,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/postings/commenting (sheriffName = {}, limit = {})",
            LogUtil.format(sheriffName), LogUtil.format(limit)
        );

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

        return database.read(() -> postingRepository.findCommentPopular(sheriffName, size));
    }

}
