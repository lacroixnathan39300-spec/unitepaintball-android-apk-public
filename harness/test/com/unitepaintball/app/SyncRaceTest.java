package com.unitepaintball.app;

import android.content.Context;
import org.json.JSONObject;
import java.nio.file.*;

/** Harness: reproduces lost offline write during flush + UI-thread stall. */
public class SyncRaceTest {
    static void check(boolean ok, String msg) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + msg);
        if (!ok) failures++;
    }
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("sync-race");
        Context ctx = new Context(dir.toFile());

        // 1) Three offline mutations queued while "offline".
        ApiClient.offline = true;
        SyncApi.post(ctx, "/api/a", "tok", new JSONObject().put("action", "create_a").put("n", 1));
        SyncApi.post(ctx, "/api/c", "tok", new JSONObject().put("action", "create_c").put("n", 2));
        SyncApi.post(ctx, "/api/d", "tok", new JSONObject().put("action", "create_d").put("n", 3));
        check(SyncApi.pendingCount(ctx) == 3, "3 offline mutations queued");

        // 2) Network back: flush runs slowly in background (200 ms per request).
        ApiClient.offline = false;
        ApiClient.delayMs = 200;
        final int[] sentHolder = {0};
        Thread flusher = new Thread(() -> sentHolder[0] = SyncApi.flush(ctx, "tok", 10));
        flusher.start();
        Thread.sleep(100);

        // 3) While flush is in flight, the user performs a NEW offline action (e.g. booking/message).
        long t0 = System.nanoTime();
        SyncApi.post(ctx, "/api/b", "tok", new JSONObject().put("action", "create_b").put("n", 99).put("net", "down"));
        long enqueueMs = (System.nanoTime() - t0) / 1_000_000;

        // 4) UI thread asks for pending count (badge) while flush is still running.
        t0 = System.nanoTime();
        int pendingNow = SyncApi.pendingCount(ctx);
        long countMs = (System.nanoTime() - t0) / 1_000_000;
        check(countMs < 50, "pendingCount does not block UI while flush runs (took " + countMs + " ms)");
        check(enqueueMs < 50, "new offline enqueue does not block (took " + enqueueMs + " ms)");

        flusher.join();
        ApiClient.delayMs = 0;
        ApiClient.offline = false;

        // 5) After flush: the new offline action MUST still be queued (no silent loss).
        JSONObject queuedB = null;
        org.json.JSONArray remaining = SyncApi.snapshot(ctx);
        for (int i = 0; i < remaining.length(); i++) {
            JSONObject it = remaining.optJSONObject(i);
            if (it != null && "/api/b".equals(it.optString("path"))) queuedB = it;
        }
        check(queuedB != null, "offline action created during flush is NOT lost (remaining=" + remaining.length() + ")");
        check(remaining.length() == 1, "exactly the unsent item remains queued (remaining=" + remaining.length() + ")");
        check(sentHolder[0] == 3, "flush sent the 3 original items (sent=" + sentHolder[0] + ")");

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
