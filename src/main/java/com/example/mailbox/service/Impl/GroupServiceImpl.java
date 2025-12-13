package com.example.mailbox.service.Impl;

import com.example.mailbox.entity.Account;
import com.example.mailbox.entity.Group;
import com.example.mailbox.entity.GroupMember;
import com.example.mailbox.repository.GroupMemberRepository;
import com.example.mailbox.repository.GroupRepository;
import com.example.mailbox.repository.UserRepository;
import com.example.mailbox.service.GroupService;
import com.example.mailbox.vo.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

        // 添加创建者为群组成员
        GroupMember member = new GroupMember();
        member.setGroupId(savedGroup.getId());
        member.setAccountId(user.getId());
        groupMemberRepository.save(member);

        // 更新成员数量
        savedGroup.setMemberCount(1);
        return groupRepository.save(savedGroup);
    }

    @Override
    public Group updateGroup(String email, Group group) {
        Group existingGroup = groupRepository.findById(group.getId())
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        // 检查是否有权限更新
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

        // 检查是否有权限删除
        if (!group.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限删除此群组");
        }

        groupRepository.deleteById(groupId);
        // 删除群组成员
        groupMemberRepository.deleteByGroupId(groupId);
    }

    @Override
    public Group getGroup(Long groupId, String email) {
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

        // 检查是否有权限添加成员
        if (!group.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限添加成员");
        }

        // 检查成员是否已存在
        if (groupMemberRepository.existsByGroupIdAndAccountId(groupId, accountId)) {
            throw new RuntimeException("成员已存在");
        }

        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setAccountId(accountId);
        groupMemberRepository.save(member);

        // 更新成员数量
        group.setMemberCount(group.getMemberCount() + 1);
        groupRepository.save(group);
    }

    @Override
    public void removeMember(Long groupId, Long accountId, String email) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("群组不存在"));

        // 检查是否有权限移除成员
        if (!group.getCreatedBy().equals(getUserIdByEmail(email))) {
            throw new RuntimeException("没有权限移除成员");
        }

        groupMemberRepository.deleteByGroupIdAndAccountId(groupId, accountId);

        // 更新成员数量
        group.setMemberCount(group.getMemberCount() - 1);
        groupRepository.save(group);
    }

    @Override
    public Page<GroupMember> getMembers(Long groupId, String email, Pageable pageable) {
        var members = groupMemberRepository.findByGroupId(groupId, pageable);
        return convertToPage(members);
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
