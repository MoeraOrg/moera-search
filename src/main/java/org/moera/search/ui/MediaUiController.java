package org.moera.search.ui;

import jakarta.inject.Inject;

import org.moera.lib.util.LogUtil;
import org.moera.search.data.Database;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.global.MaxCache;
import org.moera.search.global.PageNotFoundException;
import org.moera.search.global.UiController;
import org.moera.search.media.MediaOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@UiController
@RequestMapping("/moera/media")
public class MediaUiController {

    private static final Logger log = LoggerFactory.getLogger(MediaUiController.class);

    @Inject
    private Database database;

    @Inject
    private MediaFileRepository mediaFileRepository;

    @Inject
    private MediaOperations mediaOperations;

    @GetMapping("/public/{id}.{ext}")
    @MaxCache
    @ResponseBody
    public ResponseEntity<Resource> getDataPublic(
        @PathVariable String id,
        @RequestParam(required = false) Integer width,
        @RequestParam(required = false) Boolean download
    ) {
        log.info("GET MEDIA /media/public/{id}.ext (id = {})", LogUtil.format(id));

        MediaFile mediaFile = database.executeRead(() -> mediaFileRepository.findById(id));
        if (mediaFile == null || !mediaFile.isExposed()) {
            throw new PageNotFoundException();
        }
        return mediaOperations.serve(mediaFile, download);
    }

}
