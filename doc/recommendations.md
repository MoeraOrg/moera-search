# Recommendation Algorithms

## Scope

Moera Search exposes recommendations for postings and active nodes. Posting recommendations have two distinct paths:

- personalized recommendations for an identified client;
- global popularity lists for anonymous clients and the dedicated reading and commenting endpoints.

The service does not use text similarity, embeddings, a learned model, or a per-client numeric score. It builds a graph
of nodes, postings, publications, comments, reactions, and short-lived `Favor` events in Neo4j. It precomputes several
global scores from that graph and applies graph-based candidate selection and filtering at request time.

The active-node endpoint is a separate global ranking. The `CLOSE_TO` graph used by node search and suggestions is not
used by the recommendation endpoints described here.

## Data model used by recommendations

The important graph entities and edges are:

- `(p:Posting)-[:SOURCE]->(n:MoeraNode)`: the node where the posting originates;
- `(p:Posting)-[:OWNER]->(o:MoeraNode)`: the posting author, which may differ from the source node;
- `(pb:Publication)-[:CONTAINS]->(p)` and `(pb)-[:PUBLISHED_IN]->(publisher)`: a copy of a posting in a feed;
- `(comment:Comment)-[:UNDER]->(p)` and `(reaction:Reaction)-[:REACTS_TO]->(p|comment)`;
- `(favor:Favor)-[:DONE_BY]->(actor)` and `(favor)-[:DONE_TO]->(node|posting)`;
- `(favor)-[:CAUSED_BY]->(publication|comment|reaction|onboarding)`;
- client relationships `FRIEND`, `SUBSCRIBED`, `BLOCKS`, `DONT_RECOMMEND`, and `WAS_RECOMMENDED`.

The principal precomputed properties are:

- `Posting.recommendationOrder`, a recency value boosted by lifetime engagement and onboarding;
- `Posting.readPopularity`, decayed publication and positive-post-reaction activity;
- `Posting.commentPopularity`, decayed direct-comment activity;
- `Posting.commentFad`, a shorter-lived direct-comment activity value;
- `Posting.popularity = readPopularity + commentPopularity` for postings with a positive component;
- `MoeraNode.activity`, decayed recent posting activity.

Sheriff moderation is stored as sheriff-name arrays on source nodes, owners, and postings. The optional
`MoeraNode.badRecommendation` flag is also checked for posting results. This codebase reads that flag but contains no
writer for it.

## Data acquisition and ingest

### Node discovery and graph maintenance

When a regular node is discovered, `NameScanJob` loads its profile, subscribes the search service to updates, and
schedules initial scans of its people lists and timeline. `PeopleScanJob` records public friends, feed subscriptions,
and indefinite blocks. `TimelineScanJob` walks the timeline in pages of 50, ingests signed original postings, and turns
remote timeline stories into queued publication updates.

Subsequent `SEARCH_CONTENT_UPDATED` notifications enqueue additions, updates, and deletions for postings, publications,
comments, reactions, friendships, subscriptions, and blocks. The jobs fetch the authoritative object where needed and
verify posting, comment, and reaction signatures before ingest. Therefore recommendation inputs are maintained both by
the initial scans and by later notifications.

The personalized candidate query treats every `FRIEND` and every `SUBSCRIBED` edge as a peer connection. It does not
filter subscriptions by their `feedName` when selecting peers.

### Posting ingest

For a new posting, ingest performs the following recommendation-related work:

1. Before creating the posting, it classifies the source node as novice/returned. The node qualifies when it has fewer
   than three stored postings, or when its third-newest posting is older than 30 days.
2. It creates the `Posting`, links its source and owner, stores content metadata and the seconds-based `createdAt`, and
   adds timeline/news feed references found in the posting.
3. If the node qualified as novice/returned, it creates an onboarding `Favor` for the posting with value 50 and a
   48-hour lifetime. Its actor is a `Machine` node and its cause is an `Onboarding` node.
4. It computes `recommendationOrder` after the onboarding favor has been created.
5. It queues full comment and reaction scans when the posting reports existing activity. Empty sets are marked as
   already scanned.

Initial source feed references are stored as `Publication` nodes directly. A remote publication received from a timeline
scan or notification follows `PostingIngest.addPublication`: it stores the publication, updates the search index's
publisher metadata, and attempts to create publication favors. The initial source-feed-reference path does not invoke
favor creation.

`createPublicationFavors` matches only a publication in the `timeline` feed and only when the publisher differs from the
posting owner. It creates two identical favors: one targeting the owner node and one targeting the posting. Consequently
a `news` publication is recorded and indexed but does not contribute a favor, even though the generic publication ingest
method calls the favor logic for both timeline and news updates.

