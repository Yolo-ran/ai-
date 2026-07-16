package com.gesturegame.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 剪刀石头布对局 CSV 仓库。
 *
 * <p>每场比赛追加一行记录；启动时扫描历史结果，重新计算累计胜负和平局，
 * 不依赖某一行中缓存的统计值，避免文件被手动编辑后造成胜率失真。</p>
 */
public final class RpsCsvStatsStore {

    private static final String HEADER = String.join(",",
            "timestamp", "difficulty", "format_rounds", "played_rounds",
            "player_score", "computer_score", "result", "total_matches",
            "wins", "losses", "draws", "win_rate_percent");

    /** 单场最终结果。 */
    public enum Outcome {
        WIN,
        LOSS,
        DRAW
    }

    /** 历史累计统计。 */
    public record Summary(int totalMatches, int wins, int losses, int draws) {

        public static Summary empty() {
            return new Summary(0, 0, 0, 0);
        }

        public double winRatePercent() {
            return totalMatches == 0 ? 0.0 : wins * 100.0 / totalMatches;
        }

        private Summary plus(Outcome outcome) {
            return new Summary(
                    totalMatches + 1,
                    wins + (outcome == Outcome.WIN ? 1 : 0),
                    losses + (outcome == Outcome.LOSS ? 1 : 0),
                    draws + (outcome == Outcome.DRAW ? 1 : 0));
        }
    }

    private final Path csvPath;

    public RpsCsvStatsStore() {
        this(resolveDefaultPath());
    }

    /** 允许测试或便携版本指定记录文件位置。 */
    public RpsCsvStatsStore(Path csvPath) {
        this.csvPath = csvPath.toAbsolutePath().normalize();
    }

    public Path getCsvPath() {
        return csvPath;
    }

    /** 读取全部有效记录并重新汇总统计。 */
    public synchronized Summary loadSummary() throws IOException {
        if (!Files.exists(csvPath) || Files.size(csvPath) == 0L) {
            return Summary.empty();
        }

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        int wins = 0;
        int losses = 0;
        int draws = 0;
        int total = 0;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 7) {
                continue;
            }
            try {
                Outcome outcome = Outcome.valueOf(columns[6].trim());
                total++;
                switch (outcome) {
                    case WIN -> wins++;
                    case LOSS -> losses++;
                    case DRAW -> draws++;
                }
            } catch (IllegalArgumentException ignored) {
                // 跳过损坏或人工添加的非标准行，其余历史记录仍然有效。
            }
        }
        return new Summary(total, wins, losses, draws);
    }

    /** 追加一场比赛，并返回包含本场结果的最新累计统计。 */
    public synchronized Summary appendMatch(String difficulty,
                                             int formatRounds,
                                             int playedRounds,
                                             int playerScore,
                                             int computerScore,
                                             Outcome outcome) throws IOException {
        Summary updated = loadSummary().plus(outcome);
        Path parent = csvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean needsHeader = !Files.exists(csvPath) || Files.size(csvPath) == 0L;
        StringBuilder content = new StringBuilder();
        if (needsHeader) {
            content.append(HEADER).append(System.lineSeparator());
        }
        content.append(String.join(",",
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                safeCsvValue(difficulty),
                Integer.toString(formatRounds),
                Integer.toString(playedRounds),
                Integer.toString(playerScore),
                Integer.toString(computerScore),
                outcome.name(),
                Integer.toString(updated.totalMatches()),
                Integer.toString(updated.wins()),
                Integer.toString(updated.losses()),
                Integer.toString(updated.draws()),
                String.format(Locale.ROOT, "%.2f", updated.winRatePercent())))
                .append(System.lineSeparator());

        Files.writeString(csvPath, content.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return updated;
    }

    private static Path resolveDefaultPath() {
        String override = System.getProperty("gesturegame.data.dir", "").trim();
        if (!override.isEmpty()) {
            return Path.of(override).resolve("rps_records.csv");
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path baseDirectory;
        if (localAppData != null && !localAppData.isBlank()) {
            baseDirectory = Path.of(System.getProperty("user.dir"), "data");
        } else {
            baseDirectory = Path.of(System.getProperty("user.dir"), "data");
        }
        return baseDirectory.resolve("rps_records.csv");
    }

    private static String safeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', '_').replace('\r', ' ').replace('\n', ' ');
    }
}
