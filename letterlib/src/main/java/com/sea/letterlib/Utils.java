package com.sea.letterlib;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static android.provider.MediaStore.Video.Thumbnails.MINI_KIND;

public class Utils {

    private static final String TAG = "Letter";
    private static final int OPTIONS_NONE = 0x0;
    private static final int OPTIONS_RECYCLE_INPUT = 0x2;

    public static File convertIconThumb(Context context, String fileAbs) {
        String md5 = md5(fileAbs) + ".png";
        File file = new File(context.getCacheDir(), md5);
        if (file.exists() && file.isFile()) {
            return file;
        }
        Drawable drawable = getApkIcon(context, fileAbs);
        if (drawable != null) {
            bitmap2Img(file, drawable2Bitmap(drawable), true);
        }
        return file;
    }

    public static File convertImgThumb(Context context, String fileAbs) {
        String md5 = md5(fileAbs) + ".jpeg";
        File file = new File(context.getCacheDir(), md5);
        if (file.exists() && file.isFile()) {
            return file;
        }
        Bitmap thumbnail = extractThumbnail(BitmapFactory.decodeFile(fileAbs), 60, 60);
        bitmap2Img(file, thumbnail, false);
        return file;
    }

    private static Bitmap extractThumbnail(Bitmap source, int width, int height) {
        if (source == null) {
            return null;
        }

        float scale;
        if (source.getWidth() < source.getHeight()) {
            scale = width / (float) source.getWidth();
        } else {
            scale = height / (float) source.getHeight();
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        return transform(matrix, source, width, height, OPTIONS_NONE);
    }

    /**
     * Transform source Bitmap to targeted width and height.
     */
    private static Bitmap transform(Matrix scaler,
                                    Bitmap source,
                                    int targetWidth,
                                    int targetHeight,
                                    int options) {
        boolean scaleUp = (options & OPTIONS_NONE) != 0;
        boolean recycle = (options & OPTIONS_RECYCLE_INPUT) != 0;

        int deltaX = source.getWidth() - targetWidth;
        int deltaY = source.getHeight() - targetHeight;
        if (!scaleUp && (deltaX < 0 || deltaY < 0)) {
            /*
             * In this case the bitmap is smaller, at least in one dimension,
             * than the target.  Transform it by placing as much of the image
             * as possible into the target and leaving the top/bottom or
             * left/right (or both) black.
             */
            Bitmap b2 = Bitmap.createBitmap(targetWidth, targetHeight,
                    Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b2);

            int deltaXHalf = Math.max(0, deltaX / 2);
            int deltaYHalf = Math.max(0, deltaY / 2);
            Rect src = new Rect(
                    deltaXHalf,
                    deltaYHalf,
                    deltaXHalf + Math.min(targetWidth, source.getWidth()),
                    deltaYHalf + Math.min(targetHeight, source.getHeight()));
            int dstX = (targetWidth - src.width()) / 2;
            int dstY = (targetHeight - src.height()) / 2;
            Rect dst = new Rect(
                    dstX,
                    dstY,
                    targetWidth - dstX,
                    targetHeight - dstY);
            c.drawBitmap(source, src, dst, null);
            if (recycle) {
                source.recycle();
            }
            c.setBitmap(null);
            return b2;
        }
        float bitmapWidthF = source.getWidth();
        float bitmapHeightF = source.getHeight();

        float bitmapAspect = bitmapWidthF / bitmapHeightF;
        float viewAspect = (float) targetWidth / targetHeight;

        if (bitmapAspect > viewAspect) {
            float scale = targetHeight / bitmapHeightF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        } else {
            float scale = targetWidth / bitmapWidthF;
            if (scale < .9F || scale > 1F) {
                scaler.setScale(scale, scale);
            } else {
                scaler = null;
            }
        }

        Bitmap b1;
        if (scaler != null) {
            // this is used for minithumb and crop, so we want to filter here.
            b1 = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(), source.getHeight(), scaler, true);
        } else {
            b1 = source;
        }

        if (recycle && b1 != source) {
            source.recycle();
        }

        int dx1 = Math.max(0, b1.getWidth() - targetWidth);
        int dy1 = Math.max(0, b1.getHeight() - targetHeight);

        Bitmap b2 = Bitmap.createBitmap(
                b1,
                dx1 / 2,
                dy1 / 2,
                targetWidth,
                targetHeight);

        if (b2 != b1) {
            if (recycle || b1 != source) {
                b1.recycle();
            }
        }

        return b2;
    }

