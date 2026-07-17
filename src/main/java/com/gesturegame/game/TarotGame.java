package com.gesturegame.game;

import com.gesturegame.ai.TarotReadingService;
import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.control.TextInputDialog;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 紫黑霓虹与复古羊皮纸融合风格的三牌塔罗占读界面。
 *
 * <p>界面结构参考在线占读台：左侧牌堆，中部三卡牌阵，右侧详细解读，
 * 底部记录区；手势悬停用于选择卡牌，握拳翻牌，张手可重新洗牌。</p>
 */
public class TarotGame implements GameInterface {

    private static final Random RANDOM = new Random();
    private static final Color GOLD = Color.web("#d8a24b");
    private static final Color GOLD_SOFT = Color.web("#f3d79c");
    private static final Color BLACK_PANEL = Color.web("#060608");
    private static final Color PANEL_EDGE = Color.web("#5f3b10");
    private static final Color PURPLE = Color.web("#2d0b45");
    private static final Color PURPLE_DEEP = Color.web("#16051f");
    private static final Color PURPLE_MID = Color.web("#4a1f6d");
    private static final Color PURPLE_SOFT = Color.web("#8f6bb3");
    private static final Color GOLD_DEEP = Color.web("#b77a2a");

    private static final String[] SLOT_LABELS = {"PAST", "PRESENT", "FUTURE"};
    private static final String[] SLOT_NOTES = {"PAST INFLUENCE", "PRESENT TENSION", "EMERGING PATH"};

    private static final List<TarotMeaning> TAROT_DECK = TarotDeckData.cards();

    private final List<Card> cards = new ArrayList<>();
    private SpreadMode currentSpreadMode = SpreadMode.THREE_CARD;
    private int canvasWidth;
    private int canvasHeight;
    private int selectedIndex = -1;
    private int detailIndex = -1;
    private int flippingIndex = -1;
    private double flipProgress = 0.0;
    private double hoverPulse = 0.0;
    private double ambientPulse = 0.0;
    private double handCanvasX;
    private double handCanvasY;
    private boolean handDetected;
    private int score;
    private boolean over;
    private int modeSwitchCooldown;

    private final List<TarotMeaning> selectionDeck = new ArrayList<>();
    private final TarotGestureStateMachine interaction = new TarotGestureStateMachine();
    private final TarotReadingPanel readingPanel = new TarotReadingPanel();
    private final TarotRenderCache renderCache = new TarotRenderCache();
    private final TarotCardRenderer cardRenderer = new TarotCardRenderer(renderCache);
    private String readingQuestion = "我此刻最需要看清的是什么？";
    private String combinedInterpretation = "";
    private String combinedAdvice = "";
    private String readingSource = "本地牌义";
    private double carouselPosition;
    private int lockedDeckIndex = -1;
    private TarotMeaning lockedMeaning;
    private Card activeSelection;
    private double revealProgress;
    private double flyProgress;
    private double flyStartX;
    private double flyStartY;
    private double finalRevealProgress;
    private double fanShift;
    private double fanEntryProgress;
    private long readingGenerationId;
    private boolean aiReadingPending;
    private boolean completeFistLatched;

    private static final double CAROUSEL_SPEED = 0.026;
    private static final double CARD_ASPECT = 600.0 / 350.0;

    private javafx.scene.image.Image altarImage;
    private javafx.scene.image.Image scrollImage;

    @Override
    public String getName() {
        return "塔罗牌";
    }

    @Override
    public String getDescription() {
        return "霓虹秘境三牌占读，手势翻开完整 78 张塔罗牌";
    }

    @Override
    public String getIcon() {
        return "🔮";
    }

    public void setQuestion(String question) {
        if (question != null && !question.isBlank()) {
            readingQuestion = question.strip();
        }
    }

    @Override
    public void init(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
        this.score = 0;
        this.over = false;
        this.selectedIndex = -1;
        this.detailIndex = -1;
        this.flippingIndex = -1;
        this.flipProgress = 0.0;
        this.hoverPulse = 0.0;
        this.ambientPulse = 0.0;
        this.handDetected = false;
        this.modeSwitchCooldown = 0;
        this.interaction.reset();
        this.carouselPosition = 0.0;
        this.lockedDeckIndex = -1;
        this.lockedMeaning = null;
        this.activeSelection = null;
        this.revealProgress = 0.0;
        this.flyProgress = 0.0;
        this.finalRevealProgress = 0.0;
        this.fanShift = 0.0;
        this.fanEntryProgress = 0.0;
        this.readingGenerationId++;
        this.aiReadingPending = false;
        this.completeFistLatched = false;
        this.combinedInterpretation = "";
        this.combinedAdvice = "";
        this.readingSource = "本地牌义";
        this.readingPanel.clear();
        cards.clear();
        selectionDeck.clear();
        selectionDeck.addAll(TAROT_DECK);
        Collections.shuffle(selectionDeck, RANDOM);
        cardRenderer.prepareBack();
        
        try {
            altarImage = new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/tarot/pedestal_center.png"));
            scrollImage = new javafx.scene.image.Image(getClass().getResourceAsStream("/assets/tarot/scroll.png"));
        } catch (Exception e) {
            System.err.println("Failed to load tarot images: " + e.getMessage());
        }
    }

    @Override
    public void update(GestureData gesture) {
        if (over) {
            return;
        }

        ambientPulse += 0.045;
        if (ambientPulse > Math.PI * 2) {
            ambientPulse -= Math.PI * 2;
        }
        fanEntryProgress = Math.min(1.0, fanEntryProgress + 0.018);

        GestureType gestureType = null;
        if (gesture != null && gesture.isHandDetected()) {
            handDetected = true;
            handCanvasX = gesture.getHandX() * canvasWidth;
            handCanvasY = gesture.getHandY() * canvasHeight;
            gestureType = gesture.getGesture();
        } else {
            handDetected = false;
        }

        double shiftTarget = interaction.is(TarotGestureStateMachine.Phase.REVEALING)
                || interaction.is(TarotGestureStateMachine.Phase.AWAITING_OPEN) ? -canvasWidth * 0.115 : 0.0;
        fanShift += (shiftTarget - fanShift) * 0.09;

        switch (interaction.phase()) {
            case ROTATING -> updateRotating(gestureType);
            case HOLDING -> updateHolding(gestureType);
            case REVEALING -> updateRevealing();
            case AWAITING_OPEN -> updateAwaitingOpen(gestureType);
            case FLYING -> updateFlying(gestureType);
            case FINAL_REVEAL -> updateFinalReveal();
            case COMPLETE -> updateComplete(gestureType);
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);

        drawBackground(gc);
        drawReadingHeader(gc);
        if (interaction.is(TarotGestureStateMachine.Phase.FINAL_REVEAL)
                || interaction.is(TarotGestureStateMachine.Phase.COMPLETE)) {
            drawFinalReading(gc);
        } else {
            drawFanCarousel(gc);
            drawChosenSlots(gc);
            if (interaction.is(TarotGestureStateMachine.Phase.REVEALING)
                    || interaction.is(TarotGestureStateMachine.Phase.AWAITING_OPEN)
                    || interaction.is(TarotGestureStateMachine.Phase.FLYING)) {
                drawSingleMeaning(gc);
            }
            if (interaction.is(TarotGestureStateMachine.Phase.FLYING)) {
                drawFlyingSelection(gc);
            }
            drawSelectionProgress(gc);
            drawReadingHint(gc);
        }
        drawHandCursor(gc);
    }

    @Override
    public boolean isOver() {
        return over;
    }

    @Override
    public int getScore() {
        return 0;
    }

    @Override
    public void setDifficulty(Difficulty d) { }

    @Override
    public Difficulty getDifficulty() { return Difficulty.NORMAL; }

    @Override
    public void reset() {
        init(canvasWidth, canvasHeight);
    }

    private void updateRotating(GestureType gestureType) {
        if (selectionDeck.isEmpty()) {
            return;
        }
        if (gestureType == GestureType.OPEN) {
            carouselPosition = wrapCarousel(carouselPosition + CAROUSEL_SPEED);
        } else if (gestureType == GestureType.FIST) {
            lockedDeckIndex = Math.floorMod((int) Math.round(carouselPosition), selectionDeck.size());
            carouselPosition = lockedDeckIndex;
            lockedMeaning = selectionDeck.get(lockedDeckIndex);
            cardRenderer.prepare(lockedMeaning);
            interaction.beginHolding();
        }
    }

    private void updateHolding(GestureType gestureType) {
        TarotGestureStateMachine.HoldResult result = interaction.updateHolding(gestureType, handDetected);
        if (result == TarotGestureStateMachine.HoldResult.CANCELLED) {
            cancelHold();
        } else if (result == TarotGestureStateMachine.HoldResult.CONFIRMED) {
            confirmLockedCard();
        }
    }

    private void cancelHold() {
        lockedDeckIndex = -1;
        lockedMeaning = null;
        interaction.beginRotating();
    }

    private void confirmLockedCard() {
        int slotIndex = Math.min(cards.size(), SLOT_LABELS.length - 1);
        double cardWidth = centerCardWidth();
        activeSelection = new Card(
                0, 0, cardWidth, cardWidth * CARD_ASPECT,
                SLOT_LABELS[slotIndex], SLOT_NOTES[slotIndex],
                lockedMeaning, RANDOM.nextDouble() < 0.35, false
        );
        activeSelection.revealed = true;
        revealProgress = 0.0;
        interaction.beginRevealing();
    }

    private void updateRevealing() {
        revealProgress = Math.min(1.0, revealProgress + 0.046);
        if (revealProgress >= 1.0) {
            interaction.beginAwaitingOpen();
        }
    }

    private void updateAwaitingOpen(GestureType gestureType) {
        if (interaction.updateAwaitingOpen(gestureType, handDetected)) {
            flyProgress = 0.0;
            flyStartX = fanCenterX();
            flyStartY = fanCenterY();
            interaction.beginFlying();
        }
    }

    private void updateFlying(GestureType gestureType) {
        if (gestureType == GestureType.OPEN) {
            carouselPosition = wrapCarousel(carouselPosition + CAROUSEL_SPEED * 0.76);
        }
        flyProgress = Math.min(1.0, flyProgress + 0.032);
        if (flyProgress < 1.0) {
            return;
        }

        activeSelection.revealed = false;
        cards.add(activeSelection);
        selectionDeck.remove(lockedMeaning);
        carouselPosition = wrapCarousel(carouselPosition);
        activeSelection = null;
        lockedMeaning = null;
        lockedDeckIndex = -1;
        interaction.clearSelectionCounters();
        revealProgress = 0.0;

        if (cards.size() >= 3) {
            finalRevealProgress = 0.0;
            interaction.beginFinalReveal();
        } else {
            interaction.beginRotating();
        }
    }

