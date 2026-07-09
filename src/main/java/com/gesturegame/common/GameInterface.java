package com.gesturegame.common;

import javafx.scene.canvas.GraphicsContext;

/**
 * 游戏统一接口：所有游戏必须实现全部方法。
 *
 * <p>游戏在 JavaFX {@link javafx.scene.canvas.Canvas} 上逐帧渲染，
 * 由 {@code GameRenderer} 以 {@code AnimationTimer}（60fps）驱动 {@link #update} 与 {@link #render}。
 * 游戏只通过 {@link GestureData} 获取手势输入，不直接访问摄像头或 WebSocket。
 */
public interface GameInterface {

    /** 游戏名称（大厅卡片显示用）。 */
    String getName();

    /** 一句话介绍（大厅卡片显示用）。 */
    String getDescription();

    /** 图标 emoji（如 🍎 ✂️ 🫧 🔮 🔪 🥁）。 */
    String getIcon();

    /**
     * 初始化游戏，传入画布宽高。
     *
     * @param width  画布宽度（像素）
     * @param height 画布高度（像素）
     */
    void init(int width, int height);

    /**
     * 每帧调用，处理手势输入、更新游戏状态。
     *
     * @param gesture 本帧手势数据
     */
    void update(GestureData gesture);

    /**
     * 每帧渲染（JavaFX Canvas 的 {@link GraphicsContext}）。
     * 实现应每帧清空重画，不跨帧缓存画面。
     *
     * @param gc Canvas 的 GraphicsContext
     */
    void render(GraphicsContext gc);

    /** 游戏是否结束。 */
    boolean isOver();

    /** 当前分数。 */
    int getScore();

    /** 重新开始游戏（重置状态）。 */
    void reset();
}
