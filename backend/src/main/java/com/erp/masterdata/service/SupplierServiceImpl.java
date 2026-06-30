package com.erp.masterdata.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.mapper.SupplierMapper;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {
}
