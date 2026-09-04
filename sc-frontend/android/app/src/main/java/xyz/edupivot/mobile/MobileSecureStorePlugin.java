package xyz.edupivot.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Holds the small number of OAuth/session secrets outside the WebView. Values
 * are encrypted by Android Keystore through EncryptedSharedPreferences.
 */
@CapacitorPlugin(name = "MobileSecureStore")
public class MobileSecureStorePlugin extends Plugin {
    private static final String PREFERENCES_NAME = "edupivot.secure-store";
    private static final int MAX_VALUE_LENGTH = 32 * 1024;

    @PluginMethod
    public void get(PluginCall call) {
        String key = getValidKey(call);
        if (key == null) {
            return;
        }

        try {
            String value = preferences().getString(key, null);
            JSObject result = new JSObject();
            if (value != null) {
                result.put("value", value);
            }
            call.resolve(result);
        } catch (Exception exception) {
            call.reject("无法读取安全存储。", exception);
        }
    }

    @PluginMethod
    public void set(PluginCall call) {
        String key = getValidKey(call);
        String value = call.getString("value");
        if (key == null) {
            return;
        }
        if (value == null || value.length() > MAX_VALUE_LENGTH) {
            call.reject("安全存储值无效。");
            return;
        }

        try {
            preferences().edit().putString(key, value).apply();
            call.resolve();
        } catch (Exception exception) {
            call.reject("无法写入安全存储。", exception);
        }
    }

    @PluginMethod
    public void remove(PluginCall call) {
        String key = getValidKey(call);
        if (key == null) {
            return;
        }

        try {
            preferences().edit().remove(key).apply();
            call.resolve();
        } catch (Exception exception) {
            call.reject("无法清除安全存储。", exception);
        }
    }

    private String getValidKey(PluginCall call) {
        String key = call.getString("key");
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,64}")) {
            call.reject("安全存储键无效。");
            return null;
        }
        return key;
    }

    private SharedPreferences preferences() throws Exception {
        Context context = getContext();
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
