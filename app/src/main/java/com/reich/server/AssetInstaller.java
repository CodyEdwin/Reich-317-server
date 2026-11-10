package com.reich.server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * Extracts the server data (definitions, clipping maps, ...) that is shipped
 * inside the APK into the private storage of the application, where it can be
 * read and written by the server.
 * <p>
 * The extraction is skipped when the data is already up to date, which is
 * determined by a small marker file that contains the version of the archive.
 */
public final class AssetInstaller {

    private static final String TAG = ServerLog.TAG;

    /**
     * The name of the asset that holds the server data.
     */
    private static final String ASSET = "server-data.zip";

    /**
     * The name of the marker file that is written once the data is extracted.
     */
    private static final String MARKER = ".data-version";

    /**
     * The name of the directory (inside the private storage) that holds the
     * server data.
     */
    private static final String DIRECTORY = "server";

    /**
     * A listener that is notified about the extraction progress.
     */
    public interface ProgressListener {
        /**
         * @param percent the progress of the extraction, 0 - 100.
         * @param message a description of the current step.
         */
        void onProgress(int percent, String message);
    }

    private AssetInstaller() {
    }

    /**
     * Extracts the server data if needed and returns the directory that holds
     * it.
     *
     * @param context  the application context.
     * @param listener the progress listener, may be {@code null}.
     * @return the directory that contains {@code definitions},
     * {@code clipping} and {@code saves}.
     * @throws IOException if the data could not be extracted.
     */
    public static synchronized File install(Context context, ProgressListener listener) throws IOException {
        Context app = context.getApplicationContext();
        AssetManager assets = app.getAssets();
        File root = new File(app.getFilesDir(), DIRECTORY);

        long size = assetSize(assets);
        String stamp = BuildConfig.VERSION_CODE + ":" + size;

        File marker = new File(root, MARKER);
        if (marker.isFile() && stamp.equals(readMarker(marker)) && isComplete(root)) {
            installSampleCharacters(root);
            report(listener, 100, "Server data is ready.");
            return root;
        }

        report(listener, 0, "Preparing server data...");

        File archive = new File(app.getCacheDir(), ASSET);
        copyAsset(assets, archive);

        try {
            extract(archive, root, listener);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
        }

        writeMarker(marker, stamp);
        ensureSaveDirectories(root);
        installSampleCharacters(root);
        report(listener, 100, "Server data is ready.");
        return root;
    }

    /**
     * @return the directory that holds the extracted server data.
     */
    public static File getDataDirectory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
    }

    /**
     * @return {@code true} if the data has already been extracted.
     */
    public static boolean isInstalled(Context context) {
        File root = getDataDirectory(context);
        return isComplete(root);
    }

    private static boolean isComplete(File root) {
        return new File(root, "definitions/items.json").isFile()
                && new File(root, "definitions/npc_defs.json").isFile()
                && new File(root, "clipping/map_index").isFile();
    }

    private static long assetSize(AssetManager assets) {
        try {
            // The archive is stored uncompressed inside the APK, so its real
            // size can be read directly.
            try (android.content.res.AssetFileDescriptor descriptor = assets.openFd(ASSET)) {
                return descriptor.getLength();
            } catch (IOException ignored) {
                // The asset might be compressed, fall back to the stream.
            }
        } catch (Throwable ignored) {
            // Fall through to the stream based lookup.
        }
        try (InputStream stream = assets.open(ASSET)) {
            return stream.available();
        } catch (IOException e) {
            return -1;
        }
    }

    private static void copyAsset(AssetManager assets, File target) throws IOException {
        try (InputStream input = assets.open(ASSET);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void extract(File archive, File root, ProgressListener listener) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            long total = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    total += entry.getSize();
                }
            }

            long written = 0;
            int percent = 0;
            entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                File target = new File(root, entry.getName());
                // Never escape the data directory.
                if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
                    Log.w(TAG, "Skipping suspicious archive entry: " + entry.getName());
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Unable to create the directory " + parent);
                }

                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = input.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                        written += read;
                        int current = total > 0 ? (int) (written * 100 / total) : 0;
                        if (current > percent) {
                            percent = current;
                            report(listener, percent, "Extracting server data... " + percent + "%");
                        }
                    }
                }
            }
        }
    }

    private static void ensureSaveDirectories(File root) throws IOException {
        File saves = new File(root, "saves");
        File characters = new File(saves, "characters");
        File clans = new File(saves, "clans");
        if (!characters.isDirectory() && !characters.mkdirs()) {
            throw new IOException("Unable to create the character save directory");
        }
        if (!clans.isDirectory() && !clans.mkdirs()) {
            throw new IOException("Unable to create the clan save directory");
        }
    }

    /**
     * Copies the sample characters that are shipped with the application into
     * the save directory. Existing characters are never overwritten.
     *
     * @param root the data directory.
     */
    private static void installSampleCharacters(File root) {
        File samples = new File(root, "sample-saves");
        File[] files = samples.listFiles();
        if (files == null) {
            return;
        }
        File characters = new File(root, "saves/characters");
        for (File sample : files) {
            if (!sample.isFile() || !sample.getName().endsWith(".json")) {
                continue;
            }
            File target = new File(characters, sample.getName());
            if (target.exists()) {
                continue;
            }
            try (InputStream input = new java.io.FileInputStream(sample);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to install the sample character " + sample.getName(), e);
            }
        }

        // The samples are not needed anymore, they are restored the next time
        // the data is extracted.
        File[] remaining = samples.listFiles();
        if (remaining != null) {
            for (File file : remaining) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        samples.delete();
    }

    private static String readMarker(File marker) {
        try (InputStream input = new java.io.FileInputStream(marker)) {
            byte[] data = new byte[64];
            int read = input.read(data);
            if (read <= 0) {
                return null;
            }
            return new String(data, 0, read, "UTF-8").trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeMarker(File marker, String stamp) throws IOException {
        File parent = marker.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create the data directory");
        }
        try (OutputStream output = new FileOutputStream(marker)) {
            output.write(stamp.getBytes("UTF-8"));
        }
    }

    private static void report(ProgressListener listener, int percent, String message) {
        if (listener == null) {
            return;
        }
        listener.onProgress(percent, message);
    }
}
