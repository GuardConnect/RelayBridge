package ru.eyeone.relaybridge;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Reference-style raised controls, rendered as vectors at any screen density. */
final class UiToggleArtwork extends Drawable {
    static final int TRACK=0,THUMB=1,BADGE=2;
    private final int kind;private final float density;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private int alpha=255;
    UiToggleArtwork(int kind,float density){this.kind=kind;this.density=density;}
    @Override public int getIntrinsicWidth(){return Math.round((kind==TRACK?68:kind==THUMB?32:36)*density);}
    @Override public int getIntrinsicHeight(){return Math.round((kind==THUMB?32:36)*density);}
    @Override public boolean getPadding(Rect padding){if(kind==TRACK){padding.set(Math.round(2*density),0,Math.round(2*density),0);return true;}padding.set(0,0,0,0);return false;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){invalidateSelf();return true;}
    private boolean state(int attribute){for(int state:getState())if(state==attribute)return true;return false;}
    private void fill(){paint.reset();paint.setAntiAlias(true);paint.setAlpha(Math.round(alpha*(kind==BADGE||state(android.R.attr.state_enabled)?1f:0.45f)));}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float x=b.exactCenterX(),y=b.exactCenterY();
        if(kind==BADGE){
            fill();paint.setShader(new LinearGradient(x-8*density,0,x+8*density,0,new int[]{0xFF159CE8,0xFF45C9FF},null,Shader.TileMode.CLAMP));paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(3.5f*density);paint.setShadowLayer(2*density,0,density,0x5500A7F5);check(canvas,x,y,8*density);return;
        }
        if(kind==TRACK){
            boolean on=state(android.R.attr.state_checked);RectF pill=new RectF(b.left+density,b.top+density,b.right-density,b.bottom-density);float radius=pill.height()/2;
            fill();paint.setShader(new LinearGradient(0,pill.top,0,pill.bottom,on?new int[]{0xFF34C4FF,UiColors.ACCENT,0xFF078BCE}:new int[]{0xFF485360,0xFF333D49,0xFF252D37},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(pill,radius,radius,paint);
            fill();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(on?0xFF69D2FF:0xFF596574);canvas.drawRoundRect(pill,radius,radius,paint);
            float markX=on?b.left+18*density:b.right-18*density;fill();paint.setColor(on?Color.WHITE:0xFFB9C3CF);paint.setStrokeWidth(2.7f*density);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
            if(on)check(canvas,markX,y,6*density);else{float r=4.6f*density;canvas.drawLine(markX-r,y-r,markX+r,y+r,paint);canvas.drawLine(markX-r,y+r,markX+r,y-r,paint);}
        }else{
            float r=Math.min(b.width(),b.height())/2f-1.5f*density;
            fill();paint.setColor(0xFFCFD8E3);paint.setShadowLayer(2*density,0,1.5f*density,0x99000000);canvas.drawCircle(x,y,r,paint);
            fill();paint.setShader(new LinearGradient(0,y-r,0,y+r,new int[]{0xFFFFFFFF,0xFFE9EDF2,0xFFD6DEE7},null,Shader.TileMode.CLAMP));canvas.drawCircle(x,y,r,paint);
            fill();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(0xFFF9FDFF);canvas.drawCircle(x,y,r-density/2,paint);
        }
    }
    private void check(Canvas c,float x,float y,float r){Path p=new Path();p.moveTo(x-r,y);p.lineTo(x-r/3,y+r*0.65f);p.lineTo(x+r,y-r*0.75f);c.drawPath(p,paint);}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
