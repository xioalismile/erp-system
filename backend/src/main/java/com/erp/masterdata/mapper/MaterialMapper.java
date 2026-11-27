package com.erp.masterdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.masterdata.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    /**
     * 查询指定编号段（前缀）下的全部物料编码，用于"删除即释放 + 补最小空洞"的流水探测。
     * 例：prefix=WL0100 → 返回 WL01000001、WL01000002 …
     *
     * @param prefix 编号段前缀（不含流水，如 WL0100 / MAT）
     * @return 该号段下已使用的物料编码列表
     */
    @Select("SELECT material_code FROM material WHERE material_code LIKE CONCAT(#{prefix}, '%')")
    List<String> selectCodesByPrefix(@Param("prefix") String prefix);

    /**
     * 查询"纯数字且长度等于 len"的物料编码（无前缀规则场景）。
     * 用于编码规则未配置前缀、也不拼接分类编码/日期时：此时规则产出纯数字定长编码，
     * 号段无法用前缀界定，改按"格式"界定（全部为数字且长度一致）。
     *
     * @param len 流水位数（seq_length）
     * @return 符合该格式的物料编码列表
     */
    @Select("SELECT material_code FROM material WHERE material_code ~ '^[0-9]+$' AND length(material_code) = #{len}")
    List<String> selectPureNumericCodes(@Param("len") int len);
}
