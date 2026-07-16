package com.gesturegame.game;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 塔罗综合解读的排版与分页视图。
 *
 * <p>排版结果按内容和面板尺寸缓存。游戏每帧只绘制已经生成的行，不再反复拆分
 * DeepSeek 返回的长文本。</p>
 */
final class TarotReadingPanel {

    private static final double FONT_SIZE = 14.0;
    private static final double MIN_FONT_SIZE = 12.0;
    private static final double LINE_HEIGHT = 21.0;

    private String lastInterpretation;
    private String lastAdvice;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private List<Page> pages = List.of(Page.empty());
    private int currentPage;
    private long layoutVersion;

    void clear() {
        lastInterpretation = null;
        lastAdvice = null;
        lastWidth = -1;
        lastHeight = -1;
        pages = List.of(Page.empty());
        currentPage = 0;
        layoutVersion++;
    }

    void updateContent(String interpretation, String advice, double width, double height) {
        int widthBucket = Math.max(120, (int) Math.round(width));
        int heightBucket = Math.max(100, (int) Math.round(height));
        String safeInterpretation = Objects.requireNonNullElse(interpretation, "");
        String safeAdvice = Objects.requireNonNullElse(advice, "");
        if (lastWidth == widthBucket && lastHeight == heightBucket
                && Objects.equals(lastInterpretation, safeInterpretation)
                && Objects.equals(lastAdvice, safeAdvice)) {
            return;
        }

        lastWidth = widthBucket;
        lastHeight = heightBucket;
        lastInterpretation = safeInterpretation;
        lastAdvice = safeAdvice;
        pages = buildPages(safeInterpretation, safeAdvice, widthBucket, heightBucket);
        currentPage = 0;
        layoutVersion++;
    }

    private List<Page> buildPages(String interpretation, String advice, int width, int height) {
        LayoutAttempt regular = layout(interpretation, advice, width, height, FONT_SIZE, LINE_HEIGHT);
        if (!regular.overflow()) {
            return regular.pages();
        }

        // 极端长文本仍保持 12px 的可读下限，并最多分成两页。
        double compactLineHeight = MIN_FONT_SIZE + 6.0;
        LayoutAttempt compact = layout(interpretation, advice, width, height,
                MIN_FONT_SIZE, compactLineHeight);
        return compact.pages();
    }

    private LayoutAttempt layout(String interpretation, String advice, int width, int height,
                                 double fontSize, double lineHeight) {
        int charsPerLine = Math.max(13, (int) (width / Math.max(7.0, fontSize * 0.94)));
        List<String> interpretationLines = wrap(interpretation, charsPerLine);
        List<String> adviceLines = wrap(advice, charsPerLine);
        int maxLinesPerPage = Math.max(5, (int) ((height - 10.0) / lineHeight));

        List<ReadingLine> all = new ArrayList<>();
        for (String line : interpretationLines) {
            all.add(new ReadingLine(line, LineStyle.BODY));
        }
        all.add(new ReadingLine("", LineStyle.SPACER));
        all.add(new ReadingLine("行动建议", LineStyle.HEADING));
        for (String line : adviceLines) {
            all.add(new ReadingLine(line, LineStyle.ADVICE));
        }

        List<Page> result = new ArrayList<>(2);
        int cursor = 0;
        for (int pageIndex = 0; pageIndex < 2 && cursor < all.size(); pageIndex++) {
            int end = Math.min(all.size(), cursor + maxLinesPerPage);
            List<ReadingLine> pageLines = new ArrayList<>(all.subList(cursor, end));
            result.add(new Page(List.copyOf(pageLines), fontSize, lineHeight));
            cursor = end;
        }
        if (result.isEmpty()) {
            result.add(Page.empty());
        }

        boolean overflow = cursor < all.size();
        if (overflow) {
            Page last = result.get(result.size() - 1);
            List<ReadingLine> shortened = new ArrayList<>(last.lines());
            if (!shortened.isEmpty()) {
                ReadingLine tail = shortened.get(shortened.size() - 1);
                shortened.set(shortened.size() - 1,
                        new ReadingLine(tail.text() + "…", tail.style()));
            }
            result.set(result.size() - 1, new Page(List.copyOf(shortened), fontSize, lineHeight));
        }
        return new LayoutAttempt(List.copyOf(result), overflow);
    }

    void render(GraphicsContext gc, double x, double y, double width, double height,
                boolean aiPending) {
        Page page = pages.get(Math.min(currentPage, pages.size() - 1));
        double currentY = y;
        gc.setTextAlign(TextAlignment.LEFT);
        for (ReadingLine line : page.lines()) {
            switch (line.style()) {
                case BODY -> {
                    gc.setFill(Color.web("#352d3c", 0.94));
                    gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, page.fontSize()));
                }
                case ADVICE -> {
                    gc.setFill(Color.web("#413642", 0.92));
                    gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, page.fontSize()));
                }
                case HEADING -> {
                    gc.setFill(Color.web("#70458a"));
                    gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, page.fontSize() + 1.0));
                }
                case SPACER -> { }
            }
            if (line.style() != LineStyle.SPACER && currentY <= y + height) {
                gc.fillText(line.text(), x, currentY);
            }
            currentY += page.lineHeight();
        }

        if (aiPending) {
            double pulse = (System.nanoTime() / 350_000_000L) % 4;
            String dots = ".".repeat((int) pulse);
            gc.setFill(Color.web("#71418b", 0.94));
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 12));
            gc.fillText("AI 正在解读" + dots, x, y + height - 16);
        }
    }

    boolean nextPage() {
        if (currentPage + 1 >= pages.size()) return false;
        currentPage++;
        return true;
    }

    boolean previousPage() {
        if (currentPage <= 0) return false;
        currentPage--;
        return true;
    }

    int currentPageNumber() {
        return currentPage + 1;
    }

    int pageCount() {
        return pages.size();
    }

    long layoutVersion() {
        return layoutVersion;
    }

    private List<String> wrap(String text, int charsPerLine) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        for (String paragraph : text.replace("\r", "").split("\n")) {
            String cleaned = paragraph.strip();
            if (cleaned.isEmpty()) continue;
            int start = 0;
            while (start < cleaned.length()) {
                int end = Math.min(cleaned.length(), start + charsPerLine);
                lines.add(cleaned.substring(start, end));
                start = end;
            }
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private enum LineStyle { BODY, ADVICE, HEADING, SPACER }

    private record ReadingLine(String text, LineStyle style) { }

    private record Page(List<ReadingLine> lines, double fontSize, double lineHeight) {
        private static Page empty() {
            return new Page(List.of(), FONT_SIZE, LINE_HEIGHT);
        }
    }

    private record LayoutAttempt(List<Page> pages, boolean overflow) { }
}
