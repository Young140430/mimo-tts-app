package com.mimo.tts;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Settings Activity - Configure MiMo V2.5 TTS API, Voice, Mode, Context.
 */
public class SettingsActivity extends AppCompatActivity {
    private EditText inputBaseUrl, inputApiKey, inputContext;
    private Spinner spinnerVoice;
    private RadioGroup radioTtsMode;
    private LinearLayout layoutRefAudioHint;
    private ImageButton btnToggleVisibility;
    private View btnHelp;
    private MaterialButton btnSave, btnClear;

    private String voice = Constants.DEFAULT_VOICE;
    private String ttsMode = Constants.DEFAULT_TTS_MODE;
    private boolean showApiKey = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupClickListeners();
        loadSettings();
    }

    private void initViews() {
        inputBaseUrl = findViewById(R.id.input_base_url);
        inputApiKey = findViewById(R.id.input_api_key);
        inputContext = findViewById(R.id.input_context);
        spinnerVoice = findViewById(R.id.spinner_voice);
        radioTtsMode = findViewById(R.id.radio_tts_mode);
        layoutRefAudioHint = findViewById(R.id.layout_ref_audio_hint);
        btnToggleVisibility = findViewById(R.id.btn_toggle_visibility);
        btnHelp = findViewById(R.id.btn_help);
        btnSave = findViewById(R.id.btn_save);
        btnClear = findViewById(R.id.btn_clear);

        // Setup voice spinner
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, Constants.VOICES);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(voiceAdapter);

        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                voice = Constants.VOICES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupClickListeners() {
        btnToggleVisibility.setOnClickListener(v -> {
            showApiKey = !showApiKey;
            updateApiKeyVisibility();
        });

        btnHelp.setOnClickListener(v -> showHelpDialog());

        radioTtsMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_preset) {
                ttsMode = Constants.TTS_MODE_PRESET;
            } else if (checkedId == R.id.radio_voiceclone) {
                ttsMode = Constants.TTS_MODE_VOICECLONE;
            } else if (checkedId == R.id.radio_voicedesign) {
                ttsMode = Constants.TTS_MODE_VOICEDESIGN;
            }
            updateModeUI();
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnClear.setOnClickListener(v -> clearSettings());
    }

    private void updateModeUI() {
        // Show/hide context input based on mode
        LinearLayout layoutContext = findViewById(R.id.layout_context);
        if (Constants.TTS_MODE_VOICEDESIGN.equals(ttsMode) ||
            Constants.TTS_MODE_PRESET.equals(ttsMode)) {
            layoutContext.setVisibility(View.VISIBLE);
        } else {
            layoutContext.setVisibility(View.GONE);
        }

        // Show/hide ref audio hint
        layoutRefAudioHint.setVisibility(
                Constants.TTS_MODE_VOICECLONE.equals(ttsMode) ? View.VISIBLE : View.GONE);

        // Show/hide voice spinner for voiceclone (voice is ref audio, not preset)
        LinearLayout layoutVoice = findViewById(R.id.layout_voice);
        layoutVoice.setVisibility(
                Constants.TTS_MODE_VOICECLONE.equals(ttsMode) ? View.GONE : View.VISIBLE);
    }

    private void loadSettings() {
        SharedPreferences prefs = getPrefs();
        inputBaseUrl.setText(prefs.getString(Constants.PREF_API_BASE, Constants.DEFAULT_API_BASE));
        inputApiKey.setText(prefs.getString(Constants.PREF_API_KEY, Constants.DEFAULT_API_KEY));
        inputContext.setText(prefs.getString(Constants.PREF_CONTEXT, Constants.DEFAULT_CONTEXT));

        voice = prefs.getString(Constants.PREF_VOICE, Constants.DEFAULT_VOICE);
        ttsMode = prefs.getString(Constants.PREF_TTS_MODE, Constants.DEFAULT_TTS_MODE);

        // Set voice spinner
        for (int i = 0; i < Constants.VOICES.length; i++) {
            if (Constants.VOICES[i].equals(voice)) {
                spinnerVoice.setSelection(i);
                break;
            }
        }

        // Set TTS mode radio
        switch (ttsMode) {
            case Constants.TTS_MODE_VOICECLONE:
                radioTtsMode.check(R.id.radio_voiceclone);
                break;
            case Constants.TTS_MODE_VOICEDESIGN:
                radioTtsMode.check(R.id.radio_voicedesign);
                break;
            default:
                radioTtsMode.check(R.id.radio_preset);
                break;
        }

        updateApiKeyVisibility();
        updateModeUI();
    }

    private void saveSettings() {
        String baseUrl = inputBaseUrl.getText().toString().trim();
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请输入 API 地址", Toast.LENGTH_SHORT).show();
            return;
        }

        // Auto-add https:// if no scheme
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
            inputBaseUrl.setText(baseUrl);
        }

        getPrefs().edit()
                .putString(Constants.PREF_API_BASE, baseUrl)
                .putString(Constants.PREF_API_KEY, inputApiKey.getText().toString().trim())
                .putString(Constants.PREF_VOICE, voice)
                .putString(Constants.PREF_TTS_MODE, ttsMode)
                .putString(Constants.PREF_CONTEXT, inputContext.getText().toString().trim())
                .apply();

        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 800);
    }

    private void clearSettings() {
        new AlertDialog.Builder(this)
                .setTitle("确认清除")
                .setMessage("将清除所有已保存的设置")
                .setPositiveButton("确定", (dialog, which) -> {
                    getPrefs().edit()
                            .remove(Constants.PREF_API_BASE)
                            .remove(Constants.PREF_API_KEY)
                            .remove(Constants.PREF_VOICE)
                            .remove(Constants.PREF_TTS_MODE)
                            .remove(Constants.PREF_CONTEXT)
                            .apply();
                    loadSettings();
                    Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateApiKeyVisibility() {
        if (showApiKey) {
            inputApiKey.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            inputApiKey.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        }
        inputApiKey.setSelection(inputApiKey.getText().length());
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(getString(R.string.help_message))
                .setPositiveButton("知道了", null)
                .show();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("mimo_tts_prefs", MODE_PRIVATE);
    }
}
