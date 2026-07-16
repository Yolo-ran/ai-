package com.gesturegame.game;

import javafx.scene.image.Image;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按稳定编号延迟加载牌背和 78 张 Akaxi 塔罗正面。 */
final class TarotCardImageStore {

    private static final String ROOT = "/assets/tarot/";
    private static final List<String> MAJOR_NUMBERS = List.of(
            "0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI"
    );
    private static final List<String> MINOR_RANKS = List.of(
            "ACE", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "PAGE", "KNIGHT", "QUEEN", "KING"
    );

    private final Map<Integer, Image> fronts = new HashMap<>();
    private Image back;

    Image cardBack() {
        if (back == null) {
            back = load(ROOT + "card-back.png");
        }
        return back;
    }

    Image cardFront(TarotMeaning meaning) {
        int index = imageIndex(meaning);
        if (index < 0 || index > 77) return null;
        return fronts.computeIfAbsent(index,
                value -> load(ROOT + "fronts/" + String.format("%02d.png", value)));
    }

    int imageIndex(TarotMeaning meaning) {
        if (meaning == null) return -1;
        if ("MAJOR ARCANA".equals(meaning.family)) {
            return MAJOR_NUMBERS.indexOf(meaning.number);
        }

        int rank = MINOR_RANKS.indexOf(meaning.number);
        if (rank < 0) return -1;
        if (meaning.family.startsWith("WANDS")) return 22 + rank;
        if (meaning.family.startsWith("CUPS")) return 36 + rank;
        if (meaning.family.startsWith("SWORDS")) return 50 + rank;
        if (meaning.family.startsWith("PENTACLES")) return 64 + rank;
        return -1;
    }

    private Image load(String resource) {
        URL url = TarotCardImageStore.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("缺少塔罗图片资源: " + resource);
        }
        // 后台解码避免首次翻到某张牌时阻塞 JavaFX 动画线程。
        return new Image(url.toExternalForm(), 0, 0, true, true, true);
    }
}
