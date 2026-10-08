package com.moyue.system.util;

import com.moyue.system.domain.vo.DeptVO;
import com.moyue.system.domain.vo.MenuVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 树构建工具（菜单树 / 部门树）。
 *
 * <p>采用「一次遍历建索引 + 二次挂载」的方式，复杂度 O(n)，
 * 优于递归查询子节点（会产生 N+1 次数据库访问）。
 *
 * @author moyue
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 构建菜单树。
     *
     * @param list   平铺菜单列表
     * @param parentId 根节点的父 ID，通常传 0
     * @return 树形结构
     */
    public static List<MenuVO> buildMenuTree(List<MenuVO> list, Long parentId) {
        Map<Long, MenuVO> index = new LinkedHashMap<>();
        for (MenuVO node : list) {
            index.put(node.getId(), node);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : list) {
            Long pid = node.getParentId() == null ? 0L : node.getParentId();
            if (pid.equals(parentId)) {
                roots.add(node);
            } else {
                MenuVO parent = index.get(pid);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // 父节点被过滤掉时（如父菜单停用），降级挂到根，避免菜单丢失
                    roots.add(node);
                }
            }
        }
        sortMenu(roots);
        return roots;
    }

    /**
     * 构建部门树。
     *
     * @param list   平铺部门列表
     * @param parentId 根节点的父 ID
     * @return 树形结构
     */
    public static List<DeptVO> buildDeptTree(List<DeptVO> list, Long parentId) {
        Map<Long, DeptVO> index = new LinkedHashMap<>();
        for (DeptVO node : list) {
            index.put(node.getId(), node);
        }
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO node : list) {
            Long pid = node.getParentId() == null ? 0L : node.getParentId();
            if (pid.equals(parentId)) {
                roots.add(node);
            } else {
                DeptVO parent = index.get(pid);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        sortDept(roots);
        return roots;
    }

    private static void sortMenu(List<MenuVO> nodes) {
        nodes.sort(Comparator.comparingInt(n -> n.getOrderNum() == null ? 0 : n.getOrderNum()));
        nodes.forEach(n -> sortMenu(n.getChildren()));
    }

    private static void sortDept(List<DeptVO> nodes) {
        nodes.sort(Comparator.comparingInt(n -> n.getOrderNum() == null ? 0 : n.getOrderNum()));
        nodes.forEach(n -> sortDept(n.getChildren()));
    }
}
