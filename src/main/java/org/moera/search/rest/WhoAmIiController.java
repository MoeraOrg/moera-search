package org.moera.search.rest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.NodeType;
import org.moera.lib.node.types.WhoAmI;
import org.moera.search.config.Config;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/whoami")
@NoCache
public class WhoAmIiController {

    private static final Logger log = LoggerFactory.getLogger(WhoAmIiController.class);

    @Inject
    private Config config;

    @GetMapping
    public WhoAmI get() {
        log.info("GET /whoami");

        WhoAmI whoAmI = new WhoAmI();
        whoAmI.setNodeName(config.getNodeName());
        whoAmI.setFullName(config.getNodeFullName());
        whoAmI.setTitle(config.getNodeTitle());
        whoAmI.setType(NodeType.SEARCH);

        return whoAmI;
    }

}
