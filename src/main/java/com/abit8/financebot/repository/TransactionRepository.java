package com.abit8.financebot.repository;

import com.abit8.financebot.entity.Transaction;
import com.abit8.financebot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserAndDeletedFalse(User user);
    List<Transaction> findByUserAndDateAndDeletedFalse(User user, LocalDate date);
    List<Transaction> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);
    List<Transaction> findByUserAndDate(User user, LocalDate date);
    List<Transaction> findByUserAndDateBetweenAndDeletedFalse(User user, LocalDate startDate, LocalDate endDate);
}