package com.unitepaintball.app;
/** Structured notification routing: message text is never a routing key. */
public final class NotificationRoutes {
 private NotificationRoutes(){}
 public static String route(String category,String target){
  String t=target==null?"":target.trim();if(t.startsWith("/")&&!t.startsWith("//")&&!t.contains("\\")&&!t.contains("\r")&&!t.contains("\n"))return t;
  String c=category==null?"":category.trim().toLowerCase(java.util.Locale.ROOT);
  switch(c){
   case "friend_request":case "friendship":return "/amis";
   case "message":case "chat":case "communication":return "/communication";
   case "booking":case "reservation":return "/reservations";
   case "participation":return "/journees-ouvertes";
   case "roulette":return "/progression?tab=roulette";
   case "points":case "gamification":return "/progression";
   case "reward":case "physical_reward":case "gift":return "/progression?tab=recompenses";
   case "badge":return "/progression?tab=badges";
   case "challenge":case "mission":return "/progression?tab=missions";
   case "avatar":case "cosmetic":return "/mon-avatar";
   case "collection":return "/admin/gamification/collections";
   default:return "";
  }
 }
}
