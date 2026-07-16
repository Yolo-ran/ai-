package com.gesturegame.game;

/** 一张塔罗牌的固定名称、关键词与正逆位牌义。 */
final class TarotMeaning {
    final String family;
    final String number;
    final String title;
    final String[] keywords;
    final String uprightMeaning;
    final String reversedMeaning;
    final String advice;

    TarotMeaning(String family, String number, String title, String[] keywords,
                 String uprightMeaning, String reversedMeaning, String advice) {
        this.family = family;
        this.number = number;
        this.title = title;
        this.keywords = keywords.clone();
        this.uprightMeaning = uprightMeaning;
        this.reversedMeaning = reversedMeaning;
        this.advice = advice;
    }
}
