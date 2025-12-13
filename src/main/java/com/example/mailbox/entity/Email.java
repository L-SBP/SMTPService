package com.example.mailbox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "emails")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @ElementCollection
    @CollectionTable(name = "email_recipients", joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "recipient")
    private List<String> recipients;

    @ElementCollection
    @CollectionTable(name = "email_cc", joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "cc")
    private List<String> cc;

    @ElementCollection
    @CollectionTable(name = "email_bcc", joinColumns = @JoinColumn(name = "email_id"))
    @Column(name = "bcc")
    private List<String> bcc;

    @Column(nullable = false)
    private String subject;

    @Lob
    private String body;

    @Column(name = "has_attachment")
    private Boolean hasAttachment = false;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "is_starred")
    private Boolean isStarred = false;

    public Boolean getRead() {
        return isRead;
    }

    public void setRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Boolean getStarred() {
        return isStarred;
    }

    public void setStarred(Boolean isStarred) {
        this.isStarred = isStarred;
    }

    private Integer size;

    @Column(name = "received_time")
    private LocalDateTime receivedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "folder_type")
    private FolderType folderType = FolderType.INBOX;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Account user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (receivedTime == null) {
            receivedTime = LocalDateTime.now();
        }
    }

    public enum FolderType {
        INBOX, SENT, DRAFT, TRASH
    }
}
