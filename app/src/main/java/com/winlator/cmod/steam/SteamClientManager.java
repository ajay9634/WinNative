package com.winlator.cmod.steam;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages Steam client download, extraction, and Steamless DRM patching.
 * Ported from GameNative's SteamService and XServerScreen logic.
 */
public class SteamClientManager {
    private static final String TAG = "SteamClientManager";

    // Primary download URL from user's GitHub release
    private static final String STEAM_DOWNLOAD_URL =
            "https://github.com/maxjivi05/Components/releases/download/Components/steam.tzst";

    // Fallback CDN URLs (from GameNative)
    private static final String STEAM_PRIMARY_CDN = "https://downloads.gamenative.app/steam.tzst";
    private static final String STEAM_FALLBACK_CDN = "https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/steam.tzst";

    public interface DownloadProgressListener {
        void onProgress(float progress);
        void onComplete(boolean success, String error);
    }

    /**
     * Check if steam.tzst has been downloaded and is available for extraction.
     */
    public static boolean isSteamDownloaded(Context context) {
        File steamFile = new File(context.getFilesDir(), "steam.tzst");
        return steamFile.exists() && steamFile.length() > 0;
    }

    /**
     * Check if Steam client has already been extracted into the container's Wine prefix.
     */
    public static boolean isSteamInstalled(Context context) {
        ImageFs imageFs = ImageFs.find(context);
        File steamExe = new File(imageFs.getRootDir(),
                ImageFs.WINEPREFIX + "/drive_c/Program Files (x86)/Steam/steam.exe");
        return steamExe.exists();
    }

    /**
     * Download steam.tzst asynchronously from GitHub release (with CDN fallbacks).
     */
    public static void downloadSteam(Context context, DownloadProgressListener listener) {
        new Thread(() -> {
            File dest = new File(context.getFilesDir(), "steam.tzst");
            File tmp = new File(dest.getAbsolutePath() + ".part");

            boolean success = false;
            String error = null;

            // Try URLs in order: user's GitHub, primary CDN, fallback CDN
            String[] urls = { STEAM_DOWNLOAD_URL, STEAM_PRIMARY_CDN, STEAM_FALLBACK_CDN };

            for (String urlStr : urls) {
                try {
                    Log.d(TAG, "Attempting download from: " + urlStr);
                    downloadFile(urlStr, tmp, listener);

                    // Verify download completed
                    if (tmp.exists() && tmp.length() > 0) {
                        if (!tmp.renameTo(dest)) {
                            // Fallback copy
                            Files.copy(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            tmp.delete();
                        }
                        success = true;
                        Log.d(TAG, "Steam download completed: " + dest.length() + " bytes");
                        break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Download failed from " + urlStr + ": " + e.getMessage());
                    error = e.getMessage();
                    tmp.delete();
                }
            }

            if (!success) {
                final String finalError = error != null ? error : "All download sources failed";
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "Steam download failed: " + finalError
                            + ". Try disabling VPN.", Toast.LENGTH_LONG).show();
                });
            }

            final boolean finalSuccess = success;
            final String finalError2 = error;
            if (listener != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        listener.onComplete(finalSuccess, finalError2));
            }
        }, "SteamDownloader").start();
    }

    private static void downloadFile(String urlStr, File dest, DownloadProgressListener listener) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + responseCode);
            }

            long total = conn.getContentLengthLong();
            long downloaded = 0;

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buf)) >= 0) {
                    out.write(buf, 0, bytesRead);
                    downloaded += bytesRead;
                    if (listener != null && total > 0) {
                        final float progress = (float) downloaded / total;
                        new Handler(Looper.getMainLooper()).post(() -> listener.onProgress(progress));
                    }
                }
            }

            if (total > 0 && dest.length() != total) {
                dest.delete();
                throw new Exception("Incomplete download: " + dest.length() + "/" + total);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Extract steam.tzst into the container's image filesystem root directory.
     * This places Steam client files into the Wine prefix at:
     *   C:\Program Files (x86)\Steam\
     */
    public static boolean extractSteam(Context context) {
        if (isSteamInstalled(context)) {
            Log.d(TAG, "Steam already installed, skipping extraction");
            return true;
        }

        File steamFile = new File(context.getFilesDir(), "steam.tzst");
        if (!steamFile.exists()) {
            Log.e(TAG, "steam.tzst not found, cannot extract");
            return false;
        }

        ImageFs imageFs = ImageFs.find(context);
        try {
            Log.d(TAG, "Extracting steam.tzst to " + imageFs.getRootDir());
            TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD,
                    steamFile,
                    imageFs.getRootDir(),
                    null
            );
            Log.d(TAG, "Steam extraction complete");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract steam.tzst: " + e.getMessage());
            return false;
        }
    }

    /**
     * Run Steamless DRM stripping on a game executable.
     * Creates a batch file wrapper and executes it through Wine.
     *
     * @param context     Application context
     * @param exePath     The Windows-style path to the game executable (e.g., "Games/MyGame/game.exe")
     * @param shellRunner A callback that takes a Wine command string and returns the output
     * @return true if Steamless ran successfully
     */
    public static boolean runSteamless(Context context, String exePath, ShellCommandRunner shellRunner) {
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();

        // Check if Steamless CLI exists
        File steamlessDir = new File(rootDir, "Steamless");
        File steamlessCli = new File(steamlessDir, "Steamless.CLI.exe");
        if (!steamlessCli.exists()) {
            Log.w(TAG, "Steamless.CLI.exe not found at " + steamlessCli.getAbsolutePath());
            return false;
        }

        File batchFile = null;
        try {
            // Normalize path to Windows format
            String normalizedPath = exePath.replace('/', '\\');
            String windowsPath = "A:\\" + normalizedPath;

            // Create batch file wrapper for Steamless
            batchFile = new File(rootDir, "tmp/steamless_wrapper.bat");
            if (batchFile.getParentFile() != null) batchFile.getParentFile().mkdirs();

            String batchContent = "@echo off\r\nz:\\Steamless\\Steamless.CLI.exe \"" + windowsPath + "\"\r\n";
            FileUtils.writeString(batchFile, batchContent);

            // Execute via Wine
            String command = "wine z:\\tmp\\steamless_wrapper.bat";
            String output = shellRunner.exec(command);
            Log.d(TAG, "Steamless output: " + output);

            // Check if unpacked exe was created
            String unixPath = exePath.replace('\\', '/');
            File wineprefix = new File(rootDir, ImageFs.WINEPREFIX);
            File exe = new File(wineprefix, "dosdevices/a:/" + unixPath);
            File unpackedExe = new File(wineprefix, "dosdevices/a:/" + unixPath + ".unpacked.exe");
            File originalExe = new File(wineprefix, "dosdevices/a:/" + unixPath + ".original.exe");

            if (exe.exists() && unpackedExe.exists()) {
                // Backup original if not already backed up
                if (!originalExe.exists()) {
                    Files.copy(exe.toPath(), originalExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                // Replace with unpacked version
                Files.copy(unpackedExe.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Log.d(TAG, "Successfully patched DRM for: " + exePath);
                return true;
            } else {
                Log.w(TAG, "Steamless did not produce unpacked exe for: " + exePath);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error running Steamless on " + exePath, e);
            return false;
        } finally {
            if (batchFile != null) batchFile.delete();
        }
    }

    /**
     * Interface for executing Wine shell commands from the guest environment.
     */
    public interface ShellCommandRunner {
        String exec(String command);
    }
}
