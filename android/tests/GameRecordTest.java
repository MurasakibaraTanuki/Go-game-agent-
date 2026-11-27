package org.gomasters.mobile;

import java.util.*;

public final class GameRecordTest {
    static void check(boolean ok,String msg) { if(!ok) throw new AssertionError(msg); }
    static GameRecord record(Integer... moves) { return new GameRecord(Arrays.asList(moves)); }
    public static void main(String[] args) {
        GameRecord corner=record(1,0,19);
        check(corner.position(2)[0]=='O',"white corner before capture");
        check(corner.position(3)[0]=='.',"corner captured");
        check(corner.position(3)[1]=='X' && corner.position(3)[19]=='X',"black stones retained");
        check(corner.position(0)[0]=='.',"empty start");
        GameRecord chain=record(2,0,19,1,20);
        check(chain.position(4)[0]=='O' && chain.position(4)[1]=='O',"connected white group");
        check(chain.position(5)[0]=='.' && chain.position(5)[1]=='.',"whole group captured");
        GameRecord white=record(0,1,361,19);
        check(white.position(4)[0]=='.',"white captures black");
        check(Arrays.equals(white.position(2),white.position(3)),"pass leaves board unchanged");
        GameRecord edges=record(0,360,361,361);
        check(edges.sgf().equals("(;GM[1]FF[4]CA[UTF-8]AP[Go Masters:0.1]SZ[19]RU[Chinese]KM[7.5];B[aa];W[ss];B[];W[])\n"),"SGF orientation and passes");
        check(edges.last(0)==-1 && edges.last(4)==361 && edges.size()==4,"navigation bounds");
        char[] copy=edges.position(1); copy[0]='O';
        check(edges.position(1)[0]=='X',"immutable snapshots");
        List<Integer> source=new ArrayList<>(Arrays.asList(0));
        GameRecord snap=new GameRecord(source); source.add(1);
        check(snap.size()==1,"immutable history");
        check(record().size()==0,"empty history");
        for(Integer[] invalid : new Integer[][]{{0,0},{-1},{362},{1,0,19,0}}) {
            boolean rejected=false;
            try {record(invalid);} catch(IllegalArgumentException ex) {rejected=true;}
            check(rejected,"invalid history rejected");
        }
        GameRecord original=record(1,0,19,360);
        GameRecord alternative=original.variation(2,Arrays.asList(20,360));
        check(alternative.size()==4 && alternative.position(3)[0]=='O',"alternate avoids original capture");
        check(original.position(3)[0]=='.',"played record remains unchanged");
        check(Arrays.equals(original.position(2),alternative.position(2)),"variation root identical");
        check(original.variation(0,Arrays.asList(361)).last(1)==361,"variation pass from empty board");
        System.out.println("GameRecord tests passed");
    }
}
