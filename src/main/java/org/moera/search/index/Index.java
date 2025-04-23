package org.moera.search.index;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jakarta.inject.Inject;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.moera.search.config.Config;
import org.moera.search.data.DatabaseInitializedEvent;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class Index {

    private static final Logger log = LoggerFactory.getLogger(Index.class);

    @Inject
    private Config config;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    private OpenSearchClient client;
    private boolean ready = false;

    private static final class TrustAllTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("All client connections to this client are forbidden.");
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }

    @EventListener(DatabaseInitializedEvent.class)
    private void init() throws GeneralSecurityException {
        var credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(config.getIndex().getUser(), config.getIndex().getPassword())
        );

        var sslContext = SSLContext.getInstance("TLS");
        // TODO this should be configurable
        sslContext.init(null, new TrustManager[]{new TrustAllTrustManager()}, new SecureRandom());
        var restClient = RestClient
            .builder(
                new HttpHost(
                    config.getIndex().getHost(),
                    config.getIndex().getPort(),
                    config.getIndex().getScheme()
                )
            )
            .setHttpClientConfigCallback(
                httpClientBuilder -> httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier((hostname, session) -> true)
            )
            .build();
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new OpenSearchClient(transport);

        log.info(
            "Connected to OpenSearch {}://{}:{}",
            config.getIndex().getScheme(), config.getIndex().getHost(), config.getIndex().getPort()
        );
        ready = true;
        applicationEventPublisher.publishEvent(new IndexInitializedEvent(this));
    }

    public boolean isReady() {
        return ready;
    }

    public String index(IndexedDocument document) {
        try {
            var response = client.index(
                new IndexRequest.Builder<IndexedDocument>()
                    .index(config.getIndex().getIndexName())
                    .document(document)
                    .build()
            );
            return response.id();
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    public void update(String id, IndexedDocument document) {
        try {
            client.update(
                new UpdateRequest.Builder<IndexedDocument, IndexedDocument>()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .doc(document)
                    .build(),
                IndexedDocument.class
            );
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    public IndexedDocument get(String id) {
        try {
            var response = client.get(
                new GetRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .build(),
                IndexedDocument.class
            );
            return response.source();
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    public String getRevisionId(String id) {
        try {
            var response = client.get(
                new GetRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .sourceIncludes(List.of("revisionId"))
                    .build(),
                IndexedDocument.class
            );
            return response.source() != null ? response.source().getRevisionId() : null;
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

}