    private void updateFinalReveal() {
        finalRevealProgress = Math.min(1.0, finalRevealProgress + 0.009);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).revealed = finalCardProgress(i) >= 0.5;
        }
        if (finalRevealProgress >= 1.0) {
            for (Card card : cards) {
                card.revealed = true;
            }
            buildCombinedReading();
            interaction.complete();
        }
    }

    private void updateComplete(GestureType gestureType) {
        if (gestureType != GestureType.FIST || !handDetected) {
            completeFistLatched = false;
            return;
        }
        if (completeFistLatched) {
            return;
        }
        completeFistLatched = true;

        double panelX = canvasWidth * 0.55;
        double panelY = canvasHeight * 0.16;
        double panelW = canvasWidth * 0.39;
        double panelH = canvasHeight * 0.75;
        double buttonY = panelY + panelH - 52;

        if (isInside(handCanvasX, handCanvasY, panelX + 28, buttonY, 104, 30)) {
            restartReading();
        } else if (isInside(handCanvasX, handCanvasY, panelX + 142, buttonY, 116, 30)) {
            Platform.runLater(this::showQuestionDialog);
        } else if (readingPanel.pageCount() > 1
                && isInside(handCanvasX, handCanvasY, panelX + panelW - 96, buttonY, 30, 30)) {
            readingPanel.previousPage();
        } else if (readingPanel.pageCount() > 1
                && isInside(handCanvasX, handCanvasY, panelX + panelW - 58, buttonY, 30, 30)) {
            readingPanel.nextPage();
        }
    }

    private void restartReading() {
        readingGenerationId++;
        init(canvasWidth, canvasHeight);
    }

    private void showQuestionDialog() {
        TextInputDialog dialog = new TextInputDialog(readingQuestion);
        dialog.setTitle("输入新的占卜问题");
        dialog.setHeaderText("写下你此刻最想看清的问题");
        dialog.setContentText("问题：");
        dialog.showAndWait()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .ifPresent(value -> {
                    readingQuestion = value;
                    restartReading();
                });
    }

    private boolean isInside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private double finalCardProgress(int index) {
        if (interaction.is(TarotGestureStateMachine.Phase.COMPLETE)) {
            return 1.0;
        }
        return clamp(finalRevealProgress * 3.0 - index * 0.68, 0.0, 1.0);
    }

    private void buildCombinedReading() {
        if (cards.size() < 3) {
            return;
        }
        Card past = cards.get(0);
        Card present = cards.get(1);
        Card future = cards.get(2);
        combinedInterpretation = String.format(
                "过去由“%s”的%s能量奠定背景；现在“%s”指出你需要正视%s；未来“%s”提示局势将朝着%s展开。三张牌共同强调：先理解来处，再调整当下，未来才会出现可选择的空间。",
                past.meaning.title, primaryKeyword(past), present.meaning.title, primaryKeyword(present),
                future.meaning.title, primaryKeyword(future));
        combinedAdvice = "1. " + past.meaning.advice
                + "\n2. " + present.meaning.advice
                + "\n3. " + future.meaning.advice;
        readingSource = "本地牌义";

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            String meaning = card.reversed ? card.meaning.reversedMeaning : card.meaning.uprightMeaning;
            context.append(slotChinese(i)).append("：")
                    .append(card.meaning.title).append(card.reversed ? "（逆位）" : "（正位）")
                    .append("；关键词：").append(String.join("、", card.meaning.keywords))
                    .append("；牌义：").append(meaning)
                    .append("；原始建议：").append(card.meaning.advice).append('\n');
        }

        long requestId = ++readingGenerationId;
        String fallbackInterpretation = combinedInterpretation;
        String fallbackAdvice = combinedAdvice;
        aiReadingPending = true;
        TarotReadingService.generate(readingQuestion, context.toString(),
                        fallbackInterpretation, fallbackAdvice)
                .thenAccept(result -> Platform.runLater(() -> {
                    if (requestId != readingGenerationId
                            || !interaction.is(TarotGestureStateMachine.Phase.COMPLETE)) {
                        return;
                    }
                    combinedInterpretation = result.interpretation();
                    combinedAdvice = normalizeAdvice(result.advice());
                    readingSource = result.source();
                    aiReadingPending = false;
                }));
    }

    private String normalizeAdvice(String advice) {
        String cleaned = advice == null ? "" : advice.strip();
        if (cleaned.isEmpty()) {
            return combinedAdvice;
        }
        String[] lines = cleaned.split("\\R+");
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip().replaceFirst("^[1-3][.、\\s]+", "");
            if (line.isEmpty()) continue;
            if (normalized.length() > 0) normalized.append('\n');
            normalized.append(i + 1).append(". ").append(line);
        }
        return normalized.length() == 0 ? combinedAdvice : normalized.toString();
    }

    private String primaryKeyword(Card card) {
        if (card.meaning.keywords.length == 0) {
            return card.reversed ? "需要重新审视的" : "正在展开的";
        }
        return card.meaning.keywords[0];
    }

    private double wrapCarousel(double value) {
        if (selectionDeck.isEmpty()) {
            return 0.0;
        }
        double size = selectionDeck.size();
        double wrapped = value % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }

    private void drawReadingHeader(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setStroke(Color.web("#8deedf", 0.28));
        gc.setLineWidth(1.0);
        gc.strokeLine(canvasWidth * 0.22, canvasHeight * 0.052,
                canvasWidth * 0.39, canvasHeight * 0.052);
        gc.strokeLine(canvasWidth * 0.61, canvasHeight * 0.052,
                canvasWidth * 0.78, canvasHeight * 0.052);

        gc.setFill(Color.web("#b88aff", 0.92));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, clamp(canvasWidth * 0.010, 12, 16)));
        gc.fillText("THREE CARD READING", canvasWidth * 0.5, canvasHeight * 0.035);

        gc.setFill(Color.web("#eadbc1"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.SEMI_BOLD,
                clamp(canvasWidth * 0.017, 18, 28)));
        drawWrappedTextCentered(gc, "“" + readingQuestion + "”", canvasWidth * 0.5,
                canvasHeight * 0.072, canvasWidth * 0.62, 29, 2);

        gc.setFill(Color.web("#8fe9de", 0.68));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 13));
        gc.fillText(String.format("已选择  %d / 3", cards.size()), canvasWidth * 0.88, canvasHeight * 0.055);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawFanCarousel(GraphicsContext gc) {
        if (selectionDeck.isEmpty()) {
            return;
        }
        int base = (int) Math.floor(carouselPosition);
        for (int band = 4; band >= 0; band--) {
            for (int virtualIndex = base - 4; virtualIndex <= base + 5; virtualIndex++) {
                double relative = virtualIndex - carouselPosition;
                int currentBand = Math.min(4, (int) Math.floor(Math.abs(relative) + 0.5));
                if (currentBand != band || Math.abs(relative) > 3.65) {
                    continue;
                }
                TarotMeaning meaning = selectionDeck.get(Math.floorMod(virtualIndex, selectionDeck.size()));
                if (interaction.is(TarotGestureStateMachine.Phase.FLYING) && meaning == lockedMeaning) {
                    continue;
                }
                drawFanCard(gc, relative, meaning);
            }
        }
    }

    private void drawFanCard(GraphicsContext gc, double relative, TarotMeaning meaning) {
        double abs = Math.abs(relative);
        double unit = clamp(Math.min(canvasWidth / 1600.0, canvasHeight / 900.0) * 16.0, 9.0, 18.0);
        double xRem = interpolateFan(abs, new double[]{0.0, 11.0, 22.0, 30.0, 39.0});
        double yRem = interpolateFan(abs, new double[]{0.0, 1.3, 4.0, 7.3, 10.8});
        double scale = interpolateFan(abs, new double[]{1.0, 0.9346, 0.8498, 0.7756, 0.62});
        double alpha = abs <= 3.0 ? 1.0 : clamp(1.0 - (abs - 3.0) / 0.65, 0.0, 1.0);
        double cardWidth = centerCardWidth();
        double cardHeight = cardWidth * CARD_ASPECT;
        double entry = easeOutBack(fanEntryProgress);
        double centerX = fanCenterX() + Math.copySign(xRem * unit * entry, relative);
        double centerY = fanCenterY() + yRem * unit * entry + (1.0 - entry) * canvasHeight * 0.34;
        double angle = relative * 7.0 * entry;
        boolean activeReveal = meaning == lockedMeaning && activeSelection != null
                && (interaction.is(TarotGestureStateMachine.Phase.REVEALING)
                || interaction.is(TarotGestureStateMachine.Phase.AWAITING_OPEN));
        boolean isCenterHover = Math.abs(relative) < 0.5 && interaction.is(TarotGestureStateMachine.Phase.ROTATING);

        gc.save();
        gc.setGlobalAlpha(alpha * clamp(fanEntryProgress * 1.8, 0.0, 1.0));
        gc.translate(centerX, centerY);
        gc.rotate(angle);
        double entryScale = 0.5 + 0.5 * clamp(entry, 0.0, 1.08);
        
        // 激活卡牌特效：靠近中心时放大
        if (isCenterHover || activeReveal) {
            scale *= 1.15;
        }
        
        gc.scale(scale * entryScale, scale * entryScale);
        
        // 紫金色交织发光 (box-shadow)
        if (isCenterHover || activeReveal) {
            javafx.scene.effect.DropShadow shadow = new javafx.scene.effect.DropShadow();
            shadow.setColor(Color.web("#ba68c8", 0.8));
            shadow.setRadius(25);
            
            javafx.scene.effect.DropShadow shadow2 = new javafx.scene.effect.DropShadow();
            shadow2.setColor(Color.web("#d4af37", 0.4));
            shadow2.setRadius(50);
            shadow.setInput(shadow2);
            
            gc.setEffect(shadow);
        }

        gc.setFill(Color.web("#000000", 0.34));
        gc.fillRoundRect(-cardWidth / 2.0 + 7, -cardHeight / 2.0 + 11, cardWidth, cardHeight, 20, 20);
        
        // 绘制卡牌
        if (activeReveal) {
            drawFlipCard(gc, -cardWidth / 2.0, -cardHeight / 2.0, cardWidth, cardHeight,
                    activeSelection, revealProgress);
        } else {
            cardRenderer.drawCardBack(gc, -cardWidth / 2.0, -cardHeight / 2.0, cardWidth, cardHeight,
                    clamp(1.0 - abs / 3.0, 0.0, 1.0), isCenterHover);
        }
        gc.setEffect(null);
        gc.restore();
    }

    private double easeOutBack(double progress) {
        double t = clamp(progress, 0.0, 1.0) - 1.0;
        double c1 = 1.18;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * t * t * t + c1 * t * t;
    }

    private double interpolateFan(double absolutePosition, double[] values) {
        double clamped = clamp(absolutePosition, 0.0, values.length - 1.0001);
        int low = (int) Math.floor(clamped);
        int high = Math.min(values.length - 1, low + 1);
        double t = clamped - low;
        return values[low] + (values[high] - values[low]) * t;
    }

    private void drawChosenSlots(GraphicsContext gc) {
        double baseScale = canvasHeight / 1080.0;
        double canvasW = canvasWidth;
        double canvasH = canvasHeight;
        double centerX = canvasW / 2;

        double scrollW = 850.0 * baseScale;
        double scrollH = 90.0 * baseScale;
        double scrollY = canvasH - scrollH - (10.0 * baseScale);

        double altarW = 180.0 * baseScale;
        double altarH = 110.0 * baseScale;
        double altarGap = 50.0 * baseScale;
        
        long now = System.nanoTime();
        double floatOffset = Math.cos(now / 200_000_000.0) * 3.0 * baseScale;
        double altarY = scrollY - altarH - (15.0 * baseScale) + floatOffset;

        double firstAltarX = centerX - (altarW * 1.5 + altarGap);
        double[] altarXCoords = new double[3];

        double slotW = spreadCardWidth();
        double slotH = slotW * CARD_ASPECT;

        double glowPulse = Math.sin(now / 150_000_000.0) * 0.5 + 0.5; // 范围 0.0 ~ 1.0
        double shadowRadius = 15.0 + (20.0 * glowPulse); // 发光半径动态变大变小

        for (int i = 0; i < 3; i++) {
            altarXCoords[i] = firstAltarX + i * (altarW + altarGap);
            double x = spreadSlotX(i);
            double y = spreadSlotY();

            if (altarImage != null) {
                double ax = altarXCoords[i];
                
                // 1. 注入 3D 外发光特效（DropShadow）
                javafx.scene.effect.DropShadow altarGlow = new javafx.scene.effect.DropShadow();
                altarGlow.setColor(Color.web("#ba68c8")); // 祭坛神秘紫发光
                altarGlow.setRadius(shadowRadius);
                altarGlow.setSpread(0.3);

                gc.setEffect(altarGlow); // 挂载特效
                gc.drawImage(altarImage, ax, altarY, altarW, altarH);
                gc.setEffect(null); // 画完立刻解除滤镜，防止污染后续元素

                // 2. 引入【能量加色混合层】（BlendMode.ADD）
                gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.ADD); 

                RadialGradient energyGrad = new RadialGradient(
                    0, 0, ax + altarW/2, altarY + altarH/2 + (10 * baseScale), altarW * 0.25, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ba68c8", 0.6 + (0.3 * glowPulse))), // 中心极亮
                    new Stop(0.5, Color.web("#7b1fa2", 0.2)),
                    new Stop(1, Color.TRANSPARENT)
                );
                gc.setFill(energyGrad);
                gc.fillOval(ax + altarW/2 - (altarW * 0.25), altarY + altarH/2 + (10 * baseScale) - (altarW * 0.25), altarW * 0.5, altarW * 0.5);

                gc.setGlobalBlendMode(javafx.scene.effect.BlendMode.SRC_OVER); // 必须恢复正常的混合模式！

                gc.setFill(Color.web("#d4af37"));
                gc.setFont(Font.font("Times New Roman", 14 * baseScale));
                gc.setTextAlign(TextAlignment.CENTER);
                String label = (i == 0) ? "PAST" : (i == 1) ? "PRESENT" : "FUTURE";
                gc.fillText(label, ax + altarW / 2, altarY + altarH + (15.0 * baseScale));
            } else {
                // 复古石质卡槽 + 凹陷阴影 (Inset shadow)
                gc.setFill(Color.web("#1c1a1a"));
                gc.fillRoundRect(x, y, slotW, slotH, 13 * baseScale, 13 * baseScale);

                javafx.scene.effect.InnerShadow insetShadow = new javafx.scene.effect.InnerShadow();
                insetShadow.setRadius(15 * baseScale);
                insetShadow.setColor(Color.web("#000000", 0.9));
                gc.setEffect(insetShadow);
                gc.setFill(Color.web("#221f1f"));
                gc.fillRoundRect(x, y, slotW, slotH, 13 * baseScale, 13 * baseScale);
                gc.setEffect(null);
                
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setFill(Color.web("#d4af37"));
                gc.setFont(Font.font("Times New Roman", FontWeight.BOLD, 11 * baseScale));
                gc.fillText(SLOT_LABELS[i], x + slotW / 2.0, y - (10 * baseScale));
                gc.fillText(SLOT_NOTES[i], x + slotW / 2.0, y + slotH + (16 * baseScale));
            }

            // 落地脉冲光环效果 (Pulse glow)
            if (interaction.is(TarotGestureStateMachine.Phase.FLYING) && i == cards.size()) {
                if (flyProgress > 0.8) {
                    double pulseRadius = (flyProgress - 0.8) * 5.0 * (20 * baseScale); // 向外扩散
                    double pulseAlpha = 1.0 - (flyProgress - 0.8) * 5.0; // 渐隐
                    gc.setStroke(Color.web("#d4af37", Math.max(0, pulseAlpha)));
                    gc.setLineWidth(3.0 * baseScale);
                    gc.strokeRoundRect(x - pulseRadius, y - pulseRadius, slotW + pulseRadius * 2, slotH + pulseRadius * 2, 13 * baseScale, 13 * baseScale);
                }
            }

            if (i < cards.size()) {
                cardRenderer.drawCardBack(gc, x, y, slotW, slotH, 0.25, false);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawSingleMeaning(GraphicsContext gc) {
        if (activeSelection == null) {
            return;
        }
        double alpha = interaction.is(TarotGestureStateMachine.Phase.FLYING)
                ? clamp(1.0 - flyProgress * 1.45, 0.0, 1.0) : clamp(revealProgress * 1.4, 0.0, 1.0);
        if (alpha <= 0.01) {
            return;
        }
        double x = canvasWidth * 0.695;
        double y = canvasHeight * 0.15;
        double w = canvasWidth * 0.265;
        double h = canvasHeight * 0.60;
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.setFill(Color.web("#000000", 0.30));
        gc.fillRoundRect(x + 8, y + 10, w, h, 24, 24);
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#eadfc9", 0.97)),
                new Stop(0.55, Color.web("#d8c6a8", 0.96)),
                new Stop(1, Color.web("#bca482", 0.94))));
        gc.fillRoundRect(x, y, w, h, 24, 24);
        gc.setStroke(Color.web("#b477df", 0.72));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, y, w, h, 24, 24);
        gc.setStroke(Color.web("#665279", 0.24));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(x + 9, y + 9, w - 18, h - 18, 18, 18);

        int slot = Math.min(cards.size(), 2);
        gc.setFill(Color.web("#76508d"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.SEMI_BOLD, 12));
        gc.fillText("第 " + (slot + 1) + " 张 · " + slotChinese(slot), x + 24, y + 22);
        gc.setFill(Color.web("#241b2d"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, clamp(w * 0.074, 20, 27)));
        gc.fillText(activeSelection.meaning.title, x + 24, y + 50);
        gc.setFill(activeSelection.reversed ? Color.web("#a23f73") : Color.web("#377a70"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.SEMI_BOLD, 13));
        gc.fillText(activeSelection.reversed ? "逆位" : "正位", x + 24, y + 88);

        gc.setFill(Color.web("#624c6d", 0.92));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 13));
        gc.fillText(String.join("  ·  ", activeSelection.meaning.keywords), x + 24, y + 116);

        gc.setFill(Color.web("#392f3f", 0.94));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 14));
        String meaning = activeSelection.reversed
                ? activeSelection.meaning.reversedMeaning : activeSelection.meaning.uprightMeaning;
        double nextY = drawWrappedText(gc, meaning, x + 24, y + 151, w - 48, 23, 6, 14);

        gc.setFill(Color.web("#72428e"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 13));
        gc.fillText("行动提示", x + 24, nextY + 8);
        gc.setFill(Color.web("#423542", 0.90));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 13));
        drawWrappedText(gc, activeSelection.meaning.advice, x + 24, nextY + 34, w - 48, 21, 3, 13);
        gc.restore();
    }

    private void drawSelectionProgress(GraphicsContext gc) {
        if (!interaction.is(TarotGestureStateMachine.Phase.HOLDING)) {
            return;
        }
        double progress = interaction.holdProgress();
        double radius = centerCardWidth() * 0.68;
        double x = fanCenterX();
        double y = fanCenterY();
        gc.setStroke(Color.web("#c8baff", 0.16));
        gc.setLineWidth(5);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.setStroke(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#c66cff")),
                new Stop(1, Color.web("#8ff5e7"))));
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeArc(x - radius, y - radius, radius * 2, radius * 2,
                90, -360 * progress, javafx.scene.shape.ArcType.OPEN);
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#e8dcff"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 14));
        gc.fillText("保持握拳  " + (int) Math.round(progress * 100) + "%", x, y + radius + 17);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawReadingHint(GraphicsContext gc) {
        String hint = switch (interaction.phase()) {
            case ROTATING -> "张开手掌 · 牌组自动轮转     握拳 · 锁定中心牌";
            case HOLDING -> "保持握拳，完成选择";
            case REVEALING -> "牌面正在显现";
            case AWAITING_OPEN -> "阅读牌义后，张开手掌将牌送入牌阵";
            case FLYING -> "卡牌归位，牌组继续流转";
            default -> "";
        };
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#b7dedc", 0.66));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 13));
        gc.fillText(hint, canvasWidth * 0.5, canvasHeight * 0.955);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawFlyingSelection(GraphicsContext gc) {
        if (activeSelection == null) {
            return;
        }
        
        // 贝塞尔曲线平滑飞行轨迹
        double rawP = clamp(flyProgress, 0.0, 1.0);
        // 使用更具动感的缓动
        double p = rawP < 0.5 ? 2 * rawP * rawP : 1 - Math.pow(-2 * rawP + 2, 2) / 2;
        
        int targetIndex = Math.min(cards.size(), 2);
        double startW = centerCardWidth();
        double startH = startW * CARD_ASPECT;
        double targetW = spreadCardWidth();
        double targetH = targetW * CARD_ASPECT;
        
        double startX = flyStartX - startW / 2.0;
        double startY = flyStartY - startH / 2.0;
        double endX = spreadSlotX(targetIndex);
        double endY = spreadSlotY();
        
        // 二次贝塞尔曲线控制点 (产生向上抛落的抛物线弧度)
        double ctrlX = (startX + endX) / 2.0 + (endX - startX) * 0.2;
        double ctrlY = Math.min(startY, endY) - canvasHeight * 0.25;
        
        double invP = 1.0 - p;
        double x = invP * invP * startX + 2 * invP * p * ctrlX + p * p * endX;
        double y = invP * invP * startY + 2 * invP * p * ctrlY + p * p * endY;
        
        double w = lerp(startW, targetW, p);
        double h = lerp(startH, targetH, p);
        double flip = clamp(p / 0.42, 0.0, 1.0);
        double scaleX = Math.max(0.035, Math.abs(Math.cos(Math.PI * flip)));

        gc.save();
        gc.translate(x + w / 2.0, y + h / 2.0);
        gc.rotate(lerp(-3.0, 8.0, Math.sin(Math.PI * p)));
        // 固定尺寸牌图只缓存一次，飞牌过程中通过画布变换缩放。
        gc.scale(scaleX * w / startW, h / startH);
        if (flip < 0.5) {
            cardRenderer.drawCardFront(gc, -startW / 2.0, -startH / 2.0,
                    startW, startH, activeSelection, true);
        } else {
            cardRenderer.drawCardBack(gc, -startW / 2.0, -startH / 2.0,
                    startW, startH, 0.35, false);
        }
        gc.restore();
    }

    private void drawFinalReading(GraphicsContext gc) {
        double cardW = clamp(canvasWidth * 0.082, 92, 124);
        double cardH = cardW * CARD_ASPECT;
        double gap = cardW * 0.36;
        double groupW = cardW * 3 + gap * 2;
        double startX = canvasWidth * 0.29 - groupW / 2.0;
        double y = canvasHeight * 0.255;

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            double x = startX + i * (cardW + gap);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#9feee3", 0.84));
            gc.setFont(Font.font("Georgia", FontWeight.BOLD, 11));
            gc.fillText(SLOT_LABELS[i], x + cardW / 2.0, y - 28);
            drawFlipCard(gc, x, y, cardW, cardH, card, finalCardProgress(i));
            if (finalCardProgress(i) > 0.58) {
                gc.setFill(Color.web("#e3d2b6", 0.88));
                gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 12));
                gc.fillText(primaryKeyword(card), x + cardW / 2.0, y + cardH + 14);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);

        double panelX = canvasWidth * 0.55;
        double panelY = canvasHeight * 0.16;
        double panelW = canvasWidth * 0.39;
        double panelH = canvasHeight * 0.75;
        gc.setFill(Color.web("#000000", 0.32));
        gc.fillRoundRect(panelX + 9, panelY + 11, panelW, panelH, 28, 28);
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#eadfc9", 0.98)),
                new Stop(0.58, Color.web("#d7c4a5", 0.97)),
                new Stop(1, Color.web("#bda582", 0.95))));
        gc.fillRoundRect(panelX, panelY, panelW, panelH, 28, 28);
        gc.setStroke(Color.web("#b575dd", 0.78));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(panelX, panelY, panelW, panelH, 28, 28);
        gc.setStroke(Color.web("#685579", 0.24));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(panelX + 10, panelY + 10, panelW - 20, panelH - 20, 20, 20);

        gc.setFill(Color.web("#76508d"));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
        gc.fillText("YOUR READING", panelX + 28, panelY + 25);
        if (interaction.is(TarotGestureStateMachine.Phase.COMPLETE)) {
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setFill(Color.web("#6d5d70", 0.70));
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 11));
            gc.fillText(readingSource, panelX + panelW - 28, panelY + 27);
            gc.setTextAlign(TextAlignment.LEFT);
        }
        gc.setFill(Color.web("#251c2d"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 25));
        gc.fillText(interaction.is(TarotGestureStateMachine.Phase.COMPLETE)
                ? "三牌综合解读" : "牌阵正在展开", panelX + 28, panelY + 53);

        if (!interaction.is(TarotGestureStateMachine.Phase.COMPLETE)) {
            gc.setFill(Color.web("#514456", 0.76));
            gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 15));
            gc.fillText("三张牌将依次翻开，请等待牌阵完整显现。", panelX + 28, panelY + 103);
            return;
        }

        double contentX = panelX + 28;
        double contentY = panelY + 99;
        double buttonY = panelY + panelH - 52;
        double contentW = panelW - 56;
        double contentH = Math.max(120.0, buttonY - contentY - 16.0);
        readingPanel.updateContent(combinedInterpretation, combinedAdvice, contentW, contentH);
        readingPanel.render(gc, contentX, contentY, contentW, contentH, aiReadingPending);
        drawReadingActions(gc, panelX, buttonY, panelW);
    }

    private void drawReadingActions(GraphicsContext gc, double panelX, double buttonY, double panelW) {
        drawReadingButton(gc, panelX + 28, buttonY, 104, 30, "重新占读");
        drawReadingButton(gc, panelX + 142, buttonY, 116, 30, "输入新问题");

        if (readingPanel.pageCount() > 1) {
            drawReadingButton(gc, panelX + panelW - 96, buttonY, 30, 30, "‹");
            drawReadingButton(gc, panelX + panelW - 58, buttonY, 30, 30, "›");
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setFill(Color.web("#68596c", 0.72));
            gc.setFont(Font.font("Georgia", FontWeight.BOLD, 10));
            gc.fillText(readingPanel.currentPageNumber() + " / " + readingPanel.pageCount(),
                    panelX + panelW - 104, buttonY + 8);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    private void drawReadingButton(GraphicsContext gc, double x, double y, double width, double height,
                                   String label) {
        boolean hovered = handDetected && isInside(handCanvasX, handCanvasY, x, y, width, height);
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(hovered ? "#633c79" : "#31223c", 0.96)),
                new Stop(1, Color.web(hovered ? "#244d50" : "#171321", 0.96))));
        gc.fillRoundRect(x, y, width, height, 12, 12);
        gc.setStroke(Color.web(hovered ? "#9cf3e7" : "#a980c4", hovered ? 0.94 : 0.62));
        gc.setLineWidth(hovered ? 1.5 : 1.0);
        gc.strokeRoundRect(x, y, width, height, 12, 12);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#f0e2ca", hovered ? 1.0 : 0.86));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 11));
        gc.fillText(label, x + width / 2.0, y + 7);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawFlipCard(GraphicsContext gc, double x, double y, double w, double h,
                              Card card, double progress) {
        double p = clamp(progress, 0.0, 1.0);
        double scaleX = Math.max(0.035, Math.abs(Math.cos(Math.PI * p)));
        gc.save();
        gc.translate(x + w / 2.0, y + h / 2.0);
        gc.scale(scaleX, 1.0);
        if (p < 0.5) {
            cardRenderer.drawCardBack(gc, -w / 2.0, -h / 2.0, w, h, 0.8, true);
        } else {
            cardRenderer.drawCardFront(gc, -w / 2.0, -h / 2.0, w, h, card, true);
        }
        gc.restore();
    }

    private double centerCardWidth() {
        double baseScale = canvasHeight / 1080.0;
        return 180.0 * baseScale;
    }

    private double fanCenterX() {
        return canvasWidth * 0.5 + fanShift;
    }

    private double fanCenterY() {
        // ====== 【核心修改 3：阻断旋转牌组向下渗透】 ======
        // 将上方抽卡池的圆心往上提，绝对保证即使窗口变小，它的底部也不会撞到下方的祭坛卡牌
        return canvasHeight * 0.30;
    }

    private double spreadCardWidth() {
        double baseScale = canvasHeight / 1080.0;
        double altarW = 180.0 * baseScale;
        // ====== 【核心修改 1：让卡牌的尺寸绝对受限于祭坛底座的尺寸】 ======
        // 无论窗口怎么缩放，祭坛上的卡牌宽度【绝对不允许】超过当前底座精度的 0.8 倍
        return altarW * 0.8;
    }

    private double spreadSlotX(int index) {
        double baseScale = canvasHeight / 1080.0;
        double canvasW = canvasWidth;
        double centerX = canvasW / 2;
        double altarW = 180.0 * baseScale;
        double altarGap = 50.0 * baseScale;
        double firstAltarX = centerX - (altarW * 1.5 + altarGap);
        double currentAltarX = firstAltarX + index * (altarW + altarGap);
        
        double finalCardWidth = spreadCardWidth();
        
        // 3. 它的 X 坐标必须精准对齐对应祭坛的中心：
        return currentAltarX + (altarW / 2.0) - (finalCardWidth / 2.0);
    }

    private double spreadSlotY() {
        double baseScale = canvasHeight / 1080.0;
        double canvasH = canvasHeight;
        double scrollH = 90.0 * baseScale;
        double scrollY = canvasH - scrollH - (10.0 * baseScale);
        double altarH = 110.0 * baseScale;
        
        long now = System.nanoTime();
        double floatOffset = Math.cos(now / 200_000_000.0) * 3.0 * baseScale;
        double altarY = scrollY - altarH - (15.0 * baseScale) + floatOffset;
        
        double placedCardW = spreadCardWidth();
        // 强制保持塔罗牌的标准比例
        double placedCardH = placedCardW * CARD_ASPECT;
        
        // ====== 【核心修改 2：重新计算 Y 轴绝对安全距离】 ======
        // 祭坛顶部Y - 卡牌高度 + (altarH * 0.15) 产生轻微陷入祭坛的立体感
        double drawY = altarY - placedCardH + (altarH * 0.15);
        return drawY;
    }

    private String slotChinese(int index) {
        return switch (index) {
            case 0 -> "过去";
            case 1 -> "现在";
            default -> "未来";
        };
    }

    private double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private void rebuildSpread() {
        cards.clear();
        hoverPulse = 0.0;
        List<TarotMeaning> shuffled = new ArrayList<>(TAROT_DECK);
        Collections.shuffle(shuffled, RANDOM);
        switch (currentSpreadMode) {
            case SINGLE_CARD:
                createSingleSpread(shuffled);
                break;
            case THREE_CARD:
            default:
                createThreeCardSpread(shuffled);
                break;
        }
    }

    private void createSingleSpread(List<TarotMeaning> shuffled) {
        double cardWidth = clamp(canvasWidth * 0.11, 122, 156);
        double cardHeight = cardWidth * CARD_ASPECT;
        double x = canvasWidth * 0.44 - cardWidth / 2.0;
        double y = canvasHeight * 0.33;
        cards.add(createCard(x, y, cardWidth, cardHeight, "CARD", "CORE MESSAGE", shuffled.get(0), false));
    }

    private void createThreeCardSpread(List<TarotMeaning> shuffled) {
        double centerX = canvasWidth * 0.48;
        double cardWidth = clamp(canvasWidth * 0.095, 110, 150);
        double cardHeight = cardWidth * CARD_ASPECT;
        double gap = clamp(canvasWidth * 0.028, 24, 42);
        double totalWidth = cardWidth * 3 + gap * 2;
        double startX = centerX - totalWidth / 2.0;
        double cardY = canvasHeight * 0.33;

        for (int i = 0; i < 3; i++) {
            cards.add(createCard(
                    startX + i * (cardWidth + gap),
                    cardY,
                    cardWidth,
                    cardHeight,
                    SLOT_LABELS[i],
                    SLOT_NOTES[i],
                    shuffled.get(i),
                    false
            ));
        }
    }

    private void createCelticCrossSpread(List<TarotMeaning> shuffled) {
        String[] labels = {"PRESENT", "CHALLENGE", "ROOT", "PAST", "CROWN", "FUTURE", "SELF", "ENVIRONMENT", "HOPES", "OUTCOME"};
        String[] notes = {
                "THE HEART OF THE MATTER",
                "WHAT CROSSES YOU",
                "FOUNDATION BELOW",
                "WHAT IS FALLING AWAY",
                "GUIDING POSSIBILITY",
                "NEAR FUTURE PATH",
                "YOUR INNER STATE",
                "OUTER INFLUENCES",
                "HOPES AND FEARS",
                "LONGER ARC RESULT"
        };

        double cardWidth = clamp(canvasWidth * 0.074, 90, 114);
        double cardHeight = cardWidth * CARD_ASPECT;
        double centerX = canvasWidth * 0.40;
        double centerY = canvasHeight * 0.48;
        double gap = cardWidth * 0.34;
        double farRightX = canvasWidth * 0.60;
        double topY = canvasHeight * 0.24;

        double[][] positions = {
                {centerX - cardWidth / 2.0, centerY - cardHeight / 2.0},
                {centerX - cardWidth / 2.0 + gap * 0.70, centerY - cardHeight / 2.0 - gap * 0.50},
                {centerX - cardWidth / 2.0, centerY + cardHeight * 0.96},
                {centerX - cardWidth * 1.78, centerY - cardHeight / 2.0},
                {centerX - cardWidth / 2.0, centerY - cardHeight * 1.20},
                {centerX + cardWidth * 1.18, centerY - cardHeight / 2.0},
                {farRightX, topY},
                {farRightX, topY + cardHeight + 18},
                {farRightX, topY + (cardHeight + 18) * 2},
                {farRightX, topY + (cardHeight + 18) * 3}
        };

        for (int i = 0; i < labels.length; i++) {
            cards.add(createCard(
                    positions[i][0],
                    positions[i][1],
                    cardWidth,
                    cardHeight,
                    labels[i],
                    notes[i],
                    shuffled.get(i),
                    i == 1
            ));
        }
    }

    private Card createCard(double x, double y, double width, double height,
                            String slotLabel, String slotNote, TarotMeaning meaning) {
        return createCard(x, y, width, height, slotLabel, slotNote, meaning, false);
    }

    private Card createCard(double x, double y, double width, double height,
                            String slotLabel, String slotNote, TarotMeaning meaning, boolean rotated) {
        return new Card(x, y, width, height, slotLabel, slotNote, meaning, RANDOM.nextDouble() < 0.35, rotated);
    }

    private void cycleSpreadMode() {
        switch (currentSpreadMode) {
            case SINGLE_CARD:
                currentSpreadMode = SpreadMode.THREE_CARD;
                break;
            case THREE_CARD:
            default:
                currentSpreadMode = SpreadMode.SINGLE_CARD;
                break;
        }
        selectedIndex = -1;
        detailIndex = -1;
        flippingIndex = -1;
        flipProgress = 0.0;
        hoverPulse = 0.0;
        score = 0;
        rebuildSpread();
    }

    private int findHoveredCardIndex() {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (handCanvasX >= c.x && handCanvasX <= c.x + c.width
                    && handCanvasY >= c.y && handCanvasY <= c.y + c.height) {
                return i;
            }
        }
        return -1;
    }

    private void drawBackground(GraphicsContext gc) {
        renderCache.drawBackground(gc, canvasWidth, canvasHeight, this::paintStaticBackground);
        drawAstrolabeBase(gc);
        drawDynamicNeonVeil(gc);
    }

    private void drawAstrolabeBase(GraphicsContext gc) {
        double cx = canvasWidth * 0.5;
        double cy = fanCenterY(); // 跟随扇形中心
        double radius = Math.min(canvasWidth, canvasHeight) * 0.35;

        gc.save();
        gc.translate(cx, cy);
        gc.rotate(Math.toDegrees(ambientPulse * 0.15)); // 缓慢旋转

        // 底部发光
        gc.setFill(new RadialGradient(0, 0, 0, 0, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d4af37", 0.15)),
                new Stop(0.8, Color.web("#d4af37", 0.05)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillOval(-radius, -radius, radius * 2, radius * 2);

        // 星盘内圈和外圈
        gc.setStroke(Color.web("#d4af37", 0.4));
        gc.setLineWidth(1.5);
        gc.strokeOval(-radius * 0.9, -radius * 0.9, radius * 1.8, radius * 1.8);
        gc.setStroke(Color.web("#d4af37", 0.2));
        gc.setLineWidth(1.0);
        gc.strokeOval(-radius * 0.6, -radius * 0.6, radius * 1.2, radius * 1.2);
        gc.strokeOval(-radius * 0.3, -radius * 0.3, radius * 0.6, radius * 0.6);

        // 星盘刻度
        for (int i = 0; i < 24; i++) {
            gc.save();
            gc.rotate(i * 15);
            gc.setStroke(Color.web("#d4af37", 0.5));
            gc.strokeLine(0, -radius * 0.9, 0, -radius * 0.85);
            if (i % 2 == 0) {
                gc.setFill(Color.web("#d4af37", 0.6));
                gc.fillOval(-3, -radius * 0.95, 6, 6);
            }
            gc.restore();
        }
        
        // 装饰连线
        gc.setStroke(Color.web("#d4af37", 0.15));
        for (int i = 0; i < 8; i++) {
            gc.save();
            gc.rotate(i * 45);
            gc.strokeLine(0, -radius * 0.3, 0, -radius * 0.9);
            gc.restore();
        }

        gc.restore();
    }

    private void paintStaticBackground(GraphicsContext gc) {
        // 1. 深邃星空与四周暗角 (Radial Gradient Vignette)
        gc.setFill(new RadialGradient(
                0, 0, canvasWidth * 0.5, canvasHeight * 0.5,
                Math.max(canvasWidth, canvasHeight) * 0.7, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#140f2d")), // 中心透出深紫微光
                new Stop(0.6, Color.web("#0a0816")), // 过渡到深黑
                new Stop(1, Color.web("#000000"))  // 边缘纯黑暗角
        ));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        // 绘制静谧星空点缀
        gc.setFill(Color.web("#ffffff", 0.15));
        for (int i = 0; i < 200; i++) {
            double x = (i * 101L + 29) % Math.max(canvasWidth, 1);
            double y = (i * 163L + 47) % Math.max(canvasHeight, 1);
            double size = 0.5 + (i % 3) * 0.5;
            gc.fillOval(x, y, size, size);
        }
    }

    private void drawNeonMandalaBase(GraphicsContext gc, double cx, double cy, double radius) {
        gc.save();
        gc.translate(cx, cy);
        for (int i = 0; i < 12; i++) {
            gc.save();
            gc.rotate(i * 30.0);
            gc.setStroke(Color.web(i % 2 == 0 ? "#b460f0" : "#8df5e6", 0.14));
            gc.setLineWidth(1.15);
            gc.strokeOval(-radius * 0.11, -radius * 0.92,
                    radius * 0.22, radius * 0.66);
            gc.restore();
        }
        gc.setStroke(Color.web("#b9adff", 0.12));
        gc.strokeOval(-radius, -radius, radius * 2, radius * 2);
        gc.strokeOval(-radius * 0.68, -radius * 0.68, radius * 1.36, radius * 1.36);
        gc.setStroke(Color.web("#80eddf", 0.11));
        gc.strokeOval(-radius * 0.28, -radius * 0.28, radius * 0.56, radius * 0.56);
        gc.restore();
    }

    private void drawCornerGlyph(GraphicsContext gc, double cx, double cy, int direction) {
        gc.save();
        gc.translate(cx, cy);
        gc.scale(direction, 1);
        gc.setStroke(Color.web("#87eee0", 0.16));
        gc.setLineWidth(1.2);
        gc.strokeArc(-24, -14, 48, 28, 195, 150, javafx.scene.shape.ArcType.OPEN);
        gc.strokeArc(-13, -26, 26, 52, 70, 215, javafx.scene.shape.ArcType.OPEN);
        gc.setStroke(Color.web("#bb63ee", 0.18));
        gc.strokeOval(15, -19, 8, 8);
        gc.strokeLine(-30, 17, 6, -20);
        gc.restore();
    }

    private void drawDynamicNeonVeil(GraphicsContext gc) {
        double cx = canvasWidth * 0.5;
        double cy = canvasHeight * 0.48;
        double radius = clamp(Math.min(canvasWidth, canvasHeight) * 0.245, 150, 238);
        gc.setLineCap(StrokeLineCap.ROUND);
        for (int i = 0; i < 3; i++) {
            double phase = ambientPulse * (0.38 + i * 0.07) + i * 1.8;
            gc.setStroke(Color.web(i % 2 == 0 ? "#b86cff" : "#83f0e1", 0.12 + i * 0.025));
            gc.setLineWidth(1.1 + i * 0.35);
            gc.strokeArc(cx - radius - i * 13, cy - radius - i * 13,
                    (radius + i * 13) * 2, (radius + i * 13) * 2,
                    Math.toDegrees(phase), 54 + i * 18, javafx.scene.shape.ArcType.OPEN);
        }
        gc.setLineCap(StrokeLineCap.BUTT);

        for (int i = 0; i < 26; i++) {
            double px = 48 + Math.floorMod((long) (i * 79 + ambientPulse * 12 * (i % 3 + 1)),
                    Math.max(canvasWidth - 96, 1));
            double py = 42 + Math.floorMod((long) (i * 131 - ambientPulse * 9 * (i % 2 + 1)),
                    Math.max(canvasHeight - 84, 1));
            double pulse = 0.55 + (Math.sin(ambientPulse * 1.4 + i) + 1.0) * 0.55;
            gc.setFill(Color.web(i % 3 == 0 ? "#8ff5e8" : "#c277f1", 0.34));
            gc.fillOval(px, py, pulse, pulse);
        }
    }

    private void drawCandle(GraphicsContext gc, double x, double y) {
        // Wax
        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#D1C4A5")),
                new Stop(0.5, Color.web("#F1E9D2")),
                new Stop(1, Color.web("#A39678"))));
        gc.fillRoundRect(x - 10, y, 20, 60, 4, 4);
        // Flame
        double flameH = 15 + Math.sin(ambientPulse * 15 + x) * 3;
        gc.setFill(new RadialGradient(0, 0, x, y - 5, 20, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#FFFFFF", 0.9)),
                new Stop(0.3, Color.web("#FFD700", 0.7)),
                new Stop(1, Color.TRANSPARENT)));
        gc.fillOval(x - 15, y - 5 - flameH/2, 30, flameH * 2);
    }

    private void drawTopBar(GraphicsContext gc) {
        double barY = 24;
        double barH = 60;
        double inset = 10;

        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1A0B2E", 0.95)),
                new Stop(0.18, Color.web("#0A0A1A", 0.95)),
                new Stop(0.5, Color.web("#2D1B4E", 0.95)),
                new Stop(0.82, Color.web("#0A0A1A", 0.95)),
                new Stop(1, Color.web("#1A0B2E", 0.95))));
        gc.fillRoundRect(inset, barY, canvasWidth - inset * 2, barH, 12, 12);
        gc.setStroke(Color.web("#D4AF37", 0.86));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(inset, barY, canvasWidth - inset * 2, barH, 12, 12);
        gc.setStroke(Color.web("#B8860B", 0.34));
        gc.strokeLine(inset + 18, barY + 14, canvasWidth - inset - 18, barY + 14);
        gc.strokeLine(inset + 18, barY + barH - 14, canvasWidth - inset - 18, barY + barH - 14);

        drawMetalPlate(gc, 18, barY + 10, 112, 36, "BACK", false);
        drawMetalPlate(gc, canvasWidth - 122, barY + 10, 104, 36, "RE-DEAL", false);

        gc.setFill(Color.web("#D4AF37", 0.8));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 16));
        gc.fillText("ᚨᚱᚲᚨᚾᚨ", 150, barY + 17);
        gc.fillText("ᚾᛁᛋᚲᛟᛞᛁᛗ", canvasWidth - 286, barY + 17);

        double centerW = 340;
        double centerX = canvasWidth / 2.0 - centerW / 2.0;
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2D1B4E")),
                new Stop(0.5, Color.web("#0A0A1A")),
                new Stop(1, Color.web("#2D1B4E"))));
        gc.fillRoundRect(centerX, barY + 2, centerW, 46, 16, 16);
        gc.setStroke(Color.web("#D4AF37", 0.88));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(centerX, barY + 2, centerW, 46, 16, 16);
        gc.setFill(Color.web("#F3E5AB"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 18));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(currentSpreadMode == SpreadMode.SINGLE_CARD ? "Single Card Enlightenment" : "Three-Card Spread",
                canvasWidth / 2.0, barY + 15);
        gc.setTextAlign(TextAlignment.LEFT);

        double tabsCenter = canvasWidth * 0.50;
        double tabsGap = 186;
        drawTopTab(gc, "单张启示", tabsCenter - tabsGap / 2.0, currentSpreadMode == SpreadMode.SINGLE_CARD);
        drawTopTab(gc, "三张流向", tabsCenter + tabsGap / 2.0, currentSpreadMode == SpreadMode.THREE_CARD);
    }

    private void drawTopTab(GraphicsContext gc, String text, double centerX, boolean active) {
        double w = text.length() * 14.0 + 78;
        double x = centerX - w / 2.0;
        double y = 72;
        double h = 34;
        if (active) {
            gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#3A1F62")),
                    new Stop(1, Color.web("#1A0B2E"))));
            gc.fillRoundRect(x, y, w, h, 10, 10);
            gc.setStroke(Color.web("#D4AF37", 0.95));
            gc.strokeRoundRect(x, y, w, h, 10, 10);
            gc.setFill(new RadialGradient(0, 0, x + 18, y + 17, 8, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#FFF8DC")),
                    new Stop(1, Color.web("#DAA520"))));
            gc.fillOval(x + 10, y + 9, 16, 16);
        } else {
            gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#1A0B2E")),
                    new Stop(1, Color.web("#05050A"))));
            gc.fillRoundRect(x, y, w, h, 10, 10);
            gc.setStroke(Color.web("#8B6508", 0.82));
            gc.strokeRoundRect(x, y, w, h, 10, 10);
            gc.setFill(Color.web("#5C4033"));
            gc.fillOval(x + 12, y + 11, 10, 10);
        }
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 14));
        gc.setFill(active ? Color.web("#FFF8DC") : Color.web("#B8860B"));
        gc.fillText(text, x + 34, y + 8);
    }

    private void drawMetalPlate(GraphicsContext gc, double x, double y, double w, double h,
                                String label, boolean active) {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(active ? "#3A1F62" : "#1A0B2E")),
                new Stop(1, Color.web(active ? "#1A0B2E" : "#05050A"))));
        gc.fillRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(Color.web(active ? "#D4AF37" : "#8B6508", 0.92));
        gc.setLineWidth(1.1);
        gc.strokeRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(Color.web("#DAA520", 0.30));
        gc.strokeLine(x + 12, y + h - 10, x + w - 12, y + h - 10);
        gc.setFill(Color.web(active ? "#FFF8DC" : "#DAA520"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, x + w / 2.0, y + h / 2.0 + 4);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawDeckPanel(GraphicsContext gc) {
        double x = 54;
        double y = canvasHeight * 0.25;
        double panelW = canvasWidth * 0.13;

        gc.setFill(Color.web("#d7bf94"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 11));
        gc.fillText("秘仪牌堆", x, y - 26);

        double cardW = clamp(panelW * 0.5, 70, 92);
        double cardH = cardW * 1.6;
        double deckBreath = (Math.sin(ambientPulse) + 1.0) * 0.5;
        double deckGlowY = y - deckBreath * 4.0;

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#5a4330", 0.96)),
                new Stop(1, Color.web("#2b2117", 0.96))));
        gc.fillOval(x - 26, deckGlowY + cardH - 4, cardW + 52, 28);
        gc.setStroke(Color.web("#a98555", 0.72));
        gc.strokeOval(x - 26, deckGlowY + cardH - 4, cardW + 52, 28);

        Paint deckAura = new RadialGradient(
                0, 0, x + cardW / 2.0, deckGlowY + cardH * 0.46, cardW * 0.95, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f6d48d", 0.18 + deckBreath * 0.16)),
                new Stop(0.52, Color.web("#b88546", 0.12 + deckBreath * 0.10)),
                new Stop(1, Color.TRANSPARENT)
        );
        gc.setFill(deckAura);
        gc.fillOval(x - 24, deckGlowY - 18, cardW + 48, cardH + 40);

        cardRenderer.drawCardBack(gc, x, deckGlowY, cardW, cardH, 0.35 + deckBreath * 0.75, true);

        gc.setFill(Color.web("#916740", 0.20));
        gc.fillOval(x + 10, deckGlowY + cardH - 12, cardW - 20, 24);
        gc.setStroke(Color.web("#dcb476", 0.22 + deckBreath * 0.16));
        gc.strokeOval(x - 10, deckGlowY + cardH - 18, cardW + 20, 36);
        gc.setStroke(Color.web("#f0ca79", 0.14 + deckBreath * 0.14));
        gc.strokeOval(x - 18, deckGlowY + cardH - 26, cardW + 36, 52);

        double shuffleY = deckGlowY + cardH + 16;
        gc.setFill(Color.web("#e0cda8"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 11));
        gc.fillText("LEVER", x + 4, shuffleY - 14);

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4a382b")),
                new Stop(1, Color.web("#211814"))));
        gc.fillRoundRect(x - 10, shuffleY + 6, 72, 54, 14, 14);
        gc.setStroke(Color.web("#9e7d52", 0.80));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x - 10, shuffleY + 6, 72, 54, 14, 14);
        gc.setStroke(Color.web("#7d6243", 0.60));
        gc.strokeRoundRect(x - 4, shuffleY + 12, 60, 42, 10, 10);

        gc.setFill(Color.web("#3a281c"));
        gc.fillOval(x + 2, shuffleY + 28, 42, 12);
        gc.setStroke(Color.web("#d7bc8a"));
        gc.setLineWidth(4);
        gc.strokeLine(x + 12, shuffleY + 32, x + 40, shuffleY + 20);
        gc.setLineWidth(1.4);
        gc.setStroke(Color.web("#f0d8ab"));
        gc.strokeLine(x + 13, shuffleY + 30, x + 39, shuffleY + 18);
        gc.setFill(new RadialGradient(
                0, 0, x + 42, shuffleY + 18, 9, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ead6b2")),
                new Stop(1, Color.web("#a37a46"))));
        gc.fillOval(x + 34, shuffleY + 10, 16, 16);
        gc.setStroke(Color.web("#7b5d37", 0.88));
        gc.strokeOval(x + 34, shuffleY + 10, 16, 16);

        gc.setFill(Color.web("#d7c29f"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 11));
        gc.fillText("REMAINING " + (TAROT_DECK.size() - cards.size()), x - 4, shuffleY + 72);
    }

    private void drawSpreadPanel(GraphicsContext gc) {
        double x = canvasWidth * 0.24;
        double y = canvasHeight * 0.19;
        double w = canvasWidth * 0.42;
        double h = canvasHeight * 0.42;
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.web("#D4AF37", 0.90));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 18));
        gc.fillText(currentSpreadMode == SpreadMode.SINGLE_CARD ? "Single-Card Revelation..." : "Three-Card Spread...",
                x + w / 2.0, y + 2);
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1A0B2E", 0.96)),
                new Stop(0.25, Color.web("#05050A", 0.98)),
                new Stop(0.75, Color.web("#05050A", 0.98)),
                new Stop(1, Color.web("#1A0B2E", 0.96))));
        gc.fillRoundRect(x + 8, y + 26, w - 16, h - 12, 16, 16);
        gc.setStroke(Color.web("#B8860B", 0.82));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(x + 8, y + 26, w - 16, h - 12, 16, 16);
        gc.setStroke(Color.web("#D4AF37", 0.32));
        gc.strokeRoundRect(x + 20, y + 38, w - 40, h - 36, 12, 12);

        double plaqueW = 168;
        double plaqueX = x + w / 2.0 - plaqueW / 2.0;
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#3A1F62")),
                new Stop(1, Color.web("#1A0B2E"))));
        gc.fillRoundRect(plaqueX, y + 30, plaqueW, 30, 10, 10);
        gc.setStroke(Color.web("#D4AF37", 0.82));
        gc.strokeRoundRect(plaqueX, y + 30, plaqueW, 30, 10, 10);
        gc.setFill(Color.web("#FFF8DC"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("CURRENT SPREAD", x + w / 2.0, y + 49);
        gc.setTextAlign(TextAlignment.LEFT);

        drawGoldSweepBand(gc, x + 18, y + 62, w - 36, h - 50, 0.26, 0.22);

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            gc.setFill(Color.web("#D4AF37"));
            gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(card.slotLabel, card.x + card.width / 2.0, card.y - 18);

            if (!card.revealed) {
                gc.setStroke(Color.web("#B8860B", 0.38));
                gc.setLineDashes(6);
                gc.strokeRoundRect(card.x - 4, card.y - 4, card.width + 8, card.height + 8, 18, 18);
                gc.setLineDashes(null);
            }

            drawSpreadCard(gc, card, i == flippingIndex ? flipProgress : 1.0, i);

            gc.setFill(Color.web("#DAA520", 0.78));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 11));
            gc.fillText(card.slotNote.toUpperCase(), card.x + card.width / 2.0, card.y + card.height + 20);
        }
        gc.setTextAlign(TextAlignment.LEFT);

        if (selectedIndex >= 0 && selectedIndex < cards.size()) {
            Card selected = cards.get(selectedIndex);
            if (selected.revealed && flippingIndex < 0) {
                drawMagnifiedCardPreview(gc, selected);
            }
        }
    }

    private void drawSpreadCard(GraphicsContext gc, Card card, double progress, int index) {
        double easedProgress = easeInOut(progress);
        double displayWidth = card.width;
        double displayX = card.x;
        boolean flipping = index == flippingIndex;
        boolean activeCard = index == selectedIndex;
        double hoverLift = activeCard && flippingIndex < 0 ? 8.0 * hoverPulse : 0.0;

        if (flipping) {
            displayWidth = card.width * Math.max(0.06, Math.abs(0.5 - easedProgress) * 2.0);
            displayX = card.x + (card.width - displayWidth) / 2.0;
        }

        boolean showFront = card.revealed && !(flipping && easedProgress < 0.5);

        gc.save();
        if (card.rotated && !flipping) {
            double pivotX = card.x + card.width / 2.0;
            double pivotY = card.y + card.height / 2.0 - hoverLift;
            gc.translate(pivotX, pivotY);
            gc.rotate(90);
            displayX = -card.width / 2.0;
            double drawY = -card.height / 2.0;
            if (showFront) {
                cardRenderer.drawCardFront(gc, displayX, drawY, card.width, card.height, card, activeCard);
            } else {
                cardRenderer.drawCardBack(gc, displayX, drawY, card.width, card.height, activeCard ? 1.0 : 0.0, false);
            }
            if (activeCard && flippingIndex < 0) {
                cardRenderer.drawActiveCardHalo(gc, -card.width / 2.0, -card.height / 2.0, card.width, card.height);
            }
        } else {
            double drawY = card.y - hoverLift;
            if (showFront) {
                cardRenderer.drawCardFront(gc, displayX, drawY, displayWidth, card.height, card, activeCard);
            } else {
                cardRenderer.drawCardBack(gc, displayX, drawY, displayWidth, card.height, activeCard ? 1.0 : 0.0, false);
            }

            if (flipping) {
                drawFlipEnergy(gc, displayX, drawY, displayWidth, card.height, easedProgress);
            }

            if (activeCard && flippingIndex < 0) {
                cardRenderer.drawActiveCardHalo(gc, card.x, drawY, card.width, card.height);
            }
        }
        gc.restore();
    }

    private double easeInOut(double t) {
        double clamped = clamp(t, 0.0, 1.0);
        return clamped < 0.5
                ? 4 * clamped * clamped * clamped
                : 1 - Math.pow(-2 * clamped + 2, 3) / 2.0;
    }

    private void drawFlipEnergy(GraphicsContext gc, double x, double y, double w, double h, double easedProgress) {
        double centerX = x + w / 2.0;
        double centerStrength = 1.0 - Math.min(1.0, Math.abs(easedProgress - 0.5) / 0.5);
        double glowAlpha = 0.12 + centerStrength * 0.38;
        double sweepX = x + w * easedProgress;

        Paint coreGlow = new RadialGradient(
                0, 0, centerX, y + h * 0.5, Math.max(w, h) * 0.42, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffe8b2", glowAlpha)),
                new Stop(0.45, Color.web("#b87cff", glowAlpha * 0.52)),
                new Stop(1, Color.TRANSPARENT)
        );
        gc.setFill(coreGlow);
        gc.fillOval(x - w * 0.18, y + h * 0.10, w * 1.36, h * 0.80);

        gc.setStroke(Color.web("#ffe1a0", 0.28 + centerStrength * 0.44));
        gc.setLineWidth(1.0 + centerStrength * 1.6);
        gc.strokeLine(centerX, y + 10, centerX, y + h - 10);

        gc.setStroke(Color.web("#f3c86e", 0.18 + centerStrength * 0.34));
        gc.setLineWidth(0.9 + centerStrength);
        gc.strokeLine(centerX - 8, y + 18, centerX + 8, y + 18);
        gc.strokeLine(centerX - 8, y + h - 18, centerX + 8, y + h - 18);

        gc.setStroke(Color.web("#d896ff", 0.16 + centerStrength * 0.26));
        gc.setLineWidth(1.1);
        gc.strokeLine(sweepX, y + h * 0.14, sweepX, y + h * 0.86);
    }

    private void drawMagnifiedCardPreview(GraphicsContext gc, Card card) {
        double scale = 1.22 + hoverPulse * 0.06;
        double previewW = card.width * scale;
        double previewH = card.height * scale;
        double previewX = card.x + (card.width - previewW) / 2.0;
        double previewY = card.y - previewH * 0.10 - 8;

        gc.save();
        gc.setGlobalAlpha(0.96);
        cardRenderer.drawCardFront(gc, previewX, previewY, previewW, previewH, card, true);
        cardRenderer.drawActiveCardHalo(gc, previewX, previewY, previewW, previewH);
        gc.restore();
    }

    private void drawCelticCrossGuide(GraphicsContext gc) {
        double cardWidth = clamp(canvasWidth * 0.074, 90, 114);
        double cardHeight = cardWidth * CARD_ASPECT;
        double centerX = canvasWidth * 0.40;
        double centerY = canvasHeight * 0.43;
        double farRightX = canvasWidth * 0.60;
        double topY = canvasHeight * 0.19;

        gc.setStroke(Color.web("#8a54be", 0.28));
        gc.setLineWidth(1.0);
        gc.strokeOval(centerX - cardWidth * 1.9, centerY - cardHeight * 1.25, cardWidth * 3.8, cardHeight * 2.5);
        gc.strokeLine(centerX - cardWidth * 1.5, centerY, centerX + cardWidth * 1.5, centerY);
        gc.strokeLine(centerX, centerY - cardHeight * 1.05, centerX, centerY + cardHeight * 1.05);

        gc.setStroke(Color.web("#714394", 0.24));
        gc.strokeRoundRect(farRightX - 14, topY - 10, cardWidth + 28, (cardHeight + 18) * 4 - 4, 18, 18);

        gc.setFill(Color.web("#e7cb8c"));
        gc.setFont(Font.font("Microsoft YaHei UI", 9));
        gc.fillText("十字核心", centerX - 24, centerY - cardHeight * 1.33);
        gc.fillText("命运之柱", farRightX + cardWidth * 0.20, topY - 28);
    }

    private void drawDetailPanel(GraphicsContext gc) {
        double x = canvasWidth * 0.69;
        double y = canvasHeight * 0.27;
        double w = canvasWidth * 0.24;
        double h = canvasHeight * 0.46;

        gc.setFill(Color.web("#D4AF37"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 10));
        gc.fillText("详细解读", x, y - 20);

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2D1B4E", 0.98)),
                new Stop(0.20, Color.web("#1A0B2E", 0.98)),
                new Stop(1, Color.web("#05050A", 0.98))));
        gc.fillRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.web("#B8860B", 0.78));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.web("#D4AF37", 0.32));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(x + 10, y + 10, w - 20, h - 20, 14, 14);
        drawGoldSweepBand(gc, x + 8, y + 8, w - 16, h - 16, 0.54, 0.16);
        drawDetailBoxHardware(gc, x, y, w, h);

        Card card = getActiveDetailCard();
        if (card == null || !card.revealed) {
            gc.setFill(Color.web("#8B6508"));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 28));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("✧", x + w / 2.0, y + h * 0.42);
            gc.setFill(Color.web("#FFF8DC"));
            gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 14));
            drawWrappedTextCentered(gc, "请先翻开一张牌", x + w / 2.0, y + h * 0.50, w - 56, 18, 2);
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 12));
            gc.setFill(Color.web("#DAA520"));
            drawWrappedTextCentered(gc, "右侧将显现这张牌的深层讯息。", x + w / 2.0, y + h * 0.57, w - 56, 16, 2);
            gc.setTextAlign(TextAlignment.LEFT);
            return;
        }

        double paperX = x + 22;
        double paperY = y + 22;
        double paperW = w - 44;
        double paperH = h - 44;
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#e9d9bc", 0.95)),
                new Stop(1, Color.web("#d3b98c", 0.92))));
        gc.fillRoundRect(paperX, paperY, paperW, paperH, 12, 12);
        gc.setStroke(Color.web("#8f6f47", 0.58));
        gc.strokeRoundRect(paperX, paperY, paperW, paperH, 12, 12);
        gc.setStroke(Color.web("#b89261", 0.26));
        gc.strokeLine(paperX + 16, paperY + 46, paperX + paperW - 16, paperY + 46);

        gc.setFill(Color.web("#7e5b35"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 10));
        gc.fillText("CARD ORACLE", paperX + 16, paperY + 18);
        gc.setFill(Color.web("#4f3422"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 28));
        gc.fillText(card.meaning.title, paperX + 16, paperY + 44);
        gc.setFill(Color.web("#7b6247"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 11));
        gc.fillText(card.meaning.family + " · " + card.meaning.number, paperX + 16, paperY + 64);

        double tagW = 66;
        gc.setFill(Color.web("#7a5a35"));
        gc.fillRoundRect(paperX + paperW - tagW - 12, paperY + 12, tagW, 22, 10, 10);
        gc.setFill(Color.web("#f4dfb2"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 11));
        gc.fillText(card.reversed ? "逆位" : "正位", paperX + paperW - tagW + 6, paperY + 17);

        gc.setFill(Color.web("#6e512f"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 9));
        gc.fillText("KEYWORDS", paperX + 16, paperY + 88);
        gc.setFill(Color.web("#7c5c34"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 12));
        gc.fillText("核心关键词", paperX + 16, paperY + 104);
        gc.setFill(Color.web("#4b3524"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, 13));
        drawWrappedText(gc, String.join("、", card.meaning.keywords), paperX + 16, paperY + 124, paperW - 32, 18, 2, 13);

        gc.setFill(Color.web("#6e512f"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 9));
        gc.fillText("INTERPRETATION", paperX + 16, paperY + 158);
        gc.setFill(Color.web("#7c5c34"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 12));
        gc.fillText("牌义启示", paperX + 16, paperY + 174);
        gc.setFill(Color.web("#4b3524"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, 13));
        String meaning = card.reversed ? card.meaning.reversedMeaning : card.meaning.uprightMeaning;
        double afterMeaningY = drawWrappedText(gc, meaning, paperX + 16, paperY + 194, paperW - 32, 19, 8, 13);

        gc.setStroke(Color.web("#a37d4f", 0.40));
        gc.strokeLine(paperX + 18, afterMeaningY + 2, paperX + paperW - 18, afterMeaningY + 2);
        gc.setFill(Color.web("#6e512f"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 9));
        gc.fillText("GUIDANCE", paperX + 16, afterMeaningY + 18);
        gc.setFill(Color.web("#7c5c34"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 12));
        gc.fillText("行动建议", paperX + 16, afterMeaningY + 34);
        gc.setFill(Color.web("#4b3524"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, 12));
        drawWrappedText(gc, card.meaning.advice, paperX + 16, afterMeaningY + 54, paperW - 32, 18, 3, 12);
    }

    private void drawFlowingGoldBackdrop(GraphicsContext gc) {
        double phase = ambientPulse;
        for (int i = 0; i < 3; i++) {
            double ribbonY = canvasHeight * (0.20 + i * 0.23) + Math.sin(phase + i * 0.9) * 10;
            double ribbonH = 42 + i * 8;
            double ribbonX = -canvasWidth * 0.08 + Math.sin(phase * 0.45 + i) * 28;
            Paint ribbon = new LinearGradient(
                    0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.TRANSPARENT),
                    new Stop(clamp(0.18 + Math.sin(phase + i) * 0.05, 0.10, 0.26), Color.web("#D4AF37", 0.00)),
                    new Stop(clamp(0.34 + Math.sin(phase + i * 1.2) * 0.06, 0.24, 0.46), Color.web("#D4AF37", 0.12)),
                    new Stop(clamp(0.50 + Math.cos(phase * 0.8 + i) * 0.05, 0.42, 0.58), Color.web("#9B5DE5", 0.20)),
                    new Stop(clamp(0.66 + Math.sin(phase * 0.6 + i) * 0.06, 0.56, 0.78), Color.web("#D4AF37", 0.11)),
                    new Stop(1, Color.TRANSPARENT)
            );
            gc.setFill(ribbon);
            gc.fillRoundRect(ribbonX, ribbonY, canvasWidth * 1.18, ribbonH, ribbonH, ribbonH);
        }
    }

    private void drawGoldSweepBand(GraphicsContext gc, double x, double y, double w, double h,
                                   double verticalBias, double alphaScale) {
        double sweep = (Math.sin(ambientPulse * 0.7 + verticalBias * 6) + 1.0) * 0.5;
        double bandY = y + h * verticalBias + sweep * h * 0.22;
        Paint sweepPaint = new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.16, Color.web("#f0ca79", 0.00)),
                new Stop(0.42, Color.web("#f0ca79", alphaScale * 0.55)),
                new Stop(0.50, Color.web("#fff1bf", alphaScale)),
                new Stop(0.60, Color.web("#d9a24d", alphaScale * 0.52)),
                new Stop(1, Color.TRANSPARENT)
        );
        gc.setFill(sweepPaint);
        gc.fillRoundRect(x, bandY, w, 14, 12, 12);

        gc.setStroke(Color.web("#f0ca79", alphaScale * 0.60));
        gc.setLineWidth(0.8);
        gc.strokeLine(x + 18, bandY + 7, x + w - 18, bandY + 7);
    }

    private Card getActiveDetailCard() {
        if (detailIndex >= 0 && detailIndex < cards.size()) {
            return cards.get(detailIndex);
        }
        for (Card card : cards) {
            if (card.revealed) {
                return card;
            }
        }
        return null;
    }

    private void drawReadingNotes(GraphicsContext gc) {
        double x = canvasWidth * 0.22;
        double y = canvasHeight * 0.69;
        double w = canvasWidth * 0.48;
        double h = 118;

        gc.setFill(Color.web("#dcc296"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 10));
        gc.fillText("✧ 占读记录", x, y - 24);

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ead8b5", 0.98)),
                new Stop(0.55, Color.web("#d7bb8c", 0.95)),
                new Stop(1, Color.web("#b8925d", 0.92))));
        gc.fillRoundRect(x, y, w, h, 16, 16);
        gc.setFill(Color.web("#8a6841", 0.42));
        gc.fillOval(x - 16, y + 8, 36, 96);
        gc.fillOval(x + w - 20, y + 8, 36, 96);
        gc.setStroke(Color.web("#8f6f47", 0.70));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y, w, h, 16, 16);
        gc.setStroke(Color.web("#c39d68", 0.46));
        gc.strokeLine(x + 24, y + 32, x + w - 24, y + 32);

        gc.setFill(Color.web("#4f3422"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 18));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("READING SUMMARY...", x + w / 2.0, y + 16);
        gc.setFill(Color.web("#5e452b"));
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 18));
        gc.fillText("阅读纪要", x + w / 2.0, y + 40);

        int visibleCount = cards.size();
        double gap = 12;
        double cellW = visibleCount == 1 ? w - 40 : (w - 52 - gap * (visibleCount - 1)) / visibleCount;
        double cellX = x + 26;
        double cellY = y + 50;
        for (int i = 0; i < visibleCount; i++) {
            Card card = cards.get(i);
            double rowX = cellX + i * (cellW + gap);
            gc.setFill(Color.web("#eedcbc", 0.92));
            gc.fillRoundRect(rowX, cellY, cellW, 54, 10, 10);
            gc.setStroke(Color.web("#b39263", 0.60));
            gc.strokeRoundRect(rowX, cellY, cellW, 54, 10, 10);
            gc.setStroke(Color.web("#b39263", 0.26));
            gc.strokeLine(rowX + 12, cellY + 26, rowX + cellW - 12, cellY + 26);

            gc.setFill(Color.web("#4d3321"));
            gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 16));
            gc.fillText(card.revealed ? card.meaning.title : "未揭示", rowX + cellW / 2.0, cellY + 22);
            gc.setFill(Color.web("#654a32"));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 11));
            gc.fillText(card.slotNote.toUpperCase(), rowX + cellW / 2.0, cellY + 44);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawBottomHint(GraphicsContext gc) {
        if (scrollImage != null) {
            double baseScale = canvasHeight / 1080.0;
            double canvasW = canvasWidth;
            double canvasH = canvasHeight;
            double centerX = canvasW / 2;

            double scrollW = 850.0 * baseScale; 
            double scrollH = 90.0 * baseScale;  
            double scrollX = centerX - scrollW / 2;
            double scrollY = canvasH - scrollH - (10.0 * baseScale);

            // 绘制羊皮卷
            gc.drawImage(scrollImage, scrollX, scrollY, scrollW, scrollH);

            // 在羊皮卷正中央绘制引导文字
            gc.setFont(Font.font("Times New Roman", FontWeight.BOLD, 15 * baseScale));
            gc.setFill(Color.web("#2a1a08")); // 复古深褐色
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("张开手掌 · 牌组自动轮转    |    握拳 · 锁定中心牌", centerX, scrollY + scrollH / 2 + (5.0 * baseScale));
            gc.setTextAlign(TextAlignment.LEFT);
            return;
        }

        double barW = 540;
        double barH = 50;
        double x = canvasWidth / 2.0 - barW / 2.0;
        double y = canvasHeight - barH - 30; // 居中悬浮在底部

        // 半透明、带有金属描边的底部提示栏
        gc.setFill(Color.web("#0a0816", 0.85)); // 深色半透明背景
        gc.fillRoundRect(x, y, barW, barH, 25, 25);
        
        // 金属质感描边
        gc.setStroke(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#8b6508", 0.8)),
                new Stop(0.5, Color.web("#f3d79c", 0.9)),
                new Stop(1, Color.web("#8b6508", 0.8))));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, y, barW, barH, 25, 25);
        
        // 内部微光
        javafx.scene.effect.InnerShadow innerGlow = new javafx.scene.effect.InnerShadow();
        innerGlow.setColor(Color.web("#d4af37", 0.3));
        innerGlow.setRadius(10);
        gc.setEffect(innerGlow);
        gc.fillRoundRect(x, y, barW, barH, 25, 25);
        gc.setEffect(null);

        gc.setFill(Color.web("#e7cb8c"));
        gc.setFont(Font.font("Microsoft YaHei UI", FontWeight.NORMAL, 14));
        gc.setTextAlign(TextAlignment.CENTER);
        
        // 使用 Emoji 作为简易的手势图标
        String text = "✋  张开手掌：轮转 / 洗牌       |       ✊  握拳锁定：选择 / 翻牌";
        gc.fillText(text, canvasWidth / 2.0, y + barH / 2.0 + 5);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawDetailBoxHardware(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(Color.web("#8a6c46", 0.88));
        gc.fillRoundRect(x + w * 0.47, y - 6, 22, 12, 6, 6);
        gc.fillRoundRect(x + w * 0.47, y + h - 6, 22, 12, 6, 6);

        gc.setStroke(Color.web("#9c7b4d", 0.80));
        gc.setLineWidth(1.0);
        gc.strokeLine(x + 12, y + 16, x + 24, y + 16);
        gc.strokeLine(x + w - 24, y + 16, x + w - 12, y + 16);
        gc.strokeLine(x + 12, y + h - 16, x + 24, y + h - 16);
        gc.strokeLine(x + w - 24, y + h - 16, x + w - 12, y + h - 16);

        gc.setFill(Color.web("#aa8453", 0.95));
        gc.fillOval(x + 8, y + 8, 8, 8);
        gc.fillOval(x + w - 16, y + 8, 8, 8);
        gc.fillOval(x + 8, y + h - 16, 8, 8);
        gc.fillOval(x + w - 16, y + h - 16, 8, 8);

        gc.setFill(Color.web("#5a402b", 0.96));
        gc.fillRoundRect(x + w - 22, y + h * 0.46, 14, 34, 6, 6);
        gc.setStroke(Color.web("#c59a5f", 0.88));
        gc.strokeRoundRect(x + w - 22, y + h * 0.46, 14, 34, 6, 6);
        gc.setFill(Color.web("#d9b67c"));
        gc.fillOval(x + w - 19, y + h * 0.50, 8, 8);
    }

    private String getSpreadHeadline() {
        switch (currentSpreadMode) {
            case SINGLE_CARD:
                return "抽一张牌，凝视当下唯一讯息";
            case THREE_CARD:
            default:
                return "三张牌流，映照过去现在未来";
        }
    }

    private String getSpreadSubtitle() {
        switch (currentSpreadMode) {
            case SINGLE_CARD:
                return "用一张牌回应你此刻最核心的问题、能量与提醒。";
            case THREE_CARD:
            default:
                return "以紧凑的三张牌，快速看清问题的来处、当下与后续趋势。";
        }
    }

    private String getSpreadRecordLabel() {
        switch (currentSpreadMode) {
            case SINGLE_CARD:
                return "单张启示 · 核心讯息";
            case THREE_CARD:
            default:
                return "三张流向 · 过去 · 现在 · 未来";
        }
    }

    private void drawHandCursor(GraphicsContext gc) {
        if (!handDetected) {
            return;
        }
        gc.setStroke(Color.web("#91f3e5"));
        gc.setLineWidth(2);
        gc.strokeOval(handCanvasX - 12, handCanvasY - 12, 24, 24);
        gc.setStroke(Color.web("#bd72ef", 0.82));
        gc.strokeOval(handCanvasX - 7, handCanvasY - 7, 14, 14);
        gc.setStroke(Color.web("#91f3e5"));
        gc.strokeLine(handCanvasX - 18, handCanvasY, handCanvasX + 18, handCanvasY);
        gc.strokeLine(handCanvasX, handCanvasY - 18, handCanvasX, handCanvasY + 18);
    }

    private int getRevealedCount() {
        int count = 0;
        for (Card card : cards) {
            if (card.revealed) {
                count++;
            }
        }
        return count;
    }

    private double drawWrappedText(GraphicsContext gc, String text, double x, double y, double maxWidth,
                                   double lineHeight, int maxLines, double fontSize) {
        int charsPerLine = Math.max(8, (int) (maxWidth / Math.max(fontSize, 10)));
        List<String> lines = wrapText(text, charsPerLine, maxLines);
        double currentY = y;
        for (String line : lines) {
            gc.fillText(line, x, currentY);
            currentY += lineHeight;
        }
        return currentY;
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum SpreadMode {
        SINGLE_CARD,
        THREE_CARD
    }

    /**
     * 当前牌阵中的卡牌实例。
     */
    static final class Card {
        double x;
        double y;
        double width;
        double height;
        String slotLabel;
        String slotNote;
        TarotMeaning meaning;
        boolean reversed;
        boolean revealed;
        boolean rotated;

        Card(double x, double y, double width, double height,
             String slotLabel, String slotNote, TarotMeaning meaning, boolean reversed, boolean rotated) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.slotLabel = slotLabel;
            this.slotNote = slotNote;
            this.meaning = meaning;
            this.reversed = reversed;
            this.revealed = false;
            this.rotated = rotated;
        }
    }
}
