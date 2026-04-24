package com.mimo.tts;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Main Activity - MiMo V2.5 TTS
 * Text-to-speech using MiMo V2.5 TTS API (/v1/chat/completions).
 * Supports preset voices, voice cloning, and voice design.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI components
    private View statusDot;
    private TextView statusLabel;
    private EditText editText;
    private MaterialButton btnGenerate, btnStopGen;
    private TextView tvRefAudioInfo;
    private MaterialButton btnUploadAudio, btnRecordAudio, btnClearAudio;
    private LinearLayout recordingIndicator;
    private TextView tvRecordingStatus;
    private MaterialButton btnStopRecording;
    private LinearLayout refAudioPlayback;
    private ImageButton btnPlayRefAudio;
    private TextView tvRefDuration;
    private LinearLayout playbackCard;
    private ImageButton btnPlayResult, btnSaveResult;
    private TextView tvResultInfo;
    private ImageButton btnSettings;

    // State
    private String currentStatus = Constants.STATUS_IDLE;
    private TTSApi ttsApi;
    private AudioPlayer audioPlayer;
    private byte[] generatedAudio;

    // Reference audio
    private String refAudioDataUri;  // base64 data URI for API
    private String refAudioFilePath; // local file path for playback
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private ByteArrayOutputStream recordedAudio;
    private File tempRecordFile;

    // Play ref audio
    private AudioPlayer refAudioPlayer;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();

        ttsApi = new TTSApi();
        audioPlayer = new AudioPlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkApiUrl();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioPlayer != null) audioPlayer.destroy();
        if (refAudioPlayer != null) refAudioPlayer.destroy();
        if (ttsApi != null) ttsApi.cancel();
        stopRecordingInternal();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.REQUEST_PERMISSIONS_CODE) {
            boolean granted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startRecording();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ========== Init ==========

    private void initViews() {
        statusDot = findViewById(R.id.status_dot);
        statusLabel = findViewById(R.id.status_label);
        editText = findViewById(R.id.edit_text);
        btnGenerate = findViewById(R.id.btn_generate);
        btnStopGen = findViewById(R.id.btn_stop_gen);
        tvRefAudioInfo = findViewById(R.id.tv_ref_audio_info);
        btnUploadAudio = findViewById(R.id.btn_upload_audio);
        btnRecordAudio = findViewById(R.id.btn_record_audio);
        btnClearAudio = findViewById(R.id.btn_clear_audio);
        recordingIndicator = findViewById(R.id.recording_indicator);
        tvRecordingStatus = findViewById(R.id.tv_recording_status);
        btnStopRecording = findViewById(R.id.btn_stop_recording);
        refAudioPlayback = findViewById(R.id.ref_audio_playback);
        btnPlayRefAudio = findViewById(R.id.btn_play_ref_audio);
        tvRefDuration = findViewById(R.id.tv_ref_duration);
        playbackCard = findViewById(R.id.playback_card);
        btnPlayResult = findViewById(R.id.btn_play_result);
        btnSaveResult = findViewById(R.id.btn_save_result);
        tvResultInfo = findViewById(R.id.tv_result_info);
        btnSettings = findViewById(R.id.btn_settings);
    }

    private void setupClickListeners() {
        btnSettings.setOnClickListener(v -> openSettings());

        btnGenerate.setOnClickListener(v -> generateSpeech());
        btnStopGen.setOnClickListener(v -> cancelGeneration());

        btnUploadAudio.setOnClickListener(v -> pickAudioFile());
        btnRecordAudio.setOnClickListener(v -> toggleRecording());
        btnClearAudio.setOnClickListener(v -> clearRefAudio());
        btnStopRecording.setOnClickListener(v -> stopRecording());

        btnPlayRefAudio.setOnClickListener(v -> playRefAudio());
        btnPlayResult.setOnClickListener(v -> playResult());
        btnSaveResult.setOnClickListener(v -> saveResult());
    }

    // ========== Generate Speech ==========

    private void generateSpeech() {
        String text = editText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.enter_text_first, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getPrefs();
        String apiBase = prefs.getString(Constants.PREF_API_BASE, "");
        if (apiBase.isEmpty()) {
            Toast.makeText(this, R.string.set_api_url_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = prefs.getString(Constants.PREF_API_KEY, Constants.DEFAULT_API_KEY);
        if (apiKey.isEmpty()) {
            Toast.makeText(this, R.string.set_api_key_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String ttsMode = prefs.getString(Constants.PREF_TTS_MODE, Constants.DEFAULT_TTS_MODE);
        String voice = prefs.getString(Constants.PREF_VOICE, Constants.DEFAULT_VOICE);
        String context = prefs.getString(Constants.PREF_CONTEXT, Constants.DEFAULT_CONTEXT);

        // Determine model based on TTS mode
        String model = Constants.getModelForMode(ttsMode);

        // For voiceclone mode, use ref audio as voice
        String effectiveVoice = voice;
        if (Constants.TTS_MODE_VOICECLONE.equals(ttsMode)) {
            if (refAudioDataUri != null && !refAudioDataUri.isEmpty()) {
                effectiveVoice = refAudioDataUri;
            } else {
                Toast.makeText(this, "音色克隆模式需要上传或录制参考音频", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // For voicedesign mode, context is required as voice description
        if (Constants.TTS_MODE_VOICEDESIGN.equals(ttsMode) && (context == null || context.isEmpty())) {
            Toast.makeText(this, "音色设计模式需要在设置中填写风格描述（音色描述）", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop any current playback
        audioPlayer.stop();
        playbackCard.setVisibility(View.GONE);

        updateStatus(Constants.STATUS_GENERATING);
        btnGenerate.setVisibility(View.GONE);
        btnStopGen.setVisibility(View.VISIBLE);

        ttsApi.generate(text, model, apiBase, apiKey, effectiveVoice, ttsMode, context,
                new TTSApi.CallbackListener() {
                    @Override
                    public void onSuccess(byte[] audioData) {
                        mainHandler.post(() -> {
                            generatedAudio = audioData;

                            btnGenerate.setVisibility(View.VISIBLE);
                            btnStopGen.setVisibility(View.GONE);

                            String sizeStr = String.format(java.util.Locale.getDefault(), "%.1f KB",
                                    audioData.length / 1024.0);
                            tvResultInfo.setText(sizeStr + "  WAV");
                            playbackCard.setVisibility(View.VISIBLE);

                            updateStatus(Constants.STATUS_SUCCESS);
                            // Auto-play
                            playResult();
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        mainHandler.post(() -> {
                            btnGenerate.setVisibility(View.VISIBLE);
                            btnStopGen.setVisibility(View.GONE);
                            updateStatus(Constants.STATUS_ERROR);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void cancelGeneration() {
        ttsApi.cancel();
        btnGenerate.setVisibility(View.VISIBLE);
        btnStopGen.setVisibility(View.GONE);
        updateStatus(Constants.STATUS_IDLE);
    }

    // ========== Playback ==========

    private void playResult() {
        if (generatedAudio == null || generatedAudio.length == 0) return;

        audioPlayer.stop();
        audioPlayer.playWav(generatedAudio);
        updateStatus(Constants.STATUS_PLAYING);
    }

    private void saveResult() {
        if (generatedAudio == null) return;

        try {
            File saveDir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "MiMoTTS");
            saveDir.mkdirs();
            String savedPath = TTSApi.saveAudioFile(generatedAudio, saveDir);
            Toast.makeText(this, getString(R.string.audio_saved) + ": " + savedPath, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ========== Reference Audio ==========

    private void pickAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, Constants.REQUEST_AUDIO_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_AUDIO_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            try {
                String suffix = ".wav";
                String mimeType = getContentResolver().getType(uri);
                if (mimeType != null) {
                    if (mimeType.contains("mp3")) suffix = ".mp3";
                    else if (mimeType.contains("ogg")) suffix = ".ogg";
                    else if (mimeType.contains("flac")) suffix = ".flac";
                }

                File tempDir = new File(getCacheDir(), "ref_audio");
                tempDir.mkdirs();
                tempRecordFile = new File(tempDir, "ref_" + System.currentTimeMillis() + suffix);

                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempRecordFile)) {
                    if (is != null) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) {
                            fos.write(buf, 0, n);
                        }
                    }
                }

                refAudioFilePath = tempRecordFile.getAbsolutePath();
                refAudioDataUri = TTSApi.encodeAudioToBase64(refAudioFilePath);

                updateRefAudioUI(refAudioFilePath);
                Toast.makeText(this, "参考音频已加载", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Failed to load audio file", e);
                Toast.makeText(this, "加载音频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    Constants.REQUEST_PERMISSIONS_CODE);
            return;
        }

        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize * 4);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "录音初始化失败", Toast.LENGTH_SHORT).show();
                return;
            }

            isRecording = true;
            recordedAudio = new ByteArrayOutputStream();

            recordingIndicator.setVisibility(View.VISIBLE);
            btnRecordAudio.setText(R.string.stop_recording);
            tvRecordingStatus.setText(R.string.recording);

            audioRecord.startRecording();

            recordThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording && audioRecord != null) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        recordedAudio.write(buffer, 0, read);
                    }
                }
            }, "RecordThread");
            recordThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Recording failed", e);
            Toast.makeText(this, "录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        isRecording = false;
        recordingIndicator.setVisibility(View.GONE);
        btnRecordAudio.setText(R.string.btn_record_audio);

        if (recordThread != null) {
            try {
                recordThread.join(1000);
            } catch (InterruptedException ignored) {}
            recordThread = null;
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }

        if (recordedAudio != null && recordedAudio.size() > 0) {
            try {
                byte[] pcmData = recordedAudio.toByteArray();
                File tempDir = new File(getCacheDir(), "ref_audio");
                tempDir.mkdirs();
                tempRecordFile = new File(tempDir, "recording_" + System.currentTimeMillis() + ".wav");

                try (FileOutputStream fos = new FileOutputStream(tempRecordFile)) {
                    writeWavHeader(fos, pcmData.length, 16000, 1, 16);
                    fos.write(pcmData);
                }

                refAudioFilePath = tempRecordFile.getAbsolutePath();
                refAudioDataUri = TTSApi.encodeAudioToBase64(refAudioFilePath);
                updateRefAudioUI(refAudioFilePath);
                Toast.makeText(this, "录音完成，已设为参考音频", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Failed to save recording", e);
                Toast.makeText(this, "保存录音失败", Toast.LENGTH_SHORT).show();
            }
        }

        recordedAudio = null;
    }

    private void stopRecordingInternal() {
        isRecording = false;
        if (recordThread != null) {
            try { recordThread.join(500); } catch (InterruptedException ignored) {}
            recordThread = null;
        }
        if (audioRecord != null) {
            try { audioRecord.stop(); audioRecord.release(); } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private void clearRefAudio() {
        refAudioDataUri = null;
        refAudioFilePath = null;
        if (refAudioPlayer != null) refAudioPlayer.stop();
        tvRefAudioInfo.setText(R.string.no_ref_audio);
        refAudioPlayback.setVisibility(View.GONE);

        if (tempRecordFile != null && tempRecordFile.exists()) {
            tempRecordFile.delete();
            tempRecordFile = null;
        }
    }

    private void updateRefAudioUI(String filePath) {
        if (filePath != null) {
            File f = new File(filePath);
            String name = f.getName();
            if (name.length() > 30) name = "..." + name.substring(name.length() - 27);
            tvRefAudioInfo.setText(name);
            refAudioPlayback.setVisibility(View.VISIBLE);
        } else {
            tvRefAudioInfo.setText(R.string.no_ref_audio);
            refAudioPlayback.setVisibility(View.GONE);
        }
    }

    private void playRefAudio() {
        if (refAudioFilePath == null) return;
        if (refAudioPlayer == null) refAudioPlayer = new AudioPlayer();
        refAudioPlayer.stop();

        if (refAudioFilePath.endsWith(".wav")) {
            try {
                File f = new File(refAudioFilePath);
                byte[] data = new byte[(int) f.length()];
                try (FileInputStream fis = new FileInputStream(f)) {
                    fis.read(data);
                }
                refAudioPlayer.playWav(data);
            } catch (Exception e) {
                Log.e(TAG, "Play ref audio failed", e);
            }
        }
    }

    // ========== Status ==========

    private void updateStatus(String status) {
        currentStatus = status;
        switch (status) {
            case Constants.STATUS_IDLE:
                statusDot.setBackgroundResource(R.drawable.status_dot_idle);
                statusLabel.setText(R.string.status_idle);
                break;
            case Constants.STATUS_GENERATING:
                statusDot.setBackgroundResource(R.drawable.status_dot_generating);
                statusLabel.setText(R.string.status_generating);
                break;
            case Constants.STATUS_PLAYING:
                statusDot.setBackgroundResource(R.drawable.status_dot_playing);
                statusLabel.setText(R.string.status_playing);
                break;
            case Constants.STATUS_ERROR:
                statusDot.setBackgroundResource(R.drawable.status_dot_error);
                statusLabel.setText(R.string.status_error);
                break;
            case Constants.STATUS_SUCCESS:
                statusDot.setBackgroundResource(R.drawable.status_dot_success);
                statusLabel.setText(R.string.status_success);
                break;
        }
    }

    // ========== Utils ==========

    private void checkApiUrl() {
        SharedPreferences prefs = getPrefs();
        String apiBase = prefs.getString(Constants.PREF_API_BASE, "");
        String apiKey = prefs.getString(Constants.PREF_API_KEY, "");
        if (apiBase.isEmpty() || apiKey.isEmpty()) {
            findViewById(R.id.status_dot).setBackgroundResource(R.drawable.status_dot_error);
            statusLabel.setText(R.string.set_api_url_first);
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("mimo_tts_prefs", MODE_PRIVATE);
    }

    /**
     * Write a WAV file header.
     */
    private void writeWavHeader(FileOutputStream fos, int dataLength, int sampleRate,
                                 int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int totalLength = 36 + dataLength;

        fos.write(new byte[]{
                'R', 'I', 'F', 'F',
                (byte) (totalLength & 0xff), (byte) ((totalLength >> 8) & 0xff),
                (byte) ((totalLength >> 16) & 0xff), (byte) ((totalLength >> 24) & 0xff),
                'W', 'A', 'V', 'E',
                'f', 'm', 't', ' ',
                16, 0, 0, 0,  // chunk size
                1, 0,  // PCM format
                (byte) channels, 0,
                (byte) (sampleRate & 0xff), (byte) ((sampleRate >> 8) & 0xff),
                (byte) ((sampleRate >> 16) & 0xff), (byte) ((sampleRate >> 24) & 0xff),
                (byte) (byteRate & 0xff), (byte) ((byteRate >> 8) & 0xff),
                (byte) ((byteRate >> 16) & 0xff), (byte) ((byteRate >> 24) & 0xff),
                (byte) blockAlign, 0,
                (byte) bitsPerSample, 0,
                'd', 'a', 't', 'a',
                (byte) (dataLength & 0xff), (byte) ((dataLength >> 8) & 0xff),
                (byte) ((dataLength >> 16) & 0xff), (byte) ((dataLength >> 24) & 0xff)
        });
    }
}
