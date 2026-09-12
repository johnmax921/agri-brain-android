package tw.farmers.agribrain;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int REQUEST_FILE_CHOOSER = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全螢幕沉浸式（隱藏狀態列與導航列）
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        setContentView(R.layout.activity_main);

        // 啟動時申請所有必要權限
        requestAllPermissions();

        // 初始化 WebView
        setupWebView();
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();

        // 啟用 JavaScript
        settings.setJavaScriptEnabled(true);
        // 啟用 DOM Storage（API Key 本地儲存）
        settings.setDomStorageEnabled(true);
        // 啟用資料庫
        settings.setDatabaseEnabled(true);
        // 允許存取 file:// 路徑
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // 支援縮放
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        // 媒體自動播放
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        // 混合內容（允許 HTTPS 頁面呼叫 API）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // RWD 響應式視口
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // WebViewClient：讓所有連結在 APP 內開啟
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Groq API 呼叫讓 WebView 自己處理
                return false;
            }
        });

        // WebChromeClient：處理相機與相簿選取（核心！）
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePath,
                    FileChooserParams fileChooserParams) {

                // 清除前一次未完成的回呼
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = filePath;

                // 建立相機拍攝 Intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "無法建立相片檔案", Toast.LENGTH_SHORT).show();
                }

                if (photoFile != null) {
                    cameraImageUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        "tw.farmers.agribrain.fileprovider",
                        photoFile
                    );
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                }

                // 建立相簿選取 Intent
                Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
                galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
                galleryIntent.setType("image/*");

                // 組合兩個 Intent（讓使用者選擇相機或相簿）
                Intent chooserIntent = Intent.createChooser(galleryIntent, "選擇作物病徵照片");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                    new Intent[]{cameraIntent});

                startActivityForResult(chooserIntent, REQUEST_FILE_CHOOSER);
                return true;
            }
        });

        // 載入內嵌於 assets 的 HTML（離線瞬間開啟）
        webView.loadUrl("file:///android_asset/index.html");
    }

    private File createImageFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        String imageFileName = "AGRI_" + timestamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_FILE_CHOOSER) return;

        if (filePathCallback == null) return;

        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                // 使用者選了相機拍攝
                if (cameraImageUri != null) {
                    results = new Uri[]{cameraImageUri};
                }
            } else {
                // 使用者選了相簿圖片
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private void requestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // 相機
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // 相簿讀取（Android 版本分支）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toArray(new String[0]),
                REQUEST_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 權限取得後重新載入（確保相機功能正常）
        if (requestCode == REQUEST_PERMISSIONS && webView != null) {
            webView.reload();
        }
    }

    // 返回鍵：優先在 WebView 內上一頁
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
