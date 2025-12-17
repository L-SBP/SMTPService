package com.example.mailbox.controller;

import com.example.mailbox.entity.Group;
import com.example.mailbox.entity.GroupMember;
import com.example.mailbox.service.GroupService;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @PostMapping
    public ResponseEntity<ApiResponse<Group>> createGroup(@RequestBody Group group) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Group createdGroup = groupService.createGroup(email, group);

        return ResponseEntity.ok(new ApiResponse<>(
            true, createdGroup, "群组创建成功", null
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Group>> updateGroup(@RequestBody Group group) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Group updatedGroup = groupService.updateGroup(email, group);

        return ResponseEntity.ok(new ApiResponse<>(
            true, updatedGroup, "群组更新成功", null
        ));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<String>> deleteGroup(@PathVariable Long groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        groupService.deleteGroup(groupId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, "群组删除成功", "群组删除成功", null
        ));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Group>> getGroup(@PathVariable Long groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Group group = groupService.getGroup(groupId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, group, "获取群组详情成功", null
        ));
    }

    @GetMapping("/created")
    public ResponseEntity<ApiResponse<Page<Group>>> getCreatedGroups(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getCreatedGroups(email, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, groups, "获取创建的群组成功", null
        ));
    }

    @GetMapping("/joined")
    public ResponseEntity<ApiResponse<Page<Group>>> getJoinedGroups(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getJoinedGroups(email, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, groups, "获取加入的群组成功", null
        ));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Page<Group>>> getAllGroups(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.getAllGroups(email, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, groups, "获取所有群组成功", null
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Group>>> searchGroups(@RequestParam String query,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groups = groupService.searchGroups(email, query, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, groups, "搜索群组成功", null
        ));
    }

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<String>> addMember(@RequestBody GroupMemberRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        groupService.addMember(request.groupId, request.accountId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, "添加成员成功", "添加成员成功", null
        ));
    }

    @DeleteMapping("/{groupId}/members/{accountId}")
    public ResponseEntity<ApiResponse<String>> removeMember(@PathVariable Long groupId,
                                                             @PathVariable Long accountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        groupService.removeMember(groupId, accountId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, "移除成员成功", "移除成员成功", null
        ));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<Page<GroupMember>>> getMembers(@PathVariable Long groupId,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<GroupMember> members = groupService.getMembers(groupId, email, pageable);

        return ResponseEntity.ok(new ApiResponse<>(
            true, members, "获取群组成员成功", null
        ));
    }

    @GetMapping("/{groupId}/member-count")
    public ResponseEntity<ApiResponse<Integer>> getMemberCount(@PathVariable Long groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Integer count = groupService.getMemberCount(groupId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, count, "获取成员数量成功", null
        ));
    }

    @GetMapping("/{groupId}/members/{accountId}/check")
    public ResponseEntity<ApiResponse<Boolean>> checkMember(@PathVariable Long groupId,
                                                             @PathVariable Long accountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        Boolean isMember = groupService.isMember(groupId, accountId, email);

        return ResponseEntity.ok(new ApiResponse<>(
            true, isMember, "检查成员状态成功", null
        ));
    }

    // 群组成员请求DTO
    @AllArgsConstructor
    public static class GroupMemberRequest {
        private Long groupId;
        private Long accountId;
    }
}
