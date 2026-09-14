package com.grw.xiaobai.hybrid.llm.entity.file;

import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.SystemConstants;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;


@Data
public class FilePermissionTree {
    private String name;
    private Boolean isRoot;
    private Boolean isLeaf;
    private int level = 0;
    private Boolean isDirectory;
    private FilePermissionRule filePermissionRule;
    private static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Map<String, FilePermissionTree> childrenMap = new TreeMap<>();

    public void initDefault(List<String> rootPathList) {
        for (String rootName : rootPathList) {
            addRootPath(rootName);
        }
    }

    public boolean addRootPath(String rootName) {
        try {
            readWriteLock.writeLock().lock();
            rootName = normalizePath(rootName);
            if (childrenMap.containsKey(rootName)) {
                return false;
            }
            FilePermissionRule childFilePermissionRule = initFilePermissionRule(rootName);
            FilePermissionTree childFilePermissionTree = new FilePermissionTree();
            childFilePermissionTree.setFilePermissionRule(childFilePermissionRule);
            childFilePermissionTree.setName(rootName);
            childFilePermissionTree.setIsLeaf(true);
            childFilePermissionTree.setIsRoot(true);
            childFilePermissionTree.setLevel(1);
            childFilePermissionTree.setIsDirectory(true);
            childrenMap.put(rootName, childFilePermissionTree);
        } finally {
            readWriteLock.writeLock().unlock();
        }
        return true;
    }

    public static String normalizePath(String path) {
        String formatPath = path.replaceAll(Pattern.quote(SystemConstants.WINDOWS_FILE_SEPARATOR), SystemConstants.PATH_SEPARATOR);
        if (formatPath.endsWith(SystemConstants.PATH_SEPARATOR) && formatPath.length() > SystemConstants.PATH_SEPARATOR.length()) {
            formatPath = formatPath.substring(0, formatPath.length() - SystemConstants.PATH_SEPARATOR.length());
        }
        return formatPath;
    }

    public FilePermissionTree searchExactPath(String path) {
        FilePermissionTree[] parents = new FilePermissionTree[1];
        FilePermissionTree parentOrSelf = searchLastParentOrSelf(path, parents);
        return parentOrSelf == parents[0] ? null : parentOrSelf;
    }

    private FilePermissionTree searchLastParentOrSelfNoLock(String path, FilePermissionTree[] parents) {
        path = normalizePath(path);
        List<String> pathList = splitPath(path);
        FilePermissionTree permissionTree = this;
        FilePermissionTree parent = this;
        int index = 0;
        while (permissionTree != null && index < pathList.size()) {
            String name = pathList.get(index++);
            parent = permissionTree;
            permissionTree = permissionTree.getChildrenMap().get(name);
        }
        parents[0] = parent;
        return permissionTree == null ? parent : permissionTree;
    }

