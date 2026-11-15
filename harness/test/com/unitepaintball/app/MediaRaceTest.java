package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.nio.file.*;

public class MediaRaceTest {
    static int failures = 0;
    static void check(boolean ok, String msg){ System.out.println((ok?"PASS  ":"FAIL  ")+msg); if(!ok) failures++; }
    static int count(String prefix){ int n=0; for(String s: ApiClient.sent) if(s.startsWith(prefix)) n++; return n; }

    public static void main(String[] a) throws Exception {
        Context ctx = new Context(Files.createTempDirectory("media-race").toFile());
        ApiClient.sent.clear(); ApiClient.offline=false; ApiClient.delayMs=0;
        byte[] data = new byte[]{1,2,3,4};
        MediaSyncQueue.enqueue(ctx, data, "m1.jpg", "image/jpeg", "/api/gallery", new JSONObject().put("img","__UP_MEDIA_URL__").put("n",1), "offline");
        MediaSyncQueue.enqueue(ctx, data, "m2.jpg", "image/jpeg", "/api/gallery", new JSONObject().put("img","__UP_MEDIA_URL__").put("n",2), "offline");
        check(MediaSyncQueue.pendingCount(ctx)==2, "two media queued");

        ApiClient.delayMs = 300;
        final int[] sent = {0};
        Thread f = new Thread(() -> sent[0] = MediaSyncQueue.flush(ctx, "tok", 10)); f.start();
        Thread.sleep(100);
        long t0=System.nanoTime(); int p=MediaSyncQueue.pendingCount(ctx); long ms=(System.nanoTime()-t0)/1_000_000;
        check(ms<50, "pendingCount does not block UI during media upload (took "+ms+" ms)");
        MediaSyncQueue.enqueue(ctx, data, "m3.jpg", "image/jpeg", "/api/gallery", new JSONObject().put("img","__UP_MEDIA_URL__").put("n",3), "offline"); // during flight
        f.join(); ApiClient.delayMs=0;

        check(sent[0]==2, "flush delivered the 2 original media (sent="+sent[0]+")");
        check(MediaSyncQueue.pendingCount(ctx)==1, "media queued during flush is NOT lost (pending="+MediaSyncQueue.pendingCount(ctx)+")");

        // Upload succeeded but the follow-up POST fails: next attempt must reuse the uploaded URL, not re-upload.
        ApiClient.sent.clear();
        MediaSyncQueue.enqueue(ctx, data, "m4.jpg", "image/jpeg", "/api/gallery", new JSONObject().put("img","__UP_MEDIA_URL__").put("net","down"), "offline");
        MediaSyncQueue.flush(ctx, "tok", 10);
        check(count("MULTIPART")==2, "uploads done once per media (MULTIPART calls="+count("MULTIPART")+", expected 2 incl. m3 and m4)");
        System.out.println(failures==0?"MEDIA ALL PASS":failures+" MEDIA FAILURE(S)");
        System.exit(failures==0?0:1);
    }
}
