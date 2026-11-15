package com.unitepaintball.app;
/** A denied optional module is checked; required account data must actually arrive. */
public final class StartupSyncPolicy {
 private StartupSyncPolicy(){}
 public static boolean hasCompletedSync(long timestamp){return timestamp>0;}
 public static boolean required(String endpoint){return endpoint.equals("/api/platform")||endpoint.equals("/api/avatar/soldier")||endpoint.equals("/api/avatar")||endpoint.equals("/api/gamification?scope=profile");}
 public static boolean failure(String endpoint,int status){return !(status==403||status==404)||required(endpoint);}
 public static boolean ready(boolean platform,int failures,int pending){return platform&&failures==0&&pending==0;}
}
