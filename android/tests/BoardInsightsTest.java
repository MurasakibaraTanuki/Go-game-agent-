package org.gomasters.mobile;
import java.util.*;

public final class BoardInsightsTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static char[] empty(){char[] b=new char[361];Arrays.fill(b,'.');return b;}
    public static void main(String[] args){
        char[] b=empty();b[0]='X';b[1]='X';b[2]='O';b[19]='O';
        BoardInsights.Atari a=BoardInsights.atari(b);
        check(a.blackGroups==1&&a.whiteGroups==0,"connected group counted once");
        check(a.stones[0]&&a.stones[1]&&a.liberties[20],"all group stones and last liberty");
        b[19]='.';check(BoardInsights.atari(b).blackGroups==0,"two liberties not atari");
        b=empty();b[360]='O';b[359]='X';a=BoardInsights.atari(b);
        check(a.whiteGroups==1&&a.stones[360]&&a.liberties[341],"white corner");
        b=empty();b[18]='X';b[17]='O';a=BoardInsights.atari(b);
        check(a.blackGroups==1&&a.liberties[37]&&!a.liberties[19],"no row wrap");
        check(BoardInsights.atari(empty()).blackGroups==0,"empty board");
        double[] own=new double[361];own[0]=1;own[360]=-1;
        double[] copy=BoardInsights.ownership(own);own[0]=0;
        check(copy[0]==1&&copy[360]==-1,"copy and ordering");
        check((BoardInsights.ownershipColor(1)&0xffffff)==0x202020,"black positive");
        check((BoardInsights.ownershipColor(-1)&0xffffff)==0xffffff,"white negative");
        check(BoardInsights.ownershipColor(0.1)==0,"uncertain hidden");
        check((BoardInsights.ownershipColor(0.5)>>>24)<(BoardInsights.ownershipColor(1)>>>24),"confidence opacity");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,1.1,-1.1}){
            own[0]=invalid;boolean rejected=false;
            try{BoardInsights.ownership(own);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"invalid ownership rejected");
        }
        boolean rejected=false;try{BoardInsights.ownership(new double[360]);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"wrong size");
        System.out.println("BoardInsights tests passed");
    }
}
