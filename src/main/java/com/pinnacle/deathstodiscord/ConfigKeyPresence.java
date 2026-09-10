package com.pinnacle.deathstodiscord;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ConfigKeyPresence {

    private ConfigKeyPresence() {
    }

    static boolean containsTopLevelKey(Path configFile, String key) throws IOException {
        String yaml = Files.readString(configFile, StandardCharsets.UTF_8);
        try {
            return containsTopLevelKey(yaml, key);
        } catch (RuntimeException error) {
            throw new IOException("Could not parse config.yml while checking key presence.", error);
        }
    }

    static boolean containsTopLevelKey(String yaml, String key) {
        if (yaml == null || yaml.isEmpty() || key == null || key.isEmpty()) {
            return false;
        }

        String content = yaml.charAt(0) == '\uFEFF' ? yaml.substring(1) : yaml;
        final Node root;
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
            loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
            loaderOptions.setNestingDepthLimit(100);
            root = new Yaml(loaderOptions).compose(new StringReader(content));
        } catch (RuntimeException error) {
            return false;
        }

        Node unwrappedRoot = unwrapAnchor(root);
        if (!(unwrappedRoot instanceof MappingNode mapping)) {
            return false;
        }

        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return mappingContainsKey(mapping, key, visited);
    }

    private static boolean mappingContainsKey(MappingNode mapping, String key, Set<Node> visited) {
        if (!visited.add(mapping)) {
            return false;
        }

        for (NodeTuple tuple : mapping.getValue()) {
            Node keyNode = unwrapAnchor(tuple.getKeyNode());
            if (!(keyNode instanceof ScalarNode scalarKey)) {
                continue;
            }

            if (key.equals(scalarKey.getValue())) {
                return true;
            }

            if (Tag.MERGE.equals(scalarKey.getTag())
                    && mergedNodeContainsKey(tuple.getValueNode(), key, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mergedNodeContainsKey(Node node, String key, Set<Node> visited) {
        Node unwrapped = unwrapAnchor(node);
        if (unwrapped instanceof MappingNode mapping) {
            return mappingContainsKey(mapping, key, visited);
        }
        if (unwrapped instanceof SequenceNode sequence) {
            for (Node element : sequence.getValue()) {
                if (mergedNodeContainsKey(element, key, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Node unwrapAnchor(Node node) {
        Node current = node;
        while (current instanceof AnchorNode anchor) {
            current = anchor.getRealNode();
        }
        return current;
    }
}
