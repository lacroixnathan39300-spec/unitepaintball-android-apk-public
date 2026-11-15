package com.unitepaintball.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Catalogue natif embarqué des 13 costumes Unité Paintball. */
public final class CostumeCatalogActivity extends Activity {
 private LinearLayout root;
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,28,28,42);root.setBackgroundColor(NeonPalette.BG);sv.addView(root);setContentView(sv);title("COLLECTION COSTUMES");label("13 concepts de tenue · six pièces mélangeables",14,Color.LTGRAY);label("Les droits de déblocage et l’équipement restent contrôlés par le serveur. Cette liste locale ne confirme pas la possession d’un costume.",13,Color.LTGRAY);render();}
 private void title(String s){TextView t=label(s,26,Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);}
 private TextView label(String s,int z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setPadding(10,12,10,12);root.addView(t);return t;}
 private int rarityColor(String r){if(r.contains("Supr"))return Color.rgb(255,70,70);if(r.contains("genda"))return Color.rgb(255,190,40);if(r.contains("pique"))return Color.rgb(190,90,255);if(r.contains("Rare"))return Color.rgb(65,170,255);if(r.contains("Halloween"))return Color.rgb(255,120,25);if(r.contains("cial"))return Color.rgb(80,235,210);return Color.rgb(140,190,120);}
 private void render(){try{InputStream in=getAssets().open("avatar_costumes/costumes-mobile.json");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];for(int n;(n=in.read(buf))>0;)out.write(buf,0,n);JSONObject d=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));JSONArray a=d.getJSONArray("costumes");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);String n=o.optString("name"),r=o.optString("rarity");LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(22,20,22,20);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(NeonPalette.SURFACE);bg.setCornerRadius(28);bg.setStroke(3,rarityColor(r));card.setBackground(bg);TextView name=new TextView(this);name.setText(n);name.setTextSize(19);name.setTextColor(Color.WHITE);name.setTypeface(Typeface.DEFAULT_BOLD);card.addView(name);TextView meta=new TextView(this);meta.setText(r+"  ·  6 pièces · mélangeable");meta.setTextSize(14);meta.setTextColor(rarityColor(r));card.addView(meta);JSONObject u=o.optJSONObject("unlock");TextView unlock=new TextView(this);unlock.setText("Condition indicative : "+(u==null?"catalogue":u.optString("type","catalogue"))+" · possession à vérifier sur le compte");unlock.setTextSize(13);unlock.setTextColor(Color.LTGRAY);card.addView(unlock);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,8,0,8);root.addView(card,lp);}label("Un GLB complet contient les six pièces de base. Les 13 concepts réutilisent ce modèle avec des couleurs/effets; la liaison du rendu aux pièces réellement équipées reste à faire.",13,Color.LTGRAY);}catch(Exception e){label("Catalogue indisponible : "+e.getMessage(),14,Color.RED);}}
}
