package com.gesturegame.game;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔罗牌背、牌面边框与 78 张牌插画的独立绘制器。
 *
 * <p>复杂图案通过 {@link TarotRenderCache} 生成一次后复用；动态高亮仍在当前帧叠加。</p>
 */
final class TarotCardRenderer {

    private static final Color GOLD_SOFT = Color.web("#f3d79c");

    private final TarotRenderCache renderCache;
    private final TarotCardImageStore imageStore = new TarotCardImageStore();

    TarotCardRenderer(TarotRenderCache renderCache) {
        this.renderCache = renderCache;
    }

    void prepareBack() {
        imageStore.cardBack();
    }

    void prepare(TarotMeaning meaning) {
        imageStore.cardFront(meaning);
    }

    void drawCardBack(GraphicsContext gc, double x, double y, double w, double h,
                              double emphasis, boolean deckCard) {
        Image back = imageStore.cardBack();
        if (isReady(back)) {
            drawCardImage(gc, back, x, y, w, h, false);
        } else {
            String key = "back|" + deckCard;
            renderCache.drawCard(gc, key, x, y, w, h,
                    cachedGc -> paintCardBack(cachedGc, 0, 0, w, h, 0.0, deckCard));
        }

        if (emphasis > 0.01) {
            gc.setStroke(Color.web("#bcaeff", Math.min(0.92, 0.30 + emphasis * 0.42)));
            gc.setLineWidth(1.1 + emphasis * 1.35);
            gc.strokeRoundRect(x, y, w, h, 18, 18);
        }
    }

    private void paintCardBack(GraphicsContext gc, double x, double y, double w, double h,
                               double emphasis, boolean deckCard) {
        Paint backFill = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2D1B4E")),
                new Stop(0.5, Color.web("#1A0B2E")),
                new Stop(1, Color.web("#0A0A1A")));
        gc.setFill(backFill);
        gc.fillRoundRect(x, y, w, h, 18, 18);

        if (deckCard) {
            gc.setStroke(Color.web("#D4AF37", 0.16 + emphasis * 0.18));
            gc.setLineWidth(5.0 + emphasis * 2.0);
            gc.strokeRoundRect(x - 4, y - 4, w + 8, h + 8, 22, 22);
        }

