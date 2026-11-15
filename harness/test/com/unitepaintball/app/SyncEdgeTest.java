package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.nio.file.*;

public class SyncEdgeTest {
    static int failures = 0;
    static void check(boolean ok, String msg){ System.out.println((ok?"PASS  ":"FAIL  ")+msg); if(!ok) failures++; }

    /** Enqueue while the stub is offline; flush later sees the network back. */
    static void queueOffline(Context ctx, String path, JSONObject body) throws Exception {
        ApiClient.offline = true; SyncApi.post(ctx, path, "tok", body); ApiClient.offline = false;
    }

    public static void main(String[] a) throws Exception {
        // A) Same dedupe key edited while its send is in flight: newest body must survive.
        {
            Context ctx = new Context(Files.createTempDirectory("edge-a").toFile());
            ApiClient.delayMs = 0;
            queueOffline(ctx, "/api/profile", new JSONObject().put("action","save_profile").put("v",1));
            check(SyncApi.pendingCount(ctx)==1, "A: v1 queued");
            ApiClient.delayMs = 300;
            Thread f = new Thread(() -> SyncApi.flush(ctx, "tok", 10)); f.start();
            Thread.sleep(80);
            queueOffline(ctx, "/api/profile", new JSONObject().put("action","save_profile").put("v",2)); // dedupe during flight
            f.join(); ApiClient.delayMs = 0;
            org.json.JSONArray rem = SyncApi.snapshot(ctx);
            check(rem.length()==1, "A: one entry remains (len="+rem.length()+")");
            if(rem.length()==1) check(rem.optJSONObject(0).optJSONObject("body").optInt("v",-1)==2, "A: newest body (v2) survives in-flight send");
        }
        // B) Logout (clear) during a send must not resurrect the queue.
        {
            Context ctx = new Context(Files.createTempDirectory("edge-b").toFile());
            queueOffline(ctx, "/api/x", new JSONObject().put("action","create_x"));
            queueOffline(ctx, "/api/y", new JSONObject().put("action","create_y"));
            check(SyncApi.pendingCount(ctx)==2, "B: two queued");
            ApiClient.delayMs = 300;
            Thread f = new Thread(() -> SyncApi.flush(ctx, "tok", 10)); f.start();
            Thread.sleep(80);
            SyncApi.clear(ctx);
            f.join(); ApiClient.delayMs = 0;
            check(SyncApi.pendingCount(ctx)==0, "B: queue stays empty after clear() during flush (count="+SyncApi.pendingCount(ctx)+")");
        }
        System.out.println(failures==0?"EDGE ALL PASS":failures+" EDGE FAILURE(S)");
        System.exit(failures==0?0:1);
    }
}
