package com.abit8.financebot;

import com.abit8.financebot.entity.Period;
import com.abit8.financebot.entity.Transaction;
import com.abit8.financebot.entity.User;
import com.abit8.financebot.repository.TransactionRepository;
import com.abit8.financebot.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FinanceBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(FinanceBot.class);
    private final String botUsername;
    private final FinanceBotService financeBotService;
    private final UserSettingsService userSettingsService;
    private final ReminderService reminderService;
    private final UserService userService;
    private final MessageSource messageSource;
    private final PdfExportService pdfExportService;
    private final TransactionRepository transactionRepository;


    public FinanceBot(@Value("${bot.token}") String botToken, @Value("${bot.username}") String botUsername, FinanceBotService financeBotService, UserSettingsService userSettingsService, ReminderService reminderService, UserService userService, MessageSource messageSource, PdfExportService pdfExportService, TransactionRepository transactionRepository) {
        super(botToken);
        this.botUsername = botUsername;
        this.financeBotService = financeBotService;
        this.userSettingsService = userSettingsService;
        this.reminderService = reminderService;
        this.userService = userService;
        this.messageSource = messageSource;
        this.pdfExportService = pdfExportService;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            User user = financeBotService.registerOrGetUser(chatId);
            Locale locale = new Locale(user.getLanguage().name());

            logger.info("Received message from user {}: {}", chatId, text);

            try {
                if (text.startsWith("/start")) {
                    handleStartCommand(chatId, locale);
                } else if (text.equals("/help")) {
                    handleHelpCommand(chatId, locale);
                } else if (text.equals("/balance")) {
                    String response = financeBotService.getMonthlyBalance(user, locale);
                    sendMessage(chatId, response, false);
                } else if (text.equals("/set") || text.equals("Настройки")) {
                    String response = userSettingsService.getSettingsMessage(user, locale);
                    sendMessageWithInlineButtons(chatId, response, userSettingsService.createSettingsKeyboard(locale, user));
                } else if (text.equals("/report") || text.equals("Отчет")) {
                    String resp = financeBotService.getDailyReportV2(user, locale);
                    sendMessage(chatId, resp, false);
                } else if (text.equals("/export")) {
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                    rows.add(List.of(createInlineButton(messageSource.getMessage("for_month", null, locale), "EXPORT_MONTH"), createInlineButton(messageSource.getMessage("for_year", null, locale), "EXPORT_YEAR")));
                    markup.setKeyboard(rows);
                    // заменяем вызов
                    sendMessageWithInlineButtons(chatId, messageSource.getMessage("export_select", null, locale), markup);

                } else if (text.startsWith("del")) {
                    financeBotService.deleteTransactionsByIndexes(user, text);
                    sendMessage(chatId, messageSource.getMessage("deleted_ok", null, locale), false);
                } else if (text.matches("^[+\\-].*")) {
                    String response = financeBotService.processTransactionText(update);
                    sendMessage(chatId, response, false);
                } else {
                    sendMessage(chatId, messageSource.getMessage("invalid_format", null, locale), true);
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                logger.error("Error processing message for user {}: {}", chatId, e.getMessage());
                sendMessage(chatId, e.getMessage(), true);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private InlineKeyboardButton createInlineButton(String text, String callback) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callback);
        return b;
    }

    private void handleStartCommand(Long chatId, Locale locale) {
        String message = messageSource.getMessage("welcome", null, locale);
        try {
            SendAnimation animation = new SendAnimation();
            animation.setChatId(chatId.toString());
            animation.setAnimation(new InputFile("CgACAgQAAxkBAAIB_Wgq_BwhUrV6orrvvb_3aDUNZc06AAIlGAACWudZUXKDERdaPvwlNgQ"));
            animation.setCaption(message);
            animation.setParseMode(ParseMode.MARKDOWN);
            animation.setReplyMarkup(createMainMenu());
            animation.setWidth(512);
            animation.setHeight(512);
            telegramExecuteMethod(animation);
        } catch (Exception e) {
            logger.error("Failed to send GIF for user {}: {}", chatId, e.getMessage());
            sendMessage(chatId, message, true);
        }
    }

    private void handleHelpCommand(Long chatId, Locale locale) {
        String message = messageSource.getMessage("help", null, locale);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createInlineButton("📄 Добавление транзакций(доход/расход)", "HELP_ADD"), createInlineButton("🗑 Удаление транзакций", "HELP_DELETE")));
        rows.add(List.of(createInlineButton("📊 Отчеты", "HELP_REPORTS"), createInlineButton("🛠 Настройки", "HELP_SETTINGS")));
        markup.setKeyboard(rows);
        sendMessageWithInlineButtons(chatId, message, markup);
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        User user = financeBotService.registerOrGetUser(chatId);
        Locale locale = new Locale(user.getLanguage().name());
        logger.info("Received callback query from user {}: {}", chatId, callbackData);
        if (callbackData.equals("EXPORT_MONTH")) {
            exportPdf(update.getCallbackQuery().getMessage().getChatId(), user, Period.MONTH, locale, update);
        } else if (callbackData.equals("EXPORT_YEAR")) {
            exportPdf(update.getCallbackQuery().getMessage().getChatId(), user, Period.YEAR, locale, update);
        } else if (callbackData.equals("HELP_ADD")) {
            sendMessage(chatId, messageSource.getMessage("help_add", null, locale), false);
        } else if (callbackData.equals("HELP_DELETE")) {
            sendMessage(chatId, messageSource.getMessage("help_delete", null, locale), false);
        } else if (callbackData.equals("HELP_REPORTS")) {
            sendMessage(chatId, messageSource.getMessage("help_reports", null, locale), false);
        } else if (callbackData.equals("HELP_SETTINGS")) {
            sendMessage(chatId, messageSource.getMessage("help_settings", null, locale), false);
        } else if (callbackData.equals("user_settings_currency")) {
            sendMessageWithInlineButtons(chatId, messageSource.getMessage("select_currency", null, locale), userSettingsService.createCurrencyKeyboard(locale));
        } else if (callbackData.startsWith("user_settings_currency_")) {
            String currencyCode = callbackData.replace("user_settings_currency_", "");
            userSettingsService.updateUserCurrency(user, currencyCode);
            sendMessage(chatId, messageSource.getMessage("currency_updated", new Object[]{currencyCode.toUpperCase()}, locale), true);
        } else if (callbackData.equals("user_settings_reminder_on")) {
            reminderService.toggleReminder(user, true);
            sendMessage(chatId, messageSource.getMessage("reminder_enabled", null, locale), true);

        } else if (callbackData.equals("user_settings_reminder_off")) {
            reminderService.toggleReminder(user, false);
            sendMessage(chatId, messageSource.getMessage("reminder_disabled", null, locale), true);
        }
    }

    private ReplyKeyboardMarkup createMainMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("Отчет");
        row.add("Настройки");
        rows.add(row);

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void sendMessage(Long chatId, String text, boolean withMainMenu) {
        sendMessage(chatId, text, withMainMenu, null);
    }

    public void sendMessage(Long chatId, String text, boolean withMainMenu, ReplyKeyboardMarkup customKeyboard) {
        if (text == null || text.isEmpty()) {
            logger.warn("Attempted to send empty message to user {}", chatId);
            return;
        }
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setText(text);
        if (customKeyboard != null) {
            sendMessage.setReplyMarkup(customKeyboard);
        } else if (withMainMenu) {
            sendMessage.setReplyMarkup(createMainMenu());
        }
        telegramExecuteMethod(sendMessage);
    }

    public void sendMessageWithInlineButtons(Long chatId, String text, InlineKeyboardMarkup markup) {
        if (text == null || text.isEmpty()) return;
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setParseMode("Markdown");
        msg.setText(text);
        msg.setReplyMarkup(markup);
        telegramExecuteMethod(msg);
    }

    public Message telegramExecuteMethod(Object method) {
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }
        try {
            if (method instanceof SendMessage) {
                return execute((SendMessage) method);
            } else if (method instanceof SendPhoto) {
                return execute((SendPhoto) method);
            } else if (method instanceof SendVideo) {
                return execute((SendVideo) method);
            } else if (method instanceof SendAudio) {
                return execute((SendAudio) method);
            } else if (method instanceof SendDocument) {
                return execute((SendDocument) method);
            } else if (method instanceof SendSticker) {
                return execute((SendSticker) method);
            } else if (method instanceof SendAnimation) {
                return execute((SendAnimation) method);
            } else if (method instanceof SendVoice) {
                return execute((SendVoice) method);
            } else if (method instanceof SendVideoNote) {
                return execute((SendVideoNote) method);
            } else if (method instanceof SendMediaGroup) {
                return execute((SendMediaGroup) method).get(0);
            } else if (method instanceof EditMessageText) {
                execute((EditMessageText) method);
                return null;
            } else if (method instanceof EditMessageReplyMarkup) {
                execute((EditMessageReplyMarkup) method);
                return null;
            } else if (method instanceof EditMessageMedia) {
                execute((EditMessageMedia) method);
                return null;
            } else if (method instanceof DeleteMessage) {
                execute((DeleteMessage) method);
                return null;
            }
            return null;
        } catch (TelegramApiException e) {
            logger.error("Telegram API error: {}", e.getMessage());
            return null;
        }
    }

    public void exportPdf(Long chatId, User user, Period period, Locale locale, Update update) {
        LocalDate end = LocalDate.now();
        LocalDate start = (period == Period.MONTH) ? end.minusMonths(1) : end.minusYears(1);

        // 1️⃣ Берём транзакции пользователя за период
        List<Transaction> txs = transactionRepository.findByUserAndDateBetweenAndDeletedFalse(user, start, end);

        // 2️⃣ Имя / username (если пусто — null)
        String username = (update.getCallbackQuery().getFrom().getUserName() != null && !update.getCallbackQuery().getFrom().getUserName().isBlank()) ? update.getCallbackQuery().getFrom().getUserName() : null;


        // 3️⃣ Генерируем отчёт
        byte[] pdfBytes;
        try {
            pdfBytes = pdfExportService.exportReport(user, username, txs, start, end, locale);
        } catch (IOException e) {
            logger.error("PDF export failed for user {}: {}", chatId, e.getMessage());
            sendMessage(chatId, messageSource.getMessage("export_error", null, locale), true);
            return;
        }

        // 4️⃣ Имя файла
        String fileName = String.format("отчет_%s–%s.pdf", start.format(DateTimeFormatter.ofPattern("yyyy_MM_dd")), end.format(DateTimeFormatter.ofPattern("yyyy_MM_dd")));

        // 5️⃣ Отправляем
        SendDocument doc = new SendDocument();
        doc.setChatId(chatId.toString());
        doc.setDocument(new InputFile(new ByteArrayInputStream(pdfBytes), fileName));
        doc.setCaption(messageSource.getMessage("export_ready", null, locale));

        telegramExecuteMethod(doc);
    }

    @Scheduled(cron = "0 0 20 * * *")
    public void sendDailyReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<User> users = userService.findByRemindersEnabledTrue();
        Locale locale = new Locale("ru");

        for (User user : users) {
            // Проверяем, отправляли ли напоминание сегодня
            if (user.getLastReminderSent() == null ||
                user.getLastReminderSent().toLocalDate().isBefore(now.toLocalDate())) {
                try {
                    String message = messageSource.getMessage("reminder_daily", null, locale);
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(user.getChatId().toString());
                    sendMessage.setText(message);
                    sendMessage.setParseMode("Markdown");
                    telegramExecuteMethod(sendMessage);

                    user.setLastReminderSent(now);
                    userService.save(user);
                    logger.info("Reminder sent to user {}", user.getChatId());
                } catch (Exception e) {
                    logger.error("Failed to send reminder to user {}: {}", user.getChatId(), e.getMessage());
                }
            }
        }
    }
}