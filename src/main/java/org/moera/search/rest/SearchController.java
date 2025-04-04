package org.moera.search.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Inject;

import org.moera.lib.Rules;
import org.moera.lib.node.types.SearchNodeInfo;
import org.moera.lib.node.types.validate.ValidationUtil;
import org.moera.search.data.Database;
import org.moera.search.data.NodeRepository;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/search")
@NoCache
public class SearchController {

    public static final int MAX_NODES_PER_REQUEST = 100;

    @Inject
    private Database database;

    @Inject
    private NodeRepository nodeRepository;

    @GetMapping("/nodes")
    public List<SearchNodeInfo> searchNodes(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(required = false) Integer limit
    ) {
        limit = limit != null && limit <= MAX_NODES_PER_REQUEST ? limit : MAX_NODES_PER_REQUEST;
        ValidationUtil.assertion(limit >= 0, "limit.invalid");

        if (limit == 0 || ObjectUtils.isEmpty(query)) {
            return Collections.emptyList();
        }

        int maxSize = limit;

        return database.executeRead(() -> {
            List<SearchNodeInfo> byName = isValidNodeNamePrefix(query)
                ? nodeRepository.searchByNamePrefix(query, maxSize)
                : Collections.emptyList();
            List<SearchNodeInfo> byFullName = nodeRepository.searchByFullNamePrefix(query, maxSize);

            var result = new ArrayList<SearchNodeInfo>();
            if (byName.size() <= maxSize / 2 || byFullName.isEmpty()) {
                result.addAll(byName);
            } else {
                float ratio = (float) byName.size() / (float) (byName.size() + byFullName.size());
                result.addAll(byName.subList(0, (int) (ratio * maxSize)));
            }

            if (result.size() >= maxSize || byFullName.isEmpty()) {
                return result;
            }

            var used = byName.stream().map(SearchNodeInfo::getNodeName).collect(Collectors.toSet());
            for (SearchNodeInfo node : byFullName) {
                if (result.size() >= maxSize) {
                    break;
                }
                if (!used.contains(node.getNodeName())) {
                    result.add(node);
                }
            }

            return result;
        });
    }

    private boolean isValidNodeNamePrefix(String prefix) {
        for (int i = 0; i < prefix.length(); i++) {
            if (!Rules.isNameCharacterValid(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