    public static File convertVideoThumb(Context context, String fileAbs) {
        String md5 = md5(fileAbs) + ".jpeg";
        File file = new File(context.getCacheDir(), md5);
        if (file.exists() && file.isFile()) {
            return file;
        }
        Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(fileAbs, MINI_KIND);
        bitmap2Img(file, thumbnail, false);
        return file;
    }

    private static Bitmap drawable2Bitmap(Drawable drawable) {
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        Bitmap bitmap = Bitmap.createBitmap(w, h, config);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }

    private static void bitmap2Img(File file, Bitmap bmp, boolean apk) {
        FileOutputStream fos = null;
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            //压缩质量
            bmp.compress(apk ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 50 /*ignored for PNG*/, bos);
            fos = new FileOutputStream(file);
            fos.write(bos.toByteArray());
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "bitmap2File Exception " + file.getPath(), e);
            if (file.exists()) {
                boolean delete = file.delete();
                Log.e(TAG, "error! so delete :" + delete);
            }
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    Log.e(TAG, "bitmap2File bos IOException", e);
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "bitmap2File fos IOException", e);
                }
            }
            if (bmp != null)
                bmp.recycle();
        }
    }

    private static Drawable getApkIcon(Context context, String absPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(absPath, 0);

            // the secret are these two lines....
            pi.applicationInfo.sourceDir = absPath;
            pi.applicationInfo.publicSourceDir = absPath;
            return pi.applicationInfo.loadIcon(pm);
        } catch (Exception e) {
            return null;
        }
    }

    private static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 通过mac地址数字累加得出
     *
     * @return ...
     */
    public static int getPort(String mac) {
        if (null == mac) {
            throw new RuntimeException("mac = null !!!");
        }
        mac = mac.toUpperCase();
        String[] macs = mac.split("\\:");
        if (macs.length <= 0) {
            throw new RuntimeException("not mac !!!");
        }
        int port = 0;
        for (String s : macs) {
            if (null == s) {
                continue;
            }
            port = port + Integer.parseInt(s, 16); //16转10
        }
        return port + 8000;
    }

    public static String getApkDirForPkg(Context context, String pkgName) {
        try {
            return context.getPackageManager().getApplicationInfo(pkgName, 0).sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static String getAppNameForPkg(Context context, String pkgName) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * 获取apk文件的名称
     */
    public static String getAppNameForDir(Context context, String absPath) {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(absPath, 0);
        if (null != pi) {
            // the secret are these two lines....
            pi.applicationInfo.sourceDir = absPath;
            pi.applicationInfo.publicSourceDir = absPath;

            // Drawable ApkIcon = pi.applicationInfo.loadIcon(pm);
            return (String) pi.applicationInfo.loadLabel(pm);
        }
        return null;
    }

    public static String getMimeType(String filePath) {
        try {
            filePath = URLEncoder.encode(filePath.trim().replaceAll(" ", ""), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String ext = MimeTypeMap.getFileExtensionFromUrl(filePath);
        if (ext != null) {
            ext = ext.toLowerCase();
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }

    public static <T> List<List<T>> averageAssign(List<T> source, int amount) {

        List<List<T>> result = new ArrayList<>();

        int batch = source.size() / amount;

        for (int i = 0; i < (batch + 1); i++) {
            // 开始位置
            int x = i * amount;
            // 结束位置
            int y = (i + 1) * amount;

            int end = y < source.size() ? y : source.size();

            result.add(source.subList(x, end));
        }
        return result;
    }

    public static long getAvailableStorage() {
        return Environment.getExternalStorageDirectory().getUsableSpace();
    }
}
