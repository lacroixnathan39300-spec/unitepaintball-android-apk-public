package com.unitepaintball.app;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private ApiClient() {}

    public static JSONObject get(String path, String token) throws Exception {
        return request("GET", path, token, null);
    }
    public static JSONObject post(String path, String token, JSONObject body) throws Exception {
        return request("POST", path, token, body);
    }
    public static JSONObject patch(String path, String token, JSONObject body) throws Exception {
        return request("PATCH", path, token, body);
    }

    public static JSONObject postMultipart(String path, String token, byte[] data, String fileName, String mimeType) throws Exception {
        String boundary = "----UnitePaintball" + System.currentTimeMillis();
        URL url = new URL(AppConfig.BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "UnitePaintball-Android/" + AppConfig.APP_VERSION);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Cookie", token);
        try (OutputStream raw = c.getOutputStream(); DataOutputStream out = new DataOutputStream(raw)) {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName(fileName) + "\"\r\n");
            out.writeBytes("Content-Type: " + (mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType) + "\r\n\r\n");
            out.write(data);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(in);
        JSONObject json = parseJson(text, code);
        if (code < 200 || code >= 300) throw new ApiException(code, json.optString("error", "Erreur HTTP " + code));
        return json;
    }


    private static JSONObject parseJson(String text, int code) throws Exception {
        if (text == null || text.trim().isEmpty()) return new JSONObject();
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            throw new ApiException(code, code == 401 ? "Session expirée." : "Le serveur a renvoyé une page Web au lieu de données JSON.");
        }
        return new JSONObject(trimmed);
    }

    private static String safeFileName(String name) {
        String n = name == null || name.isEmpty() ? "photo.jpg" : name;
        return n.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private static JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        for(int attempt=0;;attempt++) {
            try{return requestOnce(method,path,token,body);}
            catch(Exception e){int status=e instanceof ApiException?((ApiException)e).status:0;
                if(!NetworkRetryPolicy.shouldRetry(method,attempt,status,e instanceof IOException))throw e;
            }
        }
    }
    private static JSONObject requestOnce(String method, String path, String token, JSONObject body) throws Exception {
        URL url = new URL(AppConfig.BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
        c.setRequestMethod(method);
        boolean heavyAdmin = path.startsWith("/api/admin-backups") || path.startsWith("/api/account-merge");
        c.setConnectTimeout(heavyAdmin ? 30000 : 15000);
        c.setReadTimeout(heavyAdmin ? 180000 : 20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "UnitePaintball-Android/" + AppConfig.APP_VERSION);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Cookie", token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(in);
        JSONObject json = parseJson(text, code);
        if (code < 200 || code >= 300) {
            String message = json.optString("error", "Erreur HTTP " + code);
            throw new ApiException(code, message);
        }
        return json;
        } finally { c.disconnect(); }
    }
    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
    public static final class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }
}
