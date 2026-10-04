package ru.eyeone.relaybridge;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Native scalable outline icons; no font or bitmap dependency. */
final class UiIcon extends Drawable {
 private final int kind,color; private final float density;
 UiIcon(int kind,int color,float density){this.kind=kind;this.color=color;this.density=density;}
 public int getIntrinsicWidth(){return Math.round(24*density);} public int getIntrinsicHeight(){return Math.round(24*density);}
 public void draw(Canvas c){c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);Paint p=new Paint(3);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();
 switch(kind){
 case 0:path.moveTo(3,10);path.lineTo(12,2);path.lineTo(21,10);path.lineTo(21,21);path.lineTo(15,21);path.lineTo(15,14);path.lineTo(9,14);path.lineTo(9,21);path.lineTo(3,21);path.close();break;
 case 1:path.moveTo(2,10);path.lineTo(22,2);path.lineTo(14,22);path.lineTo(10,14);path.close();path.moveTo(10,14);path.lineTo(22,2);break;
 case 2:path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,15);path.quadTo(18,20,12,23);path.quadTo(6,20,4,15);path.lineTo(3,6);path.close();break;
 case 3:for(int y=5;y<=19;y+=7){c.drawCircle(3,y,0.5f,p);c.drawLine(8,y,22,y,p);}break;
 case 4:path.moveTo(3,4);path.lineTo(21,4);path.lineTo(21,17);path.lineTo(8,17);path.lineTo(3,22);path.close();break;
 case 5:path.moveTo(5,3);path.lineTo(9,3);path.lineTo(11,8);path.lineTo(8,10);path.quadTo(10,15,15,17);path.lineTo(17,14);path.lineTo(22,16);path.lineTo(22,20);path.quadTo(21,23,17,22);path.quadTo(3,18,2,6);path.quadTo(2,3,5,3);break;
 case 6:path.moveTo(4,18);path.lineTo(6,15);path.lineTo(6,9);path.quadTo(6,3,12,3);path.quadTo(18,3,18,9);path.lineTo(18,15);path.lineTo(20,18);path.close();path.moveTo(10,21);path.lineTo(14,21);break;
 case 8:path.moveTo(5,2);path.lineTo(14,2);path.lineTo(19,7);path.lineTo(19,22);path.lineTo(5,22);path.close();c.drawRoundRect(8,10,16,18,1,1,p);path.moveTo(12,10);path.lineTo(12,18);path.moveTo(8,14);path.lineTo(16,14);break;
 case 7:c.drawRoundRect(2,5,22,19,1,1,p);path.moveTo(2,5);path.lineTo(12,13);path.lineTo(22,5);break;
 }
 c.drawPath(path,p);c.restore();}
 public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
