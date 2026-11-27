package com.erp.log.trace.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TraceNode {

    private Long docId;

    private String docNo;

    private String docType;

    private List<TraceNode> children = new ArrayList<>();

    public TraceNode() {
    }

    public TraceNode(Long docId, String docNo, String docType) {
        this.docId = docId;
        this.docNo = docNo;
        this.docType = docType;
    }
}
