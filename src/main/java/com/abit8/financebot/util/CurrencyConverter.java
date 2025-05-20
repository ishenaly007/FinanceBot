package com.abit8.financebot.util;

import com.abit8.financebot.model.Currency;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class CurrencyConverter {
    private static final String GOOGLE_FINANCE_URL = "https://www.google.com/finance/quote/%s-%s";

    public static BigDecimal getExchangeRate(Currency from, Currency to) throws Exception {
        return CurrencyRateCache.getRate(from, to);
    }

    // Этот метод нужен для CurrencyRateCache, чтобы получать актуальный курс, когда кэш устарел
    static BigDecimal fetchExchangeRate(Currency from, Currency to) throws Exception {
        if (from == to) return BigDecimal.ONE;

        String pair = switch (from) {
            case СОМ -> "KGS";
            case РУБ -> "RUB";
            case $ -> "USD";
        } + "-" + switch (to) {
            case СОМ -> "KGS";
            case РУБ -> "RUB";
            case $ -> "USD";
        };

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(GOOGLE_FINANCE_URL, pair.split("-")[0], pair.split("-")[1])))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Document doc = Jsoup.parse(response.body());
        String rateText = doc.select("div.YMlKec.fxKbKc").text().replace(",", ".");
        if (rateText.isEmpty()) {
            throw new IllegalStateException("Failed to parse exchange rate for " + pair);
        }
        return new BigDecimal(rateText);
    }
}