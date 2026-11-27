package com.erp.log.trace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.log.trace.dto.TraceNode;
import com.erp.log.trace.entity.DocTrace;

import java.util.List;
import java.util.Map;

public interface DocTraceService extends IService<DocTrace> {

    boolean addTrace(Map<String, Object> sourceDoc, Map<String, Object> targetDoc, List<Map<String, Object>> items);

    List<DocTrace> getForwardTrace(Long docId, String docType);

    List<DocTrace> getBackwardTrace(Long docId, String docType);

    Map<String, Object> getFullTrace(Long docId, String docType);

    TraceNode getTraceTree(Long docId, String docType);
}
