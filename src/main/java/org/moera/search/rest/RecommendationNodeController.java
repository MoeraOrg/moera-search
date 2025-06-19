package org.moera.search.rest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.Scope;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.auth.AuthenticationException;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/recommendations/nodes")
@NoCache
public class RecommendationNodeController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNodeController.class);

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

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
