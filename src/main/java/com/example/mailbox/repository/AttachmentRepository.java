package com.example.mailbox.repository;

import com.example.mailbox.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    void deleteByEmailId(Long emailId);
}
