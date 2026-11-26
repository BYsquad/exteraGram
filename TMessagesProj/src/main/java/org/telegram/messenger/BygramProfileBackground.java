package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Base64;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.net.HttpURLConnection;

public final class BygramProfileBackground {
    private static final int LIMIT = 10 * 1024 * 1024;

    public interface SaveCallback {
        void complete(boolean success, String loginUrl);
    }

    private static SharedPreferences prefs() { return MessagesController.getGlobalMainSettings(); }

    private static AtomicFile file(long userId) {
        return new AtomicFile(new File(ApplicationLoader.applicationContext.getFilesDir(), "bygram-profile-" + userId));
    }

    public static boolean hasLocal(long userId) { return file(userId).getBaseFile().isFile(); }
    public static boolean isPrivate(long userId) { return prefs().getBoolean("bygram_background_private_" + userId, true); }
    public static boolean isStretched(long userId) { return prefs().getBoolean("bygram_background_stretch_" + userId, false); }
    public static boolean showOthers(long userId) { return BygramPreferences.getUserBoolean(userId, "bygram_background_others", true); }
    public static void setShowOthers(int account, boolean show) { BygramPreferences.setBoolean(account, "bygram_background_others", show); }

    public static void save(long userId, Uri uri, boolean remove, boolean privateBackground, boolean stretch, SaveCallback callback) {
        save(userId, uri, remove, privateBackground, stretch, true, callback);
    }

