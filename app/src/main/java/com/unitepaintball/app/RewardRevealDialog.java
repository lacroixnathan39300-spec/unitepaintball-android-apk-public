package com.unitepaintball.app;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

/** Animation décorative : le gain provient exclusivement de la réponse serveur. */
public final class RewardRevealDialog {
    private RewardRevealDialog(){}
    public static void show(Activity activity,boolean roulette,String reward,Runnable after){
        Dialog dialog=new Dialog(activity);LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(28,28,28,28);box.setBackgroundColor(Color.rgb(27,12,36));
        TextView title=new TextView(activity);title.setText("UNITÉ PAINTBALL · "+(roulette?"ROULETTE":"COFFRE"));title.setTextColor(Color.rgb(255,135,0));title.setTextSize(20);title.setGravity(17);box.addView(title);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float[] progress={0};
        View art=new View(activity){@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.34f;canvas.save();if(roulette){canvas.rotate(1440*(1-progress[0]),cx,cy);for(int i=0;i<8;i++){paint.setColor(i%2==0?Color.rgb(255,135,0):Color.rgb(65,29,83));canvas.drawArc(cx-r,cy-r,cx+r,cy+r,i*45,43,true,paint);}paint.setColor(Color.rgb(210,255,50));canvas.drawCircle(cx,cy,r*.2f,paint);}else{paint.setColor(Color.rgb(255,135,0));canvas.drawRoundRect(cx-r,cy-r*.2f,cx+r,cy+r*.75f,12,12,paint);canvas.save();canvas.translate(0,-r*progress[0]*.7f);canvas.rotate(-18*progress[0],cx-r,cy-r*.2f);paint.setColor(Color.rgb(235,105,0));canvas.drawRoundRect(cx-r,cy-r*.65f,cx+r,cy-r*.2f,12,12,paint);canvas.restore();paint.setColor(Color.rgb(210,255,50));canvas.drawCircle(cx,cy+r*.2f,r*.13f,paint);}canvas.restore();paint.setColor(Color.rgb(210,255,50));paint.setTextSize(28);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("★ UNITÉ ★",cx,getHeight()-12,paint);}};
        box.addView(art,new LinearLayout.LayoutParams(-1,(int)(220*activity.getResources().getDisplayMetrics().density)));
        TextView gain=new TextView(activity);gain.setText("GAIN CONFIRMÉ\n"+reward+"\n\nEnregistré sur ton compte.");gain.setTextColor(Color.WHITE);gain.setTextSize(22);gain.setGravity(17);gain.setVisibility(View.INVISIBLE);box.addView(gain);
        Button close=new Button(activity);close.setText("RÉCUPÉRÉ · CONTINUER");close.setVisibility(View.INVISIBLE);close.setOnClickListener(v->dialog.dismiss());box.addView(close);dialog.setContentView(box);dialog.setOnDismissListener(d->after.run());dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels*.92f),-2);
        ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(ValueAnimator.areAnimatorsEnabled()?1900:0);animator.addUpdateListener(v->{progress[0]=(float)v.getAnimatedValue();art.invalidate();});animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){gain.setVisibility(View.VISIBLE);close.setVisibility(View.VISIBLE);gain.announceForAccessibility("Gain confirmé : "+reward);}});dialog.setOnDismissListener(d->{animator.cancel();after.run();});animator.start();
    }
}
