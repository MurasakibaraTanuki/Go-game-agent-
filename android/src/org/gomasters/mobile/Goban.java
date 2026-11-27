package org.gomasters.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Arrays;
import java.util.function.IntConsumer;

final class Goban extends View {
    final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    final IntConsumer submit;
    char[] stones = new char[361];
    boolean active, precision = true, autoOut = true;
    int last = -1, selected = -1, moveCount;
    void describe() {
        String desc="Доска го, 19 на 19. Ходов: "+moveCount+".";
        if(last>=0) desc+=" Последний ход: "+GoEngine.vertex(last)+".";
        if(selected>=0) desc+=" Выбран пункт: "+GoEngine.vertex(selected)+".";
        setContentDescription(desc);
    }
    float zoom = 1, panX, panY, downX, downY, prevX, prevY;
    boolean dragging;
    ValueAnimator animation;

    Goban(Context context, IntConsumer submit) {
        super(context);
        this.submit = submit;
        Arrays.fill(stones, '.');
        setContentDescription("Доска го, 19 на 19");
        setClickable(true);
    }

    float side() { return Math.min(getWidth(), getHeight()); }
    float left() { return (getWidth()-side())/2; }
    float top() { return (getHeight()-side())/2; }
    float step() { return side()/19.2f; }
    float clamp(float value, float scale) { return Math.max(side()*(1-scale), Math.min(0, value)); }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) { resetCamera(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float s = side(), d = step(), edge = .6f*d;
        c.drawColor(0xff21221e);
        c.save();
        c.clipRect(left(), top(), left()+s, top()+s);
        c.translate(left()+panX, top()+panY);
        c.scale(zoom, zoom);
        pen.setColor(0xffdcb477);
        c.drawRect(0,0,s,s,pen);
        pen.setColor(0xff563d22);
        pen.setStrokeWidth(Math.max(1,d*.026f));
        for (int i=0;i<19;i++) {
            float p=edge+i*d;
            c.drawLine(edge,p,s-edge,p,pen); c.drawLine(p,edge,p,s-edge,pen);
        }
        for (int i=3;i<=15;i+=6) for(int j=3;j<=15;j+=6)
            c.drawCircle(edge+i*d,edge+j*d,.085f*d,pen);
        for (int p=0;p<361;p++) {
            float x=edge+p%19*d, y=edge+p/19*d;
            if (stones[p]=='X' || stones[p]=='O') {
                boolean black=stones[p]=='X';
                pen.setColor(0x59402b1a);
                c.drawCircle(x+.04f*d,y+.055f*d,.46f*d,pen);
                pen.setColor(black?0xff232420:0xfff3eee2);
                c.drawCircle(x,y,.46f*d,pen);
                if (p==last) ring(c,x,y,.18f*d,black?0xffeee9dc:0xff4c483e);
            } else if (p==selected) {
                pen.setColor(0x994b6255);
                c.drawCircle(x,y,.43f*d,pen); ring(c,x,y,.19f*d,0xfffcf3da);
            }
        }
        c.restore();
    }

    private void ring(Canvas c,float x,float y,float r,int color) {
        pen.setColor(color); pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(Math.max(1,r*.2f)); c.drawCircle(x,y,r,pen);pen.setStyle(Paint.Style.FILL);
    }

    int point(float x,float y) {
        if (x<left() || x>=left()+side() || y<top() || y>=top()+side()) return -1;
        int col=Math.round(((x-left()-panX)/zoom)/step()-.6f);
        int row=Math.round(((y-top()-panY)/zoom)/step()-.6f);
        return col<0||col>18||row<0||row>18 ? -1 : row*19+col;
    }

    void animateTo(float scale,float tx,float ty) {
        if (animation!=null) animation.cancel();
        float from=zoom, ox=panX, oy=panY;
        animation=ValueAnimator.ofFloat(0,1);
        animation.setDuration(200);
        animation.addUpdateListener(a->{
            float f=(float)a.getAnimatedValue();
            zoom=from+(scale-from)*f;
            panX=clamp(ox+(tx-ox)*f,zoom); panY=clamp(oy+(ty-oy)*f,zoom); invalidate();
        });
        animation.start();
    }

    void resetCamera() {
        if(animation!=null) animation.cancel();
        zoom=1; panX=panY=0; selected=-1; describe(); invalidate();
    }

    void committed() {
        selected=-1;
        if(autoOut && zoom>1) animateTo(1,0,0);
        invalidate();
    }

    boolean cancelSelection() {
        if(selected<0 && zoom==1) return false;
        selected=-1; describe(); animateTo(1,0,0); invalidate(); return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=prevX=x; downY=prevY=y; dragging=false; return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.hypot(x-downX,y-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()) dragging=true;
                if(dragging && zoom>1) {
                    if(animation!=null) animation.cancel();
                    panX=clamp(panX+x-prevX,zoom); panY=clamp(panY+y-prevY,zoom);invalidate();
                }
                prevX=x; prevY=y; return true;
            case MotionEvent.ACTION_UP:
                if(dragging || !active || (animation!=null && animation.isRunning())) return true;
                performClick();
                int p=point(x,y);
                if(p<0 || stones[p]!='.') return true;
                if(!precision || p==selected) { submit.accept(p); return true; }
                selected=p; describe();
                if(zoom==1) {
                    float factor=2.6f;
                    animateTo(factor,clamp((x-left())*(1-factor),factor),clamp((y-top())*(1-factor),factor));
                }
                invalidate(); return true;
            default: return true;
        }
    }
}
