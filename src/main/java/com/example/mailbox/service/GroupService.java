package com.example.mailbox.service;

import com.example.mailbox.entity.Group;
import com.example.mailbox.entity.GroupMember;
import com.example.mailbox.vo.Page;

import org.springframework.data.domain.Pageable;

public interface GroupService {
    Group createGroup(String email, Group group);
    Group updateGroup(String email, Group group);
    void deleteGroup(Long groupId, String email);
    Group getGroup(Long groupId, String email);
    Page<Group> getCreatedGroups(String email, Pageable pageable);
    Page<Group> getJoinedGroups(String email, Pageable pageable);
    Page<Group> getAllGroups(String email, Pageable pageable);
    Page<Group> searchGroups(String email, String query, Pageable pageable);
    void addMember(Long groupId, Long accountId, String email);
    void removeMember(Long groupId, Long accountId, String email);
    Page<GroupMember> getMembers(Long groupId, String email, Pageable pageable);
    Integer getMemberCount(Long groupId, String email);
    Boolean isMember(Long groupId, Long accountId, String email);
}
