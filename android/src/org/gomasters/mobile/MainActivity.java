package org.gomasters.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String MAIN = "kata1-b10c128-s1141046784-d204142634.txt.gz";
    private static final String HUMAN = "b18c384nbt-humanv0.bin.gz";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Integer> moves = new ArrayList<>();
    private final long[] elapsed = new long[2];
    private volatile int epoch;
    private GoEngine engine;
    private Goban board;
    private TextView clock, status;
    private SharedPreferences prefs;
    private boolean focus, foreground, botWhite;
    private int ticking = -1;
    private long since;
    private String rank;
    private static final int EXPORT_SGF = 41;
    private String pendingSgf;
    private boolean gameOver;
    private PositionAnalysis analysis;
    private final Runnable ticker = new Runnable() {
        public void run() { renderTime(); ui.postDelayed(this, 250); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("game",MODE_PRIVATE);
        pendingSgf=state==null?null:state.getString("pendingSgf");
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff21221e);
        clock=new TextView(this); clock.setGravity(Gravity.CENTER); clock.setTextSize(20);
        clock.setMinHeight(dp(48)); clock.setOnClickListener(v->menu());
        clock.setContentDescription("Часы и меню партии");
        layout.addView(clock);
        board=new Goban(this,this::human);
        layout.addView(board,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this); status.setTextSize(16); status.setGravity(Gravity.CENTER);
        status.setMinHeight(dp(48)); status.setOnClickListener(v->menu()); layout.addView(status);
        setContentView(layout);
        board.precision=prefs.getBoolean("precision",true);
        board.autoOut=prefs.getBoolean("autoOut",true);
        focus=prefs.getBoolean("focus",false);
        setFocus();
        rank=prefs.getString("rank","5k"); botWhite=prefs.getBoolean("botWhite",true);
        elapsed[0]=prefs.getLong("blackMs",0); elapsed[1]=prefs.getLong("whiteMs",0);
        String history=prefs.getString("history","");
        if(!history.isEmpty()) for(String p:history.split(",")) moves.add(Integer.parseInt(p));
        start();
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private boolean humanTurn() { return ((moves.size()&1)==0)==botWhite; }
    private void message(String text) { status.setText(text+"   ·   Меню"); }

    private void setFocus() {
        status.setVisibility(focus?View.GONE:View.VISIBLE);
        int flags=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(focus?flags:0);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if(hasFocus && status!=null) setFocus();
    }

    private void renderTime() {
        long now=SystemClock.elapsedRealtime();
        clock.setText("●  "+format(elapsed[0]+(foreground&&ticking==0?now-since:0))+
                "       ○  "+format(elapsed[1]+(foreground&&ticking==1?now-since:0)));
    }
    private static String format(long ms) {
        long sec=ms/1000; return String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);
    }
    private void turnClock(int next) {
        long now=SystemClock.elapsedRealtime();
        if(foreground && ticking>=0) elapsed[ticking]+=now-since;
        since=now; ticking=next; renderTime();
    }
    @Override protected void onResume() {
        super.onResume(); since=SystemClock.elapsedRealtime(); foreground=true; ui.post(ticker);
    }
    @Override protected void onPause() {
        turnClock(ticking); foreground=false; ui.removeCallbacks(ticker); save(); super.onPause();
    }
    private void save() {
        StringBuilder history=new StringBuilder();
        for(int move:moves) { if(history.length()>0)history.append(','); history.append(move); }
        prefs.edit().putString("history",history.toString()).putString("rank",rank)
            .putBoolean("botWhite",botWhite).putLong("blackMs",elapsed[0]).putLong("whiteMs",elapsed[1]).apply();
    }

    private File asset(String name) throws IOException {
        File dest=new File(getFilesDir(),name);
        if(!name.endsWith(".cfg") && dest.isFile() && dest.length()>0) return dest;
        File tmp=File.createTempFile("asset-", ".part",getFilesDir());
        try {
            try(InputStream in=getAssets().open(name); OutputStream out=new FileOutputStream(tmp)) {
                byte[] buf=new byte[65536]; int count;
                while((count=in.read(buf))!=-1) out.write(buf,0,count);
            }
            Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally { tmp.delete(); }
        return dest;
    }

    private synchronized void stopEngine() {
        epoch++;
        if(engine!=null) { engine.close(); engine=null; }
    }
    private synchronized boolean attach(int id,GoEngine next) {
        if(id!=epoch) {next.close(); return false;}
        engine=next;return true;
    }
    private void post(int id,Runnable action) { ui.post(()->{if(epoch==id) action.run();}); }
    private void error(int id,Exception ex) {
        post(id,()->{
            stopEngine(); board.active=false; turnClock(-1);
            if(focus) {focus=false;setFocus();}
            message("Ошибка движка: "+ex.getMessage());
        });
    }

    private void start() {
        gameOver=false;
        stopEngine(); int id=epoch;
        board.active=false; board.moveCount=0; board.last=-1; board.resetCamera(); Arrays.fill(board.stones,'.');
        turnClock(-1); message("Загрузка KataGo…");
        final ArrayList<Integer> previous=new ArrayList<>(moves);
        final String selectedRank=rank;
        worker.execute(()->{
            try {
                File main=asset(MAIN),human=asset(HUMAN),cfg=asset("game.cfg");
                if(id!=epoch) return;
                GoEngine g=new GoEngine(new File(getApplicationInfo().nativeLibraryDir,"libkatago.so"),
                    getFilesDir(),cfg.getAbsolutePath(),main.getAbsolutePath(),human.getAbsolutePath());
                if(!attach(id,g)) return;
                g.initialize(selectedRank);
                for(int i=0;i<previous.size();i++) g.play(i,previous.get(i));
                char[] snapshot=g.board();
                boolean over=previous.size()>=2 && previous.get(previous.size()-1)==361 && previous.get(previous.size()-2)==361;
                String score=over?g.score():null;
                post(id,()->{
                    board.stones=snapshot; board.last=previous.isEmpty()?-1:previous.get(previous.size()-1);
                    board.moveCount=previous.size(); board.describe(); board.invalidate(); nextTurn(score);
                });
            } catch(Exception ex) { error(id,ex); }
        });
    }

    private void nextTurn(String result) {
        if(result!=null) {
            board.active=false; turnClock(-1); save();
            gameOver=true; stopEngine();
            if(focus) {focus=false;setFocus();}
            message("Оценка KataGo: "+result); return;
        }
        turnClock(moves.size()&1);
        if(humanTurn()) {
            board.active=true;
            boolean passed=!moves.isEmpty()&&moves.get(moves.size()-1)==361;
            message(passed?"Бот пасовал. Ваш ход":"Ваш ход");
        } else {
            message("Ход бота"); int id=epoch; GoEngine g=engine;
            final long start=SystemClock.uptimeMillis();
            worker.execute(()->{
                try {
                    int move=g.choose();
                    post(id,()->{
                        long delay=prefs.getBoolean("pace",true)?ThreadLocalRandom.current().nextLong(600,1201):0;
                        ui.postAtTime(()->{if(id==epoch) play(move,false);},start+delay);
                    });
                } catch(Exception ex) { error(id,ex); }
            });
        }
    }
    private void human(int point) { if(board.active) play(point,true); }
    private void play(int point,boolean human) {
        int id=epoch,ply=moves.size(); GoEngine g=engine;
        boolean ends=ply>0 && point==361 && moves.get(ply-1)==361;
        board.active=false;
        worker.execute(()->{
            try {
                if(human && !g.legal(point)) {
                    post(id,()->{board.selected=-1;board.describe();board.active=true;board.invalidate();message("Недопустимый ход");});
                    return;
                }
                g.play(ply,point); char[] snapshot=g.board(); String result=ends?g.score():null;
                post(id,()->{
                    board.stones=snapshot; board.last=point;
                    if(human) board.committed(); else board.invalidate();
                    moves.add(point); board.moveCount=moves.size(); board.describe(); nextTurn(result); save();
                });
            } catch(Exception ex) {error(id,ex);}
        });
    }

    private void menu() {
        String[] items={"Пас","Новая партия","Ввод и отображение",focus?"Выйти из фокуса":"Режим фокуса","Отменить выбор / вся доска","Просмотр партии / SGF"};
        new AlertDialog.Builder(this).setItems(items,(dialog,i)->{
            if(i==0) human(361);
            if(i==1) newGameDialog();
            if(i==2) settings();
            if(i==3) {focus=!focus; prefs.edit().putBoolean("focus",focus).apply();setFocus();}
            if(i==4) board.cancelSelection();
            if(i==5) review();
        }).show();
    }
    private void review() {
        final GameRecord record;
        try { record=new GameRecord(moves); }
        catch(IllegalArgumentException ex) {
            Toast.makeText(this,"Ошибка записи: "+ex.getMessage(),Toast.LENGTH_LONG).show(); return;
        }
        final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff21221e);
        TextView heading=new TextView(this);
        heading.setGravity(Gravity.CENTER); heading.setTextSize(18); heading.setMinHeight(dp(48));
        root.addView(heading);
        Goban replay=new Goban(this,p->{});
        root.addView(replay,new LinearLayout.LayoutParams(-1,0,1));
        SeekBar slider=new SeekBar(this);
        slider.setMax(record.size()); slider.setEnabled(record.size()>0);
        root.addView(slider,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout nav=new LinearLayout(this);
        Button first=reviewButton(nav,"|◀"), prev=reviewButton(nav,"◀"), next=reviewButton(nav,"▶"), last=reviewButton(nav,"▶|");
        first.setContentDescription("Начало партии"); prev.setContentDescription("Предыдущий ход");
        next.setContentDescription("Следующий ход"); last.setContentDescription("Последний ход");
        root.addView(nav);
        LinearLayout actions=new LinearLayout(this);
        Button coach=reviewButton(actions,"Разбор"), export=reviewButton(actions,"SGF"), close=reviewButton(actions,"Закрыть");
        final boolean canAnalyze=gameOver;
        coach.setEnabled(canAnalyze);
        if(!canAnalyze) coach.setContentDescription("Разбор доступен после завершения партии");
        root.addView(actions);
        java.util.function.IntConsumer show=ply->{
            replay.stones=record.position(ply); replay.last=record.last(ply);
            replay.moveCount=ply; replay.describe(); replay.invalidate();
            String move=ply==0?"Начальная позиция":
                (((ply&1)==1?"Чёрные: ":"Белые: ")+(replay.last==GameRecord.PASS?"пас":GoEngine.vertex(replay.last)));
            heading.setText(ply+" / "+record.size()+"  ·  "+move);
            first.setEnabled(ply>0); prev.setEnabled(ply>0);
            next.setEnabled(ply<record.size()); last.setEnabled(ply<record.size());
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int p,boolean user) { show.accept(p); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        first.setOnClickListener(v->slider.setProgress(0));
        prev.setOnClickListener(v->slider.setProgress(Math.max(0,slider.getProgress()-1)));
        next.setOnClickListener(v->slider.setProgress(Math.min(record.size(),slider.getProgress()+1)));
        last.setOnClickListener(v->slider.setProgress(record.size()));
        coach.setOnClickListener(v->coach(record,slider.getProgress()));
        export.setOnClickListener(v->exportSgf(record.sgf()));
        close.setOnClickListener(v->dialog.dismiss());
        slider.setProgress(record.size()); show.accept(record.size());
        dialog.setContentView(root); dialog.show();
        if(dialog.getWindow()!=null) dialog.getWindow().setLayout(-1,-1);
    }

    private void coach(GameRecord record,int selected) {
        if(!gameOver || analysis!=null)return;
        // Review shows the board AFTER a move; analysis uses its preceding position.
        final int turn=Math.max(0,selected-1);
        final int played=selected==0?-1:record.last(selected);
        final char[] position=record.position(turn);
        final PositionAnalysis task=new PositionAnalysis();
        analysis=task;
        Dialog panel=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff21221e);
        TextView header=new TextView(this);header.setGravity(Gravity.CENTER);header.setTextSize(17);
        header.setPadding(dp(12),dp(8),dp(12),dp(8));
        header.setText(selected==0?"Начальная позиция":"Перед ходом "+selected+" · "+CoachText.vertex(played));
        root.addView(header);
        Goban preview=new Goban(this,p->{});
        preview.stones=position; preview.moveCount=turn; preview.last=record.last(turn);
        preview.playedSuggestion=played; preview.describe();
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        TextView explanation=new TextView(this);explanation.setTextSize(16);
        explanation.setPadding(dp(16),dp(10),dp(16),dp(10));
        explanation.setText("KataGo анализирует позицию…");
        ScrollView scroll=new ScrollView(this);scroll.addView(explanation);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(136)));
        Button close=new Button(this);close.setText("Закрыть");root.addView(close);
        close.setOnClickListener(v->panel.dismiss());
        panel.setOnDismissListener(d->{task.close();if(analysis==task)analysis=null;});
        panel.setContentView(root);panel.show();
        if(panel.getWindow()!=null)panel.getWindow().setLayout(-1,-1);
        worker.execute(()->{
            try {
                PositionAnalysis.Result result=task.run(
                    new File(getApplicationInfo().nativeLibraryDir,"libkatago.so"),getFilesDir(),
                    asset("analysis.cfg"),asset(MAIN),record,turn,played);
                String advice=CoachText.describe(position,(turn&1)==0,result.best,result.played,played);
                ui.post(()->{
                    if(analysis!=task||!panel.isShowing())return;
                    preview.recommendation=result.best.point;preview.invalidate();
                    explanation.setText(advice+"\n\nЗелёное кольцо: рекомендация. Синее: сыгранный ход.");
                });
            } catch(Exception ex) {
                ui.post(()->{
                    if(analysis==task&&panel.isShowing()) explanation.setText("Разбор недоступен: "+ex.getMessage());
                });
            } finally {task.close();}
        });
    }

    private Button reviewButton(LinearLayout row,String label) {
        Button b=new Button(this); b.setText(label); b.setMinWidth(0);
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1)); return b;
    }

    private void exportSgf(String sgf) {
        pendingSgf=sgf;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/x-go-sgf");
        intent.putExtra(Intent.EXTRA_TITLE,"GoMasters.sgf");
        try { startActivityForResult(intent,EXPORT_SGF); }
        catch(ActivityNotFoundException ex) {
            pendingSgf=null;
            Toast.makeText(this,"Нет приложения для сохранения файла",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pendingSgf",pendingSgf);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_SGF) return;
        String sgf=pendingSgf; pendingSgf=null;
        if(result!=RESULT_OK || data==null || data.getData()==null || sgf==null) return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")) {
            if(out==null) throw new IOException("Нет потока записи");
            out.write(sgf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this,"SGF сохранён",Toast.LENGTH_SHORT).show();
        } catch(IOException|SecurityException ex) {
            Toast.makeText(this,"Не удалось сохранить SGF: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void settings() {
        boolean[] values={board.precision,board.autoOut,prefs.getBoolean("pace",true)};
        new AlertDialog.Builder(this).setTitle("Ввод и отображение").setMultiChoiceItems(
            new String[]{"Приближение и подтверждение хода","Отдалять после хода","Пауза перед ответом бота"},values,
            (d,i,on)->{
                if(i==0){board.precision=on;board.resetCamera();}
                if(i==1)board.autoOut=on;
                prefs.edit().putBoolean(new String[]{"precision","autoOut","pace"}[i],on).apply();
            }).setPositiveButton("Готово",null).show();
    }
    private void newGameDialog() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),0,dp(20),0);
        List<String> ranks=new ArrayList<>();for(int k=20;k>=1;k--)ranks.add(k+"k");for(int d=1;d<=9;d++)ranks.add(d+"d");
        Spinner levels=new Spinner(this); levels.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ranks));
        levels.setSelection(ranks.indexOf(rank));box.addView(levels);
        CheckBox white=new CheckBox(this);white.setText("Играть белыми");white.setChecked(!botWhite);box.addView(white);
        new AlertDialog.Builder(this).setTitle("Новая партия · ранг бота").setView(box)
            .setNegativeButton("Отмена",null).setPositiveButton("Начать",(d,w)->{
                turnClock(-1); rank=(String)levels.getSelectedItem();botWhite=!white.isChecked();
                moves.clear();Arrays.fill(elapsed,0);save();start();
            }).show();
    }
    @Override public void onBackPressed() {
        if(board.cancelSelection()) return;
        if(focus){ focus=false;setFocus();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(analysis!=null){analysis.close();analysis=null;}
        stopEngine(); worker.shutdownNow(); ui.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
