package devops.pipeline.service;

import devops.pipeline.domain.PipelineDefinition;
import devops.pipeline.domain.PipelineValidationError;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/** YAML 解析器只生成与执行器无关的流程描述，插件可用性校验由上层服务完成。 */
@Component
public class PipelineYamlParser {
    private static final String API_VERSION = "ai-devops/v1";

    public ParseResult parse(String yamlContent) {
        List<PipelineValidationError> errors = new ArrayList<>();
        SourceLocator locator = SourceLocator.empty();
        if (yamlContent == null || yamlContent.isBlank()) {
            errors.add(error("$", "YAML_REQUIRED", "流水线 YAML 不能为空", "请提供 YAML v1 定义"));
            return result(null, errors, locator);
        }
        Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(20);
            options.setCodePointLimit(200_000);
            loaded = new Yaml(new SafeConstructor(options)).load(yamlContent);
        } catch (YAMLException exception) {
            errors.add(error("$", "YAML_SYNTAX_INVALID", "YAML 语法无效", "请检查缩进、冒号和列表格式"));
            return result(null, errors, locator);
        }
        locator = SourceLocator.from(yamlContent);
        if (!(loaded instanceof Map<?, ?> raw)) {
            errors.add(error("$", "YAML_ROOT_INVALID", "YAML 顶层必须是对象", "请使用键值对象作为顶层定义"));
            return result(null, errors, locator);
        }
        Map<String, Object> root = stringMap(raw, "$", errors);
        requireExactApiVersion(root, errors);
        String name = string(root.get("name"), "$.name", errors, true);
        String repositoryId = string(root.get("repository"), "$.repository", errors, true);
        Map<String, PipelineDefinition.PipelineParameter> parameters = parseParameters(root.get("parameters"), errors);
        String defaultBranch = null;
        if (root.containsKey("source")) {
            if (root.get("source") instanceof Map<?, ?> sourceRaw) {
                Map<String, Object> source = stringMap(sourceRaw, "$.source", errors);
                defaultBranch = string(source.get("branch"), "$.source.branch", errors, false);
                unknownKeys(source, Set.of("branch"), "$.source", errors);
            } else {
                errors.add(error("$.source", "SOURCE_INVALID", "source 必须是对象", "请仅设置可选 branch"));
            }
        }
        unknownKeys(root, Set.of("apiVersion", "name", "repository", "source", "parameters", "stages"), "$", errors);
        List<PipelineDefinition.PipelineStage> stages = parseStages(root.get("stages"), parameters, errors);
        if (!errors.isEmpty()) {
            return result(null, errors, locator);
        }
        return result(new PipelineDefinition(name, repositoryId, defaultBranch, parameters, stages), List.of(), locator);
    }

    private Map<String, PipelineDefinition.PipelineParameter> parseParameters(Object value,
                                                                               List<PipelineValidationError> errors) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            errors.add(error("$.parameters", "PARAMETERS_INVALID", "parameters 必须是对象", "请使用参数名作为键声明参数"));
            return Map.of();
        }
        Map<String, Object> values = stringMap(raw, "$.parameters", errors);
        Map<String, PipelineDefinition.PipelineParameter> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String path = "$.parameters." + entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> parameterRaw)) {
                errors.add(error(path, "PARAMETER_INVALID", "参数声明必须是对象", "请声明 type 和可选的 required"));
                continue;
            }
            Map<String, Object> parameter = stringMap(parameterRaw, path, errors);
            String typeValue = string(parameter.get("type"), path + ".type", errors, true);
            boolean required = booleanValue(parameter.get("required"), path + ".required", errors, false);
            unknownKeys(parameter, Set.of("type", "required"), path, errors);
            PipelineDefinition.PipelineParameter.Type type = parameterType(typeValue, path, errors);
            if (type != null) {
                parameters.put(entry.getKey(), new PipelineDefinition.PipelineParameter(entry.getKey(), type, required));
            }
        }
        return Map.copyOf(parameters);
    }

    private List<PipelineDefinition.PipelineStage> parseStages(Object value,
                                                                Map<String, PipelineDefinition.PipelineParameter> parameters,
                                                                List<PipelineValidationError> errors) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            errors.add(error("$.stages", "STAGES_REQUIRED", "至少需要一个阶段", "请定义 stages 列表及其步骤"));
            return List.of();
        }
        List<PipelineDefinition.PipelineStage> stages = new ArrayList<>();
        Set<String> stepIds = new java.util.HashSet<>();
        int globalStepSequence = 1;
        for (int stageIndex = 0; stageIndex < values.size(); stageIndex++) {
            String path = "$.stages[" + stageIndex + "]";
            if (!(values.get(stageIndex) instanceof Map<?, ?> stageRaw)) {
                errors.add(error(path, "STAGE_INVALID", "阶段必须是对象", "请提供 name 和 steps"));
                continue;
            }
            Map<String, Object> stage = stringMap(stageRaw, path, errors);
            String stageName = string(stage.get("name"), path + ".name", errors, true);
            unknownKeys(stage, Set.of("name", "steps"), path, errors);
            if (!(stage.get("steps") instanceof List<?> stepValues) || stepValues.isEmpty()) {
                errors.add(error(path + ".steps", "STAGE_STEPS_REQUIRED", "阶段至少需要一个步骤", "请添加 steps"));
                continue;
            }
            List<PipelineDefinition.PipelineStep> steps = new ArrayList<>();
            for (int stepIndex = 0; stepIndex < stepValues.size(); stepIndex++) {
                String stepPath = path + ".steps[" + stepIndex + "]";
                if (!(stepValues.get(stepIndex) instanceof Map<?, ?> stepRaw)) {
                    errors.add(error(stepPath, "STEP_INVALID", "步骤必须是对象", "请提供 id、uses 和可选 with"));
                    continue;
                }
                Map<String, Object> step = stringMap(stepRaw, stepPath, errors);
                String id = string(step.get("id"), stepPath + ".id", errors, true);
                String uses = string(step.get("uses"), stepPath + ".uses", errors, true);
                unknownKeys(step, Set.of("id", "uses", "with"), stepPath, errors);
                if (id != null && !stepIds.add(id)) {
                    errors.add(error(stepPath + ".id", "STEP_ID_DUPLICATE", "步骤 id 必须全局唯一", "请为步骤指定未使用的 id"));
                }
                String[] plugin = splitPluginReference(uses, stepPath + ".uses", errors);
                Map<String, Object> input = parseInput(step.get("with"), stepPath + ".with", parameters, errors);
                if (id != null && plugin != null) {
                    steps.add(new PipelineDefinition.PipelineStep(id, globalStepSequence++, plugin[0], plugin[1], input));
                }
            }
            if (stageName != null) {
                stages.add(new PipelineDefinition.PipelineStage(stageName, stageIndex + 1, steps));
            }
        }
        return stages;
    }

    private Map<String, Object> parseInput(Object value, String path,
                                           Map<String, PipelineDefinition.PipelineParameter> parameters,
                                           List<PipelineValidationError> errors) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> raw)) {
            errors.add(error(path, "STEP_INPUT_INVALID", "with 必须是对象", "请使用键值对象传入插件参数"));
            return Map.of();
        }
        Map<String, Object> input = Map.copyOf(stringMap(raw, path, errors));
        input.forEach((key, inputValue) -> validateParameterReference(inputValue, path + "." + key, parameters, errors));
        return input;
    }

    private void validateParameterReference(Object value, String path,
                                            Map<String, PipelineDefinition.PipelineParameter> parameters,
                                            List<PipelineValidationError> errors) {
        if (value instanceof Map<?, ?> nested) {
            stringMap(nested, path, errors).forEach((key, nestedValue) ->
                    validateParameterReference(nestedValue, path + "." + key, parameters, errors));
            return;
        }
        if (value instanceof List<?> values) {
            for (int index = 0; index < values.size(); index++) {
                validateParameterReference(values.get(index), path + "[" + index + "]", parameters, errors);
            }
            return;
        }
        if (!(value instanceof String text) || !text.contains("${{")) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\$\\{\\{\\s*parameters\\.([A-Za-z][A-Za-z0-9_-]{0,63})\\s*}}$")
                .matcher(text);
        if (!matcher.matches()) {
            errors.add(error(path, "PARAMETER_REFERENCE_INVALID", "参数引用必须完整占据一个输入值", "使用 ${{ parameters.参数名 }} 格式"));
        } else if (!parameters.containsKey(matcher.group(1))) {
            errors.add(error(path, "PARAMETER_NOT_DECLARED", "引用了未声明的参数", "请先在 parameters 中声明该参数"));
        }
    }

    private PipelineDefinition.PipelineParameter.Type parameterType(String value, String path,
                                                                      List<PipelineValidationError> errors) {
        if (value == null) {
            return null;
        }
        try {
            return PipelineDefinition.PipelineParameter.Type.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(error(path, "PARAMETER_TYPE_UNSUPPORTED", "参数类型不受支持", "只支持 string、number、boolean 或 environment"));
            return null;
        }
    }

    private boolean booleanValue(Object value, String path, List<PipelineValidationError> errors, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean result) {
            return result;
        }
        errors.add(error(path, "FIELD_BOOLEAN_INVALID", "字段必须是布尔值", "请使用 true 或 false"));
        return defaultValue;
    }

    private String[] splitPluginReference(String reference, String path, List<PipelineValidationError> errors) {
        if (reference == null) {
            return null;
        }
        String[] values = reference.split("@", -1);
        if (values.length != 2 || values[0].isBlank() || values[1].isBlank() || values[0].length() > 128 || values[1].length() > 64) {
            errors.add(error(path, "PLUGIN_REFERENCE_INVALID", "uses 必须使用 插件名@版本 格式", "例如：company.maven-build@1.0.0"));
            return null;
        }
        return new String[]{values[0].trim(), values[1].trim()};
    }

    private void requireExactApiVersion(Map<String, Object> root, List<PipelineValidationError> errors) {
        String version = string(root.get("apiVersion"), "$.apiVersion", errors, true);
        if (version != null && !API_VERSION.equals(version)) {
            errors.add(error("$.apiVersion", "API_VERSION_UNSUPPORTED", "仅支持 ai-devops/v1", "请将 apiVersion 设置为 ai-devops/v1"));
        }
    }

    private Map<String, Object> stringMap(Map<?, ?> raw, String path, List<PipelineValidationError> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                errors.add(error(path, "OBJECT_KEY_INVALID", "对象键必须是非空字符串", "请使用有效字段名"));
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String string(Object value, String path, List<PipelineValidationError> errors, boolean required) {
        if (value == null) {
            if (required) {
                errors.add(error(path, "FIELD_REQUIRED", "字段不能为空", "请提供该字段"));
            }
            return null;
        }
        if (!(value instanceof String result) || result.isBlank() || result.length() > 255) {
            errors.add(error(path, "FIELD_STRING_INVALID", "字段必须是非空字符串且长度不超过 255", "请提供合法字符串"));
            return null;
        }
        return result.trim();
    }

    private void unknownKeys(Map<String, Object> values, Set<String> allowed, String path,
                             List<PipelineValidationError> errors) {
        values.keySet().stream().filter(key -> !allowed.contains(key)).forEach(key ->
                errors.add(error(path + "." + key, "FIELD_UNSUPPORTED", "存在不支持的字段", "请删除该字段或升级 YAML 版本")));
    }

    private PipelineValidationError error(String path, String code, String message, String suggestion) {
        return new PipelineValidationError(path, -1, -1, code, message, suggestion);
    }

    private ParseResult result(PipelineDefinition definition, List<PipelineValidationError> errors, SourceLocator locator) {
        return new ParseResult(definition, errors.stream().map(error -> locator.withPosition(error)).toList());
    }

    /**
     * SnakeYAML 的节点标记用于把结构化校验错误映射回源码位置。字段缺失时使用最近父节点，
     * 既便于前端定位，又不会在错误信息中回显可能含有敏感数据的 YAML 片段。
     */
    private static final class SourceLocator {
        private final Map<String, Location> locations;

        private SourceLocator(Map<String, Location> locations) {
            this.locations = locations;
        }

        static SourceLocator empty() {
            return new SourceLocator(Map.of("$", new Location(1, 1)));
        }

        static SourceLocator from(String yamlContent) {
            try {
                Node root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(yamlContent));
                if (root == null) {
                    return empty();
                }
                Map<String, Location> locations = new LinkedHashMap<>();
                index(root, "$", locations);
                return new SourceLocator(Map.copyOf(locations));
            } catch (YAMLException exception) {
                return empty();
            }
        }

        private static void index(Node node, String path, Map<String, Location> locations) {
            if (node.getStartMark() != null) {
                locations.put(path, new Location(node.getStartMark().getLine() + 1, node.getStartMark().getColumn() + 1));
            }
            if (node instanceof MappingNode mapping) {
                for (NodeTuple tuple : mapping.getValue()) {
                    if (tuple.getKeyNode() instanceof ScalarNode key) {
                        String childPath = path + "." + key.getValue();
                        if (tuple.getKeyNode().getStartMark() != null) {
                            locations.put(childPath, new Location(tuple.getKeyNode().getStartMark().getLine() + 1,
                                    tuple.getKeyNode().getStartMark().getColumn() + 1));
                        }
                        index(tuple.getValueNode(), childPath, locations);
                    }
                }
            } else if (node instanceof SequenceNode sequence) {
                for (int index = 0; index < sequence.getValue().size(); index++) {
                    index(sequence.getValue().get(index), path + "[" + index + "]", locations);
                }
            }
        }

        PipelineValidationError withPosition(PipelineValidationError error) {
            String path = error.path();
            Location location = locations.get(path);
            while (location == null && path.length() > 1) {
                int index = Math.max(path.lastIndexOf('.'), path.lastIndexOf('['));
                path = index <= 0 ? "$" : path.substring(0, index);
                location = locations.get(path);
            }
            Location resolved = location == null ? locations.getOrDefault("$", new Location(1, 1)) : location;
            return new PipelineValidationError(error.path(), resolved.line(), resolved.column(), error.ruleCode(),
                    error.message(), error.suggestion());
        }

        private record Location(int line, int column) {
        }
    }

    public record ParseResult(PipelineDefinition definition, List<PipelineValidationError> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
