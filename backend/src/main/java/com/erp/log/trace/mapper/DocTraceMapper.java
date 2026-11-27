package com.erp.log.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.log.trace.entity.DocTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocTraceMapper extends BaseMapper<DocTrace> {

    @Select("SELECT * FROM doc_trace WHERE source_doc_id = #{docId} AND source_doc_type = #{docType}")
    List<DocTrace> findForwardTraces(@Param("docId") Long docId, @Param("docType") String docType);

    @Select("SELECT * FROM doc_trace WHERE target_doc_id = #{docId} AND target_doc_type = #{docType}")
    List<DocTrace> findBackwardTraces(@Param("docId") Long docId, @Param("docType") String docType);
}