### Comments and reactions

A newly ingested comment immediately causes `recommendationOrder` to be recomputed after the `Comment` node is created.
The count used by that formula includes every stored comment, including a comment by the posting owner. A direct comment
also creates favors with value 10 and a seven-day lifetime when the commenter differs from the posting owner:

- one favor targets the posting owner;
- one favor targets the posting.

A reply to another user's comment creates an additional value-5, seven-day favor from the replier to the replied-to
comment owner. That favor targets a node, not the posting.

For a posting reaction, ingest stores the reaction and recomputes `recommendationOrder`. Only positive reactions are
counted by the formula. A positive reaction by someone other than the posting owner creates value-1, three-day favors
targeting the owner and the posting. Negative reactions create no favors and add no recommendation-order boost.

A positive reaction to another user's comment creates a value-0.25, three-day favor targeting the comment owner. It does
not target the posting, so it does not contribute to posting popularity or posting candidate generation. Reactions to
comments also do not cause `recommendationOrder` to be recomputed.

Delete ingest removes the favors caused by the deleted publication, comment, or reaction. Deleting a direct comment or a
posting reaction also recomputes `recommendationOrder`; bulk deletion does the same. Posting deletion removes its
comments, reactions, publications, onboarding favor, index document, and graph node.

### Favor parameters

| Cause | Value | Lifetime | Targets posting | Targets a node |
| --- | ---: | ---: | --- | --- |
| Timeline publication by a different node | 10 | 14 days | Yes | Posting owner |
| Positive posting reaction by a different node | 1 | 3 days | Yes | Posting owner |
| Direct comment by a different node | 10 | 7 days | Yes | Posting owner |
| Reply to another user's comment | 5 | 7 days | No | Replied-to owner |
| Positive reaction to another user's comment | 0.25 | 3 days | No | Comment owner |
| Novice/returned-node onboarding | 50 | 2 days | Yes | No |

Expired input events are not ingested as favors. Every hour, `FavorIngest.purgeExpired` first recomputes
`recommendationOrder` for postings having a favor whose deadline has passed, then deletes all expired favors. This
ordering removes the onboarding term from the score before the corresponding favor disappears.

## Precomputed ranking signals

### Recommendation order

`recommendationOrder` is computed as:

```text
r = number of positive reactions directly on the posting
c = number of comments directly under the posting
ob = number of current onboarding favors targeting the posting
external = 25 when the source node has a non-empty sourceUri, otherwise 0

engagement = r + 5*c + 25*ob - external
boost = trunc(tanh(engagement / 35) * 600000)
recommendationOrder = createdAt + boost
```

Both `createdAt` and the resulting order are in seconds. The boost is therefore capped below 600,000 seconds, about 6.94
days. It saturates through `tanh`: additional engagement has progressively less effect. Each comment has five times the
weight of a positive reaction. A current onboarding favor contributes 25 to this particular formula; its stored value of
50 is not read by this formula or by the posting-popularity queries. Only the number of matching onboarding favors
matters to `recommendationOrder`. A posting whose source node has a non-empty `sourceUri` receives a penalty of 25
before the engagement value passes through `tanh`.

The value is updated on posting fill, direct comment addition/deletion, direct posting reaction addition/deletion, and
the hourly removal of expired onboarding favors. Updating a source node profile also recomputes the value for all its
postings so changes to `sourceUri` take effect. Publication favors and their deletion do not change it.

Migration V26 initialized existing postings with the same formula without the onboarding term. Runtime recomputation
uses the current formula with onboarding and the external-source penalty. Migration V33 applies the current formula to
existing postings whose source profile already contains `sourceUri`.

### Posting popularity

Every hour, `PostingIngest.refreshPopularity` runs five database updates in this order:

1. Set `readPopularity` to zero on previously positive postings that no longer have publication- or reaction-caused
   favors.
2. Recompute `readPopularity` for postings with publication- or reaction-caused favors.
3. Set `commentPopularity` and `commentFad` to zero on previously positive postings that no longer have comment-caused
   favors.
4. Recompute `commentPopularity` and `commentFad` for postings with comment-caused favors.
5. Set `popularity` to `readPopularity + commentPopularity` where at least one component is positive.

For favor `i`, let `ageHours_i = (nowMillis - createdAtMillis) / 3,600,000`, `D_i` be its configured decay lifetime in
hours, and `x_i = ageHours_i / D_i`. The main decayed contribution is:

