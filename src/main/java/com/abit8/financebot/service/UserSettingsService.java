package com.abit8.financebot.service;

import com.abit8.financebot.entity.User;
import com.abit8.financebot.model.Currency;
import com.abit8.financebot.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserSettingsService {
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    public UserSettingsService(UserRepository userRepository, MessageSource messageSource) {
        this.userRepository = userRepository;
        this.messageSource = messageSource;
    }

    public String getSettingsMessage(User user, Locale locale) {
        return messageSource.getMessage("settings_message", new Object[]{
                user.getCurrency().toString()
        }, locale);
    }

    public InlineKeyboardMarkup createSettingsKeyboard(Locale locale, User user) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                createInlineButton(
                        messageSource.getMessage("currency", null, locale) + ": " + user.getCurrency(),
                        "user_settings_currency"
                )
        ));
        rows.add(List.of(
                createInlineButton(
                        user.isRemindersEnabled() ? "🔔 Напоминания: Вкл" : "🔔 Напоминания: Выкл",
                        user.isRemindersEnabled() ? "user_settings_reminder_off" : "user_settings_reminder_on"
                )
        ));

        markup.setKeyboard(rows);
        return markup;
    }

    public InlineKeyboardMarkup createCurrencyKeyboard(Locale locale) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                createInlineButton(Currency.СОМ.toString(), "user_settings_currency_сом"),
                createInlineButton(Currency.РУБ.toString(), "user_settings_currency_руб"),
                createInlineButton(Currency.$.toString(), "user_settings_currency_$")
        ));

        markup.setKeyboard(rows);
        return markup;
    }

    public void updateUserCurrency(User user, String currencyCode) {
        Currency currency = switch (currencyCode.toLowerCase()) {
            case "сом" -> Currency.СОМ;
            case "руб" -> Currency.РУБ;
            case "$" -> Currency.$;
            default -> user.getCurrency(); // Если неизвестная валюта, не меняем
        };
        user.setCurrency(currency);
        userRepository.save(user);
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}