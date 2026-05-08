package com.dayz.aeroverse.auth.controller;

import com.dayz.aeroverse.auth.model.dto.UpdateUserRequest;
import com.dayz.aeroverse.auth.model.dto.UserPageRequest;
import com.dayz.aeroverse.auth.model.vo.UserProfile;
import com.dayz.aeroverse.auth.service.UserManagementService;
import com.dayz.aeroverse.common.response.ApiResponse;
import com.dayz.aeroverse.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 系统用户分页查询与账号资料维护接口。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService userManagementService;

    @GetMapping
    public ApiResponse<PageResponse<UserProfile>> pageUsers(UserPageRequest request) {
        return ApiResponse.ok(userManagementService.pageUsers(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserProfile> updateUser(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userManagementService.updateUser(id, request));
    }
}
