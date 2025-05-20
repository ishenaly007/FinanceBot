package com.abit8.financebot.repository;

import com.abit8.financebot.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndEndDateAfter(Long userId, LocalDate date);
}