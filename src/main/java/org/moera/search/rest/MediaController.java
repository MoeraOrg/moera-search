package org.moera.search.rest;

import jakarta.inject.Inject;

import org.moera.lib.node.types.PublicMediaFileInfo;
import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.global.ApiController;
import org.moera.search.media.MediaOperations;
import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.api.model.PublicMediaFileInfoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
@RequestMapping("/moera/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    @Inject
    private Database database;

    @Inject
    private MediaFileRepository mediaFileRepository;

    @Inject
    private MediaOperations mediaOperations;

    private MediaFile getMediaFile(String id) {
        MediaFile mediaFile = database.read(() -> mediaFileRepository.findById(id));
        if (mediaFile == null || !mediaFile.isExposed()) {
            throw new ObjectNotFoundFailure("media.not-found");
        }
        return mediaFile;
    }

    @GetMapping("/public/{id}/info")
    public PublicMediaFileInfo getInfoPublic(@PathVariable String id) {
        log.info("GET /media/public/{id}/info (id = {})", LogUtil.format(id));

        return PublicMediaFileInfoUtil.build(getMediaFile(id));
    }

    @GetMapping("/public/{id}/data")
    public ResponseEntity<Resource> getDataPublic(
        @PathVariable String id,
        @RequestParam(required = false) Integer width,
        @RequestParam(required = false) Boolean download
    ) {
        log.info("GET /media/public/{id}/data (id = {})", LogUtil.format(id));

        return mediaOperations.serve(getMediaFile(id), download);
    }

}
