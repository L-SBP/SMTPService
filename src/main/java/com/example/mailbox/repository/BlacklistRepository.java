package com.example.mailbox.repository;

import com.example.mailbox.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, Long> {
    boolean existsByTypeAndValue(Blacklist.Type type, String value);
    boolean existsByValue(String value);
    Optional<Blacklist> findByTypeAndValue(Blacklist.Type type, String value);
}
