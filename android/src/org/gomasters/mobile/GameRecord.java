package org.gomasters.mobile;

import java.util.*;

/** Immutable snapshot of an already engine-validated, alternating 19x19 game. */
final class GameRecord {
    static final int SIZE = 19, PASS = SIZE * SIZE;
    private final List<Integer> moves;
    private final List<char[]> positions = new ArrayList<>();

    GameRecord(List<Integer> history) {
        moves = Collections.unmodifiableList(new ArrayList<>(history));
        char[] board = new char[PASS];
        Arrays.fill(board, '.');
        positions.add(board.clone());
        for (int ply = 0; ply < moves.size(); ply++) {
            int point = moves.get(ply);
            if (point != PASS) {
                if (point < 0 || point >= PASS || board[point] != '.')
                    throw new IllegalArgumentException("Invalid recorded move " + (ply + 1));
                char color = (ply & 1) == 0 ? 'X' : 'O';
                char opponent = color == 'X' ? 'O' : 'X';
                board[point] = color;
                for (int adjacent : neighbors(point)) {
                    if (board[adjacent] != opponent) continue;
                    List<Integer> group = groupWithoutLiberties(board, adjacent);
                    if (group != null) for (int stone : group) board[stone] = '.';
                }
                if (groupWithoutLiberties(board, point) != null)
                    throw new IllegalArgumentException("Suicide in recorded move " + (ply + 1));
            }
            positions.add(board.clone());
        }
    }

    int size() { return moves.size(); }
    int last(int ply) { return ply == 0 ? -1 : moves.get(ply - 1); }
    char[] position(int ply) { return positions.get(ply).clone(); }

    private static int[] neighbors(int p) {
        int[] result = new int[4];
        int n = 0;
        if (p % SIZE > 0) result[n++] = p - 1;
        if (p % SIZE < SIZE - 1) result[n++] = p + 1;
        if (p >= SIZE) result[n++] = p - SIZE;
        if (p < PASS - SIZE) result[n++] = p + SIZE;
        return Arrays.copyOf(result, n);
    }

    /** Null means a liberty exists. No independent ko/superko validation:
     * only the accepted GTP move history is used, not imported SGF. */
    private static List<Integer> groupWithoutLiberties(char[] board, int start) {
        List<Integer> group = new ArrayList<>();
        boolean[] seen = new boolean[PASS];
        char color = board[start];
        group.add(start);
        seen[start] = true;
        for (int i = 0; i < group.size(); i++) {
            for (int q : neighbors(group.get(i))) {
                if (board[q] == '.') return null;
                if (!seen[q] && board[q] == color) {
                    seen[q] = true;
                    group.add(q);
                }
            }
        }
        return group;
    }

    /** FF[4] SGF: origin at top left, passes are empty, no invented result. */
    String sgf() {
        StringBuilder out = new StringBuilder("(;GM[1]FF[4]CA[UTF-8]AP[Go Masters:0.1]SZ[19]RU[Chinese]KM[7.5]");
        for (int i = 0; i < moves.size(); i++) {
            int p = moves.get(i);
            out.append((i & 1) == 0 ? ";B[" : ";W[");
            if (p != PASS) out.append((char)('a' + p % SIZE)).append((char)('a' + p / SIZE));
            out.append(']');
        }
        return out.append(")\n").toString();
    }
}
