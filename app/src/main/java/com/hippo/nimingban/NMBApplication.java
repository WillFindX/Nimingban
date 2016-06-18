/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.nimingban;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.support.annotation.NonNull;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.hippo.conaco.Conaco;
import com.hippo.drawable.ImageWrapper;
import com.hippo.nimingban.client.NMBClient;
import com.hippo.nimingban.client.NMBDns;
import com.hippo.nimingban.client.NMBRequest;
import com.hippo.nimingban.client.ac.data.ACCdnPath;
import com.hippo.nimingban.client.data.ACSite;
import com.hippo.nimingban.network.HttpCookieDB;
import com.hippo.nimingban.network.HttpCookieWithId;
import com.hippo.nimingban.network.SimpleCookieStore;
import com.hippo.nimingban.util.BitmapUtils;
import com.hippo.nimingban.util.Crash;
import com.hippo.nimingban.util.DB;
import com.hippo.nimingban.util.ReadableTime;
import com.hippo.nimingban.util.ResImageGetter;
import com.hippo.nimingban.util.Settings;
import com.hippo.nimingban.widget.ImageWrapperHelper;
import com.hippo.okhttp.CookieDBJar;
import com.hippo.util.NetworkUtils;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.Messenger;
import com.hippo.yorozuya.Say;
import com.hippo.yorozuya.SimpleHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class NMBApplication extends Application
        implements Thread.UncaughtExceptionHandler, Messenger.Receiver, Runnable {

    private static final String TAG = NMBApplication.class.getSimpleName();

    private static final boolean LOG_NATIVE_MEMORY = false;

    private static final String AC_CDN_PATH_FILENAME = "ac_cdn_path";

    private Thread.UncaughtExceptionHandler mDefaultHandler;

    private SimpleCookieStore mSimpleCookieStore;
    private NMBClient mNMBClient;
    private Conaco<ImageWrapper> mConaco;
    private ImageWrapperHelper mImageWrapperHelper;
    private OkHttpClient mOkHttpClient;

    private boolean mConnectedWifi;

    @Override
    public void onCreate() {
        super.onCreate();

        // Prepare to crash
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        NMBAppConfig.initialize(this);
        File logFile = NMBAppConfig.getFileInAppDir("nimingban.log");
        if (logFile != null) {
            Say.initSayFile(logFile);
        }
        Settings.initialize(this);
        DB.initialize(this);
        HttpCookieDB.initialize(this);
        ReadableTime.initialize(this);
        BitmapUtils.initialize(this);
        ResImageGetter.initialize(this);
        Emoji.initialize(this);

        // Remove temp file
        FileUtils.deleteContent(NMBAppConfig.getTempDir());

        updateNetworkState(this);

        // Theme
        setTheme(Settings.getDarkTheme() ? R.style.AppTheme_Dark : R.style.AppTheme);

        Messenger.getInstance().register(Constants.MESSENGER_ID_CHANGE_THEME, this);

        try {
            update();
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore
        }

        if (LOG_NATIVE_MEMORY) {
            SimpleHandler.getInstance().post(this);
        }

        start();
    }

    private void start() {
        updateACCdnPath();
    }

    private void readACCdnPathFromFile() {
        File file = new File(getFilesDir(), AC_CDN_PATH_FILENAME);
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            String str = IOUtils.readString(is, "utf-8");
            List<ACCdnPath> list = JSON.parseArray(str, ACCdnPath.class);
            ACSite.getInstance().setCdnPath(list);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private void writeACCdnPathToFile(List<ACCdnPath> cdnPaths) {
        File file = new File(getFilesDir(), AC_CDN_PATH_FILENAME);
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(JSON.toJSONString(cdnPaths).getBytes("utf-8"));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private void updateACCdnPath() {
        // First read cdn path from file
        readACCdnPathFromFile();

        NMBRequest request = new NMBRequest();
        request.setSite(ACSite.getInstance());
        request.setMethod(NMBClient.METHOD_GET_CDN_PATH);
        request.setCallback(new NMBClient.Callback<List<ACCdnPath>>(){
            @Override
            public void onSuccess(List<ACCdnPath> result) {
                ACSite.getInstance().setCdnPath(result);
                writeACCdnPathToFile(result);
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onCancel() {
            }
        });
        getNMBClient(this).execute(request);
    }

    private void update() throws PackageManager.NameNotFoundException {
        int oldVersionCode = Settings.getVersionCode();
        PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
        Settings.putVersionCode(pi.versionCode);

        if (oldVersionCode < 6) {
            updateCookies(this);
        }

        if (oldVersionCode < 14) {
            Settings.putGuideListActivity(true);
        }

        if (oldVersionCode < 42) {
            Settings.putSetAnalysis(false);
            Settings.putAnalysis(false);
        }
    }

    public static void updateCookies(Context context) {
        SimpleCookieStore cookieStore = NMBApplication.getSimpleCookieStore(context);

        URL url = ACSite.getInstance().getSiteUrl();
        HttpCookieWithId hcwi = cookieStore.getCookie(url, "userId");
        if (hcwi != null) {
            HttpCookie oldCookie = hcwi.httpCookie;
            cookieStore.remove(url, oldCookie);

            HttpCookie newCookie = new HttpCookie("userhash", oldCookie.getValue());
            newCookie.setComment(oldCookie.getComment());
            newCookie.setCommentURL(oldCookie.getCommentURL());
            newCookie.setDiscard(oldCookie.getDiscard());
            newCookie.setDomain(oldCookie.getDomain());
            newCookie.setMaxAge(oldCookie.getMaxAge());
            newCookie.setPath(oldCookie.getPath());
            newCookie.setPortlist(oldCookie.getPortlist());
            newCookie.setSecure(oldCookie.getSecure());
            newCookie.setVersion(oldCookie.getVersion());

            cookieStore.add(url, newCookie);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (level ==  TRIM_MEMORY_BACKGROUND ) {
            if (mConaco != null) {
                mConaco.clearMemoryCache();
            }
        }
    }

    public static void updateNetworkState(Context context) {
        ((NMBApplication) context.getApplicationContext()).mConnectedWifi =
                NetworkUtils.isConnectedWifi(context);
    }

    public static boolean isConnectedWifi(Context context) {
        return ((NMBApplication) context.getApplicationContext()).mConnectedWifi;
    }

    public static SimpleCookieStore getSimpleCookieStore(@NonNull Context context) {
        NMBApplication application = ((NMBApplication) context.getApplicationContext());
        if (application.mSimpleCookieStore == null) {
            application.mSimpleCookieStore = new SimpleCookieStore();
        }
        return application.mSimpleCookieStore;
    }

    @NonNull
    public static NMBClient getNMBClient(@NonNull Context context) {
        NMBApplication application = ((NMBApplication) context.getApplicationContext());
        if (application.mNMBClient == null) {
            application.mNMBClient = new NMBClient(application);
        }
        return application.mNMBClient;
    }

    private static int getMemoryCacheMaxSize(Context context) {
        final ActivityManager activityManager = (ActivityManager) context.
                getSystemService(Context.ACTIVITY_SERVICE);
        return Math.min(20 * 1024 * 1024,
                Math.round(0.2f * activityManager.getMemoryClass() * 1024 * 1024));
    }

    @NonNull
    public static Conaco<ImageWrapper> getConaco(@NonNull Context context) {
        NMBApplication application = ((NMBApplication) context.getApplicationContext());
        if (application.mConaco == null) {
            Conaco.Builder<ImageWrapper> builder = new Conaco.Builder<>();
            builder.hasMemoryCache = true;
            builder.memoryCacheMaxSize = getMemoryCacheMaxSize(context);
            builder.hasDiskCache = true;
            builder.diskCacheDir = new File(context.getCacheDir(), "thumb");
            builder.diskCacheMaxSize = 80 * 1024 * 1024; // 80MB
            builder.okHttpClient = getOkHttpClient(context);
            builder.objectHelper = getImageWrapperHelper(context);
            application.mConaco = builder.build();
        }
        return application.mConaco;
    }

    @NonNull
    public static ImageWrapperHelper getImageWrapperHelper(@NonNull Context context) {
        NMBApplication application = ((NMBApplication) context.getApplicationContext());
        if (application.mImageWrapperHelper == null) {
            application.mImageWrapperHelper = new ImageWrapperHelper();
        }
        return application.mImageWrapperHelper;
    }

    public static OkHttpClient getOkHttpClient(@NonNull Context context) {
        NMBApplication application = ((NMBApplication) context.getApplicationContext());
        if (application.mOkHttpClient == null) {
            application.mOkHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .dns(new NMBDns())
                    .cookieJar(new CookieDBJar(getSimpleCookieStore(context)))
                    .build();
        }
        return application.mOkHttpClient;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(ex) && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, ex);
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    private boolean handleException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        try {
            ex.printStackTrace();
            Crash.saveCrashInfo2File(this, ex);
            return true;
        } catch (Throwable tr) {
            return false;
        }
    }

    @Override
    public void onReceive(int id, Object obj) {
        setTheme((Boolean) obj ? R.style.AppTheme_Dark : R.style.AppTheme);
    }

    @Override
    public void run() {
        Log.i(TAG, "Native " + FileUtils.humanReadableByteCount(Debug.getNativeHeapAllocatedSize(), false));
        SimpleHandler.getInstance().postDelayed(this, 3000);
    }
}