    public FilePermissionTree searchLastParentOrSelf(String path, FilePermissionTree[] parents) {
        try {
            readWriteLock.readLock().lock();
            return searchLastParentOrSelfNoLock(path, parents);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public FilePermissionTree addOrUpdatePathPermission(String path, FilePermissionRule filePermissionRule) {
        boolean isDir = filePermissionRule.getIsDirectory();
        path = normalizePath(path);
        List<String> pathList = splitPath(path);
        try {
            readWriteLock.writeLock().lock();
            FilePermissionTree[] parents = new FilePermissionTree[1];
            FilePermissionTree parentOrSelf = searchLastParentOrSelfNoLock(path, parents);
            if (parentOrSelf != parents[0]) {
                parentOrSelf.setFilePermissionRule(filePermissionRule);
                return parentOrSelf;
            }
            int index = parentOrSelf.level;
            while (index < pathList.size()) {
                String name = pathList.get(index++);
                FilePermissionTree childPermissionTree = new FilePermissionTree();
                childPermissionTree.setIsDirectory(true);
                parentOrSelf.childrenMap.put(name, childPermissionTree);
                FilePermissionRule childFilePermissionRule = parentOrSelf.getFilePermissionRule().deepClone();
                childFilePermissionRule.appendName(name);
                childFilePermissionRule.setIsProtected(false);
                childPermissionTree.setFilePermissionRule(childFilePermissionRule);
                childPermissionTree.setLevel(parentOrSelf.getLevel() + 1);
                childPermissionTree.setIsRoot(false);
                childPermissionTree.setIsLeaf(false);
                childPermissionTree.setName(name);
                parentOrSelf = childPermissionTree;
            }
            parentOrSelf.setIsLeaf(true);
            parentOrSelf.setIsDirectory(isDir);
            filePermissionRule.setPath(parentOrSelf.getFilePermissionRule().getPath());
            if (!Boolean.TRUE.equals(filePermissionRule.getIsDirectory())) {
                filePermissionRule.setExecute(null);
                filePermissionRule.setUpload(null);
            }
            parentOrSelf.setFilePermissionRule(filePermissionRule);
            return parentOrSelf;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public static List<String> splitPath(String path) {
        List<String> pathList = Lists.newArrayList();
        if (path.equals(SystemConstants.PATH_SEPARATOR)) {
            pathList.add(SystemConstants.PATH_SEPARATOR);
            return pathList;
        }
        if (path.startsWith(SystemConstants.PATH_SEPARATOR)) {
            pathList.add(SystemConstants.PATH_SEPARATOR);
            path = path.substring(SystemConstants.PATH_SEPARATOR.length());
        }
        if (path.endsWith(SystemConstants.PATH_SEPARATOR)) {
            path = path.substring(0, path.length() - SystemConstants.PATH_SEPARATOR.length());
        }
        if (!path.isEmpty()) {
            pathList.addAll(Arrays.asList(path.split(SystemConstants.PATH_SEPARATOR)));
        }
        return pathList;
    }


    public void deletePath(String path) {
        try {
            readWriteLock.writeLock().lock();
            FilePermissionTree[] parents = new FilePermissionTree[1];
            FilePermissionTree node = searchLastParentOrSelfNoLock(path, parents);
            FilePermissionTree parent = parents[0];
            if (node == parent) {
                return;
            }
            parent.getChildrenMap().remove(node.getName());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }


    public void removeUserFromAllPermissions(String username) {
        try {
            readWriteLock.writeLock().lock();
            removeUserFromNode(this, username);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private void removeUserFromNode(FilePermissionTree node, String username) {
        FilePermissionRule rule = node.getFilePermissionRule();
        if (rule != null) {
            List<FilePermissionRule.PermissionRule> permissionRuleList = Arrays.asList(rule.getRead(), rule.getDownload(), rule.getDelete(), rule.getExecute(), rule.getUpload());
            for (FilePermissionRule.PermissionRule pr : permissionRuleList) {
                if (pr != null && pr.getAdditionalUsers() != null) {
                    pr.getAdditionalUsers().remove(username);
                }
            }
        }
        if (node.getChildrenMap() != null) {
            for (FilePermissionTree child : node.getChildrenMap().values()) {
                removeUserFromNode(child, username);
            }
        }
    }

    private FilePermissionRule initFilePermissionRule(String path) {
        FilePermissionRule defaultPermission = new FilePermissionRule();
        defaultPermission.setPath(path);
        defaultPermission.setIsDirectory(true);
        defaultPermission.setRead(FilePermissionRule.PermissionRule.builder()
                .minRole(UserRoleEnum.admin).build());
        defaultPermission.setDownload(FilePermissionRule.PermissionRule.builder()
                .minRole(UserRoleEnum.admin).build());
        defaultPermission.setDelete(FilePermissionRule.PermissionRule.builder()
                .minRole(UserRoleEnum.admin).build());
        defaultPermission.setExecute(FilePermissionRule.PermissionRule.builder()
                .minRole(UserRoleEnum.admin).build());
        defaultPermission.setUpload(FilePermissionRule.PermissionRule.builder()
                .minRole(UserRoleEnum.admin).build());
        defaultPermission.setIsProtected(true);
        return defaultPermission;
    }

}
