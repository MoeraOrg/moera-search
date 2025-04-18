package org.moera.search.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;

import org.moera.lib.Rules;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/search")
@NoCache
public class SearchController {

    public static final int MAX_NODES_PER_REQUEST = 100;

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @GetMapping("/nodes")
    public List<SearchNodeInfo> searchNodes(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) Integer limit
    ) {
        log.info("GET /search/nodes (query = {}, limit = {})", LogUtil.format(query), LogUtil.format(limit));

        limit = limit != null && limit <= MAX_NODES_PER_REQUEST ? limit : MAX_NODES_PER_REQUEST;
        ValidationUtil.assertion(limit >= 0, "limit.invalid");

        if (limit == 0 || ObjectUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        int maxSize = limit;

        return database.read(() -> {
            String clientName = requestContext.getClientName(Scope.IDENTIFY);
            var result = new ArrayList<SearchNodeInfo>();
            var used = new HashSet<String>();
            if (!ObjectUtils.isEmpty(clientName)) {
                searchCloseNodes(result, used, clientName, query, maxSize);
            }
            if (result.size() < maxSize) {
                searchNodes(result, used, clientName, query, maxSize, false);
            }
            if (!ObjectUtils.isEmpty(clientName) && result.size() < maxSize) {
                searchNodes(result, used, clientName, query, maxSize, true);
            }
            return result;
        });
    }

    private void searchCloseNodes(
        List<SearchNodeInfo> result, Set<String> used, String clientName, String query, int limit
    ) {
        List<SearchNodeInfo> byName = isValidNodeNamePrefix(query)
            ? nodeRepository.searchCloseByNamePrefix(clientName, query, limit)
            : Collections.emptyList();
        List<SearchNodeInfo> byFullName = nodeRepository.searchCloseByFullNamePrefix(clientName, query, limit);

        addToResult(result, used, byName, byFullName, limit);
    }

    private void searchNodes(
        List<SearchNodeInfo> result, Set<String> used, String clientName, String query, int limit, boolean blocked
    ) {
        List<SearchNodeInfo> byName = isValidNodeNamePrefix(query)
            ? nodeRepository.searchByNamePrefix(clientName, query, limit, blocked)
                .stream()
                .filter(info -> !used.contains(info.getNodeName()))
                .toList()
            : Collections.emptyList();
        List<SearchNodeInfo> byFullName = nodeRepository.searchByFullNamePrefix(clientName, query, limit, blocked)
            .stream()
            .filter(info -> !used.contains(info.getNodeName()))
            .toList();

        addToResult(result, used, byName, byFullName, limit);
    }

    private boolean isValidNodeNamePrefix(String prefix) {
        for (int i = 0; i < prefix.length(); i++) {
            if (!Rules.isNameCharacterValid(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void addToResult(
        List<SearchNodeInfo> result,
        Set<String> used,
        List<SearchNodeInfo> byName,
        List<SearchNodeInfo> byFullName,
        int limit
    ) {
        int freeSpace = limit - result.size();
        if (byName.size() > freeSpace / 2 && !byFullName.isEmpty()) {
            float ratio = (float) byName.size() / (float) (byName.size() + byFullName.size());
            freeSpace = (int) (ratio * freeSpace);
        }
        addToResult(result, used, byName.subList(0, Math.min(byName.size(), freeSpace)), limit);

        if (result.size() < limit && !byFullName.isEmpty()) {
            addToResult(result, used, byFullName, limit);
        }
    }

    private void addToResult(List<SearchNodeInfo> result, Set<String> used, List<SearchNodeInfo> list, int limit) {
        for (SearchNodeInfo node : list) {
            if (result.size() >= limit) {
                break;
            }
            if (!used.contains(node.getNodeName())) {
                result.add(node);
                used.add(node.getNodeName());
            }
        }
    }

}
