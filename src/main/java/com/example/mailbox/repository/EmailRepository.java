package com.example.mailbox.repository;

import com.example.mailbox.entity.Email;
import com.example.mailbox.entity.Email.FolderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {
    Page<Email> findByUserEmailAndFolderTypeOrderByReceivedTimeDesc(String email, FolderType folderType, Pageable pageable);

    Page<Email> findByUserEmailOrderByReceivedTimeDesc(String email, Pageable pageable);

    List<Email> findByUserEmailAndReceivedTimeBetween(String email, LocalDateTime start, LocalDateTime end);

    long countByUserEmailAndIsRead(String email, Boolean isRead);

    long countByUserEmailAndIsStarred(String email, Boolean isStarred);

    // 检查邮件是否已存在
    boolean existsBySenderAndSubjectAndReceivedTime(String sender, String subject, LocalDateTime receivedTime);

    // 改用原生SQL查询（适配MySQL），避免HQL的lower()类型校验问题
    @Query(value = "SELECT e.* FROM emails e " +
            "JOIN account u ON e.user_id = u.id " +
            "WHERE u.email = :email " +
            "AND (LOWER(e.subject) LIKE CONCAT('%', LOWER(:query), '%') OR LOWER(e.body) LIKE CONCAT('%', LOWER(:query), '%'))",
            countQuery = "SELECT COUNT(e.id) FROM emails e " +
                    "JOIN account u ON e.user_id = u.id " +
                    "WHERE u.email = :email " +
                    "AND (LOWER(e.subject) LIKE CONCAT('%', LOWER(:query), '%') OR LOWER(e.body) LIKE CONCAT('%', LOWER(:query), '%'))",
            nativeQuery = true)
    Page<Email> searchByEmailAndContent(@Param("email") String email, @Param("query") String query, Pageable pageable);
}