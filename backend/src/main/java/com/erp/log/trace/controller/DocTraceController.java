package com.erp.log.trace.controller;

import com.erp.common.Result;
import com.erp.log.trace.dto.TraceNode;
import com.erp.log.trace.entity.DocTrace;
import com.erp.log.trace.service.DocTraceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doc-trace")
public class DocTraceController {

    private final DocTraceService docTraceService;

    public DocTraceController(DocTraceService docTraceService) {
        this.docTraceService = docTraceService;
    }

    @GetMapping("/forward")
    public Result<List<DocTrace>> getForwardTrace(
            @RequestParam Long docId,
            @RequestParam String docType) {
        return Result.success(docTraceService.getForwardTrace(docId, docType));
    }

    @GetMapping("/backward")
    public Result<List<DocTrace>> getBackwardTrace(
            @RequestParam Long docId,
            @RequestParam String docType) {
        return Result.success(docTraceService.getBackwardTrace(docId, docType));
    }

    @GetMapping("/full")
    public Result<Map<String, Object>> getFullTrace(
            @RequestParam Long docId,
            @RequestParam String docType) {
        return Result.success(docTraceService.getFullTrace(docId, docType));
    }

    @GetMapping("/tree")
    public Result<TraceNode> getTraceTree(
            @RequestParam Long docId,
            @RequestParam String docType) {
        return Result.success(docTraceService.getTraceTree(docId, docType));
    }
}
