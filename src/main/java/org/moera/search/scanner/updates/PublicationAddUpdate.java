package org.moera.search.scanner.updates;

import java.util.List;

import jakarta.inject.Inject;

import org.moera.search.data.Database;
import org.moera.search.data.PendingUpdate;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PublicationAddUpdate extends PendingUpdate<PublicationAddJob.Parameters> {

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    public PublicationAddUpdate() {
    }

    public PublicationAddUpdate(
        String nodeName, String postingId, String publisherName, String feedName, String storyId, long publishedAt
    ) {
        super(new PublicationAddJob.Parameters(nodeName, postingId, publisherName, feedName, storyId, publishedAt));
    }

    @Override
    protected Class<? extends Job<PublicationAddJob.Parameters, ?>> getJobClass() {
        return PublicationAddJob.class;
    }

    @Override
    protected Class<PublicationAddJob.Parameters> getJobParametersClass() {
        return PublicationAddJob.Parameters.class;
    }

    @Override
    public boolean isPrepared() {
        return database.read(() ->
            postingRepository.exists(getJobParameters().getNodeName(), getJobParameters().getPostingId())
        );
    }

    @Override
    public List<String> waitJobKeys() {
        return List.of(
            JobKeys.allContent(getJobParameters().getNodeName()),
            JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId())
        );
    }

    @Override
    public String jobKey() {
        return JobKeys.posting(getJobParameters().getNodeName(), getJobParameters().getPostingId());
    }

}
