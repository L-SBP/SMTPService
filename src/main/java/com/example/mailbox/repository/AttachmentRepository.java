package com.example.mailbox.repository;

import com.example.mailbox.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    // 补全这个方法，用于查找指定邮件的所有附件
    List<Attachment> findByEmailId(Long emailId);

    void deleteByEmailId(Long emailId);
}