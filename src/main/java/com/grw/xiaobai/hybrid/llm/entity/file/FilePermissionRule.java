package com.grw.xiaobai.hybrid.llm.entity.file;

import com.grw.xiaobai.hybrid.llm.utils.ValidationGroup;
import com.grw.xiaobai.hybrid.llm.constant.SystemConstants;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilePermissionRule {
    @NotBlank(groups = ValidationGroup.InsertOrUpdate.class, message = "Path must not be blank")
    private String path;
    @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Is directory must not be null")
    private Boolean isDirectory;

    @Valid
    @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Read permission must not be null")
    private PermissionRule read;

    @Valid
    @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Download permission must not be null")
    private PermissionRule download;

    @Valid
    @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Delete permission must not be null")
    private PermissionRule delete;

    @Valid
    private PermissionRule execute;

    @Valid
    private PermissionRule upload;

    @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Path protection field must not be null")
    private Boolean isProtected;

    @AssertTrue(groups = ValidationGroup.InsertOrUpdate.class, message = "Execute and upload permissions must not be null in directory permission configuration")
    public boolean isValidDirectoryPermission() {
        if (Boolean.TRUE.equals(isDirectory)) {
            return execute != null && upload != null;
        }
        return true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionRule {
        @NotNull(groups = ValidationGroup.InsertOrUpdate.class, message = "Minimum role for permission must not be null")
        private UserRoleEnum minRole;
        private List<String> additionalUsers;
    }

    public void appendName(String name) {
        path = path + (path.endsWith(SystemConstants.PATH_SEPARATOR) ? StringUtils.EMPTY : SystemConstants.PATH_SEPARATOR) + name;
    }

    public FilePermissionRule deepClone() {
        FilePermissionRule clone = new FilePermissionRule();
        clone.path = this.path;
        clone.isDirectory = this.isDirectory;
        clone.read = deepClonePermissionRule(this.read);
        clone.download = deepClonePermissionRule(this.download);
        clone.delete = deepClonePermissionRule(this.delete);
        clone.execute = deepClonePermissionRule(this.execute);
        clone.upload = deepClonePermissionRule(this.upload);
        clone.isProtected = this.isProtected;
        return clone;
    }

    private PermissionRule deepClonePermissionRule(PermissionRule source) {
        if (source == null) return null;
        PermissionRule clone = new PermissionRule();
        clone.minRole = source.minRole;
        clone.additionalUsers = source.additionalUsers == null ? null : new ArrayList<>(source.additionalUsers);
        return clone;
    }
}
