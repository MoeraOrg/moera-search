package org.moera.search.scanner;

import java.util.UUID;
import jakarta.inject.Inject;

import org.moera.search.Workload;
import org.moera.search.data.Database;
import org.moera.search.data.PostingRepository;
import org.moera.search.global.RequestCounter;
import org.moera.search.index.Index;
import org.moera.search.job.Jobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CommentsScanner {

    private static final Logger log = LoggerFactory.getLogger(CommentsScanner.class);

    @Inject
    private Jobs jobs;

    @Inject
    private RequestCounter requestCounter;

    @Inject
    private Database database;

    @Inject
    private Index index;

    @Inject
    private PostingRepository postingRepository;

    @Scheduled(fixedDelayString = Workload.COMMENTS_SCANNERS_START_PERIOD)
    public void scan() {
        if (!jobs.isReady() || !index.isReady()) {
            return;
        }

        try (var ignored = requestCounter.allot()) {
            try (var ignored2 = database.open()) {
                int runningCount = database.read(() -> jobs.countRunning(CommentsScanJob.class));
                if (runningCount >= Workload.COMMENTS_SCANNERS_MAX_JOBS) {
                    return;
                }
                var postings = database.read(() ->
                    postingRepository.findPostingsToScanComments(Workload.COMMENTS_SCANNERS_MAX_JOBS - runningCount)
                );
                for (var posting : postings) {
                    log.debug(
                        "Starting comments scan for posting {} at node {}",
                        posting.postingId(), posting.nodeName()
                    );
                    try {
                        UUID jobId = jobs.run(
                            CommentsScanJob.class,
                            new CommentsScanJob.Parameters(posting.nodeName(), posting.postingId())
                        );
                        if (jobId != null) {
                            database.writeNoResult(() ->
                                postingRepository.assignScanCommentsJob(posting.nodeName(), posting.postingId(), jobId)
                            );
                        }
                    } catch (Exception e) {
                        log.error(
                            "Error starting comments scan job for posting {} at node {}",
                            posting.nodeName(), posting.postingId(), e
                        );
                    }
                }
            }
        }
    }

}
