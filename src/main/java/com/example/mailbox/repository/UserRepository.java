package com.example.mailbox.repository;

import com.example.mailbox.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Account, Long> {
    
    /**
     * 根据用户名或邮箱查找用户
     * 支持多种标识符格式：
     * - 纯用户名：user123
     * - 邮箱格式：user123@example.com
     * - 带域名的用户名：user123@mb.com
     */
    @Query("SELECT a FROM Account a WHERE a.username = :identifier OR a.email = :identifier")
    Optional<Account> findByIdentifier(@Param("identifier") String identifier);
    
    Optional<Account> findByUsername(String username);
    Optional<Account> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
