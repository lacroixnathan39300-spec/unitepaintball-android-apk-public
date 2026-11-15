package com.unitepaintball.app;
import org.json.JSONObject;
/** HARNESS-ONLY network stub with controllable latency/failure. Never shipped. */
public final class ApiClient {
  public static volatile long delayMs = 0;
  public static volatile boolean offline = false;
  public static volatile java.util.function.Consumer<String> onCall = s -> {};
  public static final java.util.List<String> sent = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  public static final class ApiException extends Exception { public final int status; public ApiException(int status,String m){super(m);this.status=status;} }
  private static JSONObject call(String what, String path, JSONObject body) throws Exception {
    onCall.accept(what+" "+path);
    if (offline || (body != null && "down".equals(body.optString("net")))) throw new java.io.IOException("offline");
    if (delayMs > 0) Thread.sleep(delayMs);
    sent.add(what+" "+path+" "+(body==null?"":body.toString()));
    return new JSONObject();
  }
  public static JSONObject get(String path,String token) throws Exception { return call("GET",path,null); }
  public static JSONObject post(String path,String token,JSONObject body) throws Exception { return call("POST",path,body); }
  public static JSONObject patch(String path,String token,JSONObject body) throws Exception { return call("PATCH",path,body); }
  public static JSONObject postMultipart(String path,String token,byte[] data,String fileName,String mime) throws Exception {
    onCall.accept("MULTIPART "+path); if (offline) throw new java.io.IOException("offline"); if (delayMs>0) Thread.sleep(delayMs);
    sent.add("MULTIPART "+path); return new JSONObject().put("url","https://cdn.test/"+fileName);
  }
}
