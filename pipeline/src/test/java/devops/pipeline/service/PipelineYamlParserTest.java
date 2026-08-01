package devops.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineYamlParserTest {
    private final PipelineYamlParser parser = new PipelineYamlParser();

    @Test
    void parsesVersionedPipelineWithOrderedSteps() {
        PipelineYamlParser.ParseResult result = parser.parse("""
                apiVersion: ai-devops/v1
                name: build-and-test
                repository: repository-1
                source:
                  branch: main
                stages:
                  - name: build
                    steps:
                      - id: compile
                        uses: example.build@1.0.0
                        with:
                          goal: package
                  - name: test
                    steps:
                      - id: unit-test
                        uses: example.test@1.0.0
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.definition().repositoryId()).isEqualTo("repository-1");
        assertThat(result.definition().stages()).hasSize(2);
        assertThat(result.definition().stages().get(1).steps().getFirst().sequenceNo()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownFieldsAndDuplicateStepIds() {
        PipelineYamlParser.ParseResult result = parser.parse("""
                apiVersion: ai-devops/v1
                name: invalid
                repository: repository-1
                unexpected: value
                stages:
                  - name: build
                    steps:
                      - id: duplicate
                        uses: example.build@1.0.0
                      - id: duplicate
                        uses: example.test@1.0.0
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(error -> error.ruleCode())
                .contains("FIELD_UNSUPPORTED", "STEP_ID_DUPLICATE");
        assertThat(result.errors()).allSatisfy(error -> {
            assertThat(error.line()).isPositive();
            assertThat(error.column()).isPositive();
        });
    }

    @Test
    void requiresExplicitPluginVersion() {
        PipelineYamlParser.ParseResult result = parser.parse("""
                apiVersion: ai-devops/v1
                name: invalid-plugin
                repository: repository-1
                stages:
                  - name: build
                    steps:
                      - id: compile
                        uses: example.build
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(error -> error.ruleCode()).contains("PLUGIN_REFERENCE_INVALID");
    }

    @Test
    void parsesDeclaredParametersAndRejectsUnknownReferences() {
        PipelineYamlParser.ParseResult valid = parser.parse("""
                apiVersion: ai-devops/v1
                name: parameterized
                repository: repository-1
                parameters:
                  imageTag:
                    type: string
                    required: true
                stages:
                  - name: build
                    steps:
                      - id: compile
                        uses: example.build@1.0.0
                        with:
                          tag: ${{ parameters.imageTag }}
                """);
        PipelineYamlParser.ParseResult invalid = parser.parse("""
                apiVersion: ai-devops/v1
                name: invalid-reference
                repository: repository-1
                stages:
                  - name: build
                    steps:
                      - id: compile
                        uses: example.build@1.0.0
                        with:
                          tag: ${{ parameters.missing }}
                """);

        assertThat(valid.valid()).isTrue();
        assertThat(valid.definition().parameters()).containsKey("imageTag");
        assertThat(invalid.errors()).extracting(error -> error.ruleCode()).contains("PARAMETER_NOT_DECLARED");
    }
}
