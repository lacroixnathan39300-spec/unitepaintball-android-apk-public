package com.unitepaintball.app;
public final class NetworkRetryPolicy {
 private NetworkRetryPolicy(){}
 public static boolean shouldRetry(String method,int attempt,int status,boolean networkError){return "GET".equals(method)&&attempt==0&&(networkError||status==502||status==503||status==504);}
}
