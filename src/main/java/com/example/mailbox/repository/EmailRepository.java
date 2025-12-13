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

    // 搜索邮件
    @Query("SELECT e FROM Email e WHERE e.user.email = :email AND (LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(e.body) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Email> searchByEmailAndContent(@Param("email") String email, @Param("query") String query, Pageable pageable);
}
