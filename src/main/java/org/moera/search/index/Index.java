package org.moera.search.index;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jakarta.inject.Inject;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.moera.lib.node.types.SearchEntryType;
import org.moera.search.config.Config;
import org.moera.search.data.DatabaseInitializedEvent;
import org.moera.search.data.EntryRevision;
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
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
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

    @EventListener(DatabaseInitializedEvent.class)
    private void init() throws GeneralSecurityException {
        var host = new HttpHost(
            config.getIndex().getScheme(),
            config.getIndex().getHost(),
            config.getIndex().getPort()
        );

        var credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            new AuthScope(host),
            new UsernamePasswordCredentials(config.getIndex().getUser(), config.getIndex().getPassword().toCharArray())
        );

        // Insecure SSL: trust every certificate
        var sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
            .build();

        // Insecure SSL: disable hostname verification
        var tlsStrategy = ClientTlsStrategyBuilder.create()
            .setSslContext(sslContext)
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .buildAsync();

        var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
            .setTlsStrategy(tlsStrategy)
            .build();

        var transport = ApacheHttpClient5TransportBuilder.builder(host)
            .setMapper(new JacksonJsonpMapper())
            .setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setConnectionManager(connectionManager)
            )
            .build();

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

    public EntryRevision getRevision(String id) {
        try {
            var response = client.get(
                new GetRequest.Builder()
                    .index(config.getIndex().getIndexName())
                    .id(id)
                    .sourceIncludes(List.of("revisionId", "viewPrincipal"))
                    .build(),
                IndexedDocument.class
            );
            return response.source() != null
                ? new EntryRevision(response.source().getRevisionId(), response.source().getViewPrincipal())
                : null;
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
        Boolean attachmentPresent,
        Timestamp createdAfter,
        Timestamp createdBefore,
        boolean signedIn,
        int page,
        int limit
    ) {
        var conditions = new BoolQuery.Builder();

        if (entryType == SearchEntryType.POSTING) {
            conditions.filter(notExistsQuery("commentId"));
        } else if (entryType == SearchEntryType.COMMENT) {
            conditions.filter(existsQuery("commentId"));
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
        if (attachmentPresent != null) {
            var rangeQuery = new RangeQuery.Builder()
                .field("attachmentCount");
            if (attachmentPresent) {
                rangeQuery.gte(JsonData.of(1));
            } else {
                rangeQuery.lte(JsonData.of(0));
            }
            conditions.filter(
                new Query.Builder()
                    .range(rangeQuery.build())
                    .build()
            );
        }
        if (createdAfter != null || createdBefore != null) {
            var rangeQuery = new RangeQuery.Builder()
                .field("createdAt");
            if (createdAfter != null) {
                rangeQuery.gte(JsonData.of(createdAfter));
            }
            if (createdBefore != null) {
                rangeQuery.lte(JsonData.of(createdBefore));
            }
            conditions.filter(
                new Query.Builder()
                    .range(rangeQuery.build())
                    .build()
            );
        }
        if (!signedIn) {
            conditions.filter(
                new Query.Builder()
                    .bool(
                        new BoolQuery.Builder()
                            .should(notExistsQuery("viewPrincipal"))
                            .should(termQuery("viewPrincipal", "public"))
                            .build()
                    )
                    .build()
            );
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

    private static Query notExistsQuery(String fieldName) {
        return new Query.Builder()
            .bool(
                new BoolQuery.Builder()
                    .mustNot(existsQuery(fieldName))
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
