package com.abit8.financebot.service;

import com.abit8.financebot.entity.Category;
import com.abit8.financebot.entity.Transaction;
import com.abit8.financebot.entity.User;
import com.abit8.financebot.model.Currency;
import com.abit8.financebot.model.TransactionType;
import com.abit8.financebot.repository.CategoryRepository;
import com.abit8.financebot.repository.TransactionRepository;
import com.abit8.financebot.repository.UserRepository;
import com.abit8.financebot.util.CurrencyRateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceBotService {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final MessageSource messageSource;

    private static final Pattern TRANSACTION_PATTERN =
            Pattern.compile("^([+\\-])\\s*([\\d\\s\\.]+)(?:\\s+(\\S+))?(?:\\s+(.+))?$");

    public User registerOrGetUser(Long chatId, String username) {
        Optional<User> userOpt = userRepository.findByChatId(chatId);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            if (user.getUsername() == null && username != null) {
                user.setUsername(username);
                user = userRepository.save(user);
            }
        } else {
            user = new User();
            user.setChatId(chatId);
            user.setTrialEndDate(LocalDate.now().plusDays(7));
            user.setUsername(username); // сохраняем при создании
            user = userRepository.save(user);
        }
        return user;
    }

    public String getMonthlyBalance(User user, Locale locale) {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();

        List<Transaction> transactions = transactionRepository.findByUserAndDateBetweenAndDeletedFalse(user, startOfMonth, today);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int incomeCount = 0;
        int expenseCount = 0;

        for (Transaction transaction : transactions) {
            BigDecimal amount = transaction.getAmount();
            if (transaction.getCurrency() != user.getCurrency()) {
                try {
                    BigDecimal rate = CurrencyRateCache.getRate(transaction.getCurrency(), user.getCurrency());
                    amount = amount.multiply(rate);
                } catch (Exception e) {
                    log.error("Failed to convert currency for transaction {}: {}", transaction.getId(), e.getMessage());
                }
            }
            if (transaction.getType() == TransactionType.INCOME) {
                income = income.add(amount);
                incomeCount++;
            } else {
                expense = expense.add(amount);
                expenseCount++;
            }
        }

        BigDecimal balance = income.subtract(expense);

        String monthName = today.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale);
        monthName = monthName.substring(0, 1).toUpperCase(locale) + monthName.substring(1);

        String currency = user.getCurrency().toString().toLowerCase(locale);

        return messageSource.getMessage("monthly_balance", new Object[]{
                monthName,
                income, currency, incomeCount,
                expense, currency, expenseCount,
                balance, currency
        }, locale);
    }


    public boolean isUserPremium(User user) {
        return user.isPremium() || user.getTrialEndDate().isAfter(LocalDate.now());
    }

    public String processTransactionText(Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String telegramUsername = update.getMessage().getFrom().getUserName();
        User user = registerOrGetUser(chatId, telegramUsername);
        Locale locale = new Locale(user.getLanguage().name());

        if (!isUserPremium(user)) {
            throw new IllegalStateException(messageSource.getMessage("subscription_expired", null, locale));
        }

        Matcher matcher = TRANSACTION_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(messageSource.getMessage("invalid_format", null, locale));
        }

        String typeInput = matcher.group(1).trim();
        String amountsStr = matcher.group(2).trim();
        String categoryName = matcher.group(3);
        String comment = matcher.group(4);

        TransactionType type = typeInput.equals("+") ? TransactionType.INCOME : TransactionType.EXPENSE;
        List<BigDecimal> amounts = Arrays.stream(amountsStr.split("\\s+"))
                .map(str -> {
                    try {
                        return new BigDecimal(str);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(messageSource.getMessage("invalid_amount", null, locale));
                    }
                })
                .toList();

        if (categoryName != null && (categoryName.equals("--") || categoryName.equals("—"))) {
            categoryName = null;
        }

        Category category = null;
        if (categoryName != null) {
            String finalCategoryName = categoryName;
            category = categoryRepository.findByUserAndType(user, type).stream()
                    .filter(c -> c.getName().equalsIgnoreCase(finalCategoryName))
                    .findFirst()
                    .orElseGet(() -> {
                        Category newCategory = new Category();
                        newCategory.setUser(user);
                        newCategory.setName(finalCategoryName);
                        newCategory.setType(type);
                        return categoryRepository.save(newCategory);
                    });
        }

        for (BigDecimal amount : amounts) {
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setType(type);
            transaction.setAmount(amount);
            transaction.setCategory(category);
            transaction.setComment(comment);
            transaction.setCurrency(user.getCurrency());
            transactionRepository.save(transaction);
        }

        BigDecimal totalAmount = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        String categoryDisplay = category != null ? category.getName() : messageSource.getMessage("no_category", null, locale);
        Currency currency = user.getCurrency();
        String commentDisplay = comment != null ? " — " + comment : "";
        String typeKey = type.equals(TransactionType.INCOME) ? messageSource.getMessage("income", null, locale) : messageSource.getMessage("expense", null, locale);

        if (amounts.size() == 1) {
            return messageSource.getMessage(
                    "transaction_added_single",
                    new Object[]{typeKey, amounts.get(0), currency.toString().toLowerCase(), categoryDisplay, commentDisplay},
                    locale
            );
        } else {
            return messageSource.getMessage(
                    "transaction_added_multiple",
                    new Object[]{amounts.size(), typeKey, categoryDisplay, totalAmount, currency.toString().toLowerCase()},
                    locale
            );
        }
    }

    public String getDailyReportV2(User user, Locale locale) {
        LocalDate today = LocalDate.now();
        List<Transaction> list = transactionRepository.findByUserAndDate(user, today)
                .stream()
                .filter(t -> !t.isDeleted())
                .sorted(Comparator.comparing(Transaction::getCreatedAt))
                .toList();

        if (list.isEmpty()) {
            return messageSource.getMessage("no_transactions_today", null, locale);
        }

        // Заголовок
        StringBuilder sb = new StringBuilder();
        String formattedDate = today.format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", locale));
        sb.append(messageSource.getMessage("daily_transaction_list",
                new Object[]{formattedDate}, locale)).append("\n\n");

        // Подготовка
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        int counter = 1; // Счетчик без массива

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        // Создаем список строк с номерами
        List<String> lines = new ArrayList<>();
        for (Transaction tx : list) {
            String sign = tx.getType() == TransactionType.INCOME ? "+" : "-";
            String categoryName = tx.getCategory() != null
                    ? tx.getCategory().getName()
                    : messageSource.getMessage("no_category_title", null, locale);

            String comment = Optional.ofNullable(tx.getComment()).orElse("").trim();
            String time = tx.getTime().format(timeFmt);

            String amountStr = sign
                               + tx.getAmount().stripTrailingZeros().toPlainString()
                               + " "
                               + tx.getCurrency().toString().toLowerCase(locale);

            String line = !comment.isEmpty()
                    ? String.format("%d) %s — %s ", counter, amountStr, comment)
                    : String.format("%d) %s ", counter, amountStr);

            grouped.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(line);
            counter++;

            // Подсчет итогов
            BigDecimal amount = tx.getAmount();
            if (tx.getCurrency() != user.getCurrency()) {
                try {
                    BigDecimal rate = CurrencyRateCache.getRate(tx.getCurrency(), user.getCurrency());
                    amount = amount.multiply(rate);
                } catch (Exception e) {
                    log.error("Failed to convert currency for transaction {}: {}", tx.getId(), e.getMessage());
                }
            }
            if (tx.getType() == TransactionType.INCOME) {
                income = income.add(amount);
            } else {
                expense = expense.add(amount);
            }
        }

        // Вывод по категориям
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            sb.append("*").append(entry.getKey()).append("*\n");
            for (String line : entry.getValue()) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        // Итог дня
        sb.append("*");
        if (income.compareTo(expense) >= 0) {
            sb.append(messageSource.getMessage("income_today", null, locale))
                    .append(": +")
                    .append(income.subtract(expense).stripTrailingZeros().toPlainString());
        } else {
            sb.append(messageSource.getMessage("expense_today", null, locale))
                    .append(": -")
                    .append(expense.subtract(income).stripTrailingZeros().toPlainString());
        }
        sb.append(" ")
                .append(user.getCurrency().toString().toLowerCase(locale))
                .append("*");

        // Подсказка удаления
        sb.append("\n\\_\\_\n")
                .append(messageSource.getMessage("del_hint", null, locale));

        return sb.toString();
    }

    public void deleteTransactionsByIndexes(User user, String delCmd) {
        String[] parts = delCmd.trim().split("\\s+");
        if (parts.length < 2) return;

        LocalDate today = LocalDate.now();
        List<Transaction> orderedList = transactionRepository.findByUserAndDate(user, today)
                .stream()
                .filter(t -> !t.isDeleted())
                .sorted(Comparator.comparing(Transaction::getCreatedAt))
                .toList();

        for (int i = 1; i < parts.length; i++) {
            try {
                int idx = Integer.parseInt(parts[i]) - 1;
                if (idx >= 0 && idx < orderedList.size()) {
                    Transaction tx = orderedList.get(idx);
                    tx.setDeleted(true);
                    transactionRepository.save(tx);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}