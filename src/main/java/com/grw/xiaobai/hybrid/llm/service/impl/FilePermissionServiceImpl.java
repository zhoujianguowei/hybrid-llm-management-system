package com.grw.xiaobai.hybrid.llm.service.impl;

import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.annotation.FilePermission;
import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.constant.FilePermissionMask;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.service.FilePermissionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionRule;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionTree;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.exception.InvalidParamException;
import com.grw.xiaobai.hybrid.llm.manager.FilePermissionManager;
import com.grw.xiaobai.hybrid.llm.utils.AspectUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class FilePermissionServiceImpl implements FilePermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilePermissionServiceImpl.class);

    @Resource
    private FilePermissionManager permissionManager;
    @Resource
    private UserService userService;

    @PostConstruct
    public void init() {
        userService.addDeleteListener(this);
    }

    // ========== Permission Check ==========

    public boolean checkPermission(String path, int mask) {
        UserInfo user = userService.getCurrentUser();
        if ((mask & FilePermissionMask.DELETE_MASK) != 0 && isPathExplicitlyProtected(path)) {
            return false;
        }
        if (UserRoleEnum.admin.equals(user.getRole())) {
            return true;
        }
        return (computePermissionBits(FilePermissionTree.normalizePath(path), new File(path).isDirectory()) & mask) != 0;
    }

    public boolean isPathExplicitlyProtected(String path) {
        FilePermissionTree tree = permissionManager.getTree();
        FilePermissionTree node = tree.searchExactPath(path);
        return node != null && Boolean.TRUE.equals(node.getFilePermissionRule().getIsProtected());
    }

    private int computePermissionBits(String path, boolean isDir) {
        int bits = 0;
        UserInfo user = userService.getCurrentUser();
        FilePermissionTree tree = permissionManager.getTree();
        FilePermissionTree[] parents = new FilePermissionTree[1];
        FilePermissionTree node = tree.searchLastParentOrSelf(path, parents);

        FilePermissionRule parentOrSelfRule = node.getFilePermissionRule();
        boolean exactMatch = node != parents[0];
        FilePermissionRule selfRule = exactMatch ? parentOrSelfRule : null;

        // Check READ (no parent inheritance, only own rule || has parent dir execute permission)
        if (userMatchesAnyReadRule(user, selfRule) || (selfRule == null && userMatchesMaskRule(user, parentOrSelfRule, FilePermissionMask.EXECUTE_MASK))) {
            bits |= FilePermissionMask.READ_MASK;
        }

        // Check EXECUTE (only for directories)
        if (isDir && userMatchesMaskRule(user, parentOrSelfRule, FilePermissionMask.EXECUTE_MASK)) {
            bits |= FilePermissionMask.EXECUTE_MASK;
        }
        //detect from parent path to all accessible descendants path
        List<Integer> permissionList = Lists.newArrayList(FilePermissionMask.READ_MASK, FilePermissionMask.EXECUTE_MASK,
                FilePermissionMask.DOWNLOAD_MASK, FilePermissionMask.UPLOAD_MASK, FilePermissionMask.DELETE_MASK);
        if (isDir && (bits & FilePermissionMask.EXECUTE_MASK) == 0) {
            if (exactMatch && permissionList.stream().anyMatch(var -> checkDescendantsHasPermission(node, var, user))) {
                bits |= FilePermissionMask.EXECUTE_MASK;
            }
        }
        // Check DOWNLOAD (own rule + parent inheritance, directory with own rule requires all descendants to have it)
        if (userMatchesMaskRule(user, parentOrSelfRule, FilePermissionMask.DOWNLOAD_MASK)) {
            if (!isDir || !exactMatch || allChildrenInTreeHavePermission(node, FilePermissionMask.DOWNLOAD_MASK, user)) {
                bits |= FilePermissionMask.DOWNLOAD_MASK;
            }
        }

        // Check UPLOAD (only for directories)
        if (isDir && userMatchesMaskRule(user, parentOrSelfRule, FilePermissionMask.UPLOAD_MASK)) {
            if (!exactMatch || allChildrenInTreeHavePermission(node, FilePermissionMask.UPLOAD_MASK, user)) {
                bits |= FilePermissionMask.UPLOAD_MASK;
            }
        }

        // Check DELETE (own rule + parent inheritance, directory with own rule requires all descendants to have it)
        if (userMatchesMaskRule(user, parentOrSelfRule, FilePermissionMask.DELETE_MASK)) {
            if (!isDir || !exactMatch || allChildrenInTreeHavePermission(node, FilePermissionMask.DELETE_MASK, user)) {
                bits |= FilePermissionMask.DELETE_MASK;
            }
        }

        // Higher permission auto-grants lower permissions
        if ((bits & FilePermissionMask.DELETE_MASK) != 0) {
            bits |= FilePermissionMask.DOWNLOAD_MASK | FilePermissionMask.READ_MASK;
            if (isDir) {
                bits |= FilePermissionMask.UPLOAD_MASK | FilePermissionMask.EXECUTE_MASK;
            }
        } else if (isDir && (bits & FilePermissionMask.UPLOAD_MASK) != 0) {
            bits |= FilePermissionMask.DOWNLOAD_MASK | FilePermissionMask.EXECUTE_MASK | FilePermissionMask.READ_MASK;
        } else if ((bits & FilePermissionMask.DOWNLOAD_MASK) != 0) {
            if (isDir) {
                bits |= FilePermissionMask.EXECUTE_MASK;
            }
            bits |= FilePermissionMask.READ_MASK;
        } else if (isDir && (bits & FilePermissionMask.EXECUTE_MASK) != 0) {
            bits |= FilePermissionMask.READ_MASK;
        }

        return bits;
    }

    private boolean checkDescendantsHasPermission(FilePermissionTree parentOrSelfNode, int mask, UserInfo user) {
        for (FilePermissionTree child : parentOrSelfNode.getChildrenMap().values()) {
            if (userMatchesMaskRule(user, child.getFilePermissionRule(), mask)) {
                return true;
            }
            if (checkDescendantsHasPermission(child, mask, user)) {
                return true;
            }
        }
        return false;
    }

    private boolean allChildrenInTreeHavePermission(FilePermissionTree node, int mask, UserInfo user) {
        for (FilePermissionTree child : node.getChildrenMap().values()) {
            boolean isChildDir = Boolean.TRUE.equals(child.getIsDirectory());
            if (!isChildDir && (mask == FilePermissionMask.EXECUTE_MASK || mask == FilePermissionMask.UPLOAD_MASK)) {
                boolean matched = true;
                switch (mask) {
                    case FilePermissionMask.EXECUTE_MASK:
                        matched &= userMatchesMaskRule(user, child.getFilePermissionRule(), FilePermissionMask.READ_MASK);
                    case FilePermissionMask.UPLOAD_MASK:
                        matched &= userMatchesMaskRule(user, child.getFilePermissionRule(), FilePermissionMask.DOWNLOAD_MASK);
                }
                if (!matched) {
                    return false;
                }
            }
            if (!userMatchesMaskRule(user, child.getFilePermissionRule(), mask)) {
                return false;
            }
            if (!allChildrenInTreeHavePermission(child, mask, user)) {
                return false;
            }
        }
        return true;
    }

    private boolean userMatchesAnyReadRule(UserInfo user, FilePermissionRule rule) {
        if (rule == null) return false;
        return matchesPermissionRule(user, rule.getRead());
    }


    private boolean userMatchesMaskRule(UserInfo user, FilePermissionRule rule, int mask) {
        if (rule == null) return false;
        switch (mask) {
            case FilePermissionMask.EXECUTE_MASK:
                return matchesPermissionRule(user, rule.getExecute());
            case FilePermissionMask.DOWNLOAD_MASK:
                return matchesPermissionRule(user, rule.getDownload());
            case FilePermissionMask.UPLOAD_MASK:
                return matchesPermissionRule(user, rule.getUpload());
            case FilePermissionMask.DELETE_MASK:
                return matchesPermissionRule(user, rule.getDelete());
            default:
                return false;
        }
    }

    private boolean matchesPermissionRule(UserInfo user, FilePermissionRule.PermissionRule permRule) {
        if (permRule == null) return false;
        if (getRoleLevel(user.getRole()) >= getRoleLevel(permRule.getMinRole())) {
            return true;
        }
        return permRule.getAdditionalUsers() != null && permRule.getAdditionalUsers().contains(user.getUsername());
    }

    // ========== filePermission Bit Computation ==========

    public int computeFilePermission(FileInfo info, UserInfo user) {
        boolean isDir = Boolean.TRUE.equals(info.getIsDirectory());
        int bits = computePermissionBits(info.getPath(), isDir);
        if (isDir) {
            bits |= 1 << 14;
        }
        if (isPathExplicitlyProtected(info.getPath())) {
            bits |= FilePermissionMask.PROTECTED_MASK;
        }
        return bits;
    }

    public int computePathPermission(String path) {
        UserInfo user = userService.getCurrentUser();
        FileInfo info = FileInfo.fromFile(new File(path));
        return computeFilePermission(info, user);
    }


    public FilePermissionTree listAll() {
        return permissionManager.getTree();
    }

    public void savePathRule(FilePermissionRule rule) {
        if (!new File(rule.getPath()).exists()) {
            LOGGER.error("path non exits : {}", rule.getPath());
            throw BusinessLogicException.builder().msgKey("permission.path_not_found").msg("Path does not exist").build();
        }

        String normalizedPath = FilePermissionTree.normalizePath(rule.getPath());
        rule.setPath(normalizedPath);
        List<String> pathList = FilePermissionTree.splitPath(normalizedPath);
        if (pathList.stream().anyMatch(StringUtils::isBlank)) {
            LOGGER.error("invalid path {}, user: {}", rule.getPath(), userService.getCurrentUser().getUsername());
            throw BusinessLogicException.builder().msgKey("permission.path_invalid").msg("Invalid path").build();
        }
        FilePermissionTree tree = permissionManager.getTree();
        FilePermissionTree[] parents = new FilePermissionTree[1];
        FilePermissionTree matchedNode = tree.searchLastParentOrSelf(normalizedPath, parents);

        FilePermissionRule parentRule = matchedNode.getFilePermissionRule();
        if (matchedNode != parents[0]) {
            parentRule = parents[0].getFilePermissionRule();
        }
        autoFillDefaultPermissions(rule, parentRule);
        validateRule(rule);

        if (matchedNode != parents[0]) {
            matchedNode.setFilePermissionRule(rule.deepClone());
        } else {
            tree.addOrUpdatePathPermission(normalizedPath, rule);
        }
        permissionManager.saveToFile();
    }

    private void autoFillDefaultPermissions(FilePermissionRule rule, FilePermissionRule parentRule) {
        if (parentRule == null) return;
        if (rule.getRead() == null) {
            rule.setRead(parentRule.getRead());
        }
        if (rule.getDownload() == null) {
            rule.setDownload(parentRule.getDownload());
        }
        if (rule.getDelete() == null) {
            rule.setDelete(parentRule.getDelete());
        }
        if (rule.getExecute() == null) {
            rule.setExecute(parentRule.getExecute());
        }
        if (rule.getUpload() == null) {
            rule.setUpload(parentRule.getUpload());
        }
    }

    public void deletePathRule(String path) {
        String normalized = FilePermissionTree.normalizePath(path);
        FilePermissionTree tree = permissionManager.getTree();
        Map<String, FilePermissionTree> rootChildreanMap = tree.getChildrenMap();
        if (rootChildreanMap.containsKey(normalized)) {
            LOGGER.error("can't delete root path ");
            throw BusinessLogicException.builder().msgKey("permission.root_delete_forbidden").msg("Cannot delete root directory").build();
        }
        tree.deletePath(normalized);
        permissionManager.saveToFile();
    }

    @Override
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        FilePermission annotation = AspectUtil.getMethodOrTypeAnnotation(joinPoint, FilePermission.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        String pathParamName = annotation.pathParam();
        int mask = annotation.value().getMask();

        Object[] args = joinPoint.getArgs();
        String[] paramNames = getParameterNames(joinPoint);
        String path = extractByIndex(args, paramNames, pathParamName);

        if (path == null) {
            setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ResultModel.fail(ErrorCodeConstants.BAD_REQUEST, "Missing path parameter");
        }

        if ((mask & FilePermissionMask.DELETE_MASK) != 0 && isPathExplicitlyProtected(path)) {
            setResponseStatus(HttpServletResponse.SC_FORBIDDEN);
            return ResultModel.fail(ErrorCodeConstants.FORBIDDEN, FilePathConstants.PATH_PROTECTED_DELETE_KEY, null);
        }
        if (!checkPermission(path, mask)) {
            setResponseStatus(HttpServletResponse.SC_FORBIDDEN);
            return ResultModel.fail(ErrorCodeConstants.FORBIDDEN, annotation.rejectKey(), null);
        }

        return joinPoint.proceed();
    }


    private String[] getParameterNames(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature) {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            return methodSignature.getParameterNames();
        }
        return new String[0];
    }

    private String extractByIndex(Object[] args, String[] paramNames, String paramName) {
        if (paramNames == null || args == null) return null;
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i] != null && paramNames[i].equals(paramName)) {
                Object value = args[i];
                return value != null ? value.toString() : null;
            }
        }
        return null;
    }

    private void setResponseStatus(int status) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletResponse response = attrs.getResponse();
            if (response != null) {
                response.setStatus(status);
            }
        }
    }
    // ========== Validation ==========

    private void validateRule(FilePermissionRule rule) {
        checkRoleNarrowing(rule.getExecute(), rule.getRead());
        checkRoleNarrowing(rule.getDownload(), rule.getExecute());
        checkRoleNarrowing(rule.getUpload(), rule.getDownload());
        checkRoleNarrowing(rule.getDelete(), rule.getUpload());
        Predicate<FilePermissionRule.PermissionRule>
                predicate = var -> var != null && CollectionUtils.isNotEmpty(var.getAdditionalUsers());
        List<FilePermissionRule.PermissionRule> needCheckUserCoverRuleList = Lists.newArrayList(rule.getRead(), rule.getExecute(),
                rule.getDownload(), rule.getUpload(), rule.getDelete()).stream().filter(predicate).collect(Collectors.toList());
        for (int i = 0; i < needCheckUserCoverRuleList.size() - 1; i++) {
            checkUserSubset(needCheckUserCoverRuleList.get(i + 1), needCheckUserCoverRuleList.get(i));
        }
        for (FilePermissionRule.PermissionRule pr : getAllRules(rule)) {
            if (pr.getAdditionalUsers() != null) {
                for (String username : pr.getAdditionalUsers()) {
                    if (userService.getUserByUsername(username) == null) {
                        LOGGER.error("validateRule - user not found: {}, path: {}", username, rule.getPath());
                        throw InvalidParamException.builder().msgKey("permission.user_not_found").msg("User does not exist").build();
                    }
                }
            }
        }
    }

    private List<FilePermissionRule.PermissionRule> getAllRules(FilePermissionRule rule) {
        List<FilePermissionRule.PermissionRule> result = new ArrayList<>();
        if (rule.getRead() != null) {
            result.add(rule.getRead());
        }
        if (rule.getDownload() != null) {
            result.add(rule.getDownload());
        }
        if (rule.getDelete() != null) {
            result.add(rule.getDelete());
        }
        if (rule.getExecute() != null) {
            result.add(rule.getExecute());
        }
        if (rule.getUpload() != null) {
            result.add(rule.getUpload());
        }
        return result;
    }

    private void checkRoleNarrowing(FilePermissionRule.PermissionRule higher, FilePermissionRule.PermissionRule lower) {
        if (higher != null && lower != null) {
            if (getRoleLevel(higher.getMinRole()) < getRoleLevel(lower.getMinRole())) {
                LOGGER.error("checkRoleNarrowing - higher role {} is wider than lower role {}", higher.getMinRole(), lower.getMinRole());
                throw InvalidParamException.builder().msgKey("permission.role_hierarchy_violation").msg("Higher permission role scope cannot exceed lower permission").build();
            }
        }
    }

    private void checkUserSubset(FilePermissionRule.PermissionRule higher, FilePermissionRule.PermissionRule lower) {
        if (higher != null && CollectionUtils.isNotEmpty(higher.getAdditionalUsers())) {
            Set<String> lowerUsers = new HashSet<>();
            if (lower != null && lower.getAdditionalUsers() != null) {
                lowerUsers.addAll(lower.getAdditionalUsers());
            }
            for (String user : higher.getAdditionalUsers()) {
                if (!lowerUsers.contains(user) && !userRoleMeetsMinRole(lower, user)) {
                    LOGGER.error("checkUserSubset - higher user {} is not in lower user set {}", user, lowerUsers);
                    throw InvalidParamException.builder().msgKey("permission.user_subset_violation").msg("Higher permission users must be a subset of lower permission users").build();
                }
            }
        }
    }

    private boolean userRoleMeetsMinRole(FilePermissionRule.PermissionRule rule, String username) {
        if (rule == null) return false;
        UserInfo user = userService.getUserByUsername(username);
        return getRoleLevel(user.getRole()) >= getRoleLevel(rule.getMinRole());
    }

    private int getRoleLevel(UserRoleEnum role) {
        switch (role) {
            case admin:
                return 3;
            case user:
                return 2;
            case guest:
                return 1;
            default:
                return Integer.MAX_VALUE;
        }
    }

    @Override
    public void onUserDelete(String username) {
        permissionManager.getTree().removeUserFromAllPermissions(username);
        permissionManager.saveToFile();
    }
}
