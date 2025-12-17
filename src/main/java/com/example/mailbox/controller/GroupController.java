package com.example.mailbox.controller;

import com.example.mailbox.dto.GroupMemberDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Group;
import com.example.mailbox.entity.GroupMember;
import com.example.mailbox.service.GroupService;
import com.example.mailbox.service.UserService; // 引入 UserService
import com.example.mailbox.util.JwtUtil;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data; // 引入 Data
import lombok.NoArgsConstructor; // 引入 NoArgsConstructor
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService; // 【新增】注入 UserService 用于查用户

    @Autowired
    private JwtUtil jwtUtil;

    private String getEmailFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.extractUsername(token);
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Group>> createGroup(@RequestBody Group group, HttpServletRequest request) {
        String email = getEmailFromToken(request);
        if (email == null) return ResponseEntity.status(401).body(new ApiResponse<>(false, null, "未授权", null));

        try {
            Group createdGroup = groupService.createGroup(email, group);
            return ResponseEntity.ok(new ApiResponse<>(true, createdGroup, "群组创建成功", null));
        } catch (Exception e) {
            log.error("创建群组失败", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Group>> updateGroup(@RequestBody Group group, HttpServletRequest request) {
        String email = getEmailFromToken(request);
        if (email == null) return ResponseEntity.status(401).body(new ApiResponse<>(false, null, "未授权", null));

        try {
            Group updatedGroup = groupService.updateGroup(email, group);
            return ResponseEntity.ok(new ApiResponse<>(true, updatedGroup, "群组更新成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "更新失败: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<String>> deleteGroup(@PathVariable Long groupId, HttpServletRequest request) {
        String email = getEmailFromToken(request);
        if (email == null) return ResponseEntity.status(401).body(new ApiResponse<>(false, null, "未授权", null));

        try {
            groupService.deleteGroup(groupId, email);
            return ResponseEntity.ok(new ApiResponse<>(true, "群组删除成功", "群组删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "删除失败: " + e.getMessage(), null));
        }
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Group>> getGroup(@PathVariable Long groupId, HttpServletRequest request) {
        String email = getEmailFromToken(request);
        try {
            Group group = groupService.getGroup(groupId, email);
            return ResponseEntity.ok(new ApiResponse<>(true, group, "获取详情成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @GetMapping("/created")
    public ResponseEntity<ApiResponse<Page<Group>>> getCreatedGroups(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        if (email == null) return ResponseEntity.status(401).body(new ApiResponse<>(false, null, "未授权", null));

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getCreatedGroups(email, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, groups, "获取成功", null));
    }

    @GetMapping("/joined")
    public ResponseEntity<ApiResponse<Page<Group>>> getJoinedGroups(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        if (email == null) return ResponseEntity.status(401).body(new ApiResponse<>(false, null, "未授权", null));

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getJoinedGroups(email, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, groups, "获取成功", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Group>>> searchGroups(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.searchGroups(email, query, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, groups, "搜索成功", null));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Page<Group>>> getAllGroups(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getAllGroups(email, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, groups, "获取成功", null));
    }

    // 【重点修改】添加成员接口：支持传邮箱
    @PostMapping("/members")
    public ResponseEntity<ApiResponse<String>> addMember(@RequestBody GroupMemberRequest request, HttpServletRequest httpRequest) {
        String currentUserEmail = getEmailFromToken(httpRequest);

        Long targetAccountId = request.getAccountId();

        // 如果传入了 email，先去查用户的 ID
        if (request.getMemberEmail() != null && !request.getMemberEmail().isEmpty()) {
            Account user = userService.getUserByEmail(request.getMemberEmail());
            if (user == null) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "未找到该邮箱对应的用户", null));
            }
            targetAccountId = user.getId();
        }

        if (targetAccountId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "必须提供用户ID或邮箱", null));
        }

        try {
            groupService.addMember(request.getGroupId(), targetAccountId, currentUserEmail);
            return ResponseEntity.ok(new ApiResponse<>(true, "添加成员成功", "添加成员成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{groupId}/members/{accountId}")
    public ResponseEntity<ApiResponse<String>> removeMember(@PathVariable Long groupId,
        @PathVariable Long accountId,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        try {
            groupService.removeMember(groupId, accountId, email);
            return ResponseEntity.ok(new ApiResponse<>(true, "移除成员成功", "移除成员成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, e.getMessage(), null));
        }
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<Page<GroupMemberDTO>>> getMembers(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpServletRequest request) {

        String email = getEmailFromToken(request);
        Pageable pageable = PageRequest.of(page, size);

        // 返回类型改为 GroupMemberDTO
        Page<GroupMemberDTO> members = groupService.getMembers(groupId, email, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, members, "获取成员成功", null
        ));
    }

    @GetMapping("/{groupId}/member-count")
    public ResponseEntity<ApiResponse<Integer>> getMemberCount(@PathVariable Long groupId, HttpServletRequest request) {
        String email = getEmailFromToken(request);
        Integer count = groupService.getMemberCount(groupId, email);
        return ResponseEntity.ok(new ApiResponse<>(true, count, "获取数量成功", null));
    }

    @GetMapping("/{groupId}/members/{accountId}/check")
    public ResponseEntity<ApiResponse<Boolean>> checkMember(@PathVariable Long groupId,
        @PathVariable Long accountId,
        HttpServletRequest request) {
        String email = getEmailFromToken(request);
        Boolean isMember = groupService.isMember(groupId, accountId, email);
        return ResponseEntity.ok(new ApiResponse<>(true, isMember, "检查成功", null));
    }

    // 请求 DTO：增加了 memberEmail 字段
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupMemberRequest {
        private Long groupId;
        private Long accountId;
        private String memberEmail; // 新增字段：支持传邮箱
    }
}