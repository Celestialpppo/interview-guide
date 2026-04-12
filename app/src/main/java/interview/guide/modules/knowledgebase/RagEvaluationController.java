package interview.guide.modules.knowledgebase;

import interview.guide.common.result.Result;
import interview.guide.modules.knowledgebase.eval.model.*;
import interview.guide.modules.knowledgebase.eval.service.RagEvalDatasetService;
import interview.guide.modules.knowledgebase.eval.service.RagEvalRunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 离线评测管理接口。
 */
@RestController
@RequiredArgsConstructor
public class RagEvaluationController {

    private final RagEvalDatasetService ragEvalDatasetService;
    private final RagEvalRunService ragEvalRunService;

    @PostMapping(value = "/api/rag-evals/datasets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<RagEvalDatasetImportResponse> importDataset(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "version", required = false) String version,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "domain", required = false) String domain,
        @RequestParam(value = "labelSource", required = false) String labelSource
    ) {
        return Result.success(ragEvalDatasetService.importJsonl(
            file, name, version, description, domain, labelSource
        ));
    }

    @PostMapping("/api/rag-evals/runs")
    public Result<RagEvalRunDetailDTO> createRun(@Valid @RequestBody RagEvalRunRequest request) {
        return Result.success(ragEvalRunService.createAndExecuteRun(request));
    }

    @GetMapping("/api/rag-evals/runs/{runId}")
    public Result<RagEvalRunDetailDTO> getRun(@PathVariable Long runId) {
        return Result.success(ragEvalRunService.getRunDetail(runId));
    }

    @GetMapping("/api/rag-evals/runs/{runId}/cases")
    public Result<List<RagEvalRunCaseDTO>> listRunCases(@PathVariable Long runId) {
        return Result.success(ragEvalRunService.listRunCases(runId));
    }
}
