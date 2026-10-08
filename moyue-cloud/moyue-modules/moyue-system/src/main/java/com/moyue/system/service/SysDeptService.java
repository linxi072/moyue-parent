package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.system.domain.entity.SysDept;
import com.moyue.system.domain.vo.DeptVO;

import java.util.List;

/**
 * 部门管理域（④）服务。
 *
 * @author moyue
 */
public interface SysDeptService extends IService<SysDept> {

    /** 部门树 */
    List<DeptVO> listDeptTree(String deptName, Integer status);

    /** 新建部门（自动维护 ancestors） */
    Long createDept(SysDept entity);

    /** 编辑部门（父部门变化时重建子孙 ancestors） */
    boolean updateDept(SysDept entity);

    /** 删除部门（存在子部门或已分配用户时拒删） */
    boolean deleteDept(Long id);
}
