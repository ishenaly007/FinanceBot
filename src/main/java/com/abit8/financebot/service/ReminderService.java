package com.abit8.financebot.service;

import com.abit8.financebot.FinanceBot;
import com.abit8.financebot.entity.User;
import com.abit8.financebot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ReminderService {
    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    public ReminderService(UserRepository userRepository, MessageSource messageSource) {
        this.userRepository = userRepository;
        this.messageSource = messageSource;
    }

    public void toggleReminder(User user, boolean enable) {
        user.setRemindersEnabled(enable);
        userRepository.save(user);
    }

    public String getReminderStatusMessage(User user, Locale locale) {
        return messageSource.getMessage(
                user.isRemindersEnabled() ? "reminder_enabled" : "reminder_disabled",
                null, locale);
    }
}