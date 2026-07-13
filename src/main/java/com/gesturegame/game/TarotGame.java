package com.gesturegame.game;

import com.gesturegame.common.Difficulty;
import com.gesturegame.common.GameInterface;
import com.gesturegame.common.GestureData;
import com.gesturegame.common.GestureType;
import javafx.geometry.VPos;
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
 * 黑金神秘风格的塔罗牌占读界面。
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

    private static final List<TarotMeaning> TAROT_DECK = createTarotDeck();

    private static List<TarotMeaning> createTarotDeck() {
        List<TarotMeaning> deck = new ArrayList<>();
        deck.addAll(createMajorArcana());
        deck.addAll(createCupsArcana());
        deck.addAll(createSwordsArcana());
        deck.addAll(createWandsArcana());
        deck.addAll(createPentaclesArcana());
        return deck;
    }

    private static List<TarotMeaning> createMajorArcana() {
        return List.of(
                major("0", "愚者", new String[]{"新的开始", "冒险", "信任"},
                        "你正站在新旅程的门口，未知并非威胁，而是尚未被体验的可能。保持轻盈与好奇，新的阶段会因为你的勇气而展开。",
                        "逆位提示你在鲁莽和退缩之间摇摆，既想出发又害怕失控。此刻需要的不是冲动一跃，而是带着觉知迈出第一步。",
                        "给自己一点试错空间，但不要把逃避伪装成自由。"),
                major("I", "魔术师", new String[]{"意志", "创造", "掌控"},
                        "你已具备将想法落地的工具与专注力，关键在于把分散的资源整合成明确行动。此牌鼓励你主动发起、主动表达、主动塑造结果。",
                        "逆位表示注意力分散、技巧被误用，或表面自信下隐藏着不稳定。与其炫示能力，不如回到真实动机和执行细节。",
                        "少一点表演，多一点落实，你的能量才会真正发光。"),
                major("II", "女祭司", new String[]{"直觉", "潜意识", "静观"},
                        "答案并不在更喧闹的信息里，而在你已经感受到却尚未说出口的内在判断。放慢速度，观察细节，许多被遮蔽的线索会逐渐显形。",
                        "逆位常指向过度压抑感受、忽视直觉，或被模糊情绪困住。你需要区分幻想与洞察，让内在声音重新清晰下来。",
                        "保持沉静，但不要沉默到失去自我表达。"),
                major("III", "皇后", new String[]{"丰饶", "滋养", "创造"},
                        "这是适合孕育成果、修复关系与拥抱感官生命力的时刻。你所投入的温柔、耐心与创造，都会逐渐结出可见的果实。",
                        "逆位提示能量被过度消耗，或你正在照顾所有人却忘了照顾自己。失衡的给予最终会削弱创造力与安全感。",
                        "先让自己稳定丰盛，再谈如何照亮别人。"),
                major("IV", "皇帝", new String[]{"秩序", "边界", "权威"},
                        "局势需要更清晰的规则、节奏与结构，你的稳定感会来自可执行的安排，而非情绪化决策。此牌也提醒你承担起领导与保护的责任。",
                        "逆位意味着控制欲过强、规则僵化，或权威失效导致秩序松散。你要找回边界，而不是用压迫取代管理。",
                        "真正的力量来自稳固，而不是强硬。"),
                major("V", "教皇", new String[]{"传统", "信念", "学习"},
                        "当你面临选择时，成熟的方法、可靠的经验与长期主义会比短暂刺激更值得依靠。此牌适合学习、求教、建立精神与价值的坐标。",
                        "逆位表示你对既有框架感到不适，可能需要更新信念，或摆脱盲从。问题不是拒绝传统，而是找到真正与你契合的道路。",
                        "尊重经验，但别把他人的答案当成自己的真理。"),
                major("VI", "恋人", new String[]{"选择", "共鸣", "关系"},
                        "这张牌并不只关乎爱情，更关乎价值一致后的选择。真正重要的关系和决定，往往要求你先诚实面对自己的内心与欲望。",
                        "逆位提示关系失衡、沟通错位，或你在关键选择前回避承诺。模糊态度只会延长消耗，清晰才会带来转机。",
                        "别只问谁适合你，更要问你愿意成为什么样的人。"),
                major("VII", "战车", new String[]{"推进", "胜负", "掌控方向"},
                        "你正处于需要明确目标、集中资源、快速推进的阶段。只要意志足够聚焦，分散的力量也能被你整合成前进的动力。",
                        "逆位意味着方向摇摆、情绪失控或逞强前冲。速度不是问题，问题在于你是否真的知道自己要去哪里。",
                        "先校准方向，再谈全速前进。"),
                major("VIII", "力量", new String[]{"柔韧", "勇气", "内在掌控"},
                        "你当下最需要的不是压制，而是温柔且坚定地驯服内在的恐惧、焦躁与冲动。真正强大的人，能够与本能和平共处，而不是暴力对抗。",
                        "逆位提示自我怀疑、能量涣散，或表面的强硬掩盖了内在脆弱。请停止对自己苛刻，用稳定取代自我消耗。",
                        "把力量从对抗外界，转向照顾自己的核心。"),
                major("IX", "隐士", new String[]{"独处", "沉思", "智慧之灯"},
                        "此刻适合远离噪音，整理真正重要的问题。你正在通过独处找回判断力，这不是退场，而是为了更清楚地前行。",
                        "逆位表示过度封闭、与世界切断连接，或你把孤独当成防御。思考固然必要，但别让自我隔离变成停滞。",
                        "先照亮自己的路，再决定是否邀请别人同行。"),
                major("X", "命运之轮", new String[]{"转折", "周期", "机运"},
                        "生活的齿轮正在转动，你会感受到节奏改变、关系松动或机会到来。顺势而为比强行抗拒更能帮你抓住新的窗口。",
                        "逆位提示反复、卡顿或对变化的不甘。与其执着于控制时机，不如先接纳周期本身就是生命的一部分。",
                        "当风向改变时，调整帆，而不是责怪风。"),
                major("XI", "正义", new String[]{"因果", "平衡", "真相"},
                        "事情正走向更清晰的因果与判断，公平不会立刻温柔，但通常会非常诚实。你需要用事实、边界与责任来修正局面。",
                        "逆位意味着信息不对称、推诿责任或内心失衡。若你一直拖延面对事实，局势会以更直接的方式逼你表态。",
                        "在做判断前，先确认你愿不愿意承担结果。"),
                major("XII", "倒吊人", new String[]{"暂停", "换位", "放下执念"},
                        "短暂停滞并不是失败，而是宇宙要求你换一个角度重新理解局势。你越愿意放下固有执念，越容易获得新的洞见。",
                        "逆位表示你明知该松手，却继续僵持；表面等待，实则抗拒改变。继续硬撑只会让消耗变长。",
                        "暂停不是浪费时间，而是为更准确的行动蓄势。"),
                major("XIII", "死神", new String[]{"结束", "蜕变", "新生"},
                        "一个旧阶段已经走到尾声，真正的功课不是挽留，而是允许它体面结束。清理旧结构之后，新的生命力才有空间进入。",
                        "逆位通常表示迟迟不肯告别、害怕变化，或一边知道关系与局势已变，一边仍在重复旧模式。拖延转化会让痛感加倍。",
                        "学会告别，才有资格迎接真正适合你的新篇章。"),
                major("XIV", "节制", new String[]{"平衡", "融合", "疗愈"},
                        "你正在学习把矛盾的力量调和成新的秩序。此牌强调的是长期稳定而非瞬时高点，适合修复、协商、慢慢校准生活节奏。",
                        "逆位提示你在极端之间摇摆，情绪、作息或关系都需要重新调频。要警惕过度透支、急于求成或失衡付出。",
                        "让所有部分都回到恰到好处，事情自然会顺起来。"),
                major("XV", "恶魔", new String[]{"执着", "欲望", "束缚"},
                        "你可能正被某种依赖、欲望、比较或不安全感牵引。恶魔并非只代表黑暗，它让你看见自己究竟被什么牢牢绑住。",
                        "逆位说明你已经开始察觉束缚所在，并出现了挣脱的机会。关键在于停止自我欺骗，别再把熟悉的枷锁叫成安全感。",
                        "看清自己在交换什么，才知道代价是否值得。"),
                major("XVI", "高塔", new String[]{"击碎", "真相", "剧变"},
                        "某些看似稳固的结构会被突然打破，但这场震动的本质是清除虚假、逼近真实。短期混乱之后，往往会迎来更自由的秩序。",
                        "逆位意味着震荡已在内部积压，你可能察觉到问题却迟迟不处理。若继续回避，裂缝会以更强烈的方式出现。",
                        "越早面对真实，越能减少不必要的代价。"),
                major("XVII", "星星", new String[]{"希望", "修复", "灵感"},
                        "在经历动荡后，你正在重新找回信任、愿景与温柔的生命力。星星带来的是安静却稳定的疗愈，让你再次相信前路值得期待。",
                        "逆位提示希望感降低、灵感枯竭，或你对未来失去耐心。先恢复与自己的连接，别急着要求立刻看到全部答案。",
                        "把目光从伤口移向远处，你会再次找到方向。"),
                major("XVIII", "月亮", new String[]{"潜意识", "迷雾", "感受"},
                        "当月亮出现，现实未必如表面那样清楚，情绪、投射与隐秘信息都在影响判断。与其急于定论，不如先承认自己正在穿越迷雾。",
                        "逆位意味着混乱正在慢慢散去，隐藏的问题开始浮现。你需要做的是冷静辨别，而不是继续被恐惧牵着走。",
                        "让感受存在，但别让它替你做最终决定。"),
                major("XIX", "太阳", new String[]{"清晰", "生命力", "成功"},
                        "这是充满亮度与确定性的牌，代表真相显现、状态回升、关系变暖和成果落地。很多你曾经担心的事，会在光里显得没有那么可怕。",
                        "逆位多半不是彻底黑暗，而是快乐被遮挡、自信暂时受损，或你明明有成果却无法真正享受。别让苛求盖过真实的进展。",
                        "允许自己被看见，也允许自己感受喜悦。"),
                major("XX", "审判", new String[]{"召唤", "觉醒", "总结"},
                        "过去的经历正在汇聚成一次清晰的召唤，让你意识到该如何活出更真实的自己。这是整合、复盘、回应使命感的重要时刻。",
                        "逆位表示你在关键节点上迟疑不决，害怕为新的自己负责。旧身份若已经不合身，再回去只会更加窒息。",
                        "不要只回顾过往，更要回应未来对你的召唤。"),
                major("XXI", "世界", new String[]{"完成", "整合", "圆满"},
                        "一个重要循环即将圆满，你会看见此前所有努力如何彼此连接，最终形成完整成果。世界牌也意味着你准备好进入更高一层的阶段。",
                        "逆位提示最后一步尚未真正收尾，可能还有未完成的情绪、承诺或细节需要整合。别急着奔向下一个目标，先把这一章写完整。",
                        "完成不是停下，而是带着完整感迈向下一次出发。")
        );
    }

    private static List<TarotMeaning> createCupsArcana() {
        return List.of(
                minor("CUPS · 圣杯组", "ACE", "圣杯首牌", new String[]{"情感开启", "灵感", "连结"},
                        "新的情感能量正被打开，关系、创作或内心感受将迎来 fresh start。你适合接纳温柔流动，而不是急着下定义。",
                        "逆位提示情绪堵塞、表达不畅，或你明明渴望连结却迟迟不愿敞开。先诚实面对自己的感受。",
                        "让情绪流动，但别让幻想代替真实交流。"),
                minor("CUPS · 圣杯组", "II", "圣杯二", new String[]{"互相吸引", "平衡", "关系"},
                        "这是一张强调双向回应的牌，适合合作、和解、建立信任，也常预示关系中的默契加深。",
                        "逆位表示误解、情感失衡或关系节奏不一致。若不愿坦诚，表面的和气很难维持。",
                        "真正的连结来自平等回应，而不是单方面投入。"),
                minor("CUPS · 圣杯组", "III", "圣杯三", new String[]{"庆祝", "朋友", "共享喜悦"},
                        "你正处于适合聚会、分享成果、获得支持的阶段，群体能量会放大你的好心情与创造力。",
                        "逆位提示社交过度、表面热闹却缺乏真实支持，或关系中有隐性疏离与八卦干扰。",
                        "选择能真正滋养你的关系圈，而不是只追求热闹。"),
                minor("CUPS · 圣杯组", "IV", "圣杯四", new String[]{"冷淡", "停滞", "重新感受"},
                        "你可能对眼前机会提不起兴趣，或因为疲倦而忽略新的情感入口。此牌提醒你先看见自己的麻木来源。",
                        "逆位表示你开始从停滞中苏醒，愿意重新回应生活，但仍需要时间恢复敏锐度。",
                        "不要只盯着失落，也看看还有什么仍然值得接住。"),
                minor("CUPS · 圣杯组", "V", "圣杯五", new String[]{"失落", "遗憾", "疗伤"},
                        "某段关系或期待的落空让你停留在悲伤里，但并非一切都已失去。你需要慢慢把注意力从失去转回仍然存在的支持。",
                        "逆位表示你开始从伤痛中恢复，愿意重新相信关系与生活。疗愈虽慢，却已经启动。",
                        "允许悲伤存在，但别把自己永远困在失去的时刻。"),
                minor("CUPS · 圣杯组", "VI", "圣杯六", new String[]{"回忆", "纯真", "旧缘"},
                        "过去的人事物正在影响你，可能带来温柔、怀旧，也可能让你重新理解早年的情感模式。",
                        "逆位提示你过度沉浸旧日记忆，或被未完成的往事牵住脚步。怀念不该取代当下。",
                        "把记忆当成礼物，而不是把自己交还给过去。"),
                minor("CUPS · 圣杯组", "VII", "圣杯七", new String[]{"想象", "诱惑", "选择过多"},
                        "眼前有许多可能性，但并不是每一项都值得投入。此牌提醒你分辨愿景与幻象，别被情绪化欲望带偏。",
                        "逆位表示迷雾开始散去，你逐渐能看清真正值得的方向，但也可能因为清醒而感到失落。",
                        "从最真实的一项开始，而不是试图同时抓住全部。"),
                minor("CUPS · 圣杯组", "VIII", "圣杯八", new String[]{"离开", "寻找意义", "转身"},
                        "当熟悉的人事已无法提供真正满足时，你需要做出成熟的离开。此牌强调的是为了更深意义而出发。",
                        "逆位表示你明知该离开，却因为不舍或恐惧反复徘徊。停留太久只会延长空虚。",
                        "转身不是失败，而是对灵魂需求更诚实。"),
                minor("CUPS · 圣杯组", "IX", "圣杯九", new String[]{"满足", "实现", "享受成果"},
                        "这是一张愿望实现与情绪满足的牌，你会看见自己努力后的回报，也更容易感受到生活的丰盛感。",
                        "逆位提示满足感来得不完整，可能你得到了结果，却依然觉得空。别把外在拥有当成全部答案。",
                        "享受成果，同时继续辨认自己真正想要什么。"),
                minor("CUPS · 圣杯组", "X", "圣杯十", new String[]{"圆满", "家庭", "长久幸福"},
                        "关系、家庭或内在归属感正走向完整与温暖，这是长期稳定幸福的象征，强调共享、信任与彼此成全。",
                        "逆位表示外表圆满下仍有沟通裂缝，或你对幸福蓝图的期待与现实存在落差。",
                        "别只维持和谐表象，更要认真经营真正的情感质量。"),
                minor("CUPS · 圣杯组", "PAGE", "圣杯侍者", new String[]{"敏感", "好奇", "情感讯息"},
                        "新的情感消息、创作灵感或温柔邀请可能出现。你需要保持开放与感受力，但也别太快沉入幻想。",
                        "逆位提示情绪不成熟、逃避表达，或把心动误认成承诺。请先稳定自己的情感边界。",
                        "温柔可以保留，边界也要同时存在。"),
                minor("CUPS · 圣杯组", "KNIGHT", "圣杯骑士", new String[]{"浪漫", "追寻", "理想化"},
                        "你正被某种理想关系、创意或情感愿景吸引，并愿意主动靠近。此牌适合表达爱意，也适合浪漫型提案。",
                        "逆位表示过度理想化、承诺漂浮，或你沉醉在感觉里却忽略现实可行性。",
                        "带着诗意前行，但别把现实留在身后。"),
                minor("CUPS · 圣杯组", "QUEEN", "圣杯皇后", new String[]{"共情", "温柔", "内在智慧"},
                        "你拥有很强的情绪理解力与包容性，适合照顾关系、倾听他人，也适合让创作从内心深处生长。",
                        "逆位提示共情过度、自我牺牲或情绪边界模糊。请先安放自己的感受，再承接别人。",
                        "温柔不是无限透支自己。"),
                minor("CUPS · 圣杯组", "KING", "圣杯国王", new String[]{"成熟", "稳重", "情绪掌舵"},
                        "你正在学习以温柔但稳定的方式掌控情绪，这使你更适合处理关系、合作与复杂局面。",
                        "逆位表示情绪被压抑、操控欲出现，或表面平静下其实暗潮汹涌。成熟不是假装没感觉。",
                        "把情绪化成力量，而不是把它们锁进心底。")
        );
    }

    private static List<TarotMeaning> createSwordsArcana() {
        return List.of(
                minor("SWORDS · 宝剑组", "ACE", "宝剑首牌", new String[]{"清晰", "突破", "真相"},
                        "思路开始变得锋利清楚，你适合做判断、切开混乱、说出关键事实。真相会带来新的行动起点。",
                        "逆位提示判断迟疑、思绪打结，或你明知问题在哪却不愿面对。模糊只会延长消耗。",
                        "用清晰替代内耗，把最重要的话先说出来。"),
                minor("SWORDS · 宝剑组", "II", "宝剑二", new String[]{"僵持", "犹豫", "回避决定"},
                        "你正卡在两个选项之间，试图维持平衡，却迟迟不愿做出决定。暂时平静背后，其实是紧绷。",
                        "逆位表示信息开始泄露、情绪压不住，拖延中的问题会逐渐浮出水面。",
                        "真正的平衡，不是永远不选，而是看清后勇敢取舍。"),
                minor("SWORDS · 宝剑组", "III", "宝剑三", new String[]{"心痛", "割裂", "看见真相"},
                        "某个事实或关系让你感到痛，但这份疼痛也迫使你不再自欺。此牌的力量在于让真相刺破幻觉。",
                        "逆位表示伤口正在慢慢结痂，但若一直否认疼痛，恢复就会被拖慢。",
                        "承认痛感，是疗愈真正开始的时刻。"),
                minor("SWORDS · 宝剑组", "IV", "宝剑四", new String[]{"休整", "暂停", "恢复理智"},
                        "经过消耗后，你需要从冲突和噪音中抽离，给自己一点真正静下来恢复的时间。",
                        "逆位提示休息不足、思虑过度，或你以为自己停下了，其实神经仍在紧绷。",
                        "暂停并不是落后，而是为了以更清楚的状态重新出发。"),
                minor("SWORDS · 宝剑组", "V", "宝剑五", new String[]{"冲突", "输赢", "代价"},
                        "这张牌常指向争执、竞争和情绪性胜负。你可能赢了表面，却失去了关系、体面或更大的格局。",
                        "逆位意味着冲突的余波仍在，关系修复需要比争赢更多的诚意与反思。",
                        "先问问自己：这场胜利真的值得吗？"),
                minor("SWORDS · 宝剑组", "VI", "宝剑六", new String[]{"过渡", "离开风暴", "慢慢前往更安稳处"},
                        "你正在离开一个高压或混乱阶段，虽然过程未必轻松，但方向是更平静、更可持续的。",
                        "逆位提示旧问题仍在追赶你，身体离开了，心却还停留在原地。",
                        "允许自己慢慢移动，不必要求立刻痊愈。"),
                minor("SWORDS · 宝剑组", "VII", "宝剑七", new String[]{"策略", "试探", "隐匿"},
                        "局势里可能存在保留、试探或不完全坦诚。此牌提醒你既要有策略，也要警惕信息不透明带来的风险。",
                        "逆位表示隐藏的事开始暴露，或你不再想继续维持某种绕弯的方式。",
                        "聪明很重要，但长期关系仍然需要诚实支撑。"),
                minor("SWORDS · 宝剑组", "VIII", "宝剑八", new String[]{"受限", "困住自己", "认知牢笼"},
                        "你可能觉得自己被局势卡住，但很多束缚来自内心预设和恐惧想象，而非绝对无路可走。",
                        "逆位表示你开始松动那些自我限制，虽然仍不轻松，却已经看见出口。",
                        "先拆掉心里的栅栏，现实的路才会显现。"),
                minor("SWORDS · 宝剑组", "IX", "宝剑九", new String[]{"焦虑", "失眠", "精神压力"},
                        "这张牌指向深夜里反复扩大的担忧，你的压力真实存在，但也可能被过度放大。",
                        "逆位意味着焦虑正在逐渐释放，或你终于愿意面对那些一直压着你的念头。",
                        "不要独自忍着，把恐惧说出来，它就不会无限膨胀。"),
                minor("SWORDS · 宝剑组", "X", "宝剑十", new String[]{"极限", "结束", "痛到见底"},
                        "某个阶段已经走到极限，再继续维持只会更伤。虽然刺痛强烈，但也意味着最坏部分正在结束。",
                        "逆位提示你开始从最低点回升，但还需要慢慢缝合自己的精力与信任。",
                        "允许旧局面结束，别再把自己绑在已经崩塌的故事里。"),
                minor("SWORDS · 宝剑组", "PAGE", "宝剑侍者", new String[]{"观察", "新信息", "机智"},
                        "新的消息、思路或调查线索正在靠近，你适合保持敏锐和学习状态，但别急着武断下结论。",
                        "逆位表示沟通过于尖锐、消息失真，或你因为防备过强而错过真实信息。",
                        "聪明之外，也要保留耐心和准确。"),
                minor("SWORDS · 宝剑组", "KNIGHT", "宝剑骑士", new String[]{"快速行动", "锋利推进", "强势表达"},
                        "你会感到很强的推进欲，适合快速突破、直面问题、做出果断动作，但要防止过于冲。",
                        "逆位提示莽撞、语言伤人，或你的节奏已经快到失去判断。",
                        "速度是优势，但方向与分寸同样关键。"),
                minor("SWORDS · 宝剑组", "QUEEN", "宝剑皇后", new String[]{"理性", "边界", "洞察"},
                        "你正在变得清醒、独立且不轻易被情绪裹挟。她提醒你用理性和边界守住珍贵的秩序。",
                        "逆位表示冷硬、挑剔，或因为过去创伤而把防御筑得太高。",
                        "保持锋利可以，但别让自己失去温度。"),
                minor("SWORDS · 宝剑组", "KING", "宝剑国王", new String[]{"决断", "原则", "高维判断"},
                        "你需要以成熟、清晰、讲原则的方式处理局势。这是适合下定义、立规则、做战略判断的时刻。",
                        "逆位提示独断、控制感过强，或你在逻辑外衣下忽视了人性的复杂度。",
                        "原则重要，但真正高明的判断也懂得留有余地。")
        );
    }

    private static List<TarotMeaning> createWandsArcana() {
        return List.of(
                minor("WANDS · 权杖组", "ACE", "权杖首牌", new String[]{"点燃", "行动力", "创意火种"},
                        "新的热情与行动冲动正在升起，这是适合开新项目、发起计划、点燃创造火花的时刻。",
                        "逆位提示热度不足、动力中断，或你有冲劲却缺少真正持续的执行。",
                        "先点燃最重要的一束火，再逐步扩大它。"),
                minor("WANDS · 权杖组", "II", "权杖二", new String[]{"远景", "规划", "扩张准备"},
                        "你已经看到更远的可能，接下来要做的不是空想，而是把视野落成布局和路线。",
                        "逆位表示害怕迈出舒适区，或计划停留在脑中迟迟未执行。",
                        "远景只有与行动连接，才会真正成为未来。"),
                minor("WANDS · 权杖组", "III", "权杖三", new String[]{"拓展", "等待回报", "外部机会"},
                        "项目和努力开始向外延伸，你会看到合作、回应或更大平台逐渐到来。",
                        "逆位提示推进受阻、沟通错位，或你期待结果太快而忽略中间过程。",
                        "把眼光放远，同时稳住当下每一步。"),
                minor("WANDS · 权杖组", "IV", "权杖四", new String[]{"阶段成果", "庆祝", "稳固基础"},
                        "这是值得庆祝的小圆满，象征基础正在稳定成形，适合庆祝成果、确认关系与建立归属感。",
                        "逆位表示表面热闹之下仍有不稳定，或你对“已经完成”的判断过早。",
                        "先稳固根基，再享受掌声。"),
                minor("WANDS · 权杖组", "V", "权杖五", new String[]{"竞争", "磨合", "能量碰撞"},
                        "不同立场和野心正在碰撞，这既可能造成混乱，也可能激发新的活力与突破。",
                        "逆位表示暗中较劲、合作低效，或表面和平下积压着竞争情绪。",
                        "把冲突导向建设，而不是让它只剩摩擦。"),
                minor("WANDS · 权杖组", "VI", "权杖六", new String[]{"胜利", "被认可", "带头前行"},
                        "你的努力容易被看见并获得肯定，这是适合公开展示、推进个人品牌或承接荣誉的位置。",
                        "逆位提示期待外界认可过重，或成功后压力接踵而来。",
                        "接受掌声，但别让它决定你的价值感。"),
                minor("WANDS · 权杖组", "VII", "权杖七", new String[]{"守住立场", "捍卫成果", "坚持"},
                        "你已经站上一个相对有利的位置，接下来需要守住判断与边界，不被轻易动摇。",
                        "逆位表示防守疲惫、底气不足，或你在压力下开始怀疑自己是否值得坚持。",
                        "不必和所有人争辩，但要清楚什么不能退。"),
                minor("WANDS · 权杖组", "VIII", "权杖八", new String[]{"加速", "消息", "迅速推进"},
                        "事情会突然提速，消息、邀约或结果可能很快到来。你需要及时响应，别错过节奏窗口。",
                        "逆位提示计划延迟、消息反复，或推进速度太快反而带来混乱。",
                        "保持灵活，但也要给自己留出整理空间。"),
                minor("WANDS · 权杖组", "IX", "权杖九", new String[]{"疲惫坚持", "防御", "最后一道关卡"},
                        "你已经撑了很久，虽然疲惫，却也因此积累了经验与警觉。此牌提醒你再坚持一下，但别忘了照顾自己。",
                        "逆位表示高度戒备让你难以信任任何支持，或你明明很累却不允许自己停。",
                        "坚持可以，但别把自己耗成空壳。"),
                minor("WANDS · 权杖组", "X", "权杖十", new String[]{"负担", "责任过重", "快到极限"},
                        "你扛的东西太多了，责任感虽强，却已接近超载。不是你不够强，而是需要重新分配负荷。",
                        "逆位提示你开始意识到重担问题，有机会放下部分职责或换一种推进方式。",
                        "别把能扛当成必须一直扛。"),
                minor("WANDS · 权杖组", "PAGE", "权杖侍者", new String[]{"探索", "热情", "新冒险"},
                        "新的灵感、旅程或项目邀请出现，你会有跃跃欲试的冲动。这张牌鼓励你用年轻热情去试。",
                        "逆位表示三分钟热度、方向飘忽，或想开始很多事却难以持续。",
                        "先把最有火花的一件做出雏形。"),
                minor("WANDS · 权杖组", "KNIGHT", "权杖骑士", new String[]{"冲劲", "出发", "大胆推进"},
                        "你会感到很强的行动欲，适合快速冲刺、争取机会、走出舒适区，但要小心节奏失控。",
                        "逆位提示鲁莽、急躁或计划频繁变向。光有热度不足以支撑长期结果。",
                        "保留你的火，但别让火把路一起烧掉。"),
                minor("WANDS · 权杖组", "QUEEN", "权杖皇后", new String[]{"魅力", "自信", "吸引力"},
                        "你正处于个人魅力和创造领导力都很强的位置，适合发光、表达、吸引资源与支持。",
                        "逆位表示自信被削弱、比较心上升，或你过于强势以至于让合作变得吃力。",
                        "真正的魅力不是压过别人，而是点亮场域。"),
                minor("WANDS · 权杖组", "KING", "权杖国王", new String[]{"领导", "远见", "主导局势"},
                        "这是适合整合资源、设定大方向、以成熟领导姿态推进事情的时刻。你的火焰需要转化为稳定的带动能力。",
                        "逆位提示控制欲、急功近利，或你只想主导结果却忽视了团队承载力。",
                        "带头不等于独断，真正的领导也懂得授权。")
        );
    }

    private static List<TarotMeaning> createPentaclesArcana() {
        return List.of(
                minor("PENTACLES · 星币组", "ACE", "星币首牌", new String[]{"机会", "现实资源", "开端"},
                        "新的物质机会、工作入口或可落地的成长空间正在出现。这是一张很适合开始长期建设的牌。",
                        "逆位提示机会看似存在，却未真正落地，或你还没有准备好承接它。",
                        "先把基础条件搭好，再稳稳接住现实机会。"),
                minor("PENTACLES · 星币组", "II", "星币二", new String[]{"平衡收支", "调度", "现实 juggling"},
                        "你正在同时处理多项责任，需要在时间、金钱与精力之间找到新的平衡方式。",
                        "逆位表示现实压力失衡、安排混乱，或你在不同责任间来回拉扯得太辛苦。",
                        "先分清轻重缓急，不要把所有事都放在同一层级。"),
                minor("PENTACLES · 星币组", "III", "星币三", new String[]{"合作", "专业", "共同打磨"},
                        "一项事情正在通过专业协作逐渐成形。你的能力需要被放进更成熟的团队与流程中。",
                        "逆位提示配合不顺、分工模糊，或有人在合作中投入不足。",
                        "尊重专业与协作，会让结果比单打独斗更扎实。"),
                minor("PENTACLES · 星币组", "IV", "星币四", new String[]{"抓紧资源", "安全感", "保守"},
                        "你很在意稳定与拥有，这有助于守成，但也可能让你变得过度保守、害怕流动。",
                        "逆位表示资源失控感、执着占有，或你开始意识到紧抓不放并不能带来真正安全。",
                        "稳定重要，但别让安全感变成新的枷锁。"),
                minor("PENTACLES · 星币组", "V", "星币五", new String[]{"匮乏感", "现实压力", "需要帮助"},
                        "你可能正面对现实层面的不安、资源不足或被忽视的感觉。困难真实存在，但支持并非完全消失。",
                        "逆位表示情况开始恢复，援助窗口、工作机会或心理复原力正在回升。",
                        "别逞强，向外寻求帮助本身就是一种力量。"),
                minor("PENTACLES · 星币组", "VI", "星币六", new String[]{"给予", "回馈", "资源流动"},
                        "金钱、帮助或机会正处于流动状态，你可能是给予者，也可能是获得支持的一方。",
                        "逆位提示资源分配不平衡、关系中有隐性控制，或你给得太多却没被真正尊重。",
                        "衡量给予是否健康，也衡量接受是否带着尊严。"),
                minor("PENTACLES · 星币组", "VII", "星币七", new String[]{"耐心等待", "评估成果", "长期经营"},
                        "你的投入正在慢慢累积成果，但现在更需要观察、复盘和耐心，而不是急着拔苗助长。",
                        "逆位表示对进展焦躁、投入产出不成比例，或你开始怀疑坚持的意义。",
                        "长期的好结果，往往需要你先熬过不明显的阶段。"),
                minor("PENTACLES · 星币组", "VIII", "星币八", new String[]{"打磨", "专注", "技能提升"},
                        "这是把事情做实、把手艺练精的牌。你越专注于细节与重复练习，结果越稳定。",
                        "逆位提示敷衍、分心，或你在重复劳动中失去初心与质量感。",
                        "别急着求快，先把手上的事做得足够好。"),
                minor("PENTACLES · 星币组", "IX", "星币九", new String[]{"独立", "丰盛", "自我成就"},
                        "你逐渐拥有更成熟的独立能力与生活掌控感，这是凭自身努力换来的松弛与丰盛。",
                        "逆位提示外在条件看似不错，内在却仍缺安全感，或你过度依赖物质确认价值。",
                        "享受成果，同时记得自己的价值不只来自拥有。"),
                minor("PENTACLES · 星币组", "X", "星币十", new String[]{"家业", "传承", "长期稳定"},
                        "这张牌象征长期资源、家庭系统、事业根基与可传承的成果，是现实层面很强的稳固与圆满。",
                        "逆位表示看似稳定的结构里仍有裂缝，家庭、资产或长期计划需要重新梳理。",
                        "真正的长久，不是只守住表面，而是照顾结构内部的关系。"),
                minor("PENTACLES · 星币组", "PAGE", "星币侍者", new String[]{"学习", "务实起步", "新机会观察"},
                        "你正在以更务实的姿态面对现实成长，这是一张适合学习技能、尝试新机会的牌。",
                        "逆位提示拖延、懒散或想要结果却不愿投入基础训练。",
                        "把注意力放回基本功，机会自然会更近。"),
                minor("PENTACLES · 星币组", "KNIGHT", "星币骑士", new String[]{"稳步推进", "可靠", "耐力"},
                        "你的节奏可能不快，但很稳。这张牌强调长期执行、责任感与真正可持续的前进。",
                        "逆位表示机械重复、缺乏弹性，或你因固执而错过必要的调整时机。",
                        "稳定是优势，但别忘了偶尔抬头看方向。"),
                minor("PENTACLES · 星币组", "QUEEN", "星币皇后", new String[]{"丰盛照料", "现实掌控", "温暖务实"},
                        "你很适合打理资源、照顾生活质量、把抽象关怀转成真实可感的支持。",
                        "逆位提示过度操心、只顾现实运转而忽视内在滋养，或把安全感完全寄托在物质层面。",
                        "让丰盛落地，也别忘了让自己被温柔对待。"),
                minor("PENTACLES · 星币组", "KING", "星币国王", new String[]{"稳固成就", "财富管理", "成熟掌舵"},
                        "这是现实层面最稳健的位置之一，代表你有能力经营资源、守住价值，并建立长期可靠的结果。",
                        "逆位提示保守过度、物质掌控欲强，或你对结果和收益的执念开始压过初心。",
                        "掌控资源很好，但别让资源反过来定义你。")
        );
    }

    private static TarotMeaning major(String number, String title, String[] keywords,
                                      String uprightMeaning, String reversedMeaning, String advice) {
        return new TarotMeaning("MAJOR ARCANA", number, title, keywords, uprightMeaning, reversedMeaning, advice);
    }

    private static TarotMeaning minor(String family, String number, String title, String[] keywords,
                                      String uprightMeaning, String reversedMeaning, String advice) {
        return new TarotMeaning(family, number, title, keywords, uprightMeaning, reversedMeaning, advice);
    }

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

    @Override
    public String getName() {
        return "塔罗牌";
    }

    @Override
    public String getDescription() {
        return "黑金神秘占读台，手势翻牌查看详细牌义";
    }

    @Override
    public String getIcon() {
        return "🔮";
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
        rebuildSpread();
    }

    @Override
    public void update(GestureData gesture) {
        if (gesture == null || over) {
            return;
        }

        if (modeSwitchCooldown > 0) {
            modeSwitchCooldown--;
        }

        ambientPulse += 0.045;
        if (ambientPulse > Math.PI * 2) {
            ambientPulse -= Math.PI * 2;
        }

        if (gesture.isHandDetected()) {
            handDetected = true;
            handCanvasX = gesture.getHandX() * canvasWidth;
            handCanvasY = gesture.getHandY() * canvasHeight;
            selectedIndex = findHoveredCardIndex();
            if (selectedIndex >= 0) {
                detailIndex = selectedIndex;
            }
        } else {
            handDetected = false;
            selectedIndex = -1;
        }

        double targetPulse = selectedIndex >= 0 ? 1.0 : 0.0;
        hoverPulse += (targetPulse - hoverPulse) * 0.18;

        if (flippingIndex >= 0) {
            flipProgress += 0.065;
            Card flipping = cards.get(flippingIndex);
            if (flipProgress >= 0.5 && !flipping.revealed) {
                flipping.revealed = true;
                detailIndex = flippingIndex;
            }
            if (flipProgress >= 1.0) {
                flipProgress = 0.0;
                flippingIndex = -1;
            }
        }

        if (flippingIndex < 0 && modeSwitchCooldown == 0 && gesture.getGesture() == GestureType.PEACE) {
            cycleSpreadMode();
            modeSwitchCooldown = 18;
            return;
        }

        if (flippingIndex < 0
                && gesture.getGesture() == GestureType.FIST
                && selectedIndex >= 0
                && !cards.get(selectedIndex).revealed) {
            flippingIndex = selectedIndex;
            flipProgress = 0.0;
        }

        if (flippingIndex < 0 && gesture.getGesture() == GestureType.OPEN && getRevealedCount() == cards.size()) {
            rebuildSpread();
            selectedIndex = -1;
            detailIndex = -1;
        }
    }

    @Override
    public void render(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);

        drawBackground(gc);
        drawTopBar(gc);
        drawDeckPanel(gc);
        drawSpreadPanel(gc);
        drawDetailPanel(gc);
        drawReadingNotes(gc);
        drawBottomHint(gc);
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
        double cardHeight = cardWidth * 1.68;
        double x = canvasWidth * 0.44 - cardWidth / 2.0;
        double y = canvasHeight * 0.33;
        cards.add(createCard(x, y, cardWidth, cardHeight, "CARD", "CORE MESSAGE", shuffled.get(0), false));
    }

    private void createThreeCardSpread(List<TarotMeaning> shuffled) {
        double centerX = canvasWidth * 0.48;
        double cardWidth = clamp(canvasWidth * 0.095, 110, 150);
        double cardHeight = cardWidth * 1.68;
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
        double cardHeight = cardWidth * 1.68;
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
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#14100d")),
                new Stop(0.36, Color.web("#241913")),
                new Stop(0.68, Color.web("#120d12")),
                new Stop(1, Color.web("#070608"))));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        Paint altarLight = new RadialGradient(
                0, 0, canvasWidth * 0.50, canvasHeight * 0.42, canvasWidth * 0.55, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#b47a3c", 0.22)),
                new Stop(0.36, Color.web("#5a3522", 0.16)),
                new Stop(1, Color.TRANSPARENT)
        );
        gc.setFill(altarLight);
        gc.fillOval(canvasWidth * 0.12, canvasHeight * 0.04, canvasWidth * 0.76, canvasHeight * 0.82);

        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#261b25", 0.00)),
                new Stop(0.25, Color.web("#341b4a", 0.10)),
                new Stop(0.55, Color.web("#201129", 0.20)),
                new Stop(1, Color.web("#09070c", 0.04))
        ));
        gc.fillRoundRect(canvasWidth * 0.10, canvasHeight * 0.18, canvasWidth * 0.80, canvasHeight * 0.64, 26, 26);

        gc.setStroke(Color.web("#5b4436", 0.22));
        gc.setLineWidth(1);
        for (int i = 0; i < 6; i++) {
            double ringY = canvasHeight * 0.52 + Math.sin(i * 0.8) * 18;
            gc.strokeOval(canvasWidth * 0.22 + i * 26, ringY, canvasWidth * 0.34, 24 + i * 8);
        }

        gc.setFill(Color.web("#d9bc88", 0.18));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 18));
        String[] runes = {"ᚠ", "ᚱ", "ᚹ", "ᛟ", "ᛞ", "ᚲ", "ᛉ", "ᛒ"};
        for (int i = 0; i < 18; i++) {
            double px = 64 + (i * 97 % Math.max(canvasWidth - 128, 1));
            double py = 74 + (i * 141 % Math.max(canvasHeight - 148, 1));
            gc.fillText(runes[i % runes.length], px, py);
        }

        gc.setFill(Color.web("#f1d2a0", 0.12));
        for (int i = 0; i < 34; i++) {
            double px = 80 + (i * 67 % Math.max(canvasWidth - 160, 1));
            double py = 70 + (i * 113 % Math.max(canvasHeight - 140, 1));
            double r = 1 + (i % 3);
            gc.fillOval(px, py, r, r);
        }
        drawFlowingGoldBackdrop(gc);
    }

    private void drawTopBar(GraphicsContext gc) {
        double barY = 24;
        double barH = 60;
        double inset = 10;

        gc.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4e3b2a", 0.98)),
                new Stop(0.18, Color.web("#2e241d", 0.98)),
                new Stop(0.5, Color.web("#5b4731", 0.98)),
                new Stop(0.82, Color.web("#2e241d", 0.98)),
                new Stop(1, Color.web("#4e3b2a", 0.98))));
        gc.fillRoundRect(inset, barY, canvasWidth - inset * 2, barH, 12, 12);
        gc.setStroke(Color.web("#8f7149", 0.86));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(inset, barY, canvasWidth - inset * 2, barH, 12, 12);
        gc.setStroke(Color.web("#b89462", 0.34));
        gc.strokeLine(inset + 18, barY + 14, canvasWidth - inset - 18, barY + 14);
        gc.strokeLine(inset + 18, barY + barH - 14, canvasWidth - inset - 18, barY + barH - 14);

        drawMetalPlate(gc, 18, barY + 10, 112, 36, "BACK", false);
        drawMetalPlate(gc, canvasWidth - 122, barY + 10, 104, 36, "RE-DEAL", false);

        gc.setFill(Color.web("#b99766", 0.58));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 16));
        gc.fillText("ᚨᚱᚲᚨᚾᚨ", 150, barY + 17);
        gc.fillText("ᚾᛁᛋᚲᛟᛞᛁᛗ", canvasWidth - 286, barY + 17);

        double centerW = 340;
        double centerX = canvasWidth / 2.0 - centerW / 2.0;
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#6d5539")),
                new Stop(0.5, Color.web("#32261c")),
                new Stop(1, Color.web("#6d5539"))));
        gc.fillRoundRect(centerX, barY + 2, centerW, 46, 16, 16);
        gc.setStroke(Color.web("#d3b07c", 0.88));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(centerX, barY + 2, centerW, 46, 16, 16);
        gc.setFill(Color.web("#e8d7b7"));
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
                    new Stop(0, Color.web("#6f573b")),
                    new Stop(1, Color.web("#34261c"))));
            gc.fillRoundRect(x, y, w, h, 10, 10);
            gc.setStroke(Color.web("#cba36a", 0.95));
            gc.strokeRoundRect(x, y, w, h, 10, 10);
            gc.setFill(new RadialGradient(0, 0, x + 18, y + 17, 8, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#f3e7c9")),
                    new Stop(1, Color.web("#c39a5f"))));
            gc.fillOval(x + 10, y + 9, 16, 16);
        } else {
            gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#4a3828")),
                    new Stop(1, Color.web("#221915"))));
            gc.fillRoundRect(x, y, w, h, 10, 10);
            gc.setStroke(Color.web("#71593c", 0.82));
            gc.strokeRoundRect(x, y, w, h, 10, 10);
            gc.setFill(Color.web("#8c6f4e"));
            gc.fillOval(x + 12, y + 11, 10, 10);
        }
        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 14));
        gc.setFill(active ? Color.web("#f0dfbe") : Color.web("#ceb38a"));
        gc.fillText(text, x + 34, y + 8);
    }

    private void drawMetalPlate(GraphicsContext gc, double x, double y, double w, double h,
                                String label, boolean active) {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(active ? "#6f573b" : "#5a4530")),
                new Stop(1, Color.web(active ? "#34261c" : "#2a211a"))));
        gc.fillRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(Color.web(active ? "#c9a46c" : "#8c704b", 0.92));
        gc.setLineWidth(1.1);
        gc.strokeRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(Color.web("#c19a63", 0.30));
        gc.strokeLine(x + 12, y + h - 10, x + w - 12, y + h - 10);
        gc.setFill(Color.web(active ? "#f1dfbd" : "#d8c29c"));
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

        drawCardBack(gc, x, deckGlowY, cardW, cardH, 0.35 + deckBreath * 0.75, true);

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
        gc.setFill(Color.web("#e0c99d", 0.90));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 18));
        gc.fillText(currentSpreadMode == SpreadMode.SINGLE_CARD ? "Single-Card Revelation..." : "Three-Card Spread...",
                x + w / 2.0, y + 2);
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#5e4935", 0.96)),
                new Stop(0.25, Color.web("#2f241a", 0.98)),
                new Stop(0.75, Color.web("#2f241a", 0.98)),
                new Stop(1, Color.web("#5e4935", 0.96))));
        gc.fillRoundRect(x + 8, y + 26, w - 16, h - 12, 16, 16);
        gc.setStroke(Color.web("#90724a", 0.82));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(x + 8, y + 26, w - 16, h - 12, 16, 16);
        gc.setStroke(Color.web("#bc9860", 0.32));
        gc.strokeRoundRect(x + 20, y + 38, w - 40, h - 36, 12, 12);

        double plaqueW = 168;
        double plaqueX = x + w / 2.0 - plaqueW / 2.0;
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#706045")),
                new Stop(1, Color.web("#2f251d"))));
        gc.fillRoundRect(plaqueX, y + 30, plaqueW, 30, 10, 10);
        gc.setStroke(Color.web("#c5a36b", 0.82));
        gc.strokeRoundRect(plaqueX, y + 30, plaqueW, 30, 10, 10);
        gc.setFill(Color.web("#edd8b2"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("CURRENT SPREAD", x + w / 2.0, y + 49);
        gc.setTextAlign(TextAlignment.LEFT);

        drawGoldSweepBand(gc, x + 18, y + 62, w - 36, h - 50, 0.26, 0.22);

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            gc.setFill(Color.web("#d6bc8e"));
            gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 11));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(card.slotLabel, card.x + card.width / 2.0, card.y - 18);

            if (!card.revealed) {
                gc.setStroke(Color.web("#8c7351", 0.38));
                gc.setLineDashes(6);
                gc.strokeRoundRect(card.x - 4, card.y - 4, card.width + 8, card.height + 8, 18, 18);
                gc.setLineDashes(null);
            }

            drawSpreadCard(gc, card, i == flippingIndex ? flipProgress : 1.0, i);

            gc.setFill(Color.web("#cdaa74", 0.78));
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
                drawCardFront(gc, displayX, drawY, card.width, card.height, card, activeCard);
            } else {
                drawCardBack(gc, displayX, drawY, card.width, card.height, activeCard ? 1.0 : 0.0, false);
            }
            if (activeCard && flippingIndex < 0) {
                drawActiveCardHalo(gc, -card.width / 2.0, -card.height / 2.0, card.width, card.height);
            }
        } else {
            double drawY = card.y - hoverLift;
            if (showFront) {
                drawCardFront(gc, displayX, drawY, displayWidth, card.height, card, activeCard);
            } else {
                drawCardBack(gc, displayX, drawY, displayWidth, card.height, activeCard ? 1.0 : 0.0, false);
            }

            if (flipping) {
                drawFlipEnergy(gc, displayX, drawY, displayWidth, card.height, easedProgress);
            }

            if (activeCard && flippingIndex < 0) {
                drawActiveCardHalo(gc, card.x, drawY, card.width, card.height);
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
        drawCardFront(gc, previewX, previewY, previewW, previewH, card, true);
        drawActiveCardHalo(gc, previewX, previewY, previewW, previewH);
        gc.restore();
    }

    private void drawCelticCrossGuide(GraphicsContext gc) {
        double cardWidth = clamp(canvasWidth * 0.074, 90, 114);
        double cardHeight = cardWidth * 1.68;
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

    private void drawCardBack(GraphicsContext gc, double x, double y, double w, double h, double emphasis, boolean deckCard) {
        Paint backFill = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4b3729")),
                new Stop(0.5, Color.web("#2e2219")),
                new Stop(1, Color.web("#1a1411")));
        gc.setFill(backFill);
        gc.fillRoundRect(x, y, w, h, 18, 18);

        if (deckCard) {
            gc.setStroke(Color.web("#f0ca79", 0.16 + emphasis * 0.18));
            gc.setLineWidth(5.0 + emphasis * 2.0);
            gc.strokeRoundRect(x - 4, y - 4, w + 8, h + 8, 22, 22);
        }

        gc.setStroke(Color.web(deckCard ? "#d7b376" : "#a88456", 0.95));
        gc.setLineWidth(deckCard ? 1.8 + emphasis * 0.5 : 1.4 + emphasis);
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.web("#725339", 0.70));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 8, y + 8, w - 16, h - 16, 14, 14);

        drawCenterFiligree(gc, x + w / 2.0, y + 18, 10, false);
        drawCenterFiligree(gc, x + w / 2.0, y + h - 18, 10, true);

        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double r = Math.min(w, h) * 0.19;
        drawHexagram(gc, cx, cy, r);

        gc.setStroke(Color.web("#d8a24b", 0.56));
        gc.strokeOval(cx - r * 1.32, cy - r * 1.32, r * 2.64, r * 2.64);
        gc.strokeOval(cx - r * 0.45, cy - r * 0.45, r * 0.9, r * 0.9);

        gc.setFill(Color.web("#e7c98b", 0.82));
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

    private void drawCardFront(GraphicsContext gc, double x, double y, double w, double h, Card card, boolean active) {
        Paint frontFill = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4b392c")),
                new Stop(0.38, Color.web("#251b16")),
                new Stop(1, Color.web("#17110f")));
        gc.setFill(frontFill);
        gc.fillRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(active ? Color.web("#ffd58c") : Color.web("#8e6a45"));
        gc.setLineWidth(active ? 2.2 : 1.4);
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.web("#684d39"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x + 8, y + 8, w - 16, h - 16, 14, 14);

        gc.setStroke(Color.web("#d8a24b", 0.30));
        gc.setLineWidth(0.9);
        gc.strokeLine(x + 22, y + 24, x + w - 22, y + 24);
        gc.strokeLine(x + 22, y + h - 24, x + w - 22, y + h - 24);

        gc.setStroke(Color.web("#8e6a45", 0.42));
        gc.strokeRoundRect(x + 14, y + 36, w - 28, h - 72, 12, 12);

        gc.setFill(Color.web("#2c211b", 0.92));
        gc.fillRoundRect(x + 18, y + 10, w - 36, 20, 8, 8);
        gc.setStroke(Color.web("#d2aa60", 0.36));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(x + 18, y + 10, w - 36, 20, 8, 8);

        drawCenterFiligree(gc, x + w / 2.0, y + 34, 9, false);
        drawCenterFiligree(gc, x + w / 2.0, y + h - 34, 9, true);

        gc.setFill(Color.web("#17120f"));
        gc.fillRoundRect(x + 16, y + 40, w - 32, h - 112, 10, 10);

        gc.setFill(GOLD_SOFT);
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(10, w * 0.09)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(getDisplayArcanaIndex(card), x + w / 2.0, y + 24.5);

        gc.setFont(Font.font("KaiTi", FontWeight.BOLD, Math.max(13, w * 0.12)));
        gc.fillText(card.meaning.title, x + w / 2.0, y + 38.5);

        gc.setFill(Color.web("#cab48a", 0.86));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(9, w * 0.07)));
        String cornerRank = getCornerRank(card);
        gc.fillText(cornerRank, x + 20, y + 28);
        gc.fillText(cornerRank, x + w - 20, y + h - 14);

        String minorLabel = getMinorFamilyLabel(card);
        if (!minorLabel.isEmpty()) {
            gc.setFill(Color.web("#b99766", 0.82));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, Math.max(8, w * 0.06)));
            gc.fillText(minorLabel, x + w / 2.0, y + 52);
        }

        gc.setFill(card.reversed ? Color.web("#f0b56c") : Color.web("#d7bf86"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, Math.max(9, w * 0.078)));
        gc.fillText(card.reversed ? "REVERSED" : "UPRIGHT", x + w / 2.0, y + 64);

        drawCardIllustration(gc, x + 16, y + 40, w - 32, h - 112, card);

        gc.setFill(Color.web("#2a2019"));
        gc.fillRoundRect(x + 18, y + h - 64, w - 36, 30, 10, 10);
        gc.setStroke(Color.web("#8e6a45", 0.48));
        gc.strokeRoundRect(x + 18, y + h - 64, w - 36, 30, 10, 10);

        gc.setFill(Color.web("#e5d3b0"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, Math.max(9, w * 0.076)));
        drawWrappedTextCentered(gc, String.join(" · ", card.meaning.keywords), x + w / 2.0, y + h - 57, w - 42, 13, 2);

        gc.setFill(Color.web("#aea29a"));
        gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 10));
        drawWrappedTextCentered(gc, card.slotNote, x + w / 2.0, y + h - 24, w - 22, 15, 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private String getDisplayArcanaIndex(Card card) {
        if ("MAJOR ARCANA".equals(card.meaning.family)) {
            return card.meaning.number;
        }
        return getCornerRank(card);
    }

    private String getCornerRank(Card card) {
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

    private String getMinorFamilyLabel(Card card) {
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

    private void drawCardIllustration(GraphicsContext gc, double x, double y, double w, double h, Card card) {
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

    private void drawMinorArcanaSymbol(GraphicsContext gc, double x, double y, double w, double h, Card card) {
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

    private boolean drawSpecialMinorArcana(GraphicsContext gc, double x, double y, double w, double h, Card card) {
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

    private void drawMinorArcanaPips(GraphicsContext gc, double x, double y, double w, double h, Card card, int pipCount) {
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

    private void drawCourtArcanaSymbol(GraphicsContext gc, double x, double y, double w, double h, Card card) {
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

    private void drawActiveCardHalo(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setStroke(Color.web("#ffd489", 0.34));
        gc.setLineWidth(6.0);
        gc.strokeRoundRect(x - 8, y - 8, w + 16, h + 16, 24, 24);
        gc.setStroke(Color.web("#ffd489", 0.88));
        gc.setLineWidth(2.2);
        gc.strokeRoundRect(x - 5, y - 5, w + 10, h + 10, 22, 22);
        gc.setStroke(Color.web("#8f63c0", 0.52));
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

    private void drawGeneralTarotSymbol(GraphicsContext gc, double x, double y, double w, double h, Card card) {
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

    private void drawDetailPanel(GraphicsContext gc) {
        double x = canvasWidth * 0.69;
        double y = canvasHeight * 0.27;
        double w = canvasWidth * 0.24;
        double h = canvasHeight * 0.46;

        gc.setFill(Color.web("#d5bc91"));
        gc.setFont(Font.font("Georgia", FontWeight.SEMI_BOLD, 10));
        gc.fillText("详细解读", x, y - 20);

        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4a3426", 0.98)),
                new Stop(0.20, Color.web("#2f2118", 0.98)),
                new Stop(1, Color.web("#17110f", 0.98))));
        gc.fillRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.web("#8d704a", 0.78));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y, w, h, 18, 18);
        gc.setStroke(Color.web("#d1a85c", 0.32));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(x + 10, y + 10, w - 20, h - 20, 14, 14);
        drawGoldSweepBand(gc, x + 8, y + 8, w - 16, h - 16, 0.54, 0.16);
        drawDetailBoxHardware(gc, x, y, w, h);

        Card card = getActiveDetailCard();
        if (card == null || !card.revealed) {
            gc.setFill(Color.web("#a88c65"));
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 28));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("✧", x + w / 2.0, y + h * 0.42);
            gc.setFill(Color.web("#eddac0"));
            gc.setFont(Font.font("KaiTi", FontWeight.BOLD, 14));
            drawWrappedTextCentered(gc, "请先翻开一张牌", x + w / 2.0, y + h * 0.50, w - 56, 18, 2);
            gc.setFont(Font.font("Georgia", FontWeight.NORMAL, 12));
            gc.setFill(Color.web("#cbb48a"));
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
                    new Stop(clamp(0.18 + Math.sin(phase + i) * 0.05, 0.10, 0.26), Color.web("#f1c76a", 0.00)),
                    new Stop(clamp(0.34 + Math.sin(phase + i * 1.2) * 0.06, 0.24, 0.46), Color.web("#f1c76a", 0.12)),
                    new Stop(clamp(0.50 + Math.cos(phase * 0.8 + i) * 0.05, 0.42, 0.58), Color.web("#fff0b2", 0.20)),
                    new Stop(clamp(0.66 + Math.sin(phase * 0.6 + i) * 0.06, 0.56, 0.78), Color.web("#dfe6f2", 0.11)),
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
        gc.setFill(Color.web("#d9c39a"));
        gc.setFont(Font.font("KaiTi", FontWeight.NORMAL, 11));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("✌ 单张 / 三张切换  ·  ✊ 握拳翻牌  ·  ✋ 当前牌阵全部揭示后张手洗牌",
                canvasWidth / 2.0, canvasHeight - 22);
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
        gc.setStroke(Color.web("#f1b14f"));
        gc.setLineWidth(2);
        gc.strokeOval(handCanvasX - 12, handCanvasY - 12, 24, 24);
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

    /**
     * 牌面数据。
     */
    private static class TarotMeaning {
        String family;
        String number;
        String title;
        String[] keywords;
        String uprightMeaning;
        String reversedMeaning;
        String advice;

        TarotMeaning(String family, String number, String title, String[] keywords,
                     String uprightMeaning, String reversedMeaning, String advice) {
            this.family = family;
            this.number = number;
            this.title = title;
            this.keywords = keywords;
            this.uprightMeaning = uprightMeaning;
            this.reversedMeaning = reversedMeaning;
            this.advice = advice;
        }
    }

    private enum SpreadMode {
        SINGLE_CARD,
        THREE_CARD
    }

    /**
     * 当前牌阵中的卡牌实例。
     */
    private static class Card {
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
