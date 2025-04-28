package org.moera.search.scanner.updates;

import java.util.List;
import jakarta.inject.Inject;

import org.moera.search.data.Database;
import org.moera.search.data.PendingUpdate;
import org.moera.search.data.PostingRepository;
import org.moera.search.job.Job;
import org.moera.search.scanner.JobKeys;

public class PublicationDeleteUpdate extends PendingUpdate<PublicationDeleteJob.Parameters> {

    @Inject
    private Database database;

    @Inject
    private PostingRepository postingRepository;

    public PublicationDeleteUpdate(String nodeName, String postingId, String publisherName) {
        super(new PublicationDeleteJob.Parameters(nodeName, postingId, publisherName));
    }

    @Override
    protected Class<? extends Job<PublicationDeleteJob.Parameters, ?>> getJobClass() {
        return PublicationDeleteJob.class;
    }

    @Override
    protected Class<PublicationDeleteJob.Parameters> getJobParametersClass() {
        return PublicationDeleteJob.Parameters.class;
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
