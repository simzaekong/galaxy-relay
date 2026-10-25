package kr.infosphere.galaxyrelay;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Small native vector glyphs, independent of emoji fonts and external assets. */
public final class RelayIcon extends View {
 private final String name;private final Paint paint=new Paint(3);private final Path path=new Path();private int tint;
 public RelayIcon(Context c){this(c,"info",0xFF0086F5);}
 public RelayIcon(Context c,String name,int tint){super(c);this.name=name;this.tint=tint;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 public void tint(int color){tint=color;invalidate();}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();float scale=Math.min(getWidth(),getHeight())/24f;c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);paint.setColor(tint);paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path p=path;p.reset();switch(name){
 case "home":p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);c.drawPath(p,paint);break;
 case "bell":p.moveTo(5,17);p.cubicTo(7,14,6,11,7,8);p.cubicTo(8,3,16,3,17,8);p.cubicTo(18,11,17,14,19,17);p.close();c.drawPath(p,paint);c.drawArc(9,18,15,22,0,180,false,paint);break;
 case "settings":c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,paint);c.restore();}break;
 case "location":p.moveTo(12,21);p.cubicTo(9,17,5,13,5,9);p.cubicTo(5,0,19,0,19,9);p.cubicTo(19,13,15,17,12,21);c.drawPath(p,paint);c.drawCircle(12,9,2.4f,paint);break;
 case "phone":c.drawRoundRect(6,2,18,22,2.5f,2.5f,paint);c.drawLine(10,5,14,5,paint);c.drawLine(10,19,14,19,paint);break;
 case "link":c.save();c.rotate(-40,12,12);c.drawRoundRect(5,3,13,15,4,4,paint);c.drawRoundRect(11,9,19,21,4,4,paint);c.restore();break;
 default:c.drawCircle(12,12,8,paint);c.drawLine(12,10,12,16,paint);c.drawPoint(12,7,paint);
 }c.restore();}
}
