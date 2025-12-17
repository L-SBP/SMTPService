package com.example.mailbox.service;

import com.example.mailbox.dto.GroupMemberDTO; // 引入 DTO
import com.example.mailbox.entity.Group;
import com.example.mailbox.vo.Page;
import org.springframework.data.domain.Pageable;

public interface GroupService {
    Group createGroup(String email, Group group);
    Group updateGroup(String email, Group group);
    void deleteGroup(Long groupId, String email);
    Group getGroup(Long groupId, String email);
    Group getGroupById(Long groupId);

    Page<Group> getCreatedGroups(String email, Pageable pageable);
    Page<Group> getJoinedGroups(String email, Pageable pageable);
    Page<Group> getAllGroups(String email, Pageable pageable);
    Page<Group> searchGroups(String email, String query, Pageable pageable);

    void addMember(Long groupId, Long accountId, String email);
    void removeMember(Long groupId, Long accountId, String email);
    void joinGroup(Long groupId, String email);

    // 【修改】返回值改为 GroupMemberDTO
    Page<GroupMemberDTO> getMembers(Long groupId, String email, Pageable pageable);

    Integer getMemberCount(Long groupId, String email);
    Boolean isMember(Long groupId, Long accountId, String email);
}