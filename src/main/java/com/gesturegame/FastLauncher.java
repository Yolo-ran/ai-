package com.gesturegame;

/**
 * 普通 Java 入口，绕过 Java 启动器对 Application 子类的特殊检测。
 * 依赖放在 classpath 时也能直接启动 JavaFX，供一键快速启动器使用。
 */
public final class FastLauncher {

    private FastLauncher() { }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
