package org.gomasters.mobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** One cancellable, post-game analysis process; no Human SL or live GTP state. */
final class PositionAnalysis implements AutoCloseable {
    private Process process;
    private boolean closed;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private int serial;

    static final class Result {
        final CoachText.Candidate best, played;
        final double[] ownership;
        final List<Variation> variations;
        Result(CoachText.Candidate best,CoachText.Candidate played, Search root) {
            this.best=best;this.played=played;ownership=root.ownership;
            variations=Collections.unmodifiableList(new ArrayList<>(root.variations));
        }
    }
    static final class Variation {
        final CoachText.Candidate candidate;
        final List<Integer> moves;
        Variation(CoachText.Candidate candidate,List<Integer> moves) {
            this.candidate=candidate;this.moves=Collections.unmodifiableList(new ArrayList<>(moves));
        }
    }
    private static final class Search {
        final List<CoachText.Candidate> candidates=new ArrayList<>();
        final List<Variation> variations=new ArrayList<>();
        double[] ownership;
    }

    private synchronized void attach(Process p) throws IOException {
        if(closed){p.destroyForcibly();throw new IOException("Анализ отменён");}
        process=p;
    }

    Result run(File executable,File directory,File config,File model,GameRecord record,int turn,int played)
        throws IOException,JSONException {
        if(turn<0||turn>record.size()) throw new IllegalArgumentException("Invalid turn");
        Process p=new ProcessBuilder(executable.getAbsolutePath(),"analysis","-config",
            config.getAbsolutePath(),"-model",model.getAbsolutePath()).directory(directory)
            .redirectError(new File(directory,"analysis-engine.log")).start();
        attach(p);
        try(BufferedReader in=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(p.getOutputStream(),StandardCharsets.UTF_8))) {
            Search root=query(p,in,out,record,turn,-1,64);
            List<CoachText.Candidate> candidates=root.candidates;
            CoachText.Candidate best=CoachText.best(candidates);
            CoachText.Candidate actual=null;
            for(CoachText.Candidate c:candidates) if(c.point==played)actual=c;
            // Equal-budget constrained searches if one candidate lacks adequate visits.
            if(played>=0&&played!=best.point&&(actual==null||actual.visits<12||best.visits<12)) {
                CoachText.Candidate preferred=CoachText.best(query(p,in,out,record,turn,best.point,32).candidates);
                CoachText.Candidate selected=CoachText.best(query(p,in,out,record,turn,played,32).candidates);
                if(preferred.point!=best.point||selected.point!=played)
                    throw new IOException("KataGo не подтвердил заданные варианты");
                best=preferred; actual=selected;
            }
            return new Result(best,actual,root);
        } finally {close();}
    }

    private Search query(Process p,BufferedReader in,BufferedWriter out,
        GameRecord record,int turn,int forced,int visits) throws IOException,JSONException {
        String id="coach-"+(++serial), player=(turn&1)==0?"B":"W";
        JSONArray moves=new JSONArray();
        for(int ply=0;ply<turn;ply++)
            moves.put(new JSONArray().put((ply&1)==0?"B":"W").put(GoEngine.vertex(record.last(ply+1))));
        JSONObject request=new JSONObject().put("id",id).put("moves",moves).put("rules","chinese")
            .put("komi",7.5).put("boardXSize",19).put("boardYSize",19).put("maxVisits",visits);
        if(forced<0)request.put("includeOwnership",true);
        if(forced>=0)request.put("allowMoves",new JSONArray().put(new JSONObject()
            .put("player",player).put("moves",new JSONArray().put(GoEngine.vertex(forced))).put("untilDepth",1)));
        ScheduledFuture<?> timeout=timer.schedule(p::destroyForcibly,120,TimeUnit.SECONDS);
        try {
            out.write(request.toString());out.newLine();out.flush();
            String line=in.readLine();
            if(line==null)throw new IOException("KataGo завершился или превышено время анализа");
            JSONObject response=new JSONObject(line);
            if(!id.equals(response.optString("id")))throw new IOException("Неожиданный ответ KataGo");
            if(response.has("error"))throw new IOException(response.optString("error"));
            // Fail closed on warnings: KataGo may otherwise silently change rules or settings.
            if(response.has("warning"))throw new IOException("Предупреждение KataGo: "+response.optString("warning"));
            if(response.optBoolean("isDuringSearch")||response.getInt("turnNumber")!=turn||
                !player.equals(response.getJSONObject("rootInfo").getString("currentPlayer")))
                throw new IOException("Ответ не соответствует позиции");
            JSONArray list=response.getJSONArray("moveInfos");
            Search search=new Search();
            for(int i=0;i<list.length();i++){
                JSONObject c=list.getJSONObject(i);
                CoachText.Candidate candidate=new CoachText.Candidate(point(c.getString("move")),c.getInt("visits"),
                    c.getInt("order"),c.getDouble("scoreLead"),c.getDouble("winrate"));
                search.candidates.add(candidate);
                JSONArray pv=c.optJSONArray("pv");
                if(forced<0&&candidate.visits>0&&pv!=null&&pv.length()>0) {
                    List<Integer> lineMoves=new ArrayList<>();
                    for(int j=0;j<Math.min(8,pv.length());j++)lineMoves.add(point(pv.getString(j)));
                    if(lineMoves.get(0)==candidate.point)search.variations.add(new Variation(candidate,lineMoves));
                }
            }
            search.variations.sort(Comparator.comparingInt(v->v.candidate.order));
            if(search.variations.size()>3)search.variations.subList(3,search.variations.size()).clear();
            if(forced<0) {
                JSONArray map=response.getJSONArray("ownership");
                double[] values=new double[map.length()];
                for(int i=0;i<values.length;i++)values[i]=map.getDouble(i);
                search.ownership=BoardInsights.ownership(values);
            }
            return search;
        } finally {timeout.cancel(false);}
    }

    static int point(String vertex) throws IOException {
        if("pass".equalsIgnoreCase(vertex))return GoEngine.PASS;
        if(!vertex.matches("[A-HJ-T](?:[1-9]|1[0-9])"))throw new IOException("Неизвестная координата KataGo");
        return (19-Integer.parseInt(vertex.substring(1)))*19+GoEngine.COLS.indexOf(vertex.charAt(0));
    }

    @Override public synchronized void close() {
        closed=true;
        if(process!=null){process.destroyForcibly();process=null;}
        timer.shutdownNow();
    }
}