    private static void save(long userId, Uri uri, boolean remove, boolean privateBackground, boolean stretch,
                             boolean canRegister, SaveCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                byte[] image = remove ? null : uri != null ? read(uri) : hasLocal(userId) ? file(userId).readFully() : null;
                if (uri != null && decode(image, stretch) == null) throw new IllegalArgumentException("Invalid profile background");
                Runnable persist = () -> Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (remove) file(userId).delete();
                        else if (uri != null) write(userId, image);
                        prefs().edit().putBoolean("bygram_background_private_" + userId, privateBackground)
                                .putBoolean("bygram_background_stretch_" + userId, stretch).apply();
                        AndroidUtilities.runOnUIThread(() -> callback.complete(true, null));
                    } catch (Exception error) {
                        FileLog.e(error);
                        AndroidUtilities.runOnUIThread(() -> callback.complete(false, null));
                    }
                });
                boolean wasPublic = hasLocal(userId) && !isPrivate(userId);
                if (!wasPublic && (image == null || privateBackground)) {
                    persist.run();
                    return;
                }
                JSONObject data = BygramAccountService.json("action", "profile_background_put", "user_id", userId, "full_profile", stretch);
                data.put("image", image != null && !privateBackground ? Base64.encodeToString(image, Base64.NO_WRAP) : "");
                BygramAccountService.profileBackgroundRequest(userId, data, true, response -> {
                    if (response != null && response.optBoolean("ok")) persist.run();
                    else if (response != null && response.optBoolean("needs_registration") && canRegister) {
                        BygramAccountService.registerAccount(userId, (registered, url) -> {
                            if (registered) save(userId, uri, remove, privateBackground, stretch, false, callback);
                            else callback.complete(false, url);
                        });
                    } else callback.complete(false, null);
                });
            } catch (Exception error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> callback.complete(false, null));
            }
        });
    }

    private static void write(long userId, byte[] image) throws Exception {
        AtomicFile file = file(userId);
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(image);
            file.finishWrite(stream);
        } catch (Exception error) {
            file.failWrite(stream);
            throw error;
        }
    }

    public static void restoreExisting(long userId, Runnable callback) {
        if (userId <= 0 || hasLocal(userId) || prefs().contains("bygram_background_private_" + userId)) {
            callback.run();
            return;
        }
        fetchMetadata(userId, metadata -> {
            String revision = metadata == null ? "" : metadata.optString("revision");
            if (!revision.matches("[0-9a-f]{64}")) {
                callback.run();
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    byte[] data = download(userId, revision);
                    if (!hasLocal(userId) && !prefs().contains("bygram_background_private_" + userId)) {
                        write(userId, data);
                        prefs().edit().putBoolean("bygram_background_private_" + userId, false)
                                .putBoolean("bygram_background_stretch_" + userId, metadata.optBoolean("full_profile")).apply();
                    }
                } catch (Exception error) {
                    FileLog.e(error);
                }
                AndroidUtilities.runOnUIThread(callback);
            });
        });
    }

    private static void fetchMetadata(long profile, Utilities.Callback<JSONObject> callback) {
        BygramAccountService.profileBackgroundRequest(profile, BygramAccountService.json(
                "action", "profile_background_get", "owner_id", profile), false, callback);
    }

    private static byte[] read(Uri uri) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return read(in);
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        if (in == null) throw new IllegalArgumentException("Missing image");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > LIMIT) throw new IllegalArgumentException("Image exceeds 10 MiB");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static byte[] download(long profile, String revision) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) BygramAccountService.profileBackgroundUrl(profile, revision).openConnection();
        try {
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(25000);
            try (InputStream stream = connection.getInputStream()) {
                return read(stream);
            }
        } finally {
            connection.disconnect();
        }
    }

    public static void load(long currentUser, long profile, Utilities.Callback<Background> callback) {
        if (profile <= 0 || currentUser != profile && !showOthers(currentUser)) {
            callback.run(null);
            return;
        }
        if (currentUser == profile && hasLocal(profile)) {
            decodeAsync(() -> file(profile).readFully(), isStretched(profile), callback);
            return;
        }
        fetchMetadata(profile, response -> {
            String revision = response == null ? "" : response.optString("revision");
            if (!revision.matches("[0-9a-f]{64}")) callback.run(null);
            else decodeAsync(() -> download(profile, revision), response.optBoolean("full_profile"), callback);
        });
    }

    private interface ImageData { byte[] read() throws Exception; }

    private static void decodeAsync(ImageData data, boolean stretch, Utilities.Callback<Background> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            Background background = null;
            try { background = decode(data.read(), stretch); }
            catch (Exception error) { FileLog.e(error); }
            Background result = background;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    private static Background decode(byte[] data, boolean stretch) throws Exception {
        if (data.length == 0 || data.length > LIMIT) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 4096 || options.outHeight > 4096) return null;
        String mime = options.outMimeType;
        if (!("image/gif".equals(mime) || "image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime))) return null;
        if (Build.VERSION.SDK_INT >= 28) {
            Drawable image = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)), (decoder, info, source) -> {
                int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (max > 1600) decoder.setTargetSampleSize((max + 1599) / 1600);
            });
            return new Background(image, null, stretch);
        }
        Movie movie = "image/gif".equals(mime) ? Movie.decodeByteArray(data, 0, data.length) : null;
        if (movie != null) return new Background(null, movie, stretch);
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, (Math.max(options.outWidth, options.outHeight) + 1599) / 1600);
        android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return bitmap == null ? null : new Background(new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap), null, stretch);
    }

    public static final class Background implements Drawable.Callback {
        public final boolean stretch;
        private final Drawable image;
        private final Movie movie;
        private final long start = SystemClock.uptimeMillis();
        private View target;

        private Background(Drawable image, Movie movie, boolean stretch) {
            this.image = image;
            this.movie = movie;
            this.stretch = stretch;
        }

        public void attach(View view) {
            detach();
            target = view;
            if (image != null) image.setCallback(this);
            if (image instanceof Animatable animatable) animatable.start();
        }

        public void detach() {
            if (image instanceof Animatable animatable) animatable.stop();
            if (image != null) image.setCallback(null);
            target = null;
        }

        public void draw(Canvas canvas, int width, int height, int overlay) {
            if (width <= 0 || height <= 0) return;
            int iw = image == null ? movie.width() : image.getIntrinsicWidth();
            int ih = image == null ? movie.height() : image.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) return;
            float scale = Math.max((float) width / iw, (float) height / ih);
            int save = canvas.save();
            canvas.clipRect(0, 0, width, height);
            canvas.translate((width - iw * scale) / 2, (height - ih * scale) / 2);
            canvas.scale(scale, scale);
            if (image != null) {
                image.setBounds(0, 0, iw, ih);
                image.draw(canvas);
            } else {
                movie.setTime((int) ((SystemClock.uptimeMillis() - start) % Math.max(1, movie.duration())));
                movie.draw(canvas, 0, 0);
                if (target != null) target.postInvalidateOnAnimation();
            }
            canvas.drawColor(overlay);
            canvas.restoreToCount(save);
        }

        @Override public void invalidateDrawable(Drawable who) { if (target != null) target.invalidate(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            if (target != null) target.postDelayed(what, Math.max(0, when - SystemClock.uptimeMillis()));
        }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { if (target != null) target.removeCallbacks(what); }
    }
}