        gc.setStroke(Color.web(deckCard ? "#D4AF37" : "#B8860B", 0.95));
        gc.setLineWidth(deckCard ? 1.8 + emphasis * 0.5 : 1.4 + emphasis);
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.web("#3A1F62", 0.70));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 8, y + 8, w - 16, h - 16, 14, 14);

        drawCenterFiligree(gc, x + w / 2.0, y + 18, 10, false);
        drawCenterFiligree(gc, x + w / 2.0, y + h - 18, 10, true);

        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double r = Math.min(w, h) * 0.19;
        drawHexagram(gc, cx, cy, r);

        gc.setStroke(Color.web("#D4AF37", 0.56));
        gc.strokeOval(cx - r * 1.32, cy - r * 1.32, r * 2.64, r * 2.64);
        gc.strokeOval(cx - r * 0.45, cy - r * 0.45, r * 0.9, r * 0.9);

        gc.setFill(Color.web("#D4AF37", 0.82));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(10, w * 0.08)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("ARCANA", cx, y + h - 28);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawHexagram(GraphicsContext gc, double cx, double cy, double r) {
        gc.setStroke(Color.web("#e2b86a"));
        gc.setLineWidth(1.7);

        double[] upX = new double[3];
        double[] upY = new double[3];
        double[] downX = new double[3];
        double[] downY = new double[3];
        for (int i = 0; i < 3; i++) {
            double upAngle = Math.toRadians(-90 + i * 120);
            double downAngle = Math.toRadians(90 + i * 120);
            upX[i] = cx + Math.cos(upAngle) * r;
            upY[i] = cy + Math.sin(upAngle) * r;
            downX[i] = cx + Math.cos(downAngle) * r;
            downY[i] = cy + Math.sin(downAngle) * r;
        }
        gc.strokePolygon(upX, upY, 3);
        gc.strokePolygon(downX, downY, 3);
    }

    void drawCardFront(GraphicsContext gc, double x, double y, double w, double h,
                               TarotGame.Card card, boolean active) {
        Image front = imageStore.cardFront(card.meaning);
        if (isReady(front)) {
            drawCardImage(gc, front, x, y, w, h, card.reversed);
        } else {
            String key = "front|" + card.meaning.family + '|' + card.meaning.number + '|'
                    + card.meaning.title + '|' + card.reversed + '|' + card.slotNote;
            renderCache.drawCard(gc, key, x, y, w, h,
                    cachedGc -> paintCardFront(cachedGc, 0, 0, w, h, card));
        }

        if (active) {
            gc.setStroke(Color.web("#d7fcf4", 0.96));
            gc.setLineWidth(2.4);
            gc.strokeRoundRect(x, y, w, h, 18, 18);
        }
    }

    private boolean isReady(Image image) {
        return image != null && !image.isError() && image.getProgress() >= 1.0;
    }

    private void drawCardImage(GraphicsContext gc, Image image,
                               double x, double y, double w, double h, boolean reversed) {
        double radius = Math.min(18.0, Math.min(w, h) * 0.08);
        gc.save();
        clipRoundedRect(gc, x, y, w, h, radius);
        if (reversed) {
            gc.translate(x + w / 2.0, y + h / 2.0);
            gc.rotate(180.0);
            drawImageCover(gc, image, -w / 2.0, -h / 2.0, w, h);
        } else {
            drawImageCover(gc, image, x, y, w, h);
        }
        gc.restore();

        gc.setStroke(Color.web("#b7a9ff", 0.72));
        gc.setLineWidth(1.15);
        gc.strokeRoundRect(x, y, w, h, radius * 2, radius * 2);
    }

    private void drawImageCover(GraphicsContext gc, Image image,
                                double x, double y, double w, double h) {
        double sourceWidth = image.getWidth();
        double sourceHeight = image.getHeight();
        double targetRatio = w / h;
        double sourceRatio = sourceWidth / sourceHeight;
        double sx = 0.0;
        double sy = 0.0;
        double sw = sourceWidth;
        double sh = sourceHeight;
        if (sourceRatio > targetRatio) {
            sw = sourceHeight * targetRatio;
            sx = (sourceWidth - sw) / 2.0;
        } else {
            sh = sourceWidth / targetRatio;
            sy = (sourceHeight - sh) / 2.0;
        }
        gc.drawImage(image, sx, sy, sw, sh, x, y, w, h);
    }

    private void clipRoundedRect(GraphicsContext gc, double x, double y,
                                 double w, double h, double radius) {
        double r = Math.min(radius, Math.min(w, h) / 2.0);
        gc.beginPath();
        gc.moveTo(x + r, y);
        gc.lineTo(x + w - r, y);
        gc.arcTo(x + w, y, x + w, y + r, r);
        gc.lineTo(x + w, y + h - r);
        gc.arcTo(x + w, y + h, x + w - r, y + h, r);
        gc.lineTo(x + r, y + h);
        gc.arcTo(x, y + h, x, y + h - r, r);
        gc.lineTo(x, y + r);
        gc.arcTo(x, y, x + r, y, r);
        gc.closePath();
        gc.clip();
    }

    private void paintCardFront(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        Paint frontFill = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2D1B4E")),
                new Stop(0.38, Color.web("#1A0B2E")),
                new Stop(1, Color.web("#0A0A1A")));
        gc.setFill(frontFill);
        gc.fillRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.web("#D4AF37"));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.web("#B8860B"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 8, y + 8, w - 16, h - 16, 14, 14);

        gc.setStroke(Color.web("#D4AF37", 0.30));
        gc.setLineWidth(0.9);
        gc.strokeLine(x + 22, y + 24, x + w - 22, y + 24);
        gc.strokeLine(x + 22, y + h - 24, x + w - 22, y + h - 24);

        gc.setStroke(Color.web("#B8860B", 0.42));
        gc.strokeRoundRect(x + 14, y + 36, w - 28, h - 72, 12, 12);

        gc.setFill(Color.web("#05050A", 0.92));
        gc.fillRoundRect(x + 18, y + 10, w - 36, 20, 8, 8);
        gc.setStroke(Color.web("#D4AF37", 0.36));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(x + 18, y + 10, w - 36, 20, 8, 8);

        drawCenterFiligree(gc, x + w / 2.0, y + 34, 9, false);
        drawCenterFiligree(gc, x + w / 2.0, y + h - 34, 9, true);

        gc.setFill(Color.web("#0A0A1A"));
        gc.fillRoundRect(x + 16, y + 40, w - 32, h - 112, 10, 10);

        gc.setFill(GOLD_SOFT);
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(10, w * 0.09)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(getDisplayArcanaIndex(card), x + w / 2.0, y + 24.5);

        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, Math.max(13, w * 0.12)));
        gc.fillText(card.meaning.title, x + w / 2.0, y + 38.5);

        gc.setFill(Color.web("#D4AF37", 0.86));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(9, w * 0.07)));
        String cornerRank = getCornerRank(card);
        gc.fillText(cornerRank, x + 20, y + 28);
        gc.fillText(cornerRank, x + w - 20, y + h - 14);

        String minorLabel = getMinorFamilyLabel(card);
        if (!minorLabel.isEmpty()) {
            gc.setFill(Color.web("#B8860B", 0.82));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, Math.max(8, w * 0.06)));
            gc.fillText(minorLabel, x + w / 2.0, y + 52);
        }

        gc.setFill(card.reversed ? Color.web("#FF4500") : Color.web("#DAA520"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(9, w * 0.078)));
        gc.fillText(card.reversed ? "REVERSED" : "UPRIGHT", x + w / 2.0, y + 64);

        drawCardIllustration(gc, x + 16, y + 40, w - 32, h - 112, card);

        gc.setFill(Color.web("#1A0B2E"));
        gc.fillRoundRect(x + 18, y + h - 64, w - 36, 30, 10, 10);
        gc.setStroke(Color.web("#D4AF37", 0.48));
        gc.strokeRoundRect(x + 18, y + h - 64, w - 36, 30, 10, 10);

        gc.setFill(Color.web("#FFF8DC"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, Math.max(9, w * 0.076)));
        drawWrappedTextCentered(gc, String.join(" · ", card.meaning.keywords), x + w / 2.0, y + h - 57, w - 42, 13, 2);

        gc.setFill(Color.web("#DAA520"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 10));
        drawWrappedTextCentered(gc, card.slotNote, x + w / 2.0, y + h - 24, w - 22, 15, 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private String getDisplayArcanaIndex(TarotGame.Card card) {
        if ("MAJOR ARCANA".equals(card.meaning.family)) {
            return card.meaning.number;
        }
        return getCornerRank(card);
    }

    private String getCornerRank(TarotGame.Card card) {
        switch (card.meaning.number) {
            case "PAGE":
                return "P";
            case "KNIGHT":
                return "Kn";
            case "QUEEN":
                return "Q";
            case "KING":
                return "K";
            default:
                return card.meaning.number;
        }
    }

    private String getMinorFamilyLabel(TarotGame.Card card) {
        if ("MAJOR ARCANA".equals(card.meaning.family)) {
            return "";
        }
        if (card.meaning.family.startsWith("CUPS")) {
            return "CUPS";
        }
        if (card.meaning.family.startsWith("SWORDS")) {
            return "SWORDS";
        }
        if (card.meaning.family.startsWith("WANDS")) {
            return "WANDS";
        }
        if (card.meaning.family.startsWith("PENTACLES")) {
            return "PENTACLES";
        }
        return "";
    }

    private void drawCardIllustration(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        Color auraColor = getAuraColor(card.meaning);
        Paint aura = new RadialGradient(
                0, 0, x + w / 2.0, y + h * 0.44, Math.max(w, h) * 0.48, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(auraColor.getRed(), auraColor.getGreen(), auraColor.getBlue(), card.reversed ? 0.28 : 0.42)),
                new Stop(0.45, Color.web("#2d1e39", 0.22)),
                new Stop(1, Color.TRANSPARENT)
        );
        gc.setFill(aura);
        gc.fillOval(x + w * 0.12, y + h * 0.06, w * 0.76, h * 0.76);

        gc.setStroke(Color.web("#8755b4", 0.40));
        gc.setLineWidth(1);
        gc.strokeOval(x + w * 0.18, y + h * 0.1, w * 0.64, h * 0.64);
        gc.strokeOval(x + w * 0.28, y + h * 0.2, w * 0.44, h * 0.44);

        drawCornerOrnament(gc, x + 14, y + 14, 1, 1);
        drawCornerOrnament(gc, x + w - 14, y + 14, -1, 1);
        drawCornerOrnament(gc, x + 14, y + h - 14, 1, -1);
        drawCornerOrnament(gc, x + w - 14, y + h - 14, -1, -1);

        if (!"MAJOR ARCANA".equals(card.meaning.family)) {
            drawMinorArcanaSymbol(gc, x, y, w, h, card);
            return;
        }

        switch (card.meaning.title) {
            case "愚者":
                drawFoolSymbol(gc, x, y, w, h);
                break;
            case "魔术师":
                drawMagicianSymbol(gc, x, y, w, h);
                break;
            case "女祭司":
                drawPriestessSymbol(gc, x, y, w, h);
                break;
            case "皇后":
                drawEmpressSymbol(gc, x, y, w, h);
                break;
            case "皇帝":
                drawEmperorSymbol(gc, x, y, w, h);
                break;
            case "教皇":
                drawHierophantSymbol(gc, x, y, w, h);
                break;
            case "恋人":
                drawLoversSymbol(gc, x, y, w, h);
                break;
            case "战车":
                drawChariotSymbol(gc, x, y, w, h);
                break;
            case "力量":
                drawStrengthSymbol(gc, x, y, w, h);
                break;
            case "正义":
                drawJusticeSymbol(gc, x, y, w, h);
                break;
            case "倒吊人":
                drawHangedManSymbol(gc, x, y, w, h);
                break;
            case "隐士":
                drawHermitSymbol(gc, x, y, w, h);
                break;
            case "死神":
                drawDeathSymbol(gc, x, y, w, h);
                break;
            case "节制":
                drawTemperanceSymbol(gc, x, y, w, h);
                break;
            case "恶魔":
                drawDevilSymbol(gc, x, y, w, h);
                break;
            case "高塔":
                drawTowerSymbol(gc, x, y, w, h);
                break;
            case "审判":
                drawJudgementSymbol(gc, x, y, w, h);
                break;
            case "世界":
                drawWorldSymbol(gc, x, y, w, h);
                break;
            case "太阳":
                drawSunSymbol(gc, x, y, w, h);
                break;
            case "月亮":
                drawMoonSymbol(gc, x, y, w, h);
                break;
            case "星星":
                drawStarSymbol(gc, x, y, w, h);
                break;
            case "命运之轮":
                drawWheelSymbol(gc, x, y, w, h);
                break;
            default:
                drawGeneralTarotSymbol(gc, x, y, w, h, card);
                break;
        }
    }

    private Color getAuraColor(TarotMeaning meaning) {
        if (meaning.family.startsWith("CUPS")) {
            return Color.web("#3a6fb5");
        }
        if (meaning.family.startsWith("SWORDS")) {
            return Color.web("#8fa4c9");
        }
        if (meaning.family.startsWith("WANDS")) {
            return Color.web("#b86232");
        }
        if (meaning.family.startsWith("PENTACLES")) {
            return Color.web("#7d7440");
        }
        return Color.web("#5b3b89");
    }

    private void drawMinorArcanaSymbol(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        if (drawSpecialMinorArcana(gc, x, y, w, h, card)) {
            gc.setFill(Color.web("#d8c39d", 0.9));
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 10));
            drawWrappedTextCentered(gc, card.meaning.family.split(" · ")[1], cx, y + h - 84, w - 20, 12, 1);
            return;
        }
        int pipCount = getMinorPipCount(card.meaning.number);
        if (pipCount > 0) {
            drawMinorArcanaPips(gc, x, y, w, h, card, pipCount);
        } else if (isCourtCard(card.meaning.number)) {
            drawCourtArcanaSymbol(gc, x, y, w, h, card);
        } else {
            drawSuitGlyph(gc, card.meaning.family, cx, cy, 18);
        }

        gc.setFill(Color.web("#d8c39d", 0.9));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 10));
        drawWrappedTextCentered(gc, card.meaning.family.split(" · ")[1], cx, y + h - 84, w - 20, 12, 1);
    }

    private boolean drawSpecialMinorArcana(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        if ("圣杯首牌".equals(card.meaning.title)) {
            drawAceOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯八".equals(card.meaning.title)) {
            drawEightOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯九".equals(card.meaning.title)) {
            drawNineOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯六".equals(card.meaning.title)) {
            drawSixOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯四".equals(card.meaning.title)) {
            drawFourOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯三".equals(card.meaning.title)) {
            drawThreeOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯二".equals(card.meaning.title)) {
            drawTwoOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯五".equals(card.meaning.title)) {
            drawFiveOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯七".equals(card.meaning.title)) {
            drawSevenOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯侍者".equals(card.meaning.title)) {
            drawPageOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯骑士".equals(card.meaning.title)) {
            drawKnightOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯皇后".equals(card.meaning.title)) {
            drawQueenOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯国王".equals(card.meaning.title)) {
            drawKingOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑二".equals(card.meaning.title)) {
            drawTwoOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑四".equals(card.meaning.title)) {
            drawFourOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑首牌".equals(card.meaning.title)) {
            drawAceOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑六".equals(card.meaning.title)) {
            drawSixOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑五".equals(card.meaning.title)) {
            drawFiveOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑七".equals(card.meaning.title)) {
            drawSevenOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑八".equals(card.meaning.title)) {
            drawEightOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑三".equals(card.meaning.title)) {
            drawThreeOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑九".equals(card.meaning.title)) {
            drawNineOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑十".equals(card.meaning.title)) {
            drawTenOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑侍者".equals(card.meaning.title)) {
            drawPageOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑骑士".equals(card.meaning.title)) {
            drawKnightOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑皇后".equals(card.meaning.title)) {
            drawQueenOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("宝剑国王".equals(card.meaning.title)) {
            drawKingOfSwordsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖六".equals(card.meaning.title)) {
            drawSixOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖四".equals(card.meaning.title)) {
            drawFourOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖三".equals(card.meaning.title)) {
            drawThreeOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖首牌".equals(card.meaning.title)) {
            drawAceOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖二".equals(card.meaning.title)) {
            drawTwoOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖五".equals(card.meaning.title)) {
            drawFiveOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖七".equals(card.meaning.title)) {
            drawSevenOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖十".equals(card.meaning.title)) {
            drawTenOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("圣杯十".equals(card.meaning.title)) {
            drawTenOfCupsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖八".equals(card.meaning.title)) {
            drawEightOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖九".equals(card.meaning.title)) {
            drawNineOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖侍者".equals(card.meaning.title)) {
            drawPageOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖骑士".equals(card.meaning.title)) {
            drawKnightOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖皇后".equals(card.meaning.title)) {
            drawQueenOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("权杖国王".equals(card.meaning.title)) {
            drawKingOfWandsSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币五".equals(card.meaning.title)) {
            drawFiveOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币六".equals(card.meaning.title)) {
            drawSixOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币七".equals(card.meaning.title)) {
            drawSevenOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币首牌".equals(card.meaning.title)) {
            drawAceOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币二".equals(card.meaning.title)) {
            drawTwoOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币三".equals(card.meaning.title)) {
            drawThreeOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币四".equals(card.meaning.title)) {
            drawFourOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币八".equals(card.meaning.title)) {
            drawEightOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币九".equals(card.meaning.title)) {
            drawNineOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币十".equals(card.meaning.title)) {
            drawTenOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币侍者".equals(card.meaning.title)) {
            drawPageOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币骑士".equals(card.meaning.title)) {
            drawKnightOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币皇后".equals(card.meaning.title)) {
            drawQueenOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        if ("星币国王".equals(card.meaning.title)) {
            drawKingOfPentaclesSymbol(gc, x, y, w, h);
            return true;
        }
        return false;
    }

    private int getMinorPipCount(String number) {
        switch (number) {
            case "ACE":
                return 1;
            case "II":
                return 2;
            case "III":
                return 3;
            case "IV":
                return 4;
            case "V":
                return 5;
            case "VI":
                return 6;
            case "VII":
                return 7;
            case "VIII":
                return 8;
            case "IX":
                return 9;
            case "X":
                return 10;
            default:
                return 0;
        }
    }

    private boolean isCourtCard(String number) {
        return "PAGE".equals(number) || "KNIGHT".equals(number) || "QUEEN".equals(number) || "KING".equals(number);
    }

    private void drawMinorArcanaPips(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card, int pipCount) {
        double[][] pattern = getPipPattern(pipCount);
        double centerX = x + w / 2.0;
        double centerY = y + h * 0.42;
        double baseSize = pipCount >= 9 ? 8.5 : pipCount >= 6 ? 9.5 : pipCount >= 4 ? 11 : 13;

        gc.setStroke(Color.web("#8b5cb2", 0.24));
        gc.setLineWidth(1);
        gc.strokeOval(centerX - w * 0.22, centerY - h * 0.22, w * 0.44, h * 0.44);

        for (double[] point : pattern) {
            double px = x + w * point[0];
            double py = y + h * point[1];
            drawSuitGlyph(gc, card.meaning.family, px, py, baseSize);
        }
    }

    private double[][] getPipPattern(int count) {
        switch (count) {
            case 1:
                return new double[][]{{0.50, 0.42}};
            case 2:
                return new double[][]{{0.50, 0.28}, {0.50, 0.56}};
            case 3:
                return new double[][]{{0.50, 0.24}, {0.38, 0.42}, {0.62, 0.42}};
            case 4:
                return new double[][]{{0.36, 0.28}, {0.64, 0.28}, {0.36, 0.56}, {0.64, 0.56}};
            case 5:
                return new double[][]{{0.36, 0.27}, {0.64, 0.27}, {0.50, 0.42}, {0.36, 0.57}, {0.64, 0.57}};
            case 6:
                return new double[][]{{0.34, 0.24}, {0.66, 0.24}, {0.34, 0.42}, {0.66, 0.42}, {0.34, 0.60}, {0.66, 0.60}};
            case 7:
                return new double[][]{{0.50, 0.18}, {0.34, 0.30}, {0.66, 0.30}, {0.34, 0.44}, {0.66, 0.44}, {0.34, 0.58}, {0.66, 0.58}};
            case 8:
                return new double[][]{{0.36, 0.20}, {0.64, 0.20}, {0.36, 0.34}, {0.64, 0.34}, {0.36, 0.48}, {0.64, 0.48}, {0.36, 0.62}, {0.64, 0.62}};
            case 9:
                return new double[][]{{0.50, 0.16}, {0.34, 0.28}, {0.66, 0.28}, {0.34, 0.40}, {0.66, 0.40}, {0.50, 0.42}, {0.34, 0.54}, {0.66, 0.54}, {0.50, 0.66}};
            case 10:
                return new double[][]{{0.36, 0.16}, {0.64, 0.16}, {0.36, 0.28}, {0.64, 0.28}, {0.36, 0.40}, {0.64, 0.40}, {0.36, 0.52}, {0.64, 0.52}, {0.36, 0.64}, {0.64, 0.64}};
            default:
                return new double[][]{{0.50, 0.42}};
        }
    }

    private void drawCourtArcanaSymbol(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.40;

        gc.setStroke(Color.web("#a874da", 0.40));
        gc.setLineWidth(1.1);
        gc.strokeRoundRect(x + w * 0.26, y + h * 0.18, w * 0.48, h * 0.40, 18, 18);
        gc.strokeOval(cx - w * 0.17, cy - h * 0.16, w * 0.34, h * 0.34);

        drawSuitGlyph(gc, card.meaning.family, cx, cy, 16);

        if ("PAGE".equals(card.meaning.number)) {
            gc.setStroke(Color.web("#f0ca79"));
            gc.strokeLine(cx - 12, cy - 28, cx + 12, cy - 28);
            gc.strokeLine(cx - 8, cy - 28, cx, cy - 38);
            gc.strokeLine(cx + 8, cy - 28, cx, cy - 38);
        } else if ("KNIGHT".equals(card.meaning.number)) {
            gc.setStroke(Color.web("#f0ca79"));
            gc.strokeLine(cx - 18, cy + 24, cx + 18, cy + 24);
            gc.strokeLine(cx - 18, cy + 24, cx - 6, cy + 12);
            gc.strokeLine(cx + 18, cy + 24, cx + 6, cy + 12);
        } else if ("QUEEN".equals(card.meaning.number)) {
            drawEightPointStar(gc, cx, cy - 30, 10, 4);
        } else if ("KING".equals(card.meaning.number)) {
            gc.setStroke(Color.web("#f0ca79"));
            gc.strokeOval(cx - 20, cy - 34, 40, 16);
            gc.strokeLine(cx - 12, cy - 18, cx - 6, cy - 30);
            gc.strokeLine(cx, cy - 18, cx, cy - 32);
            gc.strokeLine(cx + 12, cy - 18, cx + 6, cy - 30);
        }
    }

    private void drawThreeOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setFill(Color.web("#8f2243", 0.28));
        gc.fillOval(cx - 20, cy - 22, 40, 44);
        gc.setStroke(Color.web("#e7a0b8"));
        gc.setLineWidth(1.8);
        gc.strokeOval(cx - 18, cy - 20, 36, 40);
        gc.strokeLine(cx - 22, cy - 18, cx + 22, cy + 18);
        gc.strokeLine(cx + 22, cy - 18, cx - 22, cy + 18);
        drawSwordIcon(gc, cx, cy - 2, 18);
        gc.setStroke(Color.web("#d7dff0"));
        gc.strokeLine(cx - 20, cy - 14, cx + 16, cy + 20);
        gc.strokeLine(cx + 20, cy - 14, cx - 16, cy + 20);
    }

    private void drawFourOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.82));
        gc.setLineWidth(1.35);
        double left = x + w * 0.28;
        double right = x + w * 0.72;
        double[] ys = {y + h * 0.24, y + h * 0.34, y + h * 0.44, y + h * 0.54};
        for (double swordY : ys) {
            gc.strokeLine(left, swordY, right, swordY);
        }
        gc.setStroke(Color.web("#8b5cb2", 0.38));
        gc.strokeLine(x + w * 0.34, y + h * 0.64, x + w * 0.66, y + h * 0.64);
    }

    private void drawAceOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        drawSwordIcon(gc, cx, cy, 22);
        gc.setStroke(Color.web("#f0ca79", 0.72));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx - 14, cy - 34, 28, 12);
        gc.strokeLine(cx - 10, cy - 22, cx - 2, cy - 30);
        gc.strokeLine(cx + 10, cy - 22, cx + 2, cy - 30);
    }

    private void drawFiveOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.82));
        gc.setLineWidth(1.35);
        drawSwordIcon(gc, x + w * 0.40, y + h * 0.38, 12);
        drawSwordIcon(gc, x + w * 0.60, y + h * 0.38, 12);
        gc.strokeLine(x + w * 0.30, y + h * 0.58, x + w * 0.46, y + h * 0.66);
        gc.strokeLine(x + w * 0.54, y + h * 0.66, x + w * 0.70, y + h * 0.58);
        gc.strokeLine(x + w * 0.50, y + h * 0.54, x + w * 0.50, y + h * 0.70);
    }

    private void drawSixOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#89b9ff", 0.42));
        gc.setLineWidth(1.1);
        gc.strokeArc(x + w * 0.28, y + h * 0.58, w * 0.44, h * 0.12, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#d7dff0", 0.82));
        gc.setLineWidth(1.25);
        for (int i = 0; i < 6; i++) {
            double px = x + w * (0.34 + i * 0.06);
            gc.strokeLine(px, y + h * 0.22, px, y + h * 0.58);
        }
    }

    private void drawTwoOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#d7dff0"));
        gc.setLineWidth(1.6);
        gc.strokeLine(cx - 22, cy - 18, cx + 22, cy + 18);
        gc.strokeLine(cx + 22, cy - 18, cx - 22, cy + 18);
        gc.strokeOval(cx - 7, cy - 30, 14, 10);
        gc.setStroke(Color.web("#8b5cb2", 0.42));
        gc.strokeArc(cx - 24, cy + 10, 48, 20, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawTwoOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        drawCupIcon(gc, cx - 16, cy + 6, 12);
        drawCupIcon(gc, cx + 16, cy + 6, 12);
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.4);
        gc.strokeLine(cx - 6, cy - 4, cx + 6, cy - 4);
        gc.strokeLine(cx - 10, cy - 18, cx, cy - 8);
        gc.strokeLine(cx + 10, cy - 18, cx, cy - 8);
        gc.strokeOval(cx - 10, cy - 30, 20, 12);
    }

    private void drawFourOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        drawCupIcon(gc, x + w * 0.34, y + h * 0.50, 9.5);
        drawCupIcon(gc, x + w * 0.50, y + h * 0.50, 9.5);
        drawCupIcon(gc, x + w * 0.66, y + h * 0.50, 9.5);
        drawCupIcon(gc, x + w * 0.50, y + h * 0.24, 10.5);
        gc.setStroke(Color.web("#9f7dd2", 0.42));
        gc.setLineWidth(1.0);
        gc.strokeArc(x + w * 0.28, y + h * 0.18, w * 0.44, h * 0.18, 200, 140, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawAceOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        drawCupIcon(gc, cx, cy, 14);
        gc.setStroke(Color.web("#89b9ff", 0.55));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx, cy - 28, cx, cy - 40);
        gc.strokeOval(cx - 6, cy - 48, 12, 12);
        gc.strokeLine(cx, cy + 24, cx, cy + 36);
    }

    private void drawSixOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] cups = {
                {0.38, 0.24}, {0.62, 0.24},
                {0.38, 0.40}, {0.62, 0.40},
                {0.38, 0.56}, {0.62, 0.56}
        };
        for (double[] p : cups) {
            drawCupIcon(gc, x + w * p[0], y + h * p[1], 8.6);
        }
        gc.setStroke(Color.web("#7fbf86", 0.50));
        gc.setLineWidth(1.2);
        gc.strokeLine(x + w * 0.50, y + h * 0.60, x + w * 0.50, y + h * 0.70);
        gc.strokeLine(x + w * 0.50, y + h * 0.62, x + w * 0.44, y + h * 0.56);
        gc.strokeLine(x + w * 0.50, y + h * 0.62, x + w * 0.56, y + h * 0.56);
    }

    private void drawNineOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] cups = {
                {0.34, 0.22}, {0.50, 0.22}, {0.66, 0.22},
                {0.34, 0.38}, {0.50, 0.38}, {0.66, 0.38},
                {0.34, 0.54}, {0.50, 0.54}, {0.66, 0.54}
        };
        for (double[] p : cups) {
            drawCupIcon(gc, x + w * p[0], y + h * p[1], 7.8);
        }
        gc.setStroke(Color.web("#8b5cb2", 0.38));
        gc.setLineWidth(1.1);
        gc.strokeArc(x + w * 0.30, y + h * 0.64, w * 0.40, h * 0.08, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawEightOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] cups = {
                {0.34, 0.28}, {0.50, 0.28}, {0.66, 0.28},
                {0.34, 0.44}, {0.50, 0.44}, {0.66, 0.44},
                {0.42, 0.60}, {0.58, 0.60}
        };
        for (double[] p : cups) {
            drawCupIcon(gc, x + w * p[0], y + h * p[1], 8.2);
        }
        gc.setStroke(Color.web("#9f7dd2", 0.42));
        gc.setLineWidth(1.0);
        gc.strokeArc(x + w * 0.58, y + h * 0.18, w * 0.12, h * 0.08, 40, 260, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawThreeOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        drawCupIcon(gc, x + w * 0.35, y + h * 0.46, 10.5);
        drawCupIcon(gc, x + w * 0.50, y + h * 0.32, 10.5);
        drawCupIcon(gc, x + w * 0.65, y + h * 0.46, 10.5);
        gc.setStroke(Color.web("#f0ca79", 0.7));
        gc.setLineWidth(1.2);
        gc.strokeArc(x + w * 0.30, y + h * 0.18, w * 0.40, h * 0.24, 210, 120, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(x + w * 0.34, y + h * 0.62, x + w * 0.66, y + h * 0.62);
    }

    private void drawFiveOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] upright = {
                {0.34, 0.28}, {0.50, 0.28}, {0.66, 0.28}
        };
        double[][] fallen = {
                {0.42, 0.54}, {0.58, 0.54}
        };
        for (double[] p : upright) {
            drawCupIcon(gc, x + w * p[0], y + h * p[1], 8.5);
        }
        gc.setStroke(Color.web("#89b9ff", 0.55));
        gc.setLineWidth(1.3);
        for (double[] p : fallen) {
            double px = x + w * p[0];
            double py = y + h * p[1];
            gc.save();
            gc.translate(px, py);
            gc.rotate(-55);
            drawCupIcon(gc, 0, 0, 8.5);
            gc.restore();
            gc.strokeLine(px - 9, py + 10, px + 11, py + 14);
        }
    }

    private void drawSevenOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] cups = {
                {0.32, 0.22}, {0.50, 0.20}, {0.68, 0.22},
                {0.28, 0.38}, {0.50, 0.36}, {0.72, 0.38},
                {0.50, 0.56}
        };
        for (double[] p : cups) {
            drawCupIcon(gc, x + w * p[0], y + h * p[1], 8.2);
        }
        gc.setStroke(Color.web("#9f7dd2", 0.45));
        gc.setLineWidth(1.0);
        gc.strokeArc(x + w * 0.22, y + h * 0.12, w * 0.56, h * 0.50, 190, 160, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawTenOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double arcY = y + h * 0.24;
        gc.setStroke(Color.web("#84aef7", 0.24));
        gc.setLineWidth(1.2);
        gc.strokeArc(cx - 34, arcY - 4, 68, 44, 200, 140, javafx.scene.shape.ArcType.OPEN);
        for (int i = 0; i < 10; i++) {
            double t = Math.toRadians(200 - (i * (140.0 / 9.0)));
            double px = cx + Math.cos(t) * 34;
            double py = arcY + Math.sin(t) * 18;
            drawCupIcon(gc, px, py, 7.5);
        }
        gc.setStroke(Color.web("#7d5bb6", 0.55));
        gc.strokeLine(cx - 30, y + h * 0.62, cx + 30, y + h * 0.62);
        gc.strokeLine(cx - 20, y + h * 0.62, cx - 28, y + h * 0.54);
        gc.strokeLine(cx + 20, y + h * 0.62, cx + 28, y + h * 0.54);
    }

    private void drawPageOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#a874da", 0.34));
        gc.setLineWidth(1.1);
        gc.strokeOval(cx - 20, cy - 28, 40, 56);
        gc.setStroke(Color.web("#f0ca79", 0.78));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 6, cy - 28, 12, 12);
        gc.strokeLine(cx, cy - 16, cx, cy + 10);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        gc.strokeLine(cx, cy + 10, cx - 10, cy + 26);
        gc.strokeLine(cx, cy + 10, cx + 10, cy + 26);
        drawCupIcon(gc, cx + 14, cy - 2, 8.8);
        gc.setStroke(Color.web("#89b9ff", 0.58));
        gc.strokeLine(cx + 12, cy - 12, cx + 20, cy - 8);
        gc.strokeLine(cx + 16, cy - 6, cx + 12, cy - 1);
        gc.strokeArc(cx - 16, cy + 24, 32, 10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawKnightOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.76));
        gc.setLineWidth(1.4);
        gc.strokeArc(cx - 24, cy - 6, 30, 24, 70, 220, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 10, cy + 8, cx + 12, cy + 8);
        gc.strokeLine(cx + 12, cy + 8, cx + 20, cy - 4);
        gc.strokeLine(cx - 4, cy + 8, cx - 10, cy + 24);
        gc.strokeLine(cx + 8, cy + 8, cx + 2, cy + 24);
        drawCupIcon(gc, cx + 18, cy - 12, 8.2);
        gc.setStroke(Color.web("#a874da", 0.34));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + w * 0.26, y + h * 0.62, x + w * 0.74, y + h * 0.62);
        gc.strokeArc(x + w * 0.36, y + h * 0.58, w * 0.28, h * 0.10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawQueenOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#a874da", 0.36));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(cx - 18, cy - 6, 36, 32, 14, 14);
        gc.setStroke(Color.web("#f0ca79", 0.78));
        gc.setLineWidth(1.3);
        drawEightPointStar(gc, cx, cy - 30, 9, 4);
        gc.strokeOval(cx - 6, cy - 22, 12, 12);
        gc.strokeLine(cx, cy - 10, cx, cy + 8);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        drawCupIcon(gc, cx, cy + 8, 10.0);
        gc.setStroke(Color.web("#89b9ff", 0.42));
        gc.strokeArc(cx - 18, cy + 20, 36, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawKingOfCupsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 18, cy - 30, 36, 14);
        gc.strokeLine(cx - 10, cy - 16, cx - 4, cy - 28);
        gc.strokeLine(cx, cy - 16, cx, cy - 30);
        gc.strokeLine(cx + 10, cy - 16, cx + 4, cy - 28);
        gc.strokeOval(cx - 7, cy - 14, 14, 14);
        gc.strokeLine(cx, cy, cx, cy + 20);
        gc.strokeLine(cx - 11, cy + 4, cx + 11, cy + 4);
        drawCupIcon(gc, cx + 16, cy + 6, 8.8);
        gc.setStroke(Color.web("#a874da", 0.34));
        gc.strokeLine(x + w * 0.32, y + h * 0.62, x + w * 0.68, y + h * 0.62);
        gc.strokeArc(x + w * 0.38, y + h * 0.58, w * 0.24, h * 0.10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawPageOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 20, cy - 30, 40, 58);
        gc.setStroke(Color.web("#d7dff0", 0.86));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 6, cy - 28, 12, 12);
        gc.strokeLine(cx, cy - 16, cx, cy + 10);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        gc.strokeLine(cx, cy + 10, cx - 10, cy + 26);
        gc.strokeLine(cx, cy + 10, cx + 10, cy + 26);
        gc.save();
        gc.translate(cx + 16, cy - 2);
        gc.rotate(18);
        drawSwordIcon(gc, 0, 0, 8.8);
        gc.restore();
        gc.setStroke(Color.web("#f0ca79", 0.58));
        gc.strokeLine(cx - 8, cy - 30, cx, cy - 38);
        gc.strokeLine(cx + 8, cy - 30, cx, cy - 38);
    }

    private void drawKnightOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#d7dff0", 0.86));
        gc.setLineWidth(1.35);
        gc.strokeArc(cx - 22, cy - 6, 28, 22, 70, 220, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 8, cy + 8, cx + 12, cy + 8);
        gc.strokeLine(cx + 12, cy + 8, cx + 20, cy - 4);
        gc.strokeLine(cx - 2, cy + 8, cx - 10, cy + 24);
        gc.strokeLine(cx + 8, cy + 8, cx, cy + 24);
        gc.save();
        gc.translate(cx + 16, cy - 10);
        gc.rotate(28);
        drawSwordIcon(gc, 0, 0, 10.5);
        gc.restore();
        gc.setStroke(Color.web("#89b9ff", 0.42));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + w * 0.28, y + h * 0.62, x + w * 0.72, y + h * 0.62);
        gc.strokeLine(x + w * 0.34, y + h * 0.58, x + w * 0.42, y + h * 0.52);
        gc.strokeLine(x + w * 0.66, y + h * 0.58, x + w * 0.58, y + h * 0.52);
    }

    private void drawQueenOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(cx - 18, cy - 6, 36, 32, 14, 14);
        gc.setStroke(Color.web("#d7dff0", 0.86));
        gc.setLineWidth(1.3);
        drawEightPointStar(gc, cx, cy - 30, 9, 4);
        gc.strokeOval(cx - 6, cy - 22, 12, 12);
        gc.strokeLine(cx, cy - 10, cx, cy + 8);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        drawSwordIcon(gc, cx + 12, cy + 4, 8.5);
        gc.setStroke(Color.web("#f0ca79", 0.56));
        gc.strokeArc(cx - 16, cy + 20, 32, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawKingOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#d7dff0", 0.88));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 18, cy - 30, 36, 14);
        gc.strokeLine(cx - 10, cy - 16, cx - 4, cy - 28);
        gc.strokeLine(cx, cy - 16, cx, cy - 30);
        gc.strokeLine(cx + 10, cy - 16, cx + 4, cy - 28);
        gc.strokeOval(cx - 7, cy - 14, 14, 14);
        gc.strokeLine(cx, cy, cx, cy + 20);
        gc.strokeLine(cx - 11, cy + 4, cx + 11, cy + 4);
        drawSwordIcon(gc, cx + 14, cy + 4, 9.0);
        gc.setStroke(Color.web("#8b5cb2", 0.36));
        gc.strokeLine(x + w * 0.32, y + h * 0.62, x + w * 0.68, y + h * 0.62);
        gc.setStroke(Color.web("#f0ca79", 0.58));
        gc.strokeLine(cx - 12, cy - 34, cx, cy - 42);
        gc.strokeLine(cx + 12, cy - 34, cx, cy - 42);
    }

    private void drawPageOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.32));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 20, cy - 30, 40, 58);
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 6, cy - 28, 12, 12);
        gc.strokeLine(cx, cy - 16, cx, cy + 10);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        gc.strokeLine(cx, cy + 10, cx - 10, cy + 26);
        gc.strokeLine(cx, cy + 10, cx + 10, cy + 26);
        gc.save();
        gc.translate(cx + 12, cy + 2);
        gc.rotate(-18);
        drawWandIcon(gc, 0, 0, 8.8);
        gc.restore();
        gc.setStroke(Color.web("#ffca77", 0.62));
        gc.strokeLine(cx - 8, cy - 30, cx, cy - 38);
        gc.strokeLine(cx + 8, cy - 30, cx, cy - 38);
    }

    private void drawKnightOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.4);
        gc.strokeArc(cx - 24, cy - 8, 30, 24, 70, 220, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 8, cy + 8, cx + 12, cy + 8);
        gc.strokeLine(cx + 12, cy + 8, cx + 20, cy - 4);
        gc.strokeLine(cx - 2, cy + 8, cx - 10, cy + 24);
        gc.strokeLine(cx + 8, cy + 8, cx, cy + 24);
        gc.save();
        gc.translate(cx + 16, cy - 8);
        gc.rotate(-8);
        drawWandIcon(gc, 0, 0, 10.4);
        gc.restore();
        gc.setStroke(Color.web("#7fbf86", 0.48));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + w * 0.30, y + h * 0.62, x + w * 0.70, y + h * 0.62);
        gc.strokeLine(x + w * 0.44, y + h * 0.56, x + w * 0.36, y + h * 0.48);
        gc.strokeLine(x + w * 0.56, y + h * 0.56, x + w * 0.64, y + h * 0.48);
    }

    private void drawQueenOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(cx - 18, cy - 6, 36, 32, 14, 14);
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.3);
        drawEightPointStar(gc, cx, cy - 30, 9, 4);
        gc.strokeOval(cx - 6, cy - 22, 12, 12);
        gc.strokeLine(cx, cy - 10, cx, cy + 8);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        drawWandIcon(gc, cx + 10, cy + 4, 8.8);
        gc.setStroke(Color.web("#ffca77", 0.60));
        gc.strokeArc(cx - 16, cy + 20, 32, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#7fbf86", 0.50));
        gc.strokeLine(cx + 2, cy + 12, cx + 10, cy + 18);
    }

    private void drawKingOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.86));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 18, cy - 30, 36, 14);
        gc.strokeLine(cx - 10, cy - 16, cx - 4, cy - 28);
        gc.strokeLine(cx, cy - 16, cx, cy - 30);
        gc.strokeLine(cx + 10, cy - 16, cx + 4, cy - 28);
        gc.strokeOval(cx - 7, cy - 14, 14, 14);
        gc.strokeLine(cx, cy, cx, cy + 20);
        gc.strokeLine(cx - 11, cy + 4, cx + 11, cy + 4);
        gc.save();
        gc.translate(cx + 14, cy + 4);
        gc.rotate(-10);
        drawWandIcon(gc, 0, 0, 9.4);
        gc.restore();
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.strokeLine(x + w * 0.32, y + h * 0.62, x + w * 0.68, y + h * 0.62);
        gc.setStroke(Color.web("#ffca77", 0.62));
        gc.strokeLine(cx - 12, cy - 34, cx, cy - 42);
        gc.strokeLine(cx + 12, cy - 34, cx, cy - 42);
    }

    private void drawPageOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.32));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 20, cy - 30, 40, 58);
        gc.setStroke(Color.web("#f0ca79", 0.80));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 6, cy - 28, 12, 12);
        gc.strokeLine(cx, cy - 16, cx, cy + 10);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        gc.strokeLine(cx, cy + 10, cx - 10, cy + 26);
        gc.strokeLine(cx, cy + 10, cx + 10, cy + 26);
        drawPentacleIcon(gc, cx + 14, cy - 2, 8.4);
        gc.setStroke(Color.web("#7fbf86", 0.52));
        gc.strokeArc(cx - 16, cy + 22, 32, 10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawKnightOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.35);
        gc.strokeArc(cx - 22, cy - 6, 28, 22, 70, 220, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 8, cy + 8, cx + 12, cy + 8);
        gc.strokeLine(cx + 12, cy + 8, cx + 20, cy - 4);
        gc.strokeLine(cx - 2, cy + 8, cx - 10, cy + 24);
        gc.strokeLine(cx + 8, cy + 8, cx, cy + 24);
        drawPentacleIcon(gc, cx + 16, cy - 10, 9.0);
        gc.setStroke(Color.web("#9a7b49", 0.58));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + w * 0.30, y + h * 0.62, x + w * 0.70, y + h * 0.62);
        gc.setStroke(Color.web("#7fbf86", 0.44));
        gc.strokeArc(x + w * 0.38, y + h * 0.58, w * 0.24, h * 0.10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawQueenOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(cx - 18, cy - 6, 36, 32, 14, 14);
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.3);
        drawEightPointStar(gc, cx, cy - 30, 9, 4);
        gc.strokeOval(cx - 6, cy - 22, 12, 12);
        gc.strokeLine(cx, cy - 10, cx, cy + 8);
        gc.strokeLine(cx - 10, cy - 2, cx + 10, cy - 2);
        drawPentacleIcon(gc, cx + 2, cy + 8, 9.2);
        gc.setStroke(Color.web("#7fbf86", 0.54));
        gc.strokeArc(cx - 18, cy + 20, 36, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx + 12, cy + 10, cx + 18, cy + 18);
    }

    private void drawKingOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.86));
        gc.setLineWidth(1.3);
        gc.strokeOval(cx - 18, cy - 30, 36, 14);
        gc.strokeLine(cx - 10, cy - 16, cx - 4, cy - 28);
        gc.strokeLine(cx, cy - 16, cx, cy - 30);
        gc.strokeLine(cx + 10, cy - 16, cx + 4, cy - 28);
        gc.strokeOval(cx - 7, cy - 14, 14, 14);
        gc.strokeLine(cx, cy, cx, cy + 20);
        gc.strokeLine(cx - 11, cy + 4, cx + 11, cy + 4);
        drawPentacleIcon(gc, cx + 14, cy + 4, 9.2);
        gc.setStroke(Color.web("#9a7b49", 0.60));
        gc.strokeLine(x + w * 0.32, y + h * 0.62, x + w * 0.68, y + h * 0.62);
        gc.setStroke(Color.web("#7fbf86", 0.50));
        gc.strokeLine(cx - 12, cy - 34, cx, cy - 42);
        gc.strokeLine(cx + 12, cy - 34, cx, cy - 42);
    }

    private void drawNineOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.82));
        gc.setLineWidth(1.4);
        for (int i = 0; i < 9; i++) {
            double py = y + h * (0.18 + i * 0.052);
            gc.strokeLine(x + w * 0.26, py, x + w * 0.74, py);
        }
        gc.setStroke(Color.web("#8f2243", 0.65));
        gc.strokeOval(x + w * 0.38, y + h * 0.50, w * 0.24, h * 0.12);
        gc.strokeLine(x + w * 0.50, y + h * 0.56, x + w * 0.50, y + h * 0.66);
    }

    private void drawTenOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.84));
        gc.setLineWidth(1.3);
        double baseY = y + h * 0.60;
        gc.strokeLine(x + w * 0.28, baseY, x + w * 0.72, baseY);
        for (int i = 0; i < 10; i++) {
            double px = x + w * (0.30 + (i % 5) * 0.10);
            double py = y + h * (0.22 + (i / 5) * 0.16);
            gc.strokeLine(px, py, px, py + h * 0.32);
        }
    }

    private void drawSevenOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.78));
        gc.setLineWidth(1.2);
        for (int i = 0; i < 5; i++) {
            double px = x + w * (0.30 + i * 0.09);
            gc.strokeLine(px, y + h * 0.18, px, y + h * 0.42);
        }
        gc.setStroke(Color.web("#8b5cb2", 0.34));
        gc.strokeArc(x + w * 0.34, y + h * 0.42, w * 0.30, h * 0.12, 180, 180, javafx.scene.shape.ArcType.OPEN);

        gc.save();
        gc.translate(x + w * 0.42, y + h * 0.56);
        gc.rotate(-52);
        drawSwordIcon(gc, 0, 0, 10.5);
        gc.restore();

        gc.save();
        gc.translate(x + w * 0.58, y + h * 0.54);
        gc.rotate(-38);
        drawSwordIcon(gc, 0, 0, 10.5);
        gc.restore();

        gc.setStroke(Color.web("#f0ca79", 0.60));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + w * 0.46, y + h * 0.46, x + w * 0.54, y + h * 0.52);
        gc.strokeLine(x + w * 0.50, y + h * 0.42, x + w * 0.60, y + h * 0.46);
    }

    private void drawEightOfSwordsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#d7dff0", 0.72));
        gc.setLineWidth(1.15);
        for (int i = 0; i < 4; i++) {
            double py = y + h * (0.20 + i * 0.11);
            gc.strokeLine(x + w * 0.28, py, x + w * 0.40, py + h * 0.12);
            gc.strokeLine(x + w * 0.72, py, x + w * 0.60, py + h * 0.12);
        }

        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79", 0.72));
        gc.setLineWidth(1.25);
        gc.strokeOval(cx - 7, cy - 20, 14, 14);
        gc.strokeLine(cx, cy - 6, cx, cy + 18);
        gc.strokeLine(cx - 10, cy + 2, cx + 10, cy + 2);
        gc.strokeLine(cx - 8, cy + 18, cx - 14, cy + 32);
        gc.strokeLine(cx + 8, cy + 18, cx + 14, cy + 32);
        gc.setStroke(Color.web("#8b5cb2", 0.50));
        gc.strokeLine(cx - 8, cy - 10, cx + 8, cy - 10);
        gc.strokeArc(cx - 22, cy + 22, 44, 16, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawSixOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.5);
        for (int i = 0; i < 5; i++) {
            double px = x + w * (0.28 + i * 0.11);
            gc.strokeLine(px, y + h * 0.56, px - 6, y + h * 0.24);
        }
        gc.setLineWidth(1.8);
        gc.strokeLine(cx, y + h * 0.68, cx, y + h * 0.26);
        gc.strokeOval(cx - 10, cy - 8, 20, 20);
    }

    private void drawFourOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.5);
        double left1 = x + w * 0.32;
        double left2 = x + w * 0.42;
        double right1 = x + w * 0.58;
        double right2 = x + w * 0.68;
        double top = y + h * 0.24;
        double bottom = y + h * 0.62;
        gc.strokeLine(left1, bottom, left1, top);
        gc.strokeLine(left2, bottom, left2, top);
        gc.strokeLine(right1, bottom, right1, top);
        gc.strokeLine(right2, bottom, right2, top);
        gc.strokeLine(left1, top, right2, top);
        gc.strokeArc(x + w * 0.36, y + h * 0.18, w * 0.28, h * 0.14, 200, 140, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawTwoOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.6);
        gc.strokeLine(x + w * 0.40, y + h * 0.24, x + w * 0.40, y + h * 0.64);
        gc.strokeLine(x + w * 0.60, y + h * 0.24, x + w * 0.60, y + h * 0.64);
        gc.strokeOval(x + w * 0.46, y + h * 0.30, w * 0.08, h * 0.10);
    }

    private void drawAceOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.46;
        drawWandIcon(gc, cx - 4, cy + 6, 18);
        gc.setStroke(Color.web("#7fbf86", 0.62));
        gc.setLineWidth(1.1);
        gc.strokeLine(cx + 8, cy - 22, cx + 16, cy - 30);
        gc.strokeLine(cx + 2, cy - 18, cx - 6, cy - 28);
    }

    private void drawThreeOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.45);
        gc.strokeLine(x + w * 0.34, y + h * 0.26, x + w * 0.34, y + h * 0.66);
        gc.strokeLine(x + w * 0.50, y + h * 0.22, x + w * 0.50, y + h * 0.66);
        gc.strokeLine(x + w * 0.66, y + h * 0.26, x + w * 0.66, y + h * 0.66);
        gc.setStroke(Color.web("#8b5cb2", 0.40));
        gc.strokeArc(x + w * 0.28, y + h * 0.56, w * 0.44, h * 0.10, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawFiveOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeLine(x + w * 0.30, y + h * 0.26, x + w * 0.60, y + h * 0.58);
        gc.strokeLine(x + w * 0.40, y + h * 0.22, x + w * 0.66, y + h * 0.62);
        gc.strokeLine(x + w * 0.50, y + h * 0.24, x + w * 0.36, y + h * 0.66);
        gc.strokeLine(x + w * 0.62, y + h * 0.26, x + w * 0.42, y + h * 0.66);
        gc.strokeLine(x + w * 0.72, y + h * 0.28, x + w * 0.50, y + h * 0.66);
    }

    private void drawSevenOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79", 0.74));
        gc.setLineWidth(1.2);
        for (int i = 0; i < 3; i++) {
            double startX = x + w * (0.24 + i * 0.10);
            gc.strokeLine(startX, y + h * 0.66, startX + 10, y + h * 0.44);
        }
        for (int i = 0; i < 3; i++) {
            double startX = x + w * (0.56 + i * 0.10);
            gc.strokeLine(startX, y + h * 0.66, startX - 10, y + h * 0.44);
        }
        gc.setStroke(Color.web("#ffca77"));
        gc.setLineWidth(1.8);
        gc.strokeLine(x + w * 0.50, y + h * 0.68, x + w * 0.46, y + h * 0.22);
        gc.strokeOval(x + w * 0.42, y + h * 0.18, w * 0.12, h * 0.10);
        gc.setStroke(Color.web("#7fbf86", 0.46));
        gc.setLineWidth(1.0);
        gc.strokeArc(x + w * 0.38, y + h * 0.30, w * 0.24, h * 0.20, 210, 120, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawTenOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.28);
        for (int i = 0; i < 5; i++) {
            double px = x + w * (0.30 + i * 0.08);
            gc.strokeLine(px, y + h * 0.24, px + 6, y + h * 0.64);
        }
        for (int i = 0; i < 5; i++) {
            double px = x + w * (0.34 + i * 0.08);
            gc.strokeLine(px, y + h * 0.22, px + 8, y + h * 0.62);
        }
    }

    private void drawEightOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.6);
        double startX = x + w * 0.26;
        double startY = y + h * 0.26;
        for (int i = 0; i < 8; i++) {
            double offset = i * 7.5;
            gc.strokeLine(startX - 6 + offset, startY + offset * 0.18, startX + 38 + offset, startY - 30 + offset * 0.18);
            gc.strokeOval(startX + 34 + offset, startY - 34 + offset * 0.18, 5, 5);
        }
    }

    private void drawNineOfWandsSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.25);
        for (int i = 0; i < 8; i++) {
            double px = x + w * (0.30 + (i % 4) * 0.12);
            double py = y + h * (0.18 + (i / 4) * 0.18);
            gc.strokeLine(px, py + h * 0.18, px, py + h * 0.40);
        }
        gc.setLineWidth(1.7);
        gc.strokeLine(x + w * 0.50, y + h * 0.24, x + w * 0.50, y + h * 0.68);
        gc.strokeOval(x + w * 0.44, y + h * 0.18, w * 0.12, h * 0.08);
    }

    private void drawNineOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] points = {
                {0.34, 0.22}, {0.50, 0.22}, {0.66, 0.22},
                {0.34, 0.38}, {0.50, 0.38}, {0.66, 0.38},
                {0.34, 0.54}, {0.50, 0.54}, {0.66, 0.54}
        };
        gc.setStroke(Color.web("#73559b", 0.28));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x + w * 0.25, y + h * 0.16, w * 0.50, h * 0.46, 16, 16);
        for (double[] point : points) {
            drawPentacleIcon(gc, x + w * point[0], y + h * point[1], 9.0);
        }
        gc.setStroke(Color.web("#7fbf86", 0.55));
        gc.strokeLine(x + w * 0.32, y + h * 0.67, x + w * 0.68, y + h * 0.67);
        gc.strokeLine(x + w * 0.42, y + h * 0.67, x + w * 0.36, y + h * 0.60);
        gc.strokeLine(x + w * 0.58, y + h * 0.67, x + w * 0.64, y + h * 0.60);
    }

    private void drawTenOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] outer = {
                {0.32, 0.22}, {0.50, 0.18}, {0.68, 0.22},
                {0.26, 0.38}, {0.74, 0.38},
                {0.32, 0.54}, {0.50, 0.58}, {0.68, 0.54},
                {0.40, 0.38}, {0.60, 0.38}
        };
        gc.setStroke(Color.web("#73559b", 0.30));
        gc.setLineWidth(1.0);
        gc.strokeOval(x + w * 0.24, y + h * 0.16, w * 0.52, h * 0.48);
        for (double[] point : outer) {
            drawPentacleIcon(gc, x + w * point[0], y + h * point[1], 8.5);
        }
        gc.setStroke(Color.web("#9a7b49", 0.60));
        gc.strokeLine(x + w * 0.34, y + h * 0.66, x + w * 0.66, y + h * 0.66);
        gc.strokeLine(x + w * 0.38, y + h * 0.66, x + w * 0.38, y + h * 0.56);
        gc.strokeLine(x + w * 0.62, y + h * 0.66, x + w * 0.62, y + h * 0.56);
    }

    private void drawFiveOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] points = {
                {0.34, 0.22}, {0.50, 0.18}, {0.66, 0.22}, {0.40, 0.42}, {0.60, 0.42}
        };
        gc.setStroke(Color.web("#73559b", 0.36));
        gc.setLineWidth(1.1);
        gc.strokeLine(x + w * 0.32, y + h * 0.18, x + w * 0.32, y + h * 0.62);
        gc.strokeLine(x + w * 0.68, y + h * 0.18, x + w * 0.68, y + h * 0.62);
        gc.strokeLine(x + w * 0.32, y + h * 0.26, x + w * 0.68, y + h * 0.26);
        for (double[] p : points) {
            drawPentacleIcon(gc, x + w * p[0], y + h * p[1], 8.0);
        }
        gc.setStroke(Color.web("#89b9ff", 0.45));
        gc.strokeLine(x + w * 0.38, y + h * 0.66, x + w * 0.46, y + h * 0.56);
        gc.strokeLine(x + w * 0.54, y + h * 0.66, x + w * 0.62, y + h * 0.56);
    }

    private void drawSixOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] points = {
                {0.36, 0.24}, {0.50, 0.18}, {0.64, 0.24},
                {0.36, 0.50}, {0.50, 0.56}, {0.64, 0.50}
        };
        for (double[] p : points) {
            drawPentacleIcon(gc, x + w * p[0], y + h * p[1], 8.5);
        }
        gc.setStroke(Color.web("#9a7b49", 0.65));
        gc.setLineWidth(1.2);
        gc.strokeLine(x + w * 0.50, y + h * 0.28, x + w * 0.50, y + h * 0.46);
        gc.strokeLine(x + w * 0.42, y + h * 0.40, x + w * 0.58, y + h * 0.40);
    }

    private void drawAceOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#d9c06a"));
        gc.setLineWidth(1.4);
        gc.strokeOval(cx - 18, cy - 18, 36, 36);
        drawPentacleIcon(gc, cx, cy, 12);
        gc.setStroke(Color.web("#7fbf86", 0.52));
        gc.strokeArc(cx - 20, cy + 18, 40, 16, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawTwoOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double leftX = x + w * 0.38;
        double rightX = x + w * 0.62;
        double cy = y + h * 0.42;
        drawPentacleIcon(gc, leftX, cy - 10, 10.0);
        drawPentacleIcon(gc, rightX, cy + 12, 10.0);
        gc.setStroke(Color.web("#f0ca79", 0.68));
        gc.setLineWidth(1.2);
        gc.strokeOval(x + w * 0.28, y + h * 0.22, w * 0.26, h * 0.22);
        gc.strokeOval(x + w * 0.46, y + h * 0.36, w * 0.26, h * 0.22);
        gc.strokeLine(x + w * 0.46, y + h * 0.33, x + w * 0.54, y + h * 0.43);
        gc.strokeLine(x + w * 0.46, y + h * 0.53, x + w * 0.54, y + h * 0.43);
        gc.setStroke(Color.web("#89b9ff", 0.40));
        gc.strokeArc(x + w * 0.28, y + h * 0.60, w * 0.44, h * 0.08, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawThreeOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#73559b", 0.34));
        gc.setLineWidth(1.15);
        gc.strokeArc(x + w * 0.30, y + h * 0.18, w * 0.40, h * 0.20, 0, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(x + w * 0.30, y + h * 0.28, x + w * 0.30, y + h * 0.56);
        gc.strokeLine(x + w * 0.70, y + h * 0.28, x + w * 0.70, y + h * 0.56);
        gc.strokeLine(x + w * 0.30, y + h * 0.56, x + w * 0.70, y + h * 0.56);
        drawPentacleIcon(gc, x + w * 0.50, y + h * 0.24, 8.8);
        drawPentacleIcon(gc, x + w * 0.40, y + h * 0.44, 8.8);
        drawPentacleIcon(gc, x + w * 0.60, y + h * 0.44, 8.8);
        gc.setStroke(Color.web("#9a7b49", 0.60));
        gc.strokeLine(x + w * 0.46, y + h * 0.60, x + w * 0.54, y + h * 0.60);
    }

    private void drawFourOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        drawPentacleIcon(gc, x + w * 0.50, y + h * 0.22, 8.8);
        drawPentacleIcon(gc, x + w * 0.50, y + h * 0.40, 9.4);
        drawPentacleIcon(gc, x + w * 0.38, y + h * 0.60, 8.6);
        drawPentacleIcon(gc, x + w * 0.62, y + h * 0.60, 8.6);
        gc.setStroke(Color.web("#73559b", 0.34));
        gc.setLineWidth(1.1);
        gc.strokeLine(x + w * 0.40, y + h * 0.30, x + w * 0.60, y + h * 0.30);
        gc.strokeLine(x + w * 0.42, y + h * 0.48, x + w * 0.58, y + h * 0.48);
        gc.strokeLine(x + w * 0.46, y + h * 0.48, x + w * 0.42, y + h * 0.70);
        gc.strokeLine(x + w * 0.54, y + h * 0.48, x + w * 0.58, y + h * 0.70);
    }

    private void drawSevenOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] points = {
                {0.34, 0.22}, {0.50, 0.18}, {0.66, 0.22},
                {0.38, 0.40}, {0.62, 0.40},
                {0.42, 0.56}, {0.58, 0.56}
        };
        for (double[] p : points) {
            drawPentacleIcon(gc, x + w * p[0], y + h * p[1], 8.2);
        }
        gc.setStroke(Color.web("#7fbf86", 0.56));
        gc.setLineWidth(1.2);
        gc.strokeLine(x + w * 0.50, y + h * 0.66, x + w * 0.50, y + h * 0.48);
        gc.strokeArc(x + w * 0.40, y + h * 0.62, w * 0.20, h * 0.08, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawEightOfPentaclesSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double[][] points = {
                {0.38, 0.22}, {0.62, 0.22},
                {0.38, 0.36}, {0.62, 0.36},
                {0.38, 0.50}, {0.62, 0.50},
                {0.38, 0.64}, {0.62, 0.64}
        };
        for (double[] p : points) {
            drawPentacleIcon(gc, x + w * p[0], y + h * p[1], 8.0);
        }
        gc.setStroke(Color.web("#9a7b49", 0.62));
        gc.setLineWidth(1.1);
        gc.strokeLine(x + w * 0.26, y + h * 0.68, x + w * 0.74, y + h * 0.68);
    }

    private void drawSuitGlyph(GraphicsContext gc, String family, double cx, double cy, double size) {
        if (family.startsWith("CUPS")) {
            drawCupIcon(gc, cx, cy, size);
        } else if (family.startsWith("SWORDS")) {
            drawSwordIcon(gc, cx, cy, size + 2);
        } else if (family.startsWith("WANDS")) {
            drawWandIcon(gc, cx, cy, size + 3);
        } else if (family.startsWith("PENTACLES")) {
            drawPentacleIcon(gc, cx, cy, size + 1);
        } else {
            drawHexagram(gc, cx, cy, size);
        }
    }

    private void drawCornerOrnament(GraphicsContext gc, double x, double y, int dirX, int dirY) {
        gc.setStroke(Color.web("#d8a24b", 0.76));
        gc.setLineWidth(1);
        gc.strokeLine(x, y, x + dirX * 12, y);
        gc.strokeLine(x, y, x, y + dirY * 12);
        gc.strokeArc(x + dirX * 2 - 6, y + dirY * 2 - 6, 12, 12, dirX > 0 ? 180 : 270, 90, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#8f63c0", 0.55));
        gc.strokeLine(x + dirX * 5, y + dirY * 2, x + dirX * 9, y + dirY * 2);
        gc.strokeLine(x + dirX * 2, y + dirY * 5, x + dirX * 2, y + dirY * 9);
        gc.setStroke(Color.web("#f1cf88", 0.84));
        gc.strokeOval(x + dirX * 5 - 2, y + dirY * 5 - 2, 4, 4);
    }

    private void drawCenterFiligree(GraphicsContext gc, double cx, double cy, double width, boolean inverted) {
        double dir = inverted ? -1 : 1;
        gc.setStroke(Color.web("#d8a24b", 0.60));
        gc.setLineWidth(0.9);
        gc.strokeLine(cx - width, cy, cx + width, cy);
        gc.strokeArc(cx - width - 4, cy - 5 * dir, 8, 10, inverted ? 180 : 0, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeArc(cx + width - 4, cy - 5 * dir, 8, 10, inverted ? 0 : 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#8f63c0", 0.46));
        gc.strokeOval(cx - 2, cy - 2, 4, 4);
    }

    void drawActiveCardHalo(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#9af5e8", 0.30));
        gc.setLineWidth(6.0);
        gc.strokeRoundRect(x - 8, y - 8, w + 16, h + 16, 24, 24);
        gc.setStroke(Color.web("#d2c5ff", 0.90));
        gc.setLineWidth(2.2);
        gc.strokeRoundRect(x - 5, y - 5, w + 10, h + 10, 22, 22);
        gc.setStroke(Color.web("#b760ed", 0.56));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x - 10, y - 10, w + 20, h + 20, 26, 26);
    }

    private void drawPriestessSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#ebd2a3"));
        gc.setLineWidth(1.45);
        gc.strokeLine(cx - 26, cy - 34, cx - 26, cy + 34);
        gc.strokeLine(cx + 26, cy - 34, cx + 26, cy + 34);
        gc.strokeOval(cx - 9, cy - 18, 18, 18);
        gc.strokeLine(cx, cy, cx, cy + 24);
        gc.strokeLine(cx - 11, cy + 6, cx + 11, cy + 6);
        gc.setStroke(Color.web("#7b60b4", 0.68));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 34, cy - 42, 16, 16);
        gc.strokeOval(cx + 18, cy - 42, 16, 16);
        gc.setStroke(Color.web("#f1cf88", 0.72));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx - 18, cy - 54, 36, 12);
        gc.strokeLine(cx - 8, cy - 28, cx, cy - 38);
        gc.strokeLine(cx + 8, cy - 28, cx, cy - 38);
        gc.setStroke(Color.web("#7b60b4", 0.55));
        gc.strokeLine(cx - 42, cy + 32, cx + 42, cy + 32);
        gc.strokeArc(cx - 22, cy + 26, 44, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawFoolSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.45);
        gc.strokeOval(cx - 6, cy - 26, 12, 12);
        gc.strokeLine(cx, cy - 14, cx + 8, cy + 10);
        gc.strokeLine(cx, cy - 8, cx - 12, cy + 4);
        gc.strokeLine(cx + 8, cy + 10, cx + 18, cy + 24);
        gc.strokeLine(cx + 8, cy + 10, cx - 2, cy + 26);
        gc.strokeLine(cx + 4, cy - 10, cx + 18, cy - 18);
        gc.strokeLine(cx - 8, cy - 4, cx - 18, cy - 16);
        gc.strokeLine(cx - 18, cy - 16, cx - 24, cy - 22);
        gc.strokeLine(cx - 18, cy - 16, cx - 10, cy - 18);
        gc.setStroke(Color.web("#f1cf88", 0.76));
        gc.strokeLine(cx + 6, cy - 18, cx + 16, cy - 30);
        gc.strokeOval(cx + 15, cy - 34, 10, 10);
        gc.setStroke(Color.web("#7fbf86", 0.56));
        gc.strokeLine(cx - 26, cy + 30, cx - 6, cy + 30);
        gc.strokeLine(cx - 26, cy + 30, cx - 22, cy + 20);
        gc.strokeLine(cx - 6, cy + 30, cx - 2, cy + 20);
        gc.setStroke(Color.web("#8f63c0", 0.44));
        gc.strokeArc(cx - 16, cy + 26, 32, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawMagicianSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - 7, cy - 26, 14, 14);
        gc.strokeLine(cx, cy - 12, cx, cy + 10);
        gc.strokeLine(cx - 12, cy - 2, cx + 12, cy - 2);
        gc.strokeLine(cx - 6, cy + 10, cx - 14, cy + 28);
        gc.strokeLine(cx + 6, cy + 10, cx + 14, cy + 28);
        gc.strokeLine(cx, cy - 34, cx, cy - 48);
        gc.strokeOval(cx - 14, cy - 56, 28, 12);
        gc.setStroke(Color.web("#8f63c0", 0.62));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 9, cy - 46, 18, 8);
        gc.strokeOval(cx - 5, cy - 50, 10, 16);
        gc.setStroke(Color.web("#f0ca79", 0.78));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx - 28, cy + 18, cx + 28, cy + 18);
        gc.strokeLine(cx - 22, cy + 18, cx - 22, cy + 8);
        gc.strokeLine(cx + 22, cy + 18, cx + 22, cy + 8);
        drawCupIcon(gc, cx - 22, cy + 4, 5.2);
        drawSwordIcon(gc, cx - 7, cy + 3, 5.8);
        drawWandIcon(gc, cx + 9, cy + 8, 6.2);
        drawPentacleIcon(gc, cx + 24, cy + 3, 5.2);
    }

    private void drawEmpressSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.45);
        gc.strokeOval(cx - 8, cy - 20, 16, 16);
        gc.strokeLine(cx, cy - 4, cx, cy + 14);
        gc.strokeLine(cx - 12, cy + 2, cx + 12, cy + 2);
        gc.strokeArc(cx - 22, cy + 6, 44, 24, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 8, cy + 14, cx - 16, cy + 30);
        gc.strokeLine(cx + 8, cy + 14, cx + 16, cy + 30);
        gc.setStroke(Color.web("#f1cf88", 0.78));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx - 12, cy - 26, cx - 4, cy - 38);
        gc.strokeLine(cx, cy - 26, cx, cy - 40);
        gc.strokeLine(cx + 12, cy - 26, cx + 4, cy - 38);
        gc.strokeArc(cx - 20, cy - 20, 40, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#7fbf86", 0.60));
        gc.strokeArc(cx - 30, cy + 22, 60, 18, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - 18, cy + 36, cx - 10, cy + 24);
        gc.strokeLine(cx + 18, cy + 36, cx + 10, cy + 24);
        gc.strokeLine(cx - 4, cy + 30, cx, cy + 24);
        gc.strokeLine(cx + 4, cy + 30, cx, cy + 24);
    }


    private void drawEmperorSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.45);
        gc.strokeOval(cx - 8, cy - 20, 16, 16);
        gc.strokeLine(cx, cy - 4, cx, cy + 14);
        gc.strokeLine(cx - 12, cy + 2, cx + 12, cy + 2);
        gc.strokeLine(cx - 8, cy + 14, cx - 16, cy + 30);
        gc.strokeLine(cx + 8, cy + 14, cx + 16, cy + 30);
        gc.strokeLine(cx - 20, cy + 30, cx + 20, cy + 30);
        gc.strokeLine(cx - 18, cy + 20, cx - 18, cy + 36);
        gc.strokeLine(cx + 18, cy + 20, cx + 18, cy + 36);
        gc.setStroke(Color.web("#f1cf88", 0.78));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx - 12, cy - 26, cx - 4, cy - 38);
        gc.strokeLine(cx, cy - 26, cx, cy - 40);
        gc.strokeLine(cx + 12, cy - 26, cx + 4, cy - 38);
        gc.setStroke(Color.web("#8f63c0", 0.44));
        gc.strokeLine(cx - 24, cy + 36, cx - 14, cy + 22);
        gc.strokeLine(cx + 24, cy + 36, cx + 14, cy + 22);
    }

    private void drawHierophantSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.40;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.4);
        gc.strokeLine(cx - 24, cy - 30, cx - 24, cy + 26);
        gc.strokeLine(cx + 24, cy - 30, cx + 24, cy + 26);
        gc.strokeOval(cx - 7, cy - 18, 14, 14);
        gc.strokeLine(cx, cy - 4, cx, cy + 18);
        gc.strokeLine(cx - 10, cy + 4, cx + 10, cy + 4);
        gc.strokeLine(cx, cy - 28, cx, cy - 44);
        gc.strokeLine(cx - 16, cy - 34, cx + 16, cy - 34);
        gc.strokeLine(cx - 10, cy - 22, cx + 10, cy - 22);
        gc.setStroke(Color.web("#f1cf88", 0.76));
        gc.setLineWidth(1.15);
        gc.strokeOval(cx - 12, cy - 52, 24, 10);
        gc.setStroke(Color.web("#89b9ff", 0.46));
        gc.strokeLine(cx - 18, cy + 26, cx - 8, cy + 38);
        gc.strokeLine(cx + 18, cy + 26, cx + 8, cy + 38);
        gc.strokeLine(cx - 8, cy + 38, cx + 8, cy + 38);
    }

    private void drawLoversSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 23, cy - 6, 16, 16);
        gc.strokeOval(cx + 7, cy - 6, 16, 16);
        gc.strokeLine(cx - 15, cy + 10, cx - 15, cy + 24);
        gc.strokeLine(cx + 15, cy + 10, cx + 15, cy + 24);
        gc.strokeLine(cx - 15, cy + 16, cx - 7, cy + 30);
        gc.strokeLine(cx + 15, cy + 16, cx + 7, cy + 30);
        gc.strokeLine(cx - 4, cy + 12, cx + 4, cy + 12);
        gc.setStroke(Color.web("#f1cf88", 0.76));
        gc.setLineWidth(1.15);
        gc.strokeLine(cx, cy - 20, cx - 10, cy - 34);
        gc.strokeLine(cx, cy - 20, cx + 10, cy - 34);
        gc.strokeOval(cx - 8, cy - 42, 16, 10);
        gc.setStroke(Color.web("#8f63c0", 0.48));
        gc.strokeLine(cx - 15, cy + 10, cx, cy - 2);
        gc.strokeLine(cx + 15, cy + 10, cx, cy - 2);
        gc.setStroke(Color.web("#7fbf86", 0.44));
        gc.strokeArc(cx - 28, cy + 28, 56, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawChariotSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.44;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.4);
        gc.strokeRect(cx - 18, cy - 18, 36, 22);
        gc.strokeLine(cx - 10, cy - 18, cx - 10, cy - 32);
        gc.strokeLine(cx + 10, cy - 18, cx + 10, cy - 32);
        gc.strokeLine(cx - 10, cy - 32, cx + 10, cy - 32);
        gc.strokeLine(cx, cy - 32, cx, cy - 42);
        gc.strokeOval(cx - 6, cy - 50, 12, 10);
        gc.setStroke(Color.web("#f1cf88", 0.76));
        gc.setLineWidth(1.15);
        gc.strokeLine(cx - 24, cy + 6, cx + 24, cy + 6);
        gc.strokeOval(cx - 22, cy + 8, 12, 12);
        gc.strokeOval(cx + 10, cy + 8, 12, 12);
        gc.setStroke(Color.web("#8f63c0", 0.48));
        gc.strokeLine(cx - 14, cy + 18, cx - 6, cy + 8);
        gc.strokeLine(cx + 14, cy + 18, cx + 6, cy + 8);
        gc.setStroke(Color.web("#7fbf86", 0.42));
        gc.strokeLine(cx - 26, cy + 28, cx - 6, cy + 28);
        gc.strokeLine(cx + 6, cy + 28, cx + 26, cy + 28);
    }

    private void drawStrengthSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 8, cy - 18, 16, 16);
        gc.strokeLine(cx, cy - 2, cx, cy + 16);
        gc.strokeLine(cx - 10, cy + 4, cx + 10, cy + 4);
        gc.strokeLine(cx, cy + 16, cx - 10, cy + 32);
        gc.strokeLine(cx, cy + 16, cx + 10, cy + 32);
        gc.setStroke(Color.web("#f1cf88", 0.78));
        gc.setLineWidth(1.15);
        gc.strokeOval(cx - 14, cy - 36, 14, 10);
        gc.strokeOval(cx, cy - 36, 14, 10);
        gc.setStroke(Color.web("#8f2243", 0.58));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx - 22, cy + 2, 44, 18);
        gc.strokeLine(cx - 6, cy + 4, cx - 16, cy - 10);
        gc.strokeLine(cx + 6, cy + 4, cx + 16, cy - 10);
        gc.setStroke(Color.web("#7fbf86", 0.44));
        gc.strokeArc(cx - 26, cy + 26, 52, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawJusticeSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 7, cy - 18, 14, 14);
        gc.strokeLine(cx, cy - 2, cx, cy + 18);
        gc.strokeLine(cx - 10, cy + 4, cx + 10, cy + 4);
        gc.setStroke(Color.web("#d7dff0", 0.76));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx + 18, cy - 28, cx + 18, cy + 22);
        gc.strokeLine(cx + 18, cy - 28, cx + 30, cy - 8);
        gc.strokeLine(cx + 18, cy - 28, cx + 6, cy - 8);
        gc.setStroke(Color.web("#f0ca79", 0.82));
        gc.setLineWidth(1.05);
        gc.strokeLine(cx - 18, cy - 10, cx + 18, cy - 10);
        gc.strokeLine(cx - 12, cy - 10, cx - 18, cy + 4);
        gc.strokeLine(cx + 12, cy - 10, cx + 18, cy + 4);
        gc.strokeOval(cx - 24, cy + 6, 12, 6);
        gc.strokeOval(cx + 12, cy + 6, 12, 6);
        gc.setStroke(Color.web("#8f63c0", 0.44));
        gc.strokeLine(cx - 10, cy + 30, cx + 10, cy + 30);
    }

    private void drawHangedManSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeLine(cx - 18, cy - 34, cx + 18, cy - 34);
        gc.strokeLine(cx - 12, cy - 34, cx - 12, cy - 12);
        gc.strokeLine(cx + 12, cy - 34, cx + 12, cy - 12);
        gc.strokeOval(cx - 7, cy - 6, 14, 14);
        gc.strokeLine(cx, cy + 8, cx, cy + 26);
        gc.strokeLine(cx, cy + 26, cx - 12, cy + 38);
        gc.strokeLine(cx, cy + 26, cx + 10, cy + 36);
        gc.strokeLine(cx, cy + 14, cx - 10, cy + 24);
        gc.strokeLine(cx, cy + 14, cx + 10, cy + 24);
        gc.setStroke(Color.web("#8f63c0", 0.50));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - 16, cy - 20, 32, 40);
        drawEightPointStar(gc, cx, cy - 18, 7, 3);
    }

    private void drawHermitSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 7, cy - 20, 14, 14);
        gc.strokeLine(cx, cy - 6, cx - 4, cy + 18);
        gc.strokeLine(cx - 4, cy + 18, cx + 8, cy + 34);
        gc.strokeLine(cx - 4, cy + 18, cx - 14, cy + 32);
        gc.setStroke(Color.web("#d7dff0", 0.76));
        gc.setLineWidth(1.15);
        gc.strokeLine(cx + 16, cy - 30, cx + 16, cy + 26);
        gc.setStroke(Color.web("#f1cf88", 0.82));
        gc.strokeOval(cx + 10, cy - 34, 12, 12);
        drawEightPointStar(gc, cx + 16, cy - 28, 8, 3);
        gc.setStroke(Color.web("#8f63c0", 0.42));
        gc.strokeArc(cx - 22, cy + 28, 44, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawDeathSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 8, cy - 18, 16, 16);
        gc.strokeLine(cx, cy - 2, cx, cy + 18);
        gc.strokeLine(cx - 10, cy + 4, cx + 10, cy + 4);
        gc.strokeLine(cx, cy + 18, cx - 10, cy + 34);
        gc.strokeLine(cx, cy + 18, cx + 10, cy + 34);
        gc.setStroke(Color.web("#8f2243", 0.72));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx + 16, cy - 8, cx + 16, cy - 34);
        gc.strokeLine(cx + 16, cy - 34, cx + 34, cy - 28);
        gc.strokeLine(cx + 34, cy - 28, cx + 20, cy - 18);
        gc.setStroke(Color.web("#f1cf88", 0.74));
        gc.setLineWidth(1.05);
        gc.strokeLine(cx - 18, cy + 34, cx + 18, cy + 34);
        gc.strokeLine(cx - 10, cy + 34, cx - 16, cy + 46);
        gc.strokeLine(cx + 10, cy + 34, cx + 16, cy + 46);
    }

    private void drawTemperanceSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeOval(cx - 7, cy - 18, 14, 14);
        gc.strokeLine(cx, cy - 4, cx, cy + 16);
        gc.strokeLine(cx - 10, cy + 4, cx + 10, cy + 4);
        gc.strokeLine(cx, cy + 16, cx - 8, cy + 32);
        gc.strokeLine(cx, cy + 16, cx + 8, cy + 32);
        drawCupIcon(gc, cx - 16, cy - 2, 6.8);
        drawCupIcon(gc, cx + 16, cy + 8, 6.8);
        gc.setStroke(Color.web("#89b9ff", 0.62));
        gc.setLineWidth(1.1);
        gc.strokeLine(cx - 8, cy - 4, cx + 8, cy + 8);
        gc.strokeLine(cx, cy - 28, cx, cy + 30);
        gc.setStroke(Color.web("#7fbf86", 0.46));
        gc.strokeArc(cx - 18, cy + 28, 36, 12, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawDevilSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.4);
        gc.strokeOval(cx - 10, cy - 20, 20, 20);
        gc.strokeLine(cx, cy, cx, cy + 20);
        gc.strokeLine(cx - 12, cy + 6, cx + 12, cy + 6);
        gc.strokeLine(cx - 6, cy + 20, cx - 14, cy + 34);
        gc.strokeLine(cx + 6, cy + 20, cx + 14, cy + 34);
        gc.setStroke(Color.web("#8f2243", 0.72));
        gc.setLineWidth(1.2);
        gc.strokeLine(cx - 12, cy - 28, cx - 4, cy - 42);
        gc.strokeLine(cx + 12, cy - 28, cx + 4, cy - 42);
        gc.strokeLine(cx - 18, cy + 34, cx - 8, cy + 44);
        gc.strokeLine(cx + 18, cy + 34, cx + 8, cy + 44);
        gc.strokeLine(cx - 12, cy + 44, cx + 12, cy + 44);
        gc.setStroke(Color.web("#f1cf88", 0.66));
        gc.strokeLine(cx - 20, cy + 12, cx - 20, cy + 26);
        gc.strokeLine(cx + 20, cy + 12, cx + 20, cy + 26);
        gc.strokeLine(cx - 20, cy + 12, cx + 20, cy + 12);
    }

    private void drawTowerSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double topY = y + h * 0.20;
        double bottomY = y + h * 0.66;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.35);
        gc.strokeLine(cx - 14, bottomY, cx - 8, topY);
        gc.strokeLine(cx + 14, bottomY, cx + 8, topY);
        gc.strokeLine(cx - 8, topY, cx + 8, topY);
        gc.strokeLine(cx - 12, y + h * 0.40, cx + 12, y + h * 0.40);
        gc.strokeLine(cx - 10, y + h * 0.52, cx + 10, y + h * 0.52);
        gc.setStroke(Color.web("#efbf5a", 0.82));
        gc.setLineWidth(1.7);
        gc.strokeLine(cx + 2, topY - 10, cx + 18, topY + 12);
        gc.strokeLine(cx + 10, topY - 6, cx - 2, topY + 18);
        gc.setStroke(Color.web("#8f2243", 0.68));
        gc.setLineWidth(1.1);
        gc.strokeLine(cx - 2, y + h * 0.28, cx + 8, y + h * 0.42);
        gc.strokeLine(cx + 8, y + h * 0.42, cx - 6, y + h * 0.58);
        gc.strokeLine(cx - 18, y + h * 0.26, cx - 26, y + h * 0.40);
        gc.strokeLine(cx + 18, y + h * 0.24, cx + 28, y + h * 0.38);
    }

    private void drawJudgementSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.38;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx, cy - 34, cx, cy + 8);
        gc.strokeLine(cx, cy - 34, cx + 18, cy - 20);
        gc.strokeLine(cx + 18, cy - 20, cx + 26, cy - 24);
        gc.strokeLine(cx + 18, cy - 20, cx + 26, cy - 16);
        gc.setStroke(Color.web("#89b9ff", 0.52));
        gc.strokeLine(cx - 20, cy + 26, cx + 20, cy + 26);
        gc.strokeLine(cx - 14, cy + 26, cx - 14, cy + 40);
        gc.strokeLine(cx + 14, cy + 26, cx + 14, cy + 40);
    }

    private void drawWorldSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#f0ca79"));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - 28, cy - 34, 56, 68);
        drawEightPointStar(gc, cx, cy, 18, 7);
        gc.setStroke(Color.web("#7fbf86", 0.52));
        gc.strokeOval(cx - 40, cy - 12, 12, 12);
        gc.strokeOval(cx + 28, cy - 12, 12, 12);
        gc.strokeOval(cx - 6, cy - 46, 12, 12);
        gc.strokeOval(cx - 6, cy + 34, 12, 12);
    }

    private void drawSunSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setFill(Color.web("#efbf5a", 0.18));
        gc.fillOval(cx - 34, cy - 34, 68, 68);
        gc.setStroke(Color.web("#f1ca73"));
        gc.setLineWidth(1.8);
        gc.strokeOval(cx - 24, cy - 24, 48, 48);
        gc.strokeOval(cx - 34, cy - 34, 68, 68);
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            double sx = cx + Math.cos(angle) * 30;
            double sy = cy + Math.sin(angle) * 30;
            double ex = cx + Math.cos(angle) * 48;
            double ey = cy + Math.sin(angle) * 48;
            gc.strokeLine(sx, sy, ex, ey);
        }
        gc.setStroke(Color.web("#7fbf86", 0.48));
        gc.setLineWidth(1.1);
        gc.strokeArc(cx - 18, cy + 20, 36, 14, 180, 180, javafx.scene.shape.ArcType.OPEN);
    }

    private void drawMoonSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setFill(Color.web("#f4dcb0", 0.92));
        gc.fillOval(cx - 24, cy - 28, 48, 56);
        gc.setFill(Color.web("#141017"));
        gc.fillOval(cx - 10, cy - 28, 48, 56);
        gc.setStroke(Color.web("#7965ad", 0.6));
        gc.strokeLine(cx - 42, cy + 44, cx + 42, cy + 44);
        gc.strokeLine(cx - 22, cy + 44, cx - 12, cy + 28);
        gc.strokeLine(cx + 22, cy + 44, cx + 12, cy + 28);
        drawEightPointStar(gc, cx, cy - 18, 7, 3);
    }

    private void drawStarSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.38;
        drawEightPointStar(gc, cx, cy, 26, 12);
        drawEightPointStar(gc, cx - 34, cy + 24, 10, 5);
        drawEightPointStar(gc, cx + 34, cy + 24, 10, 5);
        drawEightPointStar(gc, cx - 18, cy + 40, 7, 3);
        drawEightPointStar(gc, cx + 18, cy + 40, 7, 3);
        gc.setStroke(Color.web("#6750a0", 0.55));
        gc.strokeLine(cx - 42, cy + 58, cx + 42, cy + 58);
    }

    private void drawWheelSymbol(GraphicsContext gc, double x, double y, double w, double h) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.42;
        gc.setStroke(Color.web("#efcf8a"));
        gc.setLineWidth(1.6);
        gc.strokeOval(cx - 30, cy - 30, 60, 60);
        gc.strokeOval(cx - 16, cy - 16, 32, 32);
        gc.strokeOval(cx - 8, cy - 8, 16, 16);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            gc.strokeLine(cx, cy, cx + Math.cos(angle) * 30, cy + Math.sin(angle) * 30);
        }
        gc.setStroke(Color.web("#8b5cb2", 0.45));
        gc.strokeLine(cx - 24, cy - 24, cx + 24, cy + 24);
        gc.strokeLine(cx + 24, cy - 24, cx - 24, cy + 24);
    }

    private void drawCupIcon(GraphicsContext gc, double cx, double cy, double size) {
        gc.setStroke(Color.web("#89b9ff"));
        gc.setLineWidth(1.8);
        gc.strokeArc(cx - size, cy - size * 0.8, size * 2, size * 1.5, 180, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeLine(cx - size * 0.6, cy + size * 0.05, cx - size * 0.2, cy + size);
        gc.strokeLine(cx + size * 0.6, cy + size * 0.05, cx + size * 0.2, cy + size);
        gc.strokeLine(cx - size * 0.35, cy + size, cx + size * 0.35, cy + size);
        gc.strokeLine(cx, cy + size, cx, cy + size * 1.45);
        gc.strokeOval(cx - size * 0.45, cy + size * 1.42, size * 0.9, size * 0.22);
    }

    private void drawSwordIcon(GraphicsContext gc, double cx, double cy, double size) {
        gc.setStroke(Color.web("#d7dff0"));
        gc.setLineWidth(1.8);
        gc.strokeLine(cx, cy - size * 1.25, cx, cy + size * 0.95);
        gc.strokeLine(cx, cy - size * 1.25, cx - size * 0.18, cy - size * 0.92);
        gc.strokeLine(cx, cy - size * 1.25, cx + size * 0.18, cy - size * 0.92);
        gc.strokeLine(cx - size * 0.65, cy + size * 0.1, cx + size * 0.65, cy + size * 0.1);
        gc.strokeLine(cx - size * 0.25, cy + size * 0.95, cx + size * 0.25, cy + size * 0.95);
    }

    private void drawWandIcon(GraphicsContext gc, double cx, double cy, double size) {
        gc.setStroke(Color.web("#e09a61"));
        gc.setLineWidth(2.0);
        gc.strokeLine(cx - size * 0.65, cy + size * 0.95, cx + size * 0.55, cy - size * 1.05);
        gc.setStroke(Color.web("#ffca77"));
        gc.setLineWidth(1.2);
        gc.strokeOval(cx + size * 0.35, cy - size * 1.2, size * 0.34, size * 0.34);
        gc.strokeLine(cx + size * 0.52, cy - size * 0.85, cx + size * 0.78, cy - size * 0.58);
        gc.strokeLine(cx + size * 0.52, cy - size * 0.85, cx + size * 0.26, cy - size * 0.58);
    }

    private void drawPentacleIcon(GraphicsContext gc, double cx, double cy, double size) {
        gc.setStroke(Color.web("#d9c06a"));
        gc.setLineWidth(1.6);
        gc.strokeOval(cx - size, cy - size, size * 2, size * 2);
        drawFivePointStar(gc, cx, cy, size * 0.78);
    }

    private void drawFivePointStar(GraphicsContext gc, double cx, double cy, double r) {
        double[] xs = new double[5];
        double[] ys = new double[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + i * 72);
            xs[i] = cx + Math.cos(angle) * r;
            ys[i] = cy + Math.sin(angle) * r;
        }
        int[] order = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < order.length - 1; i++) {
            gc.strokeLine(xs[order[i]], ys[order[i]], xs[order[i + 1]], ys[order[i + 1]]);
        }
    }

    private void drawGeneralTarotSymbol(GraphicsContext gc, double x, double y, double w, double h, TarotGame.Card card) {
        double cx = x + w / 2.0;
        double cy = y + h * 0.4;

        gc.setStroke(Color.web("#e4c27c"));
        gc.setLineWidth(1.6);
        gc.strokeOval(cx - 18, cy - 18, 36, 36);
        gc.strokeLine(cx, cy - 42, cx, cy + 44);
        gc.strokeLine(cx - 36, cy + 28, cx + 36, cy + 28);

        double seed = Math.abs(card.meaning.title.hashCode() % 5);
        if (seed % 2 == 0) {
            drawEightPointStar(gc, cx, cy - 2, 20, 8);
        } else {
            drawHexagram(gc, cx, cy, 20);
        }
    }

    private void drawEightPointStar(GraphicsContext gc, double cx, double cy, double outerR, double innerR) {
        gc.setStroke(Color.web("#f0cf86"));
        gc.setLineWidth(1.4);
        double[] xs = new double[16];
        double[] ys = new double[16];
        for (int i = 0; i < 16; i++) {
            double r = i % 2 == 0 ? outerR : innerR;
            double angle = Math.toRadians(-90 + i * 22.5);
            xs[i] = cx + Math.cos(angle) * r;
            ys[i] = cy + Math.sin(angle) * r;
        }
        gc.strokePolygon(xs, ys, xs.length);
    }


    private void drawWrappedTextCentered(GraphicsContext gc, String text, double centerX, double y,
                                         double maxWidth, double lineHeight, int maxLines) {
        int charsPerLine = Math.max(6, (int) (maxWidth / 10));
        List<String> lines = wrapText(text, charsPerLine, maxLines);
        double currentY = y;
        for (String line : lines) {
            gc.fillText(line, centerX, currentY);
            currentY += lineHeight;
        }
    }

    private List<String> wrapText(String text, int charsPerLine, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        String cleaned = text.replace("\n", "").trim();
        int start = 0;
        while (start < cleaned.length() && lines.size() < maxLines) {
            int end = Math.min(cleaned.length(), start + charsPerLine);
            if (end < cleaned.length() && lines.size() == maxLines - 1) {
                lines.add(cleaned.substring(start, Math.max(start + 1, end - 1)) + "…");
                return lines;
            }
            lines.add(cleaned.substring(start, end));
            start = end;
        }
        return lines;
    }
}
