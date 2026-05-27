package com.lw.ai.glasses.douyinapi;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.bytedance.sdk.open.aweme.CommonConstants;
import com.bytedance.sdk.open.aweme.authorize.model.Authorization;
import com.bytedance.sdk.open.aweme.common.handler.IApiEventHandler;
import com.bytedance.sdk.open.aweme.common.model.BaseReq;
import com.bytedance.sdk.open.aweme.common.model.BaseResp;
import com.bytedance.sdk.open.aweme.share.Share;
import com.bytedance.sdk.open.douyin.DouYinOpenApiFactory;
import com.bytedance.sdk.open.douyin.api.DouYinOpenApi;
import com.lw.ai.glasses.R;
import com.lw.ai.glasses.ui.MainActivity;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 主要功能：接受授权返回结果的activity
 * <p>
 * <p>
 * 也可通过request.callerLocalEntry = "com.xxx.xxx...activity"; 定义自己的回调类
 */
public class DouYinEntryActivity extends Activity implements IApiEventHandler {

    private static final String TAG = "DouYinEntryActivity";
    private static final String ACCESS_TOKEN_URL = "https://open.douyin.com/oauth/access_token/";
    private static final String CLIENT_KEY = "xxx";
    private static final String CLIENT_SECRET = "xxx";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    DouYinOpenApi douYinOpenApi;
    EditText etCode;
    Button btnCopy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.public_layout_auth);
        etCode = findViewById(R.id.etCode);
        btnCopy = findViewById(R.id.btnCopy);
        douYinOpenApi = DouYinOpenApiFactory.create(this);
        douYinOpenApi.handleIntent(getIntent(), this);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyEditTextContentToClipboard(DouYinEntryActivity.this,etCode);
            }
        });
    }

    @Override
    public void onReq(BaseReq req) {
        Log.d("ly", "-------授权结果--------");
    }

    @Override
    public void onResp(BaseResp resp) {
        Log.d("ly", "-------授权结果--------");
        if (resp.getType() == CommonConstants.ModeType.SHARE_CONTENT_TO_TT_RESP) {
            Share.Response response = (Share.Response) resp;
            Toast.makeText(
                    this,
                    getString(R.string.douyin_auth_response, response.errorCode, response.errorMsg),
                    Toast.LENGTH_SHORT
            ).show();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else if (resp.getType() == CommonConstants.ModeType.SEND_AUTH_RESPONSE) {
            Authorization.Response response = (Authorization.Response) resp;
            Log.d("ly", "-------授权结果--------"+resp.isSuccess()+", code:"+response.authCode);
            etCode.setText(response.authCode);
            if (resp.isSuccess()) {
                Toast.makeText(this, getString(R.string.douyin_auth_success, response.grantedPermissions),
                        Toast.LENGTH_LONG).show();
                requestAccessToken(response.authCode);
            } else {
                finish();
            }
        }

    }

    @Override
    public void onErrorIntent(Intent intent) {
        // 错误数据
        Toast.makeText(this, getString(R.string.intent_error), Toast.LENGTH_LONG).show();
    }

    private void requestAccessToken(String authCode) {
        FormBody body = new FormBody.Builder()
                .add("client_key", CLIENT_KEY)
                .add("client_secret", CLIENT_SECRET)
                .add("code", authCode)
                .add("grant_type", "authorization_code")
                .build();
        Request request = new Request.Builder()
                .url(ACCESS_TOKEN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build();

        HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request access token failed", e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DouYinEntryActivity.this, getString(R.string.access_token_failed), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "access_token response: " + responseBody);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });
    }

    /**
     * 将EditText的内容复制到剪贴板
     * @param context 上下文
     * @param editText 目标EditText
     */
    public static void copyEditTextContentToClipboard(Context context, EditText editText) {
        // 步骤1：获取EditText文本内容并判空
        String content = editText.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        // 步骤2：获取剪贴板服务
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        // 步骤3：创建ClipData
        ClipData clipData = ClipData.newPlainText("EditTextContent", content);

        // 步骤4：设置到剪贴板
        clipboardManager.setPrimaryClip(clipData);

        // 提示用户
        Toast.makeText(context, context.getString(R.string.clipboard_copied), Toast.LENGTH_SHORT).show();
    }
}
