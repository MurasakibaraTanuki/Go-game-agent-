package org.gomasters.mobile;

import java.util.*;

/** Board facts and display-only ownership estimates. Positive ownership is Black. */
final class BoardInsights {
    private static final int N=361;
    private static int[] neighbors(int p) {
        int[] a=new int[4]; int n=0;
        if(p%19>0)a[n++]=p-1;
        if(p%19<18)a[n++]=p+1;
        if(p>=19)a[n++]=p-19;
        if(p<342)a[n++]=p+19;
        return Arrays.copyOf(a,n);
    }
    static final class Atari {
        final boolean[] stones=new boolean[N];
        final boolean[] liberties=new boolean[N];
        int blackGroups,whiteGroups;
        String summary() {
            if(blackGroups+whiteGroups==0)return "Групп с одним дыханием нет. Это не исключает другие слабости.";
            return "Группы в атари: чёрные "+blackGroups+", белые "+whiteGroups+". Оранжевым отмечены камни, жёлтым — единственные дыхания.";
        }
    }
    static Atari atari(char[] board) {
        if(board.length!=N)throw new IllegalArgumentException("Board size");
        Atari result=new Atari(); boolean[] seen=new boolean[N];
        for(int p=0;p<N;p++) {
            char color=board[p];
            if(seen[p]||(color!='X'&&color!='O'))continue;
            List<Integer> group=new ArrayList<>(); Set<Integer> liberties=new HashSet<>();
            group.add(p);seen[p]=true;
            for(int i=0;i<group.size();i++)for(int q:neighbors(group.get(i))) {
                if(board[q]=='.')liberties.add(q);
                else if(board[q]==color&&!seen[q]){seen[q]=true;group.add(q);}
            }
            if(liberties.size()!=1)continue;
            for(int q:group)result.stones[q]=true;
            result.liberties[liberties.iterator().next()]=true;
            if(color=='X')result.blackGroups++;else result.whiteGroups++;
        }
        return result;
    }
    static double[] ownership(double[] values) {
        if(values.length!=N)throw new IllegalArgumentException("Ownership size");
        for(double v:values)if(!Double.isFinite(v)||v < -1.000001||v > 1.000001)
            throw new IllegalArgumentException("Invalid ownership");
        return values.clone();
    }
    static int ownershipColor(double value) {
        double magnitude=Math.min(1,Math.abs(value));
        if(magnitude<0.15)return 0;
        int alpha=(int)Math.round(190*magnitude);
        return (alpha<<24)|(value>0?0x00202020:0x00ffffff);
    }
}
