package org.moera.search.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;

import org.moera.lib.Rules;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SearchNodeFilter;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.SearchNodePageInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.lib.util.LogUtil;
import org.moera.search.auth.RequestContext;
import org.moera.search.data.Database;
import org.moera.search.data.NodeSearchRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.moera.search.scanner.ingest.SheriffIngest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/search/nodes")
@NoCache
public class SearchNodesController {

    private static final Logger log = LoggerFactory.getLogger(SearchNodesController.class);

    private static final int DEFAULT_NODES_PER_REQUEST = 20;
    private static final int MAX_NODES_PER_REQUEST = 100;

    @Inject
    private RequestContext requestContext;

    @Inject
    private Database database;

    @Inject
    private NodeSearchRepository nodeSearchRepository;

    @Inject
    private SheriffIngest sheriffIngest;

    @GetMapping("/suggestions")
    public List<SearchNodeInfo> suggestions(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) String sheriff,
        @RequestParam(required = false) Integer limit
    ) {
        log.info(
            "GET /search/nodes/suggestions (query = {}, sheriff = {}, limit = {})",
            LogUtil.format(query), LogUtil.format(sheriff), LogUtil.format(limit)
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

        String clientName = requestContext.getClientName(Scope.VIEW_PEOPLE);
        int maxSize = limit;

        if (query == null || query.trim().isEmpty()) {
            if (ObjectUtils.isEmpty(clientName)) {
                return Collections.emptyList();
            }
            return database.read(() -> nodeSearchRepository.searchClose(clientName, sheriff, maxSize));
        }

        return database.read(() -> {
            var result = new ArrayList<SearchNodeInfo>();
            var used = new HashSet<String>();
            if (!ObjectUtils.isEmpty(clientName)) {
                searchCloseNodes(result, used, clientName, query, sheriff, maxSize);
            }
            if (result.size() < maxSize) {
                searchNodes(result, used, clientName, query, sheriff, maxSize, false);
            }
            if (!ObjectUtils.isEmpty(clientName) && result.size() < maxSize) {
                searchNodes(result, used, clientName, query, sheriff, maxSize, true);
            }
            return result;
        });
    }

    private void searchCloseNodes(
        List<SearchNodeInfo> result, Set<String> used, String clientName, String query, String sheriffName, int limit
    ) {
        List<SearchNodeInfo> byName = isValidNodeNamePrefix(query)
            ? nodeSearchRepository.searchCloseByNamePrefix(clientName, query, sheriffName, 0, limit)
            : Collections.emptyList();
        List<SearchNodeInfo> byFullName = nodeSearchRepository.searchCloseByFullNamePrefix(
            clientName, query, sheriffName, 0, limit
        );

        addToSuggestions(result, used, byName, byFullName, limit);
    }

    private void searchNodes(
        List<SearchNodeInfo> result, Set<String> used, String clientName, String query, String sheriffName, int limit,
        boolean blocked
    ) {
        List<SearchNodeInfo> byName = isValidNodeNamePrefix(query)
            ? nodeSearchRepository.searchByNamePrefix(clientName, query, sheriffName, 0, limit, blocked)
                .stream()
                .filter(info -> !used.contains(info.getNodeName()))
                .toList()
            : Collections.emptyList();
        List<SearchNodeInfo> byFullName = nodeSearchRepository.searchByFullNamePrefix(
            clientName, query, sheriffName, 0, limit, blocked
        )
            .stream()
            .filter(info -> !used.contains(info.getNodeName()))
            .toList();

        addToSuggestions(result, used, byName, byFullName, limit);
    }

    private boolean isValidNodeNamePrefix(String prefix) {
        for (int i = 0; i < prefix.length(); i++) {
            if (!Rules.isNameCharacterValid(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void addToSuggestions(
        List<SearchNodeInfo> suggestions,
        Set<String> used,
        List<SearchNodeInfo> byName,
        List<SearchNodeInfo> byFullName,
        int limit
    ) {
        int freeSpace = limit - suggestions.size();
        if (byName.size() > freeSpace / 2 && !byFullName.isEmpty()) {
            float ratio = (float) byName.size() / (float) (byName.size() + byFullName.size());
            freeSpace = (int) (ratio * freeSpace);
        }
        addToSuggestions(suggestions, used, byName.subList(0, Math.min(byName.size(), freeSpace)), limit);

        if (suggestions.size() < limit && !byFullName.isEmpty()) {
            addToSuggestions(suggestions, used, byFullName, limit);
        }
    }

    private void addToSuggestions(
        List<SearchNodeInfo> suggestions,
        Set<String> used,
        List<SearchNodeInfo> list,
        int limit
    ) {
        for (SearchNodeInfo node : list) {
            if (suggestions.size() >= limit) {
                break;
            }
            if (!used.contains(node.getNodeName())) {
                suggestions.add(node);
                used.add(node.getNodeName());
            }
        }
    }

    private record SearchCounts(
        int closeName, int closeFullName, int name, int fullName, int blockedName, int blockedFullName
    ) {
    }

    @PostMapping
    public SearchNodePageInfo search(@RequestBody SearchNodeFilter filter) {
        log.info("POST /search/nodes");

        filter.validate();

        if (filter.getPage() == null) {
            filter.setPage(0);
        }
        if (filter.getLimit() == null) {
            filter.setLimit(DEFAULT_NODES_PER_REQUEST);
        }
        if (filter.getLimit() > MAX_NODES_PER_REQUEST) {
            filter.setLimit(MAX_NODES_PER_REQUEST);
        }
        sheriffIngest.activate(filter.getSheriffName());

        var clientName = requestContext.getClientName(Scope.VIEW_PEOPLE);

        var page = new SearchNodePageInfo();

        if (filter.getQuery() == null || filter.getQuery().trim().isEmpty()) {
            page.setNodes(Collections.emptyList());
            return page;
        }

        var counts = database.read(() -> {
            boolean validNodeNamePrefix = isValidNodeNamePrefix(filter.getQuery());

            var closeNameCount = 0;
            var closeFullNameCount = 0;
            if (!ObjectUtils.isEmpty(clientName)) {
                if (validNodeNamePrefix) {
                    closeNameCount = nodeSearchRepository.countCloseByNamePrefix(
                        clientName, filter.getQuery(), filter.getSheriffName()
                    );
                }
                closeFullNameCount = nodeSearchRepository.countCloseByFullNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName()
                );
            }

            var nameCount = 0;
            if (validNodeNamePrefix) {
                nameCount = nodeSearchRepository.countByNamePrefix(filter.getQuery(), filter.getSheriffName());
            }
            var fullNameCount = nodeSearchRepository.countByFullNamePrefix(filter.getQuery(), filter.getSheriffName());

            var blockedNameCount = 0;
            var blockedFullNameCount = 0;
            if (!ObjectUtils.isEmpty(clientName)) {
                if (validNodeNamePrefix) {
                    blockedNameCount = nodeSearchRepository.countBlockedByNamePrefix(
                        clientName, filter.getQuery(), filter.getSheriffName()
                    );
                }
                blockedFullNameCount = nodeSearchRepository.countBlockedByFullNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName()
                );
            }

            return new SearchCounts(
                closeNameCount, closeFullNameCount, nameCount, fullNameCount, blockedNameCount, blockedFullNameCount
            );
        });
        page.setTotal(counts.name + counts.fullName);

        if (page.getTotal() <= filter.getPage() * filter.getLimit()) {
            page.setNodes(Collections.emptyList());
            return page;
        }

        var nodes = new ArrayList<SearchNodeInfo>();
        var used = new HashSet<String>();

        database.readNoResult(() -> {
            var offset = filter.getPage() * filter.getLimit();
            if (0 < filter.getLimit() && counts.closeName > 0 && offset < counts.closeName) {
                var found = nodeSearchRepository.searchCloseByNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit()
                );
                nodes.addAll(found);
                used.addAll(found.stream().map(SearchNodeInfo::getNodeName).toList());
                offset = Math.max(offset - counts.closeName, 0);
            }
            if (nodes.size() < filter.getLimit() && counts.closeFullName > 0 && offset < counts.closeFullName) {
                var found = nodeSearchRepository.searchCloseByFullNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit()
                );
                nodes.addAll(found.stream().filter(node -> !used.contains(node.getNodeName())).toList());
                used.addAll(found.stream().map(SearchNodeInfo::getNodeName).toList());
                offset = Math.max(offset - counts.closeFullName, 0);
            }
            var notBlockedNameCount = counts.name - counts.blockedName;
            if (nodes.size() < filter.getLimit() && notBlockedNameCount > 0 && offset < notBlockedNameCount) {
                var found = nodeSearchRepository.searchByNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit(), false
                );
                nodes.addAll(found.stream().filter(node -> !used.contains(node.getNodeName())).toList());
                used.addAll(found.stream().map(SearchNodeInfo::getNodeName).toList());
                offset = Math.max(offset - notBlockedNameCount, 0);
            }
            var notBlockedFullNameCount = counts.name - counts.blockedName;
            if (nodes.size() < filter.getLimit() && notBlockedFullNameCount > 0 && offset < notBlockedFullNameCount) {
                var found = nodeSearchRepository.searchByFullNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit(), false
                );
                nodes.addAll(found.stream().filter(node -> !used.contains(node.getNodeName())).toList());
                used.addAll(found.stream().map(SearchNodeInfo::getNodeName).toList());
                offset = Math.max(offset - notBlockedFullNameCount, 0);
            }
            if (nodes.size() < filter.getLimit() && counts.blockedName > 0 && offset < counts.blockedName) {
                var found = nodeSearchRepository.searchByNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit(), true
                );
                nodes.addAll(found.stream().filter(node -> !used.contains(node.getNodeName())).toList());
                used.addAll(found.stream().map(SearchNodeInfo::getNodeName).toList());
                offset = Math.max(offset - counts.blockedName, 0);
            }
            if (nodes.size() < filter.getLimit() && counts.blockedFullName > 0 && offset < counts.blockedFullName) {
                var found = nodeSearchRepository.searchByNamePrefix(
                    clientName, filter.getQuery(), filter.getSheriffName(), offset, filter.getLimit(), true
                );
                nodes.addAll(found.stream().filter(node -> !used.contains(node.getNodeName())).toList());
            }
        });
        page.setNodes(nodes);

        return page;
    }

}
