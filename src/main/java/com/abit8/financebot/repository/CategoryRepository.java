package com.abit8.financebot.repository;

import com.abit8.financebot.entity.Category;
import com.abit8.financebot.entity.User;
import com.abit8.financebot.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserAndType(User user, TransactionType type);
}