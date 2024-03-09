package com.poupa.vinylmusicplayer.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.ToNumberStrategy;
import com.poupa.vinylmusicplayer.R;
import com.poupa.vinylmusicplayer.ui.activities.SettingsActivity;
import com.poupa.vinylmusicplayer.util.OopsHandler;
import com.poupa.vinylmusicplayer.util.SafeToast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class SharedPreferencesImporter extends AppCompatActivity {

    private Context context;
    private ActivityResultLauncher importFilePicker;

    private SharedPreferences sharedPreferences;

    static final String FILE_FORMAT = "file_format";
    static final String VERSION_CODE = "version_code";
    static final String CURRENT_FILE_FORMAT = "json";

    static final int minCompatibleVersion = 192;
    static final int maxCompatibleVersion = Integer.MAX_VALUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = this.getApplicationContext();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        importFilePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            // Unless the selection has been cancelled, create the export file
            if(result != null) {
                importSettings(result);
            }
            // Finishes the last activity to return to the settings activity.
            this.finish();
        });
        importFilePicker.launch(new String[]{"text/plain"});
    }

    private void importSettings(Uri location) {
        // Prepare the table used to store the lines
        Map<String, ?> preferences;
        Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
        SharedPreferences.Editor spEditor = sharedPreferences.edit();

        try {
            // Try to open the file
            ParcelFileDescriptor file = this.context.getContentResolver().openFileDescriptor(location, "r") ;
            if(file == null) return;

            // Read the content from the file line by line
            BufferedReader reader = new BufferedReader(new FileReader(file.getFileDescriptor()));
            preferences = gson.fromJson(reader, Map.class);

            String fileFormat = (String) preferences.get(FILE_FORMAT);
            int savedPrefsVersionCode = (int) preferences.get(VERSION_CODE);

            // Checks whether the file format saved in the preferences file is compatible with the current settings parser.
            if(Objects.equals(fileFormat, CURRENT_FILE_FORMAT)) {
                // Checks whether the version of the app and the saved settings are compatible.
                if(minCompatibleVersion <= savedPrefsVersionCode && savedPrefsVersionCode <= maxCompatibleVersion) {
                    for (Map.Entry<String, ?> entry : preferences.entrySet()) {
                        if (Objects.equals(entry.getKey(), FILE_FORMAT) || Objects.equals(entry.getKey(), VERSION_CODE)) continue;

                        Object object = entry.getValue();
                        String key = entry.getKey();
                        if (object instanceof String) {
                            spEditor.putString(key, (String) object);
                        } else if (object instanceof Long) {
                            spEditor.putInt(key, Math.toIntExact((Long) object));
                        } else if (object instanceof Boolean) {
                            spEditor.putBoolean(key, (Boolean) object);
                        } else if (object instanceof Float) {
                            spEditor.putFloat(key, (Float) object);
                        }
                    }
                    reader.close();
                    file.close();
                } else {
                    // Future version of the app needs to come back here and provide importer/converter code
                    SafeToast.show(this.context, R.string.unsupported_saved_pref_version);
                }

            } else {
                // Future version of the app needs to come back here and provide importer/converter code
                SafeToast.show(this.context, R.string.unsupported_saved_pref_format);
            }
        }
        catch(IOException exception) {
            // An error happened while reading the file
            SafeToast.show(this.context, R.string.cannot_import_settings);
            OopsHandler.collectStackTrace(exception);
        }
        spEditor.apply();
    }

}
