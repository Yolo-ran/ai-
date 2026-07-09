package com.gesturegame.common;

import javafx.scene.canvas.GraphicsContext;

/**
 * 游戏统一接口，定义大厅与具体游戏之间的最小合同。
 */
public interface GameInterface {

    /**
     * 返回游戏名称，用于大厅展示。
     *
     * @return 游戏名称
     */
    String getName();

    /**
     * 返回一句话介绍，用于大厅说明。
     *
     * @return 游戏简介
     */
    String getDescription();

    /**
     * 返回游戏图标，用于大厅卡片展示。
     *
     * @return 图标 emoji
     */
    String getIcon();

    /**
     * 初始化游戏。
     *
     * @param width  画布宽度
     * @param height 画布高度
     */
    void init(int width, int height);

    /**
     * 每帧根据最新手势数据更新游戏状态。
     *
     * @param gesture 当前帧手势数据
     */
    void update(GestureData gesture);

    /**
     * 每帧渲染游戏画面。
     *
     * @param gc JavaFX 画布上下文
     */
    void render(GraphicsContext gc);

    /**
     * 返回游戏是否结束。
     *
     * @return true 表示结束
     */
    boolean isOver();

    /**
     * 返回当前分数。
     *
     * @return 分数
     */
    int getScore();

    /**
     * 重置游戏状态，用于重新开始。
     */
    void reset();
}
