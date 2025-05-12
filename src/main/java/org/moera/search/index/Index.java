package org.moera.search.index;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jakarta.inject.Inject;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.moera.lib.node.types.SearchEntryType;
import org.moera.search.config.Config;
import org.moera.search.data.DatabaseInitializedEvent;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfigParam;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class Index {

    private static final Logger log = LoggerFactory.getLogger(Index.class);

    @Inject
    private Config config;

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    @Inject
    private LanguageAnalyzer languageAnalyzer;

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

    public void delete(String id) {
        try {
            client.delete(
                new DeleteRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .build()
            );
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    public void deleteBulk(List<String> ids) {
        var operations = new ArrayList<BulkOperation>();
        for (String id : ids) {
            operations.add(
                new BulkOperation.Builder()
                    .delete(new DeleteOperation.Builder().id(id).build())
                    .build()
            );
        }

        try {
            client.bulk(
                new BulkRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .operations(operations)
                    .build()
            );
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    public boolean exists(String id) {
        try {
            var response = client.get(
                new GetRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .source(new SourceConfigParam.Builder().fetch(false).build())
                    .build(),
                IndexedDocument.class
            );
            return response.found();
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

    public record IndexSearchResult(int total, List<String> documentIds) {
    }

    public IndexSearchResult search(
        SearchEntryType entryType,
        String text,
        List<String> hashtags,
        String publisherName,
        Boolean inNewsfeed,
        List<String> owners,
        List<String> repliedTo,
        Integer minImageCount,
        Integer maxImageCount,
        Boolean videoPresent,
        int page,
        int limit
    ) {
        var conditions = new BoolQuery.Builder();

        if (entryType == SearchEntryType.POSTING) {
            conditions.mustNot(existsQuery("commentId"));
        } else if (entryType == SearchEntryType.COMMENT) {
            conditions.must(existsQuery("commentId"));
        }

        conditions.must(
            new Query.Builder()
                .multiMatch(
                    new MultiMatchQuery.Builder()
                        .fields(languageAnalyzer.getSearchFields(text))
                        .query(text)
                        .build()
                )
                .build()
        );

        if (!ObjectUtils.isEmpty(hashtags)) {
            conditions.filter(termsQuery("hashtags", hashtags));
        }
        if (!ObjectUtils.isEmpty(publisherName)) {
            conditions.filter(termQuery(inNewsfeed ? "news" : "publishers", publisherName));
        }
        if (!ObjectUtils.isEmpty(owners)) {
            conditions.filter(termsQuery("ownerName", owners));
        }
        if (!ObjectUtils.isEmpty(repliedTo)) {
            conditions.filter(termsQuery("repliedToName", repliedTo));
        }
        if (minImageCount != null || maxImageCount != null) {
            var rangeQuery = new RangeQuery.Builder()
                .field("imageCount");
            if (minImageCount != null) {
                rangeQuery.gte(JsonData.of(minImageCount));
            }
            if (maxImageCount != null) {
                rangeQuery.lte(JsonData.of(maxImageCount));
            }
            conditions.filter(
                new Query.Builder()
                    .range(rangeQuery.build())
                    .build()
            );
        }
        if (videoPresent != null) {
            conditions.filter(termQuery("videoPresent", videoPresent));
        }

        try {
            var response = client.search(
                new SearchRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .fields(Collections.emptyList())
                    .query(
                        new Query.Builder()
                            .bool(conditions.build())
                            .build()
                    )
                    .from(page * limit)
                    .size(limit)
                    .build(),
                Object.class
            );
            var totalHits = response.hits().total();
            return new IndexSearchResult(
                totalHits != null ? (int) totalHits.value() : 0,
                response.hits().hits().stream().map(Hit::id).toList()
            );
        } catch (IOException e) {
            throw new TransientIndexException(e);
        }
    }

    private static Query existsQuery(String fieldName) {
        return new Query.Builder()
            .exists(
                new ExistsQuery.Builder()
                    .field(fieldName)
                    .build()
            )
            .build();
    }

    private static Query termQuery(String fieldName, String value) {
        return new Query.Builder()
            .term(
                new TermQuery.Builder()
                    .field(fieldName)
                    .value(FieldValue.of(value))
                    .build()
            )
            .build();
    }

    private static Query termQuery(String fieldName, boolean value) {
        return new Query.Builder()
            .term(
                new TermQuery.Builder()
                    .field(fieldName)
                    .value(FieldValue.of(value))
                    .build()
            )
            .build();
    }

    private static Query termsQuery(String fieldName, List<String> values) {
        return new Query.Builder()
            .terms(
                new TermsQuery.Builder()
                .field(fieldName)
                .terms(
                    new TermsQueryField.Builder()
                        .value(values.stream().map(FieldValue::of).toList())
                        .build()
                )
                .build()
            )
            .build();
    }

}