```text
contribution_i = value_i * (1 - x_i^2)
```

`readPopularity` sums this value for posting-targeting favors caused by `Publication` or `Reaction`. In practice these
are timeline-publication and positive-post-reaction favors. Their different lifetimes cause publications to decay over
14 days and positive reactions over three days.

`commentPopularity` sums the same value for posting-targeting favors caused by direct `Comment` nodes, with a seven-day
lifetime. `commentFad` uses a faster curve over the same favors:

```text
fadContribution_i = max(value_i * (1 - 9*x_i^2), 0)
```

Thus a comment stops contributing to `commentFad` after one third of its seven-day lifetime, about 56 hours, while it
can continue contributing to `commentPopularity` for the full week.

The queries do not independently clamp the main contribution to zero and do not test favor deadlines. Normally the
hourly purge bounds `x` at one, but expired favors can briefly produce negative contributions if popularity refresh runs
before favor purge. The final `popularity` property is only rewritten for a posting when a component is positive; a
prior value is not explicitly zeroed by the last step when both components cease to be positive.

### Active-node score

`ActivityUpdater` runs every 15 minutes and asks Neo4j to refresh at most 500 nodes whose activity has never been
computed or is at least one day old. For each selected node, only source postings from the last 90 days are used. With
`D = 2,160` hours:

```text
x_p = ageHours(posting p) / D
node.activity = sum(1 - x_p^2) for source postings p newer than 90 days
```

Each new posting starts near 1 and decays quadratically to zero at 90 days. Posting engagement does not affect this
score. The query uses an inner `MATCH` for recent postings and has no zeroing pass. A node with no posting in the 90-day
window is dropped from the update query, so an older non-null `activity` value is not cleared and its
`activityUpdatedAt` is not advanced by this implementation.

## Request-time posting recommendation algorithm

The posting controller defaults `feed` to `news`, defaults `limit` to 20, caps it at 200, rejects a negative limit, and
returns immediately for zero. A non-empty sheriff name is validated and, on first use, schedules an asynchronous sheriff
scan before the query runs.

### Common result filters and fields

All posting recommendation queries eventually apply the same result fragment. A candidate is retained only if:

- its source node does not have `badRecommendation = true`;
- when a sheriff is supplied, the source node is not marked by that sheriff;
- its owner is not owner-marked by that sheriff;
- the posting itself is not marked by that sheriff.

No sheriff filtering is applied when `sheriff` is null. The fragment requires a source node and an owner node. It does
not explicitly check posting visibility, scan status, age, or whether `createdAt` is present.

The limit is applied after candidate ordering and these filters, but before optional avatar matching and result
projection. Each result contains source node name, posting ID, owner identity/avatar, heading, total positive reactions,
positive reactions in the last 24 hours, total comments, and comments in the last 24 hours. The last-day counts are
response metadata; they do not affect any request-time ordering.

### Identified client: primary personalized candidates

For an identified client `c`, the first query constructs a distinct union of postings from two sources:

1. A posting targeted by any `Favor` whose actor is a node connected from `c` by either `SUBSCRIBED` or `FRIEND`.
2. A posting targeted by any `Favor` whose actor is the singleton `Machine` node.

This means the useful peer actions are timeline republication, a direct comment, and a positive reaction on the posting,
because those are the normal events that create posting-targeting favors. Node-targeting reply and comment-reaction
favors cannot produce a posting candidate. The onboarding favor is performed by `Machine`, so it makes the posting a
candidate through the global branch for as long as the favor exists. All onboarding favors share the same singleton
`Machine` actor.

Candidates are deduplicated by the Cypher `UNION` and ordered only by descending `p.recommendationOrder`. The number of
peers who acted, the number or value of their favors, favor age, relationship type, and subscription feed do not alter
the rank. A favor only establishes candidate membership. Because the query has no explicit deadline predicate, a favor
that has just expired can continue to establish membership until the hourly purge removes it.

The query then excludes a candidate when any of the following holds:

- the client has `SUBSCRIBED`, `BLOCKS`, or node-level `DONT_RECOMMEND` to the posting's source node;
- the client has posting-level `WAS_RECOMMENDED` or `DONT_RECOMMEND` for this posting and the requested feed;
- the posting is already contained in a `timeline` publication at the client node.

The first rule deliberately removes sources the client already subscribes to, even though subscribed nodes also
participate as peer actors in candidate generation. Any `BLOCKS` edge excludes the source regardless of its stored
blocked operation. Node-level exclusion is based on the source node, not separately on the posting owner.

