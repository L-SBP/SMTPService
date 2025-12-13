package com.example.mailbox.repository;

import com.example.mailbox.entity.GroupMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Page<GroupMember> findByGroupId(Long groupId, Pageable pageable);

    boolean existsByGroupIdAndAccountId(Long groupId, Long accountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GroupMember gm WHERE gm.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Transactional
    @Query("DELETE FROM GroupMember gm WHERE gm.groupId = :groupId AND gm.accountId = :accountId")
    void deleteByGroupIdAndAccountId(@Param("groupId") Long groupId, @Param("accountId") Long accountId);
}
