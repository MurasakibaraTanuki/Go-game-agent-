package org.gomasters.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Serialized local KataGo GTP session. close() may also be called from the UI. */
final class GoEngine implements AutoCloseable {
    static final int SIZE = 19, PASS = 361;
    static final String COLS = "ABCDEFGHJKLMNOPQRST";
    private final Random random = new Random();
    private final Process process;
    private final BufferedReader input;
    private final BufferedWriter output;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private int serial;
    private double[] cachedPolicy;

    GoEngine(File executable, File dir, String config, String main, String human) throws IOException {
        process = new ProcessBuilder(executable.getAbsolutePath(), "gtp", "-config", config,
                "-model", main, "-human-model", human).directory(dir)
                .redirectError(new File(dir, "engine.log")).start();
        input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
    }

    void initialize(String rank) throws IOException {
        if (!rank.matches("([1-9]|1[0-9]|20)k|[1-9]d")) throw new IOException("Недопустимый ранг");
        command("boardsize 19");
        command("kata-set-rules chinese");
        command("komi 7.5");
        command("kata-set-param humanSLProfile rank_" + rank);
    }

    private String command(String cmd) throws IOException {
        int id = ++serial;
        ScheduledFuture<?> timeout = timer.schedule(process::destroyForcibly, 90, TimeUnit.SECONDS);
        try {
            output.write(id + " " + cmd + "\n");
            output.flush();
            StringBuilder reply = new StringBuilder();
            for (String line; (line = input.readLine()) != null;) {
                if (line.isEmpty()) {
                    if (reply.length() == 0) continue;
                    String msg = reply.toString().trim();
                    String prefix = "=" + id;
                    if (msg.equals(prefix)) return "";
                    if (msg.startsWith(prefix + " ") || msg.startsWith(prefix + "\n"))
                        return msg.substring(prefix.length()).trim();
                    throw new IOException("KataGo: " + msg.substring(0, Math.min(140, msg.length())));
                }
                if (reply.length() + line.length() > 131072) throw new IOException("Слишком большой ответ KataGo");
                reply.append(line).append('\n');
            }
            throw new IOException("KataGo остановился");
        } finally {
            timeout.cancel(false);
        }
    }

    static String vertex(int point) {
        if (point == PASS) return "pass";
        if (point < 0 || point >= PASS) throw new IllegalArgumentException("Invalid point");
        return COLS.substring(point % SIZE, point % SIZE + 1) + (SIZE - point / SIZE);
    }

    private double[] policy() throws IOException {
        if (cachedPolicy != null) return cachedPolicy;
        List<String> fields = Arrays.asList(command("kata-raw-human-nn 0").split("\\s+"));
        int grid = fields.indexOf("policy"), pass = fields.indexOf("policyPass");
        if (grid < 0 || pass < 0 || grid + PASS >= fields.size() || pass + 1 >= fields.size())
            throw new IOException("Неполный humanPolicy");
        double[] values = new double[PASS+1];
        for (int i = 0; i <= PASS; i++) {
            String field = fields.get(i == PASS ? pass+1 : grid+1+i);
            values[i] = field.equalsIgnoreCase("nan") ? -1 : Double.parseDouble(field);
            if (Double.isInfinite(values[i])) throw new IOException("Некорректный humanPolicy");
        }
        cachedPolicy = values;
        return values;
    }

    int choose() throws IOException {
        double[] weights = policy();
        double total = 0;
        for (double p : weights) if (p > 0) total += p;
        if (!(total > 0)) throw new IOException("Модель не предложила ход");
        double threshold = random.nextDouble() * total;
        int last = PASS;
        for (int i = 0; i <= PASS; i++) {
            if (weights[i] <= 0) continue;
            last = i;
            threshold -= weights[i];
            if (threshold < 0) return i;
        }
        return last;
    }

    boolean legal(int point) throws IOException { return point == PASS || policy()[point] >= 0; }

    void play(int ply, int point) throws IOException {
        command("play " + ((ply & 1) == 0 ? "B " : "W ") + vertex(point));
        cachedPolicy = null;
    }

    char[] board() throws IOException {
        char[] stones = new char[PASS];
        int next = SIZE;
        for (String line : command("showboard").split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.matches("[0-9]+\\s+[.XO0-9\\s]+")) continue;
            String[] parts = trimmed.split("\\s+", 2);
            String cells = parts[1].replaceAll("[^XO.]","");
            if (Integer.parseInt(parts[0]) != next || cells.length() != SIZE)
                throw new IOException("Некорректная строка доски");
            cells.getChars(0, SIZE, stones, (SIZE-next)*SIZE);
            next--;
        }
        if (next != 0) throw new IOException("Неполная доска");
        return stones;
    }

    String score() throws IOException { return command("final_score"); }

    public void close() {
        process.destroyForcibly();
        timer.shutdownNow();
    }
}