The requested `feed` affects only matching of per-post acceptance/rejection history. It does not select candidate
favors, change ranking, or change the fixed check for an existing client `timeline` publication.

### Identified client: fallback candidates

If the primary query returns fewer than the requested limit, the controller runs a second query over every posting. It
uses the same client exclusions, common moderation filters, and output projection. Its ordering is:

```text
1. postings where popularity is non-null before postings where it is null
2. popularity descending
3. recommendationOrder descending
```

The fallback does not require popularity to be positive, so postings with zero or negative stored values remain
eligible. The query requests the full user limit rather than only the missing count. The controller appends results in
fallback order, skips posting locations already returned by the primary query, and stops at the requested size.

### Anonymous client: overall popularity

An anonymous request to `GET /recommendations/postings` does not use personalized candidate generation. It selects
postings with non-null positive `popularity`, orders by that value descending, applies the common moderation filters,
and takes up to 200 results for the cache. The requested `feed` has no effect in this path.

### Reading and commenting lists

`GET /recommendations/postings/reading` is global for every caller. It requires positive `readPopularity` and orders by
`readPopularity` descending.

`GET /recommendations/postings/commenting` is also global. It requires positive `commentPopularity` and orders first by
`coalesce(commentFad, 0)` descending, then by `commentPopularity` descending. This favors very recent comment bursts;
the longer-lived score breaks ties and ranks entries after their fad value reaches zero.

Both paths apply the common sheriff and bad-recommendation filters and return the same engagement metadata as other
posting lists.

## Popular-list cache

The anonymous overall list and the reading and commenting lists are cached as serialized JSON in one
`CachePopularPostings` node per sheriff key. A null sheriff is normalized to the empty string. The three list fields are
`popular`, `popularReading`, and `popularCommenting`; each cache miss computes up to 200 entries, and a request returns
a prefix of that cached list.

The cache node receives `deadline = now + 1 hour` only when the node is first created. Filling another field, or
rewriting a field on the same cache node, does not extend the deadline. A scheduled job runs hourly and deletes nodes
whose deadline is in the past.

Cache reads do not compare the deadline with the current time. An expired entry can therefore be served until the purge
job deletes its node. If a field is filled after the shared deadline has expired but before deletion, it inherits that
expired deadline. Personalized posting recommendations and active-node recommendations are not cached here.

## Feedback and exclusions

Accepting a posting recommendation replaces any existing same-feed feedback edge with `WAS_RECOMMENDED {feedName:
feed}`. Rejecting it similarly writes `DONT_RECOMMEND {feedName: feed}`. Either marker suppresses the posting in later
personalized requests for that feed. Feedback does not modify a score and does not suppress the posting in another feed
or in global popularity endpoints.

The node exclusion endpoint writes client-to-node `DONT_RECOMMEND` without a feed. Personalized posting recommendations
use that relationship to suppress every posting whose source is the excluded node. Removing the edge allows such
postings again.

## Active-node recommendations

`GET /recommendations/nodes/active` defaults to 20 results, caps the limit at 200, validates/activates an optional
sheriff, and selects all nodes with non-null `activity`. It applies sheriff filtering to both the node's direct sheriff
marks and its owner sheriff marks, orders solely by `activity` descending, and takes the requested limit.

Subscriber and posting counts are calculated only after ranking and limiting:

- subscribers are incoming `SUBSCRIBED {feedName: "timeline"}` edges;
- postings are incoming `Publication {feedName: "timeline"}` nodes published in the recommended node.

These counts are returned as display metadata and do not affect rank. The active-node query is not client-specific and
does not read friendship, subscription, block, or `DONT_RECOMMEND` relationships. As implemented, a node saved through
`POST /recommendations/nodes/excluded/{nodeName}` can still appear in `GET /recommendations/nodes/active`; the exclusion
edge only participates in personalized posting filtering.

## Timing and consistency characteristics

Recommendation state is eventually consistent for several independent reasons:

- content and social changes arrive through queued jobs after notifications, while initial discovery runs separate
  people and timeline scans;
- posting popularity is refreshed hourly;
- expired favors are purged hourly in a separate scheduled task;
- active-node scores are checked every 15 minutes, but an individual node is eligible only once per day and updates are
  limited to 500 nodes per run;
- sheriff activation queues its first scan asynchronously, so the request that triggers activation can precede the
  marks;
- global popular lists have a shared one-hour cache deadline and can remain available until the hourly cache purge.

Within those delays, request-time queries use the currently stored graph and precomputed properties without an
additional recalculation.
