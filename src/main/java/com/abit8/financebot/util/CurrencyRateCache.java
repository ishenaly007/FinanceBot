package com.abit8.financebot.util;

import com.abit8.financebot.model.Currency;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyRateCache {
    private static class RateEntry {
        BigDecimal rate;
        LocalDateTime timestamp;

        RateEntry(BigDecimal rate, LocalDateTime timestamp) {
            this.rate = rate;
            this.timestamp = timestamp;
        }
    }

    private static final Map<String, RateEntry> rateCache = new ConcurrentHashMap<>();
    private static final long TTL_MINUTES = 60;

    public static BigDecimal getRate(Currency from, Currency to) throws Exception {
        if (from == to) return BigDecimal.ONE;

        String key = from + "-" + to;
        RateEntry entry = rateCache.get(key);

        if (entry != null && entry.timestamp.isAfter(LocalDateTime.now().minusMinutes(TTL_MINUTES))) {
            return entry.rate;
        }

        BigDecimal rate = CurrencyConverter.fetchExchangeRate(from, to);
        rateCache.put(key, new RateEntry(rate, LocalDateTime.now()));
        return rate;
    }

    public static void clearCache() {
        rateCache.clear();
    }
}