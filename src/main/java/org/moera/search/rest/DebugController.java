package org.moera.search.rest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.Result;
import org.moera.search.global.ApiController;
import org.moera.search.scanner.CloseToUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/debug")
@Profile("dev")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    @Inject
    private CloseToUpdater closeToUpdater;

    @PostMapping("/close-tos/update")
    public Result closeTosUpdate() {
        log.info("POST /debug/close-tos/update");

        closeToUpdater.update();

        return Result.OK;
    }

    @PostMapping("/close-tos/cleanup")
    public Result closeTosCleanup() {
        log.info("POST /debug/close-tos/cleanup");

        closeToUpdater.cleanup();

        return Result.OK;
    }

}
