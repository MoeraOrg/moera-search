package org.moera.search.media;

import java.io.IOException;
import java.util.List;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.search.data.MediaFile;
import org.moera.search.data.MediaFileRepository;
import org.moera.search.job.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreparePublicDirectServingJob extends Job<PreparePublicDirectServingJob.Parameters, Object> {

    public static class Parameters {

        public Parameters() {
        }

    }

    private static final Logger log = LoggerFactory.getLogger(PreparePublicDirectServingJob.class);

    private static final int PAGE_SIZE = 1024;

    @Inject
    private MediaFileRepository mediaFileRepository;

    @Inject
    private MediaOperations mediaOperations;

    public PreparePublicDirectServingJob() {
    }

    @Override
    protected void setParameters(String parameters, ObjectMapper objectMapper) throws JsonProcessingException {
        this.parameters = objectMapper.readValue(parameters, Parameters.class);
    }

    @Override
    protected void setState(String state, ObjectMapper objectMapper) throws JsonProcessingException {
        this.state = null;
    }

    @Override
    protected void started() {
        super.started();
        log.info("Creating links for serving public media files directly");
    }

    @Override
    protected void execute() {
        int offset = 0;
        int pageNumber = 0;
        List<MediaFile> page;
        do {
            int pageStart = offset;
            page = database.read(() -> mediaFileRepository.findAllExposed(pageStart, PAGE_SIZE));
            log.info("Processing page {} of public media files", pageNumber++);
            for (MediaFile mediaFile : page) {
                try {
                    mediaOperations.createPublicServingLink(mediaFile);
                } catch (IOException e) {
                    log.error("Could not create a link for {}: {}", mediaFile.getFileName(), e.getMessage());
                }
            }
            offset += page.size();
        } while (!page.isEmpty());
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        log.info("Links created successfully");
    }

}
