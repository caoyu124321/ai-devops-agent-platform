package devops.pipeline.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import devops.iam.identity.IdentityService;
import devops.pipeline.service.PipelineRunService;
import devops.pipeline.service.PipelineService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 验证 REST 层只完成 HTTP 映射，并将已认证用户与通用运行参数交给服务层。 */
class PipelineControllerTest {
    private final PipelineService pipelineService = mock(PipelineService.class);
    private final PipelineRunService pipelineRunService = mock(PipelineRunService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PipelineController(pipelineService, pipelineRunService,
                new PipelineCurrentActorResolver())).setControllerAdvice(new PipelineExceptionHandler()).build();
    }

    @Test
    void mapsRunParametersAndIdempotencyKeyToService() throws Exception {
        when(pipelineRunService.create(eq("user-1"), eq("version-1"), eq("main"), eq(null),
                eq(Map.of("imageTag", "1.2.3")), eq("run-key")))
                .thenReturn(new PipelineRunService.RunView("run-1", "version-1", "QUEUED", "main", null,
                        Instant.EPOCH, null, null, null, null));

        mockMvc.perform(post("/api/v1/pipeline-versions/version-1/runs")
                        .requestAttr("iamPrincipal", new IdentityService.SessionPrincipal("session-1",
                                new IdentityService.UserView("user-1", "tester", "tester@example.com")))
                        .header("Idempotency-Key", "run-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branch\":\"main\",\"parameters\":{\"imageTag\":\"1.2.3\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("run-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(pipelineRunService).create("user-1", "version-1", "main", null, Map.of("imageTag", "1.2.3"),
                "run-key");
    }
}
