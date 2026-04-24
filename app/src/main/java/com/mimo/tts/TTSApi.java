package com.mimo.tts;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MiMo V2.5 TTS API client.
 * Uses OpenAI-compatible /v1/chat/completions endpoint with audio output.
 */
public class TTSApi {
    private static final String TAG = "TTSApi";
    private static final MediaType JSON_MT = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private Call currentCall;

    public interface CallbackListener {
        void onSuccess(byte[] audioData);
        void onFailure(String error);
    }

    public TTSApi() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate speech using MiMo V2.5 TTS API.
     *
     * @param text       Text to synthesize
     * @param model      Model name (e.g., "mimo-v2.5-tts")
     * @param apiBase    API base URL (e.g., "https://api.xiaomimimo.com/v1")
     * @param apiKey     API key
     * @param voice      Voice name (preset) or base64 data URI (voiceclone)
     * @param ttsMode    TTS mode: preset / voiceclone / voicedesign
     * @param context    Style/description text for voicedesign mode, or null
     * @param listener   Callback for success/failure
     */
    public void generate(String text, String model, String apiBase, String apiKey,
                         String voice, String ttsMode, String context,
                         CallbackListener listener) {
        try {
            JSONArray messages = new JSONArray();

            // For voicedesign or with context: add user message with context
            if (context != null && !context.isEmpty()) {
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", context);
                messages.put(userMsg);
            }

            // Assistant message with the text to synthesize
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", text);
            messages.put(assistantMsg);

            // Build audio config
            JSONObject audioConfig = new JSONObject();
            audioConfig.put("format", "wav");
            // voicedesign mode does not support voice field
            if (!Constants.TTS_MODE_VOICEDESIGN.equals(ttsMode)) {
                audioConfig.put("voice", voice);
            }

            // Build payload
            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("audio", audioConfig);

            String url = apiBase.replaceAll("/+$", "") + "/chat/completions";

            RequestBody body = RequestBody.create(payload.toString(), JSON_MT);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);

            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            currentCall = client.newCall(requestBuilder.build());
            currentCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Request failed", e);
                    if (!call.isCanceled()) {
                        listener.onFailure(e.getMessage() != null ? e.getMessage() : "请求失败");
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String respBody = response.body() != null ? response.body().string() : "";

                        if (!response.isSuccessful()) {
                            String errBody = respBody.length() > 300 ? respBody.substring(0, 300) : respBody;
                            Log.e(TAG, "Error " + response.code() + ": " + errBody);
                            listener.onFailure("HTTP " + response.code() + ": " + errBody);
                            return;
                        }

                        // Parse JSON response: choices[0].message.audio.data (base64)
                        JSONObject json = new JSONObject(respBody);
                        JSONArray choices = json.getJSONArray("choices");
                        if (choices.length() == 0) {
                            listener.onFailure("响应中无结果");
                            return;
                        }

                        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                        JSONObject audio = message.optJSONObject("audio");
                        if (audio == null) {
                            listener.onFailure("响应中无音频数据");
                            return;
                        }

                        String audioBase64 = audio.getString("data");
                        byte[] audioData = Base64.decode(audioBase64, Base64.NO_WRAP);

                        if (audioData.length == 0) {
                            listener.onFailure("解码后的音频数据为空");
                            return;
                        }

                        Log.d(TAG, "Received " + audioData.length + " bytes (base64 decoded)");
                        listener.onSuccess(audioData);
                    } catch (Exception e) {
                        Log.e(TAG, "Response processing error", e);
                        listener.onFailure(e.getMessage() != null ? e.getMessage() : "响应处理失败");
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Build request failed", e);
            listener.onFailure(e.getMessage() != null ? e.getMessage() : "构建请求失败");
        }
    }

    /**
     * Cancel the current request.
     */
    public void cancel() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }
    }

    /**
     * Encode a local audio file to a base64 data URI.
     */
    public static String encodeAudioToBase64(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("音频文件不存在: " + filePath);
        }

        String ext = filePath.toLowerCase();
        String mime = "audio/wav";
        if (ext.endsWith(".mp3")) mime = "audio/mpeg";
        else if (ext.endsWith(".flac")) mime = "audio/flac";
        else if (ext.endsWith(".ogg")) mime = "audio/ogg";

        byte[] fileBytes = readFileBytes(file);
        String b64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP);
        return "data:" + mime + ";base64," + b64;
    }

    private static byte[] readFileBytes(File file) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        try {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read != data.length) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                java.io.FileInputStream fis2 = new java.io.FileInputStream(file);
                while ((n = fis2.read(buf)) != -1) baos.write(buf, 0, n);
                fis2.close();
                return baos.toByteArray();
            }
            return data;
        } finally {
            fis.close();
        }
    }

    /**
     * Save audio data to a file.
     */
    public static String saveAudioFile(byte[] data, File outputDir) throws IOException {
        String filename = "tts_" + System.currentTimeMillis() + ".wav";
        File outFile = new File(outputDir, filename);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(data);
        }
        return outFile.getAbsolutePath();
    }
}
