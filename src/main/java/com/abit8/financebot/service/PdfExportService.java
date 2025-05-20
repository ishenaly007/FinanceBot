package com.abit8.financebot.service;

import com.abit8.financebot.entity.Transaction;
import com.abit8.financebot.entity.User;
import com.abit8.financebot.model.Currency;
import com.abit8.financebot.util.CurrencyRateCache;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PdfExportService {

    public static Map<String, String> messagesRu = new HashMap<String, String>() {{
        put("reportTitle", "Финансовый отчет");
        put("periodFrom", "с");
        put("periodTo", "по");
        put("income", "Доходы");
        put("expenses", "Расходы");
        put("balance", "Остаток");
        put("operations", "Операций");
        put("date", "Дата");
        put("sum", "Сумма");
        put("category", "Категория");
        put("comment", "Комментарий");
        put("type", "Тип");
        put("incomeType", "Доход");
        put("expenseType", "Расход");
        put("withoutCategory", "Без категории");
        put("footer", "Сформировано ботом FinanceKG \u2022 financebot.kg");
        put("expenseChart", "Расходы по категориям");
        put("incomeChart", "Доходы по категориям");
        put("barChartByDay", "Доходы и расходы по дням");
        put("barChartByMonth", "Доходы и расходы по месяцам");
        put("dateAxis", "Дата");
        put("sumAxis", "Сумма");
        put("topIncomeCategories", "Топ-5 категорий доходов");
    }};

    public static Map<String, String> messagesKy = new HashMap<String, String>() {{
        put("reportTitle", "Каржылык отчет");
        put("periodFrom", "дан");
        put("periodTo", "чейин");
        put("income", "Кириш");
        put("expenses", "Чыгым");
        put("balance", "Калдык");
        put("operations", "Операциялар");
        put("date", "Күн");
        put("sum", "Сомма");
        put("category", "Категория");
        put("comment", "Комментарий");
        put("type", "Тип");
        put("incomeType", "Кириш");
        put("expenseType", "Чыгым");
        put("withoutCategory", "Категория жок");
        put("footer", "FinanceKG тарабынан түзүлгөн \u2022 financebot.kg");
        put("expenseChart", "Чыгымдар категориясы боюнча");
        put("incomeChart", "Кириштер категориясы боюнча");
        put("barChartByDay", "Кириштер жана чыгымдар күндөр боюнча");
        put("barChartByMonth", "Кириштер жана чыгымдар айлар боюнча");
        put("dateAxis", "Күн");
        put("sumAxis", "Сомма");
        put("topIncomeCategories", "Кириштердин эң мыкты 5 категориясы");
    }};

    public byte[] exportReport(User user, String username, List<Transaction> transactions,
                               LocalDate startDate, LocalDate endDate, Locale locale) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        pdf.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdf);
        document.setMargins(20, 20, 20, 20);

        InputStream regStream = getClass().getResourceAsStream("/fonts/Roboto_Condensed-Regular.ttf");
        FontProgram regProgram = FontProgramFactory.createFont(regStream.readAllBytes());
        PdfFont font = PdfFontFactory.createFont(regProgram, PdfEncodings.IDENTITY_H);

        InputStream boldStream = getClass().getResourceAsStream("/fonts/Roboto_Condensed-Bold.ttf");
        FontProgram boldProgram = FontProgramFactory.createFont(boldStream.readAllBytes());
        PdfFont bold = PdfFontFactory.createFont(boldProgram, PdfEncodings.IDENTITY_H);

        document.setFont(font);
        document.setFontSize(12);

        // Загрузка шрифтов для диаграмм
        Font robotoRegular = null;
        try {
            robotoRegular = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/fonts/Roboto_Condensed-Regular.ttf"));
        } catch (FontFormatException e) {
            throw new RuntimeException(e);
        }
        Font robotoBold = null;
        try {
            robotoBold = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/fonts/Roboto_Condensed-Bold.ttf"));
        } catch (FontFormatException e) {
            throw new RuntimeException(e);
        }

        // Обложка и сводка на первом листе
        Paragraph title = new Paragraph(getMessage("reportTitle", locale))
                .setFont(bold)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
        String periodText = getMessage("periodFrom", locale) + " " + startDate.format(dateFormatter) + " " +
                            getMessage("periodTo", locale) + " " + endDate.format(dateFormatter);
        Paragraph period = new Paragraph(periodText)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(12);
        document.add(period);

        if (username != null && !username.isEmpty()) {
            Paragraph userPara = new Paragraph(username)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12);
            document.add(userPara);
        }

        Paragraph datePara = new Paragraph(LocalDate.now().format(dateFormatter))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setFontColor(ColorConstants.GRAY);
        document.add(datePara);

        Paragraph footer2 = new Paragraph(getMessage("footer", locale))
                .setFontSize(12)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer2);//еще 1 сюда чтобы понятно было

        // Сводка
        Currency currency = user.getCurrency();
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;
        List<Transaction> filteredTransactions = transactions.stream()
                .filter(t -> !t.isDeleted())
                .toList();

        for (Transaction t : filteredTransactions) {
            BigDecimal amount = t.getAmount();
            if (t.getCurrency() != currency) {
                try {
                    BigDecimal rate = CurrencyRateCache.getRate(t.getCurrency(), currency);
                    amount = amount.multiply(rate);
                } catch (Exception e) {
                    // Логируем ошибку, но не прерываем обработку
                    System.err.println("Failed to convert currency for transaction " + t.getId() + ": " + e.getMessage());
                }
            }
            if ("INCOME".equalsIgnoreCase(t.getType().name())) {
                incomeSum = incomeSum.add(amount);
            } else if ("EXPENSE".equalsIgnoreCase(t.getType().name())) {
                expenseSum = expenseSum.add(amount);
            }
        }
        BigDecimal balance = incomeSum.subtract(expenseSum);
        int operationsCount = filteredTransactions.size();

        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth();
        Cell incomeCell = new Cell().setBackgroundColor(new DeviceRgb(40, 167, 69)).setPadding(10);
        Paragraph incTitle = new Paragraph(getMessage("income", locale))
                .setFont(bold).setFontColor(ColorConstants.WHITE);
        Paragraph incValue = new Paragraph(formatCurrency(incomeSum.doubleValue(), currency, locale))
                .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE);
        incomeCell.add(incTitle).add(incValue).setTextAlignment(TextAlignment.CENTER);
        Cell expenseCell = new Cell().setBackgroundColor(new DeviceRgb(220, 53, 69)).setPadding(10);
        Paragraph expTitle = new Paragraph(getMessage("expenses", locale))
                .setFont(bold).setFontColor(ColorConstants.WHITE);
        Paragraph expValue = new Paragraph(formatCurrency(expenseSum.doubleValue(), currency, locale))
                .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE);
        expenseCell.add(expTitle).add(expValue).setTextAlignment(TextAlignment.CENTER);
        Cell balanceCell = new Cell().setBackgroundColor(new DeviceRgb(23, 162, 184)).setPadding(10);
        Paragraph balTitle = new Paragraph(getMessage("balance", locale))
                .setFont(bold).setFontColor(ColorConstants.WHITE);
        Paragraph balValue = new Paragraph(formatCurrency(balance.doubleValue(), currency, locale))
                .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE);
        balanceCell.add(balTitle).add(balValue).setTextAlignment(TextAlignment.CENTER);
        Cell opsCell = new Cell().setBackgroundColor(new DeviceRgb(108, 117, 125)).setPadding(10);
        Paragraph opsTitle = new Paragraph(getMessage("operations", locale))
                .setFont(bold).setFontColor(ColorConstants.WHITE);
        Paragraph opsValue = new Paragraph(String.valueOf(operationsCount))
                .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE);
        opsCell.add(opsTitle).add(opsValue).setTextAlignment(TextAlignment.CENTER);

        summaryTable.addCell(incomeCell)
                .addCell(expenseCell)
                .addCell(balanceCell)
                .addCell(opsCell);
        document.add(summaryTable);

        document.add(new Paragraph("\n"));

        // Диаграмма расходов
        DefaultPieDataset expenseDataset = new DefaultPieDataset();
        for (Transaction t : filteredTransactions) {
            if ("EXPENSE".equalsIgnoreCase(t.getType().name())) {
                String category = t.getCategory() != null && !t.getCategory().getName().isBlank()
                        ? t.getCategory().getName()
                        : getMessage("withoutCategory", locale);
                BigDecimal amount = t.getAmount();
                if (t.getCurrency() != currency) {
                    try {
                        BigDecimal rate = CurrencyRateCache.getRate(t.getCurrency(), currency);
                        amount = amount.multiply(rate);
                    } catch (Exception e) {
                        System.err.println("Failed to convert currency for transaction " + t.getId() + ": " + e.getMessage());
                    }
                }
                double current = expenseDataset.getIndex(category) >= 0 ? expenseDataset.getValue(category).doubleValue() : 0.0;
                expenseDataset.setValue(category, current + amount.doubleValue());
            }
        }
        JFreeChart expenseChart = ChartFactory.createPieChart(
                getMessage("expenseChart", locale), expenseDataset, true, false, false);
        PiePlot expensePlot = (PiePlot) expenseChart.getPlot();
        expensePlot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {2}"));
        applyFontsToChart(expenseChart, robotoRegular, robotoBold);
        BufferedImage expenseChartImage = expenseChart.createBufferedImage(1000, 800); // Увеличенный размер
        ByteArrayOutputStream expenseChartBaos = new ByteArrayOutputStream();
        ImageIO.write(expenseChartImage, "png", expenseChartBaos);
        Image expenseChartImg = new Image(ImageDataFactory.create(expenseChartBaos.toByteArray()))
                .setAutoScale(true)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        document.add(expenseChartImg);

        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // Диаграмма доходов и топ-5 категорий
        DefaultPieDataset incomeDataset = new DefaultPieDataset();
        Map<String, BigDecimal> incomeByCategory = new HashMap<>();
        for (Transaction t : filteredTransactions) {
            if ("INCOME".equalsIgnoreCase(t.getType().name())) {
                String category = t.getCategory() != null && !t.getCategory().getName().isBlank()
                        ? t.getCategory().getName()
                        : getMessage("withoutCategory", locale);
                BigDecimal amount = t.getAmount();
                if (t.getCurrency() != currency) {
                    try {
                        BigDecimal rate = CurrencyRateCache.getRate(t.getCurrency(), currency);
                        amount = amount.multiply(rate);
                    } catch (Exception e) {
                        System.err.println("Failed to convert currency for transaction " + t.getId() + ": " + e.getMessage());
                    }
                }
                double current = incomeDataset.getIndex(category) >= 0 ? incomeDataset.getValue(category).doubleValue() : 0.0;
                incomeDataset.setValue(category, current + amount.doubleValue());
                incomeByCategory.merge(category, amount, BigDecimal::add);
            }
        }
        JFreeChart incomeChart = ChartFactory.createPieChart(
                getMessage("incomeChart", locale), incomeDataset, true, false, false);
        PiePlot incomePlot = (PiePlot) incomeChart.getPlot();
        incomePlot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {2}"));
        applyFontsToChart(incomeChart, robotoRegular, robotoBold);
        BufferedImage incomeChartImage = incomeChart.createBufferedImage(1000, 800); // Увеличенный размер
        ByteArrayOutputStream incomeChartBaos = new ByteArrayOutputStream();
        ImageIO.write(incomeChartImage, "png", incomeChartBaos);
        Image incomeChartImg = new Image(ImageDataFactory.create(incomeChartBaos.toByteArray()))
                .setAutoScale(true)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        document.add(incomeChartImg);

        document.add(new Paragraph("\n"));

        // Таблица топ-5 категорий доходов
        Paragraph topIncomeTitle = new Paragraph(getMessage("topIncomeCategories", locale))
                .setFont(bold)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(topIncomeTitle);

        Table topIncomeTable = new Table(UnitValue.createPercentArray(new float[]{3, 2}))
                .useAllAvailableWidth();
        topIncomeTable.addHeaderCell(new Cell().add(new Paragraph(getMessage("category", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        topIncomeTable.addHeaderCell(new Cell().add(new Paragraph(getMessage("sum", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));

        // Таблица топ-5 категорий доходов
        List<Map.Entry<String, BigDecimal>> topCategories = incomeByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        for (Map.Entry<String, BigDecimal> entry : topCategories) {
            topIncomeTable.addCell(new Cell().add(new Paragraph(entry.getKey()))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
            topIncomeTable.addCell(new Cell().add(new Paragraph(formatCurrency(entry.getValue().doubleValue(), currency, locale)))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        }

        document.add(topIncomeTable);

        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // Гистограмма доходов и расходов
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        boolean groupByDay = daysBetween <= 31;

        String currencySymbol = currency.name().toLowerCase();
        // Формируем подпись оси X с годом или диапазоном годов
        String yearLabel;
        int startYear = startDate.getYear();
        int endYear = endDate.getYear();
        if (startYear == endYear) {
            yearLabel = " (" + startYear + ")";
        } else {
            yearLabel = " (" + startYear + "-" + endYear + ")";
        }
        String dateAxisLabel = getMessage("dateAxis", locale) + yearLabel;
        String sumAxisLabel = getMessage("sumAxis", locale) + " (" + currencySymbol + ")";

        JFreeChart barChart;

        if (groupByDay) {
            List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).toList();
            Map<LocalDate, BigDecimal[]> dailyTotals = new HashMap<>();
            for (LocalDate date : dates) {
                dailyTotals.put(date, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
            for (Transaction t : filteredTransactions) {
                LocalDate date = t.getDate();
                if (dailyTotals.containsKey(date)) {
                    BigDecimal amount = t.getAmount();
                    if (t.getCurrency() != currency) {
                        try {
                            BigDecimal rate = CurrencyRateCache.getRate(t.getCurrency(), currency);
                            amount = amount.multiply(rate);
                        } catch (Exception e) {
                            System.err.println("Failed to convert currency for transaction " + t.getId() + ": " + e.getMessage());
                        }
                    }
                    if ("INCOME".equalsIgnoreCase(t.getType().name())) {
                        dailyTotals.get(date)[0] = dailyTotals.get(date)[0].add(amount);
                    } else if ("EXPENSE".equalsIgnoreCase(t.getType().name())) {
                        dailyTotals.get(date)[1] = dailyTotals.get(date)[1].add(amount);
                    }
                }
            }
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            double maxValue = 0;
            for (LocalDate date : dates) {
                BigDecimal[] totals = dailyTotals.get(date);
                dataset.addValue(totals[0].doubleValue(), getMessage("income", locale), date.format(DateTimeFormatter.ofPattern("dd.MM", locale)));
                dataset.addValue(totals[1].doubleValue(), getMessage("expenses", locale), date.format(DateTimeFormatter.ofPattern("dd.MM", locale)));
                maxValue = Math.max(maxValue, totals[0].doubleValue());
                maxValue = Math.max(maxValue, totals[1].doubleValue());
            }
            String chartTitle = getMessage("barChartByDay", locale);
            barChart = ChartFactory.createBarChart(
                    chartTitle,
                    dateAxisLabel, // Ось X: даты
                    sumAxisLabel,  // Ось Y: суммы
                    dataset,
                    PlotOrientation.VERTICAL, // Вертикальная ориентация
                    true,
                    false,
                    false
            );
            CategoryPlot plot = (CategoryPlot) barChart.getPlot();
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
            BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, new Color(40, 167, 69, 200));
            renderer.setSeriesPaint(1, new Color(220, 53, 69, 200));
            renderer.setItemMargin(0.2); // Меньше расстояние внутри группы (доходы + расходы)
            renderer.setMaximumBarWidth(0.02);
            // Метки с валютой
            renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getInstance(locale)));
            renderer.setDefaultItemLabelsVisible(true);
            renderer.setDefaultItemLabelFont(robotoRegular.deriveFont(16f));
            // Вертикальные метки цен
            renderer.setDefaultPositiveItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                    org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, -Math.PI / 2));
            renderer.setDefaultNegativeItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                    org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, -Math.PI / 2));
            renderer.setItemLabelAnchorOffset(8);
            renderer.setShadowVisible(true);
            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
            // Увеличение расстояния между категориями (днями)
            plot.getDomainAxis().setCategoryMargin(0.4); // 40% от ширины категории
            // Увеличение верхнего предела оси Y
            plot.getRangeAxis().setUpperMargin(0.15); // 15% дополнительного пространства сверху
        } else {
            YearMonth startYM = YearMonth.from(startDate);
            YearMonth endYM = YearMonth.from(endDate);
            List<YearMonth> yearMonths = new ArrayList<>();
            for (YearMonth ym : yearMonths) {
                System.out.println(ym.format(DateTimeFormatter.ofPattern("MMM yyyy", locale)));
            }
            for (YearMonth ym = startYM; !ym.isAfter(endYM); ym = ym.plusMonths(1)) {
                yearMonths.add(ym);
            }
            Map<YearMonth, BigDecimal[]> monthlyTotals = new HashMap<>();
            for (YearMonth ym : yearMonths) {
                monthlyTotals.put(ym, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
            for (Transaction t : filteredTransactions) {
                YearMonth ym = YearMonth.from(t.getDate());
                if (monthlyTotals.containsKey(ym)) {
                    BigDecimal amount = t.getAmount();
                    if (t.getCurrency() != currency) {
                        try {
                            BigDecimal rate = CurrencyRateCache.getRate(t.getCurrency(), currency);
                            amount = amount.multiply(rate);
                        } catch (Exception e) {
                            System.err.println("Failed to convert currency for transaction " + t.getId() + ": " + e.getMessage());
                        }
                    }
                    if ("INCOME".equalsIgnoreCase(t.getType().name())) {
                        monthlyTotals.get(ym)[0] = monthlyTotals.get(ym)[0].add(amount);
                    } else if ("EXPENSE".equalsIgnoreCase(t.getType().name())) {
                        monthlyTotals.get(ym)[1] = monthlyTotals.get(ym)[1].add(amount);
                    }
                }
            }
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            double maxValue = 0;
            for (YearMonth ym : yearMonths) {
                BigDecimal[] totals = monthlyTotals.get(ym);
                String month = ym.format(DateTimeFormatter.ofPattern("MMM", locale));
                String year = ym.format(DateTimeFormatter.ofPattern("yyyy", locale));
                String category = month + " " + year;
                dataset.addValue(totals[0].doubleValue(), getMessage("income", locale), category);
                dataset.addValue(totals[1].doubleValue(), getMessage("expenses", locale), category);
                maxValue = Math.max(maxValue, totals[0].doubleValue());
                maxValue = Math.max(maxValue, totals[1].doubleValue());
            }
            String chartTitle = getMessage("barChartByMonth", locale);
            barChart = ChartFactory.createBarChart(
                    chartTitle,
                    dateAxisLabel, // Ось X: месяцы
                    sumAxisLabel,  // Ось Y: суммы
                    dataset,
                    PlotOrientation.VERTICAL, // Вертикальная ориентация
                    true,
                    false,
                    false
            );
            CategoryPlot plot = (CategoryPlot) barChart.getPlot();
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
            BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setSeriesPaint(0, new Color(40, 167, 69, 200));
            renderer.setSeriesPaint(1, new Color(220, 53, 69, 200));
            renderer.setItemMargin(0.1); // Меньше расстояние внутри группы (доходы + расходы)
            renderer.setMaximumBarWidth(0.1);
            // Метки с валютой
            renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2} " + currencySymbol, NumberFormat.getInstance(locale)));
            renderer.setDefaultItemLabelsVisible(true);
            renderer.setDefaultItemLabelFont(robotoRegular.deriveFont(20f));
            // Вертикальные метки цен
            renderer.setDefaultPositiveItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                    org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, -Math.PI / 2));
            renderer.setDefaultNegativeItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                    org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, org.jfree.chart.ui.TextAnchor.CENTER_LEFT, -Math.PI / 2));
            renderer.setItemLabelAnchorOffset(8);
            renderer.setShadowVisible(true);
            plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
            // Увеличение расстояния между категориями (месяцами)
            plot.getDomainAxis().setCategoryMargin(0.4); // 40% от ширины категории
            // Увеличение верхнего предела оси Y
            plot.getRangeAxis().setUpperMargin(0.2); // 20% дополнительного пространства сверху
        }
        applyFontsToChart(barChart, robotoRegular, robotoBold);
        BufferedImage barChartImage = barChart.createBufferedImage(1200, 1200); // Высота 1200 для вертикальных меток
        ByteArrayOutputStream barChartBaos = new ByteArrayOutputStream();
        ImageIO.write(barChartImage, "png", barChartBaos);
        Image barChartImg = new Image(ImageDataFactory.create(barChartBaos.toByteArray()))
                .setAutoScale(true)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        document.add(barChartImg);

        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));

        // Таблица транзакций
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 3, 4, 2}))
                .useAllAvailableWidth();
        table.addHeaderCell(new Cell().add(new Paragraph(getMessage("date", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        table.addHeaderCell(new Cell().add(new Paragraph(getMessage("sum", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        table.addHeaderCell(new Cell().add(new Paragraph(getMessage("category", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        table.addHeaderCell(new Cell().add(new Paragraph(getMessage("comment", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));
        table.addHeaderCell(new Cell().add(new Paragraph(getMessage("type", locale)))
                .setFont(bold).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f)));

        DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale);
        for (Transaction t : filteredTransactions) {
            // Определяем цвет фона для колонки type
            DeviceRgb typeBackgroundColor = "INCOME".equalsIgnoreCase(t.getType().name())
                    ? new DeviceRgb(40, 167, 69)
                    : new DeviceRgb(220, 53, 69);

            Cell c1 = new Cell().add(new Paragraph(t.getDate().format(dtFormatter)))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
            Cell c2 = new Cell().add(new Paragraph(formatCurrency(t.getAmount().doubleValue(), t.getCurrency(), locale)))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
            String catVal = (t.getCategory() != null && !t.getCategory().toString().isBlank())
                    ? t.getCategory().getName() : getMessage("withoutCategory", locale);
            Cell c3 = new Cell().add(new Paragraph(catVal))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
            String commVal = (t.getComment() != null) ? t.getComment() : "";
            Cell c4 = new Cell().add(new Paragraph(commVal))
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
            String typeStr = "INCOME".equalsIgnoreCase(t.getType().name())
                    ? getMessage("incomeType", locale)
                    : getMessage("expenseType", locale);
            Cell c5 = new Cell().add(new Paragraph(typeStr))
                    .setBackgroundColor(typeBackgroundColor)
                    .setFontColor(ColorConstants.WHITE)
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 0.5f));
            table.addCell(c1).addCell(c2).addCell(c3).addCell(c4).addCell(c5);
        }
        document.add(table);

        document.add(new Paragraph("\n"));

        Paragraph footer = new Paragraph(getMessage("footer", locale))
                .setFontSize(12)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    private void applyFontsToChart(JFreeChart chart, Font baseRegular, Font baseBold) {
        // Заголовок: bold 28pt (увеличено с 14pt)
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(baseBold.deriveFont(40f));
        }

        // Легенда: regular 20pt (увеличено с 10pt)
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(baseRegular.deriveFont(28f));
        }

        // Настройки для конкретных типов диаграмм
        if (chart.getPlot() instanceof PiePlot) {
            PiePlot plot = (PiePlot) chart.getPlot();
            plot.setLabelFont(baseRegular.deriveFont(24f)); // Увеличено с 10pt
        } else if (chart.getPlot() instanceof CategoryPlot) {
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            // Метки осей: bold 24pt (увеличено с 12pt)
            plot.getDomainAxis().setLabelFont(baseBold.deriveFont(28f));
            plot.getRangeAxis().setLabelFont(baseBold.deriveFont(28f));
            // Деления осей: regular 20pt (увеличено с 10pt)
            plot.getDomainAxis().setTickLabelFont(baseRegular.deriveFont(24f));
            plot.getRangeAxis().setTickLabelFont(baseRegular.deriveFont(24f));
            // Метки элементов: regular 16pt (увеличено с 8pt)
            if (plot.getRenderer() instanceof BarRenderer) {
                BarRenderer renderer = (BarRenderer) plot.getRenderer();
                renderer.setDefaultItemLabelFont(baseRegular.deriveFont(18f));
            }
        }
    }

    private String getMessage(String key, Locale locale) {
        if (locale != null && "kg".equalsIgnoreCase(locale.getLanguage())) {
            return messagesKy.getOrDefault(key, messagesRu.get(key));
        }
        return messagesRu.getOrDefault(key, "");
    }

    private String formatCurrency(double amount, Currency currency, Locale locale) {
        NumberFormat numberFormatter = NumberFormat.getNumberInstance(locale);
        String formattedAmount = numberFormatter.format(amount);
        String currencySymbol = currency.name().toLowerCase();
        return formattedAmount + " " + currencySymbol;
    }
}