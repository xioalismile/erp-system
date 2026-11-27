package com.erp.log.trace.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.log.trace.dto.TraceNode;
import com.erp.log.trace.entity.DocTrace;
import com.erp.log.trace.mapper.DocTraceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DocTraceServiceImpl extends ServiceImpl<DocTraceMapper, DocTrace> implements DocTraceService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addTrace(Map<String, Object> sourceDoc, Map<String, Object> targetDoc, List<Map<String, Object>> items) {
        if (sourceDoc == null || targetDoc == null) {
            return false;
        }

        Long sourceDocId = (Long) sourceDoc.get("id");
        String sourceDocNo = (String) sourceDoc.get("docNo");
        String sourceDocType = (String) sourceDoc.get("docType");

        Long targetDocId = (Long) targetDoc.get("id");
        String targetDocNo = (String) targetDoc.get("docNo");
        String targetDocType = (String) targetDoc.get("docType");

        if (sourceDocId == null || targetDocId == null) {
            return false;
        }

        String traceNo = "TR" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();

        if (items != null && !items.isEmpty()) {
            List<DocTrace> traceList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                DocTrace trace = new DocTrace();
                trace.setTraceNo(traceNo);
                trace.setSourceDocId(sourceDocId);
                trace.setSourceDocNo(sourceDocNo);
                trace.setSourceDocType(sourceDocType);
                trace.setTargetDocId(targetDocId);
                trace.setTargetDocNo(targetDocNo);
                trace.setTargetDocType(targetDocType);
                trace.setSourceItemId((Long) item.get("sourceItemId"));
                trace.setTargetItemId((Long) item.get("targetItemId"));
                trace.setMaterialId((Long) item.get("materialId"));
                trace.setMaterialName((String) item.get("materialName"));
                trace.setQuantity((java.math.BigDecimal) item.get("quantity"));
                trace.setTraceDirection("FORWARD");
                trace.setCreateTime(LocalDateTime.now());
                traceList.add(trace);
            }
            return saveBatch(traceList);
        } else {
            DocTrace trace = new DocTrace();
            trace.setTraceNo(traceNo);
            trace.setSourceDocId(sourceDocId);
            trace.setSourceDocNo(sourceDocNo);
            trace.setSourceDocType(sourceDocType);
            trace.setTargetDocId(targetDocId);
            trace.setTargetDocNo(targetDocNo);
            trace.setTargetDocType(targetDocType);
            trace.setTraceDirection("FORWARD");
            trace.setCreateTime(LocalDateTime.now());
            return save(trace);
        }
    }

    @Override
    public List<DocTrace> getForwardTrace(Long docId, String docType) {
        return baseMapper.findForwardTraces(docId, docType);
    }

    @Override
    public List<DocTrace> getBackwardTrace(Long docId, String docType) {
        return baseMapper.findBackwardTraces(docId, docType);
    }

    @Override
    public Map<String, Object> getFullTrace(Long docId, String docType) {
        Map<String, Object> result = new HashMap<>();
        result.put("forward", getForwardTrace(docId, docType));
        result.put("backward", getBackwardTrace(docId, docType));
        return result;
    }

    @Override
    public TraceNode getTraceTree(Long docId, String docType) {
        List<DocTrace> forwardTraces = getForwardTrace(docId, docType);
        List<DocTrace> backwardTraces = getBackwardTrace(docId, docType);

        String currentDocNo = null;
        if (!forwardTraces.isEmpty()) {
            currentDocNo = forwardTraces.get(0).getSourceDocNo();
        } else if (!backwardTraces.isEmpty()) {
            currentDocNo = backwardTraces.get(0).getTargetDocNo();
        }

        TraceNode root = new TraceNode(docId, currentDocNo, docType);

        buildForwardTree(root, docId, docType, new HashSet<>());
        buildBackwardTree(root, docId, docType, new HashSet<>());

        return root;
    }

    private void buildForwardTree(TraceNode parent, Long docId, String docType, Set<String> visited) {
        String key = docId + "_" + docType;
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);

        List<DocTrace> traces = baseMapper.findForwardTraces(docId, docType);
        for (DocTrace trace : traces) {
            TraceNode child = new TraceNode(trace.getTargetDocId(), trace.getTargetDocNo(), trace.getTargetDocType());
            parent.getChildren().add(child);
            buildForwardTree(child, trace.getTargetDocId(), trace.getTargetDocType(), visited);
        }
    }

    private void buildBackwardTree(TraceNode parent, Long docId, String docType, Set<String> visited) {
        String key = docId + "_" + docType;
        if (visited.contains(key)) {
            return;
        }
        visited.add(key);

        List<DocTrace> traces = baseMapper.findBackwardTraces(docId, docType);
        for (DocTrace trace : traces) {
            TraceNode child = new TraceNode(trace.getSourceDocId(), trace.getSourceDocNo(), trace.getSourceDocType());
            parent.getChildren().add(child);
            buildBackwardTree(child, trace.getSourceDocId(), trace.getSourceDocType(), visited);
        }
    }
}
