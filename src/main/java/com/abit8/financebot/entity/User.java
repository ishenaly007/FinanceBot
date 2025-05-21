package com.abit8.financebot.entity;

import com.abit8.financebot.model.Currency;
import com.abit8.financebot.model.Language;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", unique = true, nullable = false)
    private Long chatId;

    @Column(name = "language")
    @Enumerated(EnumType.STRING)
    private Language language = Language.RU;

    @Column(name = "currency")
    @Enumerated(EnumType.STRING)
    private Currency currency = Currency.СОМ;

    @Column(name = "export_format")
    private String exportFormat = "PDF";

    @Column(name = "stats_time")
    private LocalTime statsTime = LocalTime.of(20, 0);

    @Column(name = "is_premium")
    private boolean isPremium = false;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    @Column(name = "username")
    private String username;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "user_state")
    private String userState;

    @Column(name = "reminders_enabled")
    private boolean remindersEnabled = true;

    @Column(name = "last_reminder_sent")
    private LocalDateTime lastReminderSent;

    @Column(name = "last_input_time")
    private LocalDateTime lastInputTime;

    @Transient
    private List<BigDecimal> transactionStateAmounts = new ArrayList<>();

    @Transient
    private Category transactionStateCategory;

    public void clearTransactionState() {
        this.transactionStateAmounts.clear();
        this.transactionStateCategory = null;
    }
}