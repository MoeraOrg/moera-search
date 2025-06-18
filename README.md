# Moera Search Engine

## Resources

* Live network: https://web.moera.org
* Live search engine: https://search.moera.org
* Read more about Moera at https://moera.org
* Learn more about search in Moera: https://moera.org/overview/other.html
* Bugs and feature requests: https://github.com/MoeraOrg/moera-issues/issues

* How to set up a complete Moera Development Environment:
  http://moera.org/development/development-environment.html

## Installation instructions

1. As prerequisites, you need to have Java 17+, Neo4j Community 2025.04+ (with APOC plugin)
   and OpenSearch 2.19+ installed.
2. Create an empty Neo4j database and an empty OpenSearch index.
3. Create a directory `<media>`, where the server will keep media files.
4. Go to the source directory.
5. Create `application-dev.yml` file with the following content:

   ```yaml
    search:
      signing-key: "26c2bf7f3108c8b177a6a99ec006ffcc33efbe8c7aeeac0afee6619744a2df82"
      address: "127.0.0.1"
      naming-server: "https://naming-dev.moera.org/moera-naming"

    database:
      url: <db url>
      user: <db user>
      password: <db password>
      database: <db name>

    index:
      host: <opensearch host>
      user: <opensearch user>
      password: <opensearch password>
      index-name: <opensearch index name>

    media:
      path: <media>
   ```

   `signing-key` is a private signing key for `search` node on the development naming server.
   If you register a different node name, change `signing-key` and `node-name` settings accordingly.

6. By default, the server runs on port 8082. If you want it to run on a
   different port, add these lines to the file above:

   ```yaml
   server:
     port: <port number>
   ```
7. Execute `./run` script.
8. If you use your own [naming server][1], make sure its location is set
   correctly in node settings.

[1]: https://github.com/MoeraOrg/moera-naming
