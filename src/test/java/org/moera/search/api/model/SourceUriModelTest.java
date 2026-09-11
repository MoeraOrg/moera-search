package org.moera.search.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.moera.lib.node.types.RepliedTo;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;

class SourceUriModelTest {

    @Test
    void returnsNodeAndReplySourceUris() {
        Node node = node(Map.of(
            "name", "alice.example",
            "fullName", "Alice",
            "sourceUri", "https://source.example/alice",
            "title", "Alice's node"
        ));

        var info = SearchNodeInfoUtil.build(node, null, null, 1.0f);

        assertThat(info.getNodeSourceUri()).isEqualTo("https://source.example/alice");

        RepliedTo repliedTo = new RepliedTo();
        repliedTo.setSourceUri("https://source.example/replied-to");

        assertThat(SearchRepliedToUtil.build(repliedTo).getSourceUri())
            .isEqualTo("https://source.example/replied-to");
    }

    private static Node node(Map<String, String> properties) {
        return (Node) Proxy.newProxyInstance(
            Node.class.getClassLoader(),
            new Class<?>[]{Node.class},
            (proxy, method, args) -> method.getName().equals("get")
                ? Values.value(properties.get((String) args[0]))
                : null
        );
    }

}
