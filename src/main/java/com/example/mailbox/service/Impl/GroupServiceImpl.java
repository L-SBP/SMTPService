package com.example.mailbox.service.Impl;

import com.example.mailbox.dto.GroupMemberDTO;
import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Group;
import com.example.mailbox.entity.GroupMember;
import com.example.mailbox.repository.GroupMemberRepository;
import com.example.mailbox.repository.GroupRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.GroupService;
import com.example.mailbox.vo.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupServiceImpl implements GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Group createGroup(String email, Group group) {
        Account user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        group.setCreatedBy(user.getId());
        Group savedGroup = groupRepository.save(group);

        GroupMember member = new GroupMember();
        member.setGroupId(savedGroup.getId());
        member.setAccountId(user.getId());
        groupMemberRepository.save(member);

        savedGroup.setMemberCount(1);
        return groupRepository.save(savedGroup);
    }

    @Override
    public Group updateGroup(String email, Group group) {
        Group existingGroup = groupRepository.findById(group.getId())
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        if (!existingGroup.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限更新此群组");
        }

        existingGroup.setName(group.getName());
        existingGroup.setDescription(group.getDescription());
        return groupRepository.save(existingGroup);
    }

    @Override
    public void deleteGroup(Long groupId, String email) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        if (!group.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限删除此群组");
        }

        groupRepository.deleteById(groupId);
        groupMemberRepository.deleteByGroupId(groupId);
    }

    @Override
    public Group getGroup(Long groupId, String email) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));
    }

    @Override
    public Group getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));
    }

    @Override
    public Page<Group> getCreatedGroups(String email, Pageable pageable) {
        var groups = groupRepository.findByCreatedByOrderByCreatedAtDesc(
            getUserIdByEmail(email), pageable
        );
        return convertToPage(groups);
    }

    @Override
    public Page<Group> getJoinedGroups(String email, Pageable pageable) {
        var groups = groupRepository.findByMemberId(
            getUserIdByEmail(email), pageable
        );
        return convertToPage(groups);
    }

    @Override
    public Page<Group> getAllGroups(String email, Pageable pageable) {
        var groups = groupRepository.findAll(pageable);
        return convertToPage(groups);
    }

    @Override
    public Page<Group> searchGroups(String email, String query, Pageable pageable) {
        var groups = groupRepository.searchByNameOrDescription(query, pageable);
        return convertToPage(groups);
    }

    @Override
    public void addMember(Long groupId, Long accountId, String email) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        if (!group.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限添加成员");
        }

        if (groupMemberRepository.existsByGroupIdAndAccountId(groupId, accountId)) {
            throw new RuntimeException("成员已存在");
        }

        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setAccountId(accountId);
        groupMemberRepository.save(member);

        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);
    }

    @Override
    public void removeMember(Long groupId, Long accountId, String email) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        // 1. 权限检查：只有群主能操作
        Long currentUserId = getUserIdByEmail(email);
        if (!group.getCreatedBy().equals(currentUserId)) {
            throw new RuntimeException("没有权限移除成员");
        }

        // 2. 【关键修复】禁止群主移除自己
        if (group.getCreatedBy().equals(accountId)) {
            throw new RuntimeException("群主无法被移除，请先解散群组");
        }

        groupMemberRepository.deleteByGroupIdAndAccountId(groupId, accountId);

        group.setMemberCount(group.getMemberCount() - 1);
        groupRepository.save(group);
    }

    @Override
    public void joinGroup(Long groupId, String email) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        Account user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (groupMemberRepository.existsByGroupIdAndAccountId(groupId, user.getId())) {
            throw new RuntimeException("您已经是该群组成员");
        }

        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setAccountId(user.getId());
        groupMemberRepository.save(member);

        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);
    }

    // 【关键修复】重构 getMembers 方法，返回 DTO
    @Override
    public Page<GroupMemberDTO> getMembers(Long groupId, String email, Pageable pageable) {
        Group group = groupRepository.findById(groupId).orElse(null);
        Long creatorId = (group != null) ? group.getCreatedBy() : null;

        // 1. 先查出分页的 GroupMember 实体
        org.springframework.data.domain.Page<GroupMember> memberPage = groupMemberRepository.findByGroupId(groupId, pageable);

        // 2. 转换为 DTO 并填充用户信息
        List<GroupMemberDTO> dtoList = memberPage.getContent().stream().map(member -> {
            GroupMemberDTO dto = new GroupMemberDTO();
            dto.setId(member.getId());
            dto.setGroupId(member.getGroupId());
            dto.setAccountId(member.getAccountId());
            dto.setJoinedAt(member.getJoinedAt());

            // 判断角色
            if (creatorId != null && creatorId.equals(member.getAccountId())) {
                dto.setRole("OWNER");
            } else {
                dto.setRole("MEMBER");
            }

            // 查询并填充用户信息 (虽然在循环里查库性能一般，但考虑到群成员分页显示，通常问题不大)
            // 优化方案是收集所有 ID 一次性查询，这里为了代码简单直接查
            userRepository.findById(member.getAccountId()).ifPresent(user -> {
                dto.setUsername(user.getUsername());
                dto.setEmail(user.getEmail());
            });

            return dto;
        }).collect(Collectors.toList());

        // 3. 重新封装成分页对象
        return new Page<>(
            dtoList,
            memberPage.getTotalElements(),
            memberPage.getTotalPages(),
            memberPage.getSize(),
            memberPage.getNumber(),
            memberPage.isFirst(),
            memberPage.isLast()
        );
    }

    @Override
    public Integer getMemberCount(Long groupId, String email) {
        return groupRepository.countMembers(groupId);
    }

    @Override
    public Boolean isMember(Long groupId, Long accountId, String email) {
        return groupRepository.existsByGroupIdAndAccountId(groupId, accountId);
    }

    private Long getUserIdByEmail(String email) {
        Account user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return user.getId();
    }

    private <T> Page<T> convertToPage(org.springframework.data.domain.Page<T> springPage) {
        return new Page<>(
            springPage.getContent(),
            springPage.getTotalElements(),
            springPage.getTotalPages(),
            springPage.getSize(),
            springPage.getNumber(),
            springPage.isFirst(),
            springPage.isLast()
        );
    }
}