package com.example.mailbox.repository;

import com.example.mailbox.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    Page<Group> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE g.id IN (SELECT gm.groupId FROM GroupMember gm WHERE gm.accountId = :accountId)")
    Page<Group> findByMemberId(@Param("accountId") Long accountId, Pageable pageable);

    @Query("SELECT g FROM Group g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(g.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Group> searchByNameOrDescription(@Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.groupId = :groupId")
    Integer countMembers(@Param("groupId") Long groupId);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GroupMember gm WHERE gm.groupId = :groupId AND gm.accountId = :accountId")
    Boolean existsByGroupIdAndAccountId(@Param("groupId") Long groupId, @Param("accountId") Long accountId);
}
