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
        Result(CoachText.Candidate best,CoachText.Candidate played) {this.best=best;this.played=played;}
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
            List<CoachText.Candidate> candidates=query(p,in,out,record,turn,-1,64);
            CoachText.Candidate best=CoachText.best(candidates);
            CoachText.Candidate actual=null;
            for(CoachText.Candidate c:candidates) if(c.point==played)actual=c;
            // Equal-budget constrained searches if one candidate lacks adequate visits.
            if(played>=0&&played!=best.point&&(actual==null||actual.visits<12||best.visits<12)) {
                CoachText.Candidate preferred=CoachText.best(query(p,in,out,record,turn,best.point,32));
                CoachText.Candidate selected=CoachText.best(query(p,in,out,record,turn,played,32));
                if(preferred.point!=best.point||selected.point!=played)
                    throw new IOException("KataGo не подтвердил заданные варианты");
                best=preferred; actual=selected;
            }
            return new Result(best,actual);
        } finally {close();}
    }

    private List<CoachText.Candidate> query(Process p,BufferedReader in,BufferedWriter out,
        GameRecord record,int turn,int forced,int visits) throws IOException,JSONException {
        String id="coach-"+(++serial), player=(turn&1)==0?"B":"W";
        JSONArray moves=new JSONArray();
        for(int ply=0;ply<turn;ply++)
            moves.put(new JSONArray().put((ply&1)==0?"B":"W").put(GoEngine.vertex(record.last(ply+1))));
        JSONObject request=new JSONObject().put("id",id).put("moves",moves).put("rules","chinese")
            .put("komi",7.5).put("boardXSize",19).put("boardYSize",19).put("maxVisits",visits);
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
            if(response.optBoolean("isDuringSearch")||response.getInt("turnNumber")!=turn||
                !player.equals(response.getJSONObject("rootInfo").getString("currentPlayer")))
                throw new IOException("Ответ не соответствует позиции");
            JSONArray list=response.getJSONArray("moveInfos");
            List<CoachText.Candidate> candidates=new ArrayList<>();
            for(int i=0;i<list.length();i++){
                JSONObject c=list.getJSONObject(i);
                candidates.add(new CoachText.Candidate(point(c.getString("move")),c.getInt("visits"),
                    c.getInt("order"),c.getDouble("scoreLead"),c.getDouble("winrate")));
            }
            return candidates;
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
