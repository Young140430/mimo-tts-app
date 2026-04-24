package com.mimo.tts;

public class Constants {
    // Status
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_PLAYING = "playing";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_SUCCESS = "success";

    // SharedPreferences keys
    public static final String PREF_API_BASE = "apiBase";
    public static final String PREF_API_KEY = "apiKey";
    public static final String PREF_MODEL = "model";
    public static final String PREF_VOICE = "voice";
    public static final String PREF_TTS_MODE = "ttsMode";
    public static final String PREF_CONTEXT = "context";

    // Defaults
    public static final String DEFAULT_API_BASE = "https://api.xiaomimimo.com/v1";
    public static final String DEFAULT_API_KEY = "";
    public static final String DEFAULT_MODEL = "mimo-v2.5-tts";
    public static final String DEFAULT_VOICE = "茉莉";
    public static final String DEFAULT_TTS_MODE = "preset";
    public static final String DEFAULT_CONTEXT = "";

    // TTS modes
    public static final String TTS_MODE_PRESET = "preset";
    public static final String TTS_MODE_VOICECLONE = "voiceclone";
    public static final String TTS_MODE_VOICEDESIGN = "voicedesign";

    // Models for each mode
    public static final String MODEL_PRESET = "mimo-v2.5-tts";
    public static final String MODEL_VOICECLONE = "mimo-v2.5-tts-voiceclone";
    public static final String MODEL_VOICEDESIGN = "mimo-v2.5-tts-voicedesign";

    // Preset voices
    public static final String[] VOICES = {"冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean"};

    // TTS mode display names
    public static final String[] TTS_MODES = {TTS_MODE_PRESET, TTS_MODE_VOICECLONE, TTS_MODE_VOICEDESIGN};
    public static final String[] TTS_MODE_LABELS = {"预置音色", "音色克隆", "音色设计"};

    // Permission request code
    public static final int REQUEST_PERMISSIONS_CODE = 1001;
    public static final int REQUEST_AUDIO_FILE = 2001;

    /**
     * Get the model name for a given TTS mode.
     */
    public static String getModelForMode(String ttsMode) {
        switch (ttsMode) {
            case TTS_MODE_VOICECLONE:
                return MODEL_VOICECLONE;
            case TTS_MODE_VOICEDESIGN:
                return MODEL_VOICEDESIGN;
            default:
                return MODEL_PRESET;
        }
    }
}
