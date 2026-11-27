package org.gomasters.mobile;

import java.util.*;

/** Pure presentation logic. All evaluations are from Black's perspective. */
final class CoachText {
    static final int PASS=361;
    static final class Candidate {
        final int point, visits, order;
        final double lead, winrate;
        Candidate(int point,int visits,int order,double lead,double winrate) {
            if(point<0 || point>PASS || visits<0 || order<0 ||
                !Double.isFinite(lead) || !Double.isFinite(winrate) || winrate<0 || winrate>1)
                throw new IllegalArgumentException("Invalid evaluation");
            this.point=point; this.visits=visits; this.order=order;
            this.lead=lead; this.winrate=winrate;
        }
    }

    static Candidate best(List<Candidate> candidates) {
        Candidate best=null;
        for(Candidate c:candidates) if(c.visits>0 && (best==null || c.order<best.order)) best=c;
        if(best==null) throw new IllegalArgumentException("No evaluated moves");
        return best;
    }

    static String vertex(int point) {
        if(point==PASS) return "пас";
        return "ABCDEFGHJKLMNOPQRST".charAt(point%19)+Integer.toString(19-point/19);
    }

    static String describe(char[] board,boolean black,Candidate best,Candidate alternative,int played) {
        String advice="KataGo предлагает "+vertex(best.point)+".";
        String comparison="";
        if(played==best.point) comparison=" Сыгранный ход совпадает с рекомендацией.";
        else if(played>=0) {
            if(alternative!=null && alternative.point==played && best.visits>=12 && alternative.visits>=12) {
                double delta=(black?1:-1)*(best.lead-alternative.lead);
                if(delta>=2) comparison=String.format(Locale.ROOT," Поиск оценивает этот вариант примерно на %.1f очка лучше сыгранного %s.",delta,vertex(played));
                else if(delta<=-2) comparison=" Оценка очков не подтверждает преимущество рекомендации над сыгранным ходом.";
                else comparison=" Оценки очков близки; явная ошибка не установлена.";
            } else comparison=" Для надёжного сравнения с "+vertex(played)+" пока недостаточно расчёта.";
        }
        String tactical=tactic(board,black,best.point);
        if(tactical.isEmpty() && played<0) tactical=" Это приоритет поиска, а не доказательство единственного правильного хода.";
        return advice+comparison+tactical;
    }

    private static int[] neighbors(int p) {
        int[] a=new int[4];int n=0;
        if(p%19>0)a[n++]=p-1;
        if(p%19<18)a[n++]=p+1;
        if(p>=19)a[n++]=p-19;
        if(p<342)a[n++]=p+19;
        return Arrays.copyOf(a,n);
    }

    /** Report directly verifiable liberties, never assert life/death from atari alone. */
    static String tactic(char[] board,boolean black,int point) {
        if(board.length!=PASS || point<0 || point>=PASS || board[point]!='.') return "";
        char own=black?'X':'O';
        boolean[] seen=new boolean[PASS];
        int captured=0, threatened=0;
        for(int q:neighbors(point)) {
            if(board[q]=='.' || seen[q])continue;
            char color=board[q];
            ArrayList<Integer> group=new ArrayList<>();
            Set<Integer> liberties=new HashSet<>();
            group.add(q);seen[q]=true;
            for(int i=0;i<group.size();i++) for(int p:neighbors(group.get(i))) {
                if(board[p]=='.')liberties.add(p);
                else if(board[p]==color&&!seen[p]){seen[p]=true;group.add(p);}
            }
            if(liberties.size()==1&&liberties.contains(point)) {
                if(color==own)threatened+=group.size(); else captured+=group.size();
            }
        }
        if(captured>0) return " Ход снимает камни соперника ("+captured+").";
        if(threatened>0) return " Здесь единственное дыхание своей группы; её спасение ещё нужно проверить.";
        return "";
    }
}
