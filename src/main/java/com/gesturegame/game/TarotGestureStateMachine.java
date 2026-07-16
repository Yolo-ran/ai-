package com.gesturegame.game;

import com.gesturegame.common.GestureType;

/**
 * 塔罗三牌占读的手势状态机。
 *
 * <p>它只负责“当前处于哪一步”和手势稳定计时，不接触牌面绘制、牌库数据或
 * AI 解读。短暂丢帧会被容错，不会因为一次识别抖动立即取消选牌。</p>
 */
final class TarotGestureStateMachine {

    static final int SELECTION_HOLD_FRAMES = 66;
    private static final int SELECTION_DROPOUT_FRAMES = 8;
    private static final int MEANING_DWELL_FRAMES = 36;

    enum Phase {
        ROTATING,
        HOLDING,
        REVEALING,
        AWAITING_OPEN,
        FLYING,
        FINAL_REVEAL,
        COMPLETE
    }

    enum HoldResult {
        HOLDING,
        CONFIRMED,
        CANCELLED
    }

    private Phase phase = Phase.ROTATING;
    private int fistHoldFrames;
    private int fistDropoutFrames;
    private int meaningDwellFrames;

    void reset() {
        phase = Phase.ROTATING;
        clearSelectionCounters();
        meaningDwellFrames = 0;
    }

    Phase phase() {
        return phase;
    }

    boolean is(Phase expected) {
        return phase == expected;
    }

    void beginRotating() {
        phase = Phase.ROTATING;
        clearSelectionCounters();
        meaningDwellFrames = 0;
    }

    void beginHolding() {
        phase = Phase.HOLDING;
        fistHoldFrames = 1;
        fistDropoutFrames = 0;
    }

    HoldResult updateHolding(GestureType gestureType, boolean handDetected) {
        if (phase != Phase.HOLDING) return HoldResult.CANCELLED;

        if (handDetected && gestureType == GestureType.FIST) {
            fistDropoutFrames = 0;
            fistHoldFrames++;
        } else {
            fistDropoutFrames++;
            // 识别抖动期间缓慢回退进度，而不是一帧清零。
            fistHoldFrames = Math.max(1, fistHoldFrames - 2);
            if (fistDropoutFrames > SELECTION_DROPOUT_FRAMES) {
                beginRotating();
                return HoldResult.CANCELLED;
            }
        }

        return fistHoldFrames >= SELECTION_HOLD_FRAMES
                ? HoldResult.CONFIRMED : HoldResult.HOLDING;
    }

    void beginRevealing() {
        phase = Phase.REVEALING;
        meaningDwellFrames = 0;
    }

    void beginAwaitingOpen() {
        phase = Phase.AWAITING_OPEN;
        meaningDwellFrames = 0;
    }

    boolean updateAwaitingOpen(GestureType gestureType, boolean handDetected) {
        if (phase != Phase.AWAITING_OPEN) return false;
        meaningDwellFrames++;
        return handDetected && gestureType == GestureType.OPEN
                && meaningDwellFrames >= MEANING_DWELL_FRAMES;
    }

    void beginFlying() {
        phase = Phase.FLYING;
    }

    void beginFinalReveal() {
        phase = Phase.FINAL_REVEAL;
    }

    void complete() {
        phase = Phase.COMPLETE;
    }

    void clearSelectionCounters() {
        fistHoldFrames = 0;
        fistDropoutFrames = 0;
    }

    int fistHoldFrames() {
        return fistHoldFrames;
    }

    double holdProgress() {
        return Math.max(0.0, Math.min(1.0,
                fistHoldFrames / (double) SELECTION_HOLD_FRAMES));
    }
}
