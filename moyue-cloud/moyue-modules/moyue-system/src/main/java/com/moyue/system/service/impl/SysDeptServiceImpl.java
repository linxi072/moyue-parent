package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.system.domain.entity.SysDept;
import com.moyue.system.domain.entity.SysUser;
import com.moyue.system.domain.vo.DeptVO;
import com.moyue.system.mapper.SysDeptMapper;
import com.moyue.system.mapper.SysUserMapper;
import com.moyue.system.service.SysDeptService;
import com.moyue.system.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门管理域（④）实现。
 *
 * <p>ancestors 字段（如 {@code 0,100,101}）用于「本部门及以下」数据权限的
 * 前缀匹配查询，是数据权限落地的必要冗余，父部门变更时必须级联重建。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final SysUserMapper userMapper;

    @Override
    public List<DeptVO> listDeptTree(String deptName, Integer status) {
        List<DeptVO> list = list(new LambdaQueryWrapper<SysDept>()
                        .like(StringUtils.hasText(deptName), SysDept::getDeptName, deptName)
                        .eq(status != null, SysDept::getStatus, status)
                        .orderByAsc(SysDept::getOrderNum))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return TreeUtils.buildDeptTree(list, 0L);
    }

    @Override
    public Long createDept(SysDept entity) {
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        entity.setAncestors(resolveAncestors(entity.getParentId()));
        if (entity.getOrderNum() == null) {
            entity.setOrderNum(0);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDept(SysDept entity) {
        SysDept exist = getById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("部门");
        }
        if (entity.getParentId() != null && entity.getParentId().equals(entity.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上级部门不能是当前部门");
        }
        boolean parentChanged = entity.getParentId() != null
                && !entity.getParentId().equals(exist.getParentId());
        if (parentChanged) {
            entity.setAncestors(resolveAncestors(entity.getParentId()));
        }
        boolean ok = updateById(entity);
        if (parentChanged) {
            rebuildChildrenAncestors(entity.getId(), entity.getAncestors());
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDept(Long id) {
        if (getById(id) == null) {
            throw BusinessException.notFound("部门");
        }
        long children = lambdaQuery().eq(SysDept::getParentId, id).count();
        if (children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在下级部门，不允许删除");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
        if (users > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门下已分配用户，不允许删除");
        }
        return removeById(id);
    }

    private String resolveAncestors(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        SysDept parent = getById(parentId);
        if (parent == null) {
            return "0";
        }
        String parentAncestors = StringUtils.hasText(parent.getAncestors()) ? parent.getAncestors() : "0";
        return parentAncestors + "," + parentId;
    }

    private void rebuildChildrenAncestors(Long deptId, String ancestors) {
        List<SysDept> children = lambdaQuery().eq(SysDept::getParentId, deptId).list();
        List<SysDept> updates = new ArrayList<>();
        for (SysDept child : children) {
            String childAncestors = ancestors + "," + deptId;
            child.setAncestors(childAncestors);
            updates.add(child);
            rebuildChildrenAncestors(child.getId(), childAncestors);
        }
        if (!updates.isEmpty()) {
            updateBatchById(updates);
        }
    }

    private DeptVO toVO(SysDept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setAncestors(dept.getAncestors());
        vo.setDeptName(dept.getDeptName());
        vo.setOrderNum(dept.getOrderNum());
        vo.setLeader(dept.getLeader());
        vo.setPhone(dept.getPhone());
        vo.setEmail(dept.getEmail());
        vo.setStatus(dept.getStatus());
        vo.setCreateTime(dept.getCreateTime());
        return vo;
    }
}
