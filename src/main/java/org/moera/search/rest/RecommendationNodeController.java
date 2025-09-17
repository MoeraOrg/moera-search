package org.moera.search.rest;

import java.util.Collections;
import java.util.List;
import jakarta.inject.Inject;

import org.moera.lib.node.types.RecommendedNodeInfo;
import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.auth.AuthenticationException;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.data.NodeSearchRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.scanner.ingest.SheriffIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/recommendations/nodes")
@NoCache
public class RecommendationNodeController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNodeController.class);

    private static final int DEFAULT_NODES_PER_REQUEST = 20;
    private static final int MAX_NODES_PER_REQUEST = 200;

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @Inject
    private NodeSearchRepository nodeSearchRepository;

    @Inject
    private SheriffIngest sheriffIngest;

    @GetMapping("/active")
    public List<RecommendedNodeInfo> active(
        @RequestParam(required = false) String sheriff,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /recommendations/nodes/active (sheriff = {}, limit = {})",
            LogUtil.format(sheriff), LogUtil.format(limit)
        );

        if (limit == null) {
            limit = DEFAULT_NODES_PER_REQUEST;
        }
        if (limit > MAX_NODES_PER_REQUEST) {
            limit = MAX_NODES_PER_REQUEST;
        }
        ValidationUtil.assertion(limit >= 0, "limit.invalid");
        sheriffIngest.activate(sheriff);

        if (limit == 0) {
            return Collections.emptyList();
        }

        int size = limit;

        return database.read(() -> nodeSearchRepository.searchActive(sheriff, size));
    }

    @PostMapping("/excluded/{nodeName}")
    public Result excludeNode(@PathVariable String nodeName) {
        log.info("POST /recommendations/nodes/excluded/{nodeName} (nodeName = {})", LogUtil.format(nodeName));

        var clientName = requestContext.getClientName(Scope.UPDATE_FEEDS);
        if (clientName == null) {
            throw new AuthenticationException();
        }

        var exists = database.read(() -> nodeRepository.exists(nodeName));
        if (!exists) {
            throw new ObjectNotFoundFailure("not-found");
        }

        database.writeNoResult(() -> nodeRepository.addDontRecommend(clientName, nodeName));

        return Result.OK;
    }

    @DeleteMapping("/excluded/{nodeName}")
    public Result allowNode(@PathVariable String nodeName) {
        log.info("DELETE /recommendations/nodes/excluded/{nodeName} (nodeName = {})", LogUtil.format(nodeName));

        var clientName = requestContext.getClientName(Scope.UPDATE_FEEDS);
        if (clientName == null) {
            throw new AuthenticationException();
        }

        var exists = database.read(() -> nodeRepository.exists(nodeName));
        if (!exists) {
            throw new ObjectNotFoundFailure("not-found");
        }

        database.writeNoResult(() -> nodeRepository.deleteDontRecommend(clientName, nodeName));

        return Result.OK;
    }

}
