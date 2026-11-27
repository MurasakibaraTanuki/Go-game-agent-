package org.gomasters.mobile;
import java.util.*;

public final class CoachTextTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static CoachText.Candidate c(int p,int visits,int order,double lead){
        return new CoachText.Candidate(p,visits,order,lead,0.5);
    }
    public static void main(String[] args){
        char[] empty=new char[361];Arrays.fill(empty,'.');
        check(CoachText.best(Arrays.asList(c(0,0,0,0),c(1,24,2,0),c(2,20,1,0))).point==2,"engine order");
        check(CoachText.vertex(0).equals("A19")&&CoachText.vertex(360).equals("T1"),"coordinates");
        check(CoachText.vertex(361).equals("пас"),"pass");
        check(CoachText.describe(empty,true,c(0,32,0,4),c(1,32,1,1),1).contains("3.0"),"black lead");
        check(CoachText.describe(empty,false,c(0,32,0,-4),c(1,32,1,-1),1).contains("3.0"),"white lead");
        check(CoachText.describe(empty,true,c(0,32,0,1),c(1,32,1,4),1).contains("не подтверждает"),"contradictory score");
        check(CoachText.describe(empty,true,c(0,32,0,1),c(1,32,1,0),1).contains("близки"),"small gap");
        check(CoachText.describe(empty,true,c(0,32,0,5),c(1,1,1,0),1).contains("недостаточно"),"low visits");
        check(CoachText.describe(empty,true,c(0,32,0,5),null,0).contains("совпадает"),"same move");
        char[] corner=empty.clone();corner[0]='O';corner[1]='X';
        check(CoachText.tactic(corner,true,19).contains("(1)"),"capture");
        check(CoachText.tactic(corner,false,19).contains("единственное дыхание"),"own group in atari");
        check(CoachText.tactic(empty,true,19).isEmpty(),"no invented tactic");
        check(CoachText.tactic(corner,true,361).isEmpty(),"pass no tactic");
        System.out.println("CoachText tests passed");
    }
}
