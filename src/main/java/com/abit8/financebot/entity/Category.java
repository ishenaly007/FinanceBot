package com.abit8.financebot.entity;

import com.abit8.financebot.model.TransactionType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "categories", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name", "type"}))
@Data
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private TransactionType type;
}