package com.pinnacle.deathstodiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigKeyPresenceTest {

    private static final String KEY = "max-discord-content-characters";

    @Test
    void detectsExplicitNullTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "webhook-url: \"example\"\nmax-discord-content-characters:\n", KEY));
    }

    @Test
    void detectsUniformlyIndentedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "  webhook-url: \"example\"\n  max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsUniformlyIndentedQuotedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "    webhook-url: \"example\"\n    \"max-discord-content-characters\": null\n", KEY));
    }

    @Test
    void detectsUnicodeEscapedDoubleQuotedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "\"max\\u002ddiscord-content-characters\": null\n", KEY));
    }

    @Test
    void detectsHexEscapedDoubleQuotedFlowKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "{\"max\\x2ddiscord-content-characters\": null, objective-name: deaths}\n", KEY));
    }

    @Test
    void detectsUnicodeEscapedExplicitMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "? \"max\\U0000002ddiscord-content-characters\"\n: null\n", KEY));
    }

    @Test
    void differentEscapedDoubleQuotedKeyDoesNotMatch() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "\"max\\u002ediscord-content-characters\": null\n", KEY));
    }

    @Test
    void detectsTaggedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "!!str max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsAnchoredTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "&limit max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsCombinedPropertiesOnQuotedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "!!str &limit \"max\\u002ddiscord-content-characters\": null\n", KEY));
    }

    @Test
    void detectsVerbatimTaggedTopLevelKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "!<tag:yaml.org,2002:str> max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsPropertiesOnExplicitMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "? &limit !!str max-discord-content-characters\n: null\n", KEY));
    }

    @Test
    void detectsPropertiesOnFlowStyleRootKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "{!!str &limit max-discord-content-characters: null, objective-name: deaths}\n", KEY));
    }

    @Test
    void ignoresPropertiesOnNestedKey() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "nested:\n  !!str &limit max-discord-content-characters: null\nobjective-name: deaths\n", KEY));
    }

    @Test
    void differentTaggedKeyDoesNotMatch() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "!!str max-discord-content-character: null\n", KEY));
    }

    @Test
    void detectsTopLevelKeyWithValue() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "max-discord-content-characters: 1900\n", KEY));
    }

    @Test
    void detectsExplicitYamlMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "? max-discord-content-characters\n: null\nobjective-name: deaths\n", KEY));
    }

    @Test
    void detectsMultilineExplicitYamlMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "?\n  max-discord-content-characters\n: null\nobjective-name: deaths\n", KEY));
    }

    @Test
    void detectsMultilineDecoratedExplicitYamlMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "?\n  !!str &limit \"max\\u002ddiscord-content-characters\"\n: null\n", KEY));
    }

    @Test
    void detectsIndentedQuotedExplicitYamlMappingKeyWithComment() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "  ? \"max-discord-content-characters\" # explicit setting\n  : null\n  objective-name: deaths\n", KEY));
    }

    @Test
    void ignoresNestedExplicitYamlMappingKey() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "nested:\n  ? max-discord-content-characters\n  : null\nobjective-name: deaths\n", KEY));
    }

    @Test
    void detectsFlowStyleRootMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "{webhook-url: \"example\", max-discord-content-characters: null}\n", KEY));
    }

    @Test
    void detectsQuotedFlowStyleRootMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "{webhook-url: \"example\", 'max-discord-content-characters': null}\n", KEY));
    }

    @Test
    void detectsExplicitFlowStyleRootMappingKey() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "{? max-discord-content-characters : null, objective-name: deaths}\n", KEY));
    }

    @Test
    void detectsMultilineFlowStyleRootMappingKeyAfterDocumentMarker() {
        String yaml = "--- # config\n"
                + "  {webhook-url: \"example\",\n"
                + "   # content limit stays explicit\n"
                + "   max-discord-content-characters: null}\n";

        assertTrue(ConfigKeyPresence.containsTopLevelKey(yaml, KEY));
    }

    @Test
    void ignoresNestedFlowStyleMappingKey() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "{nested: {max-discord-content-characters: null}, objective-name: deaths}\n", KEY));
    }

    @Test
    void ignoresColumnZeroContinuationInsideNestedFlowMapping() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "nested: {\nmax-discord-content-characters: null\n}\nobjective-name: deaths\n", KEY));
    }

    @Test
    void ignoresColumnZeroContinuationInsideNestedFlowSequence() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "nested: [\n{max-discord-content-characters: null}\n]\nobjective-name: deaths\n", KEY));
    }

    @Test
    void resumesRootScanningAfterNestedFlowValueCloses() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "nested: {\nother: null\n}\nmax-discord-content-characters: 1900\n", KEY));
    }

    @Test
    void detectsAnchoredRootFlowMapping() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "&config {max-discord-content-characters: 500}\n", KEY));
    }

    @Test
    void detectsTaggedRootFlowMapping() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "!!map {max-discord-content-characters: null}\n", KEY));
    }

    @Test
    void detectsCombinedDecoratorsOnRootFlowMapping() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "!!map &config {\"max\\u002ddiscord-content-characters\": null}\n", KEY));
    }

    @Test
    void ignoresDecoratedNestedFlowContinuation() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "nested: &child {\nmax-discord-content-characters: null\n}\nobjective-name: deaths\n", KEY));
    }

    @Test
    void detectsNullKeyInheritedThroughYamlMerge() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "defaults: &defaults {max-discord-content-characters: null}\n"
                        + "<<: *defaults\nobjective-name: deaths\n", KEY));
    }

    @Test
    void detectsKeyInheritedThroughYamlMergeSequence() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "first: &first {other: true}\n"
                        + "second: &second {max-discord-content-characters: null}\n"
                        + "<<: [*first, *second]\n", KEY));
    }

    @Test
    void detectsKeyInheritedThroughNestedYamlMerge() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "base: &base {max-discord-content-characters: null}\n"
                        + "middle: &middle {<<: *base, other: true}\n"
                        + "<<: *middle\n", KEY));
    }

    @Test
    void ignoresUnmergedAnchorContainingTargetKey() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "defaults: &defaults {max-discord-content-characters: null}\n"
                        + "objective-name: deaths\n", KEY));
    }

    @Test
    void treatsOmittedLegacyKeyAsAbsent() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "webhook-url: \"example\"\nobjective-name: deaths\n", KEY));
    }

    @Test
    void ignoresCommentedOrNestedKeys() {
        String yaml = "# max-discord-content-characters:\n"
                + "nested:\n"
                + "  max-discord-content-characters:\n";

        assertFalse(ConfigKeyPresence.containsTopLevelKey(yaml, KEY));
    }

    @Test
    void ignoresNestedKeyWhenRootMappingIsIndented() {
        String yaml = "  nested:\n"
                + "    max-discord-content-characters: null\n"
                + "  objective-name: deaths\n";

        assertFalse(ConfigKeyPresence.containsTopLevelKey(yaml, KEY));
    }

    @Test
    void documentMarkersDoNotChangeIndentedRootLevel() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "---\n  webhook-url: \"example\"\n  max-discord-content-characters: null\n...\n", KEY));
    }

    @Test
    void documentMarkersWithInlineCommentsDoNotChangeIndentedRootLevel() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "--- # config\n  webhook-url: \"example\"\n  max-discord-content-characters: null\n... # end\n", KEY));
    }

    @Test
    void markerLikeRootContentDoesNotCreateFalsePositive() {
        assertFalse(ConfigKeyPresence.containsTopLevelKey(
                "---not-a-document-marker: true\n  max-discord-content-characters: null\n", KEY));
    }

    @Test
    void detectsQuotedTopLevelKeysAndUtf8Bom() {
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "\uFEFF\"max-discord-content-characters\": null\n", KEY));
        assertTrue(ConfigKeyPresence.containsTopLevelKey(
                "'max-discord-content-characters': null\n", KEY));
    }
}
