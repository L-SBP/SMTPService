package com.example.mailbox.controller;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Blacklist;
import com.example.mailbox.repository.BlacklistRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.vo.ApiResponse;
import com.example.mailbox.vo.Page;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // 仅管理员可访问
public class AdminController {

    @Autowired private UserRepository userRepository;
    @Autowired private BlacklistRepository blacklistRepository;

    // --- 用户管理 ---

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<Account>>> getAllUsers(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        var usersPage = userRepository.findAll(pageable);

        Page<Account> responsePage = new Page<>(
                usersPage.getContent(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.getSize(),
                usersPage.getNumber(),
                usersPage.isFirst(),
                usersPage.isLast()
        );
        return ResponseEntity.ok(new ApiResponse<>(true, responsePage, "获取用户列表成功", null));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<String>> updateUserStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        return userRepository.findById(id).map(user -> {
            user.setEnabled(enabled);
            userRepository.save(user);
            String status = enabled ? "解封" : "封禁";
            return ResponseEntity.ok(new ApiResponse<>(true, "用户已" + status, "操作成功", null));
        }).orElse(ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "用户不存在", null)));
    }

    // --- 黑名单管理 ---

    @GetMapping("/blacklist")
    public ResponseEntity<ApiResponse<List<Blacklist>>> getBlacklist() {
        return ResponseEntity.ok(new ApiResponse<>(true, blacklistRepository.findAll(), "获取黑名单成功", null));
    }

    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<Blacklist>> addToBlacklist(@RequestBody BlacklistRequest request) {
        if (blacklistRepository.existsByTypeAndValue(request.getType(), request.getValue())) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "该记录已存在", null));
        }

        Blacklist blacklist = new Blacklist();
        blacklist.setType(request.getType());
        blacklist.setValue(request.getValue());

        return ResponseEntity.ok(new ApiResponse<>(true, blacklistRepository.save(blacklist), "添加成功", null));
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<ApiResponse<String>> removeFromBlacklist(@PathVariable Long id) {
        blacklistRepository.deleteById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "删除成功", "删除成功", null));
    }

    @Data
    public static class BlacklistRequest {
        private Blacklist.Type type;
        private String value;
    }
}