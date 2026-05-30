package org.duiduidui.calendar.voice.aliyun;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

/**
 * 阿里云语音合成（TTS）REST API 客户端。
 *
 * 文档：https://help.aliyun.com/document_detail/324205.html
 *
 * 流程：
 *   1. 获取 Token（复用 ASR 相同的认证方式）
 *   2. 将文本 POST 至 TTS 网关
 *   3. 返回 PCM 音频字节数组（16kHz 16bit 单声道）
 */
public class AliyunTTSClient {

    private static final String DEFAULT_GATEWAY = "nls-gateway.cn-shanghai.aliyuncs.com";

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String gateway;

    private String cachedToken;
    private long tokenExpireAt;

    public AliyunTTSClient(String appKey, String accessKeyId, String accessKeySecret) {
        this(appKey, accessKeyId, accessKeySecret, DEFAULT_GATEWAY);
    }

    public AliyunTTSClient(String appKey, String accessKeyId, String accessKeySecret, String gateway) {
        this.appKey = appKey;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.gateway = gateway;
    }

    /**
     * 将文本合成为 PCM 音频。
     *
     * @param text 要朗读的文本
     * @return PCM 音频字节数组（16kHz 16bit 单声道）
     */
    public byte[] synthesize(String text) throws IOException {
        String token = acquireToken();

        String url = String.format("https://%s/stream/v1/tts", gateway);

        JSONObject body = new JSONObject();
        body.put("appkey", appKey);
        body.put("text", text);
        body.put("format", "pcm");
        body.put("sample_rate", 16000);
        body.put("voice", "xiaoyun");

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(URI.create(url));
            httpPost.setHeader("X-NLS-Token", token);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));

            return client.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode == 401 || statusCode == 403) {
                    throw new IOException("TTS 认证失败");
                }
                if (statusCode != 200) {
                    String errBody = EntityUtils.toString(response.getEntity());
                    throw new IOException("TTS 请求失败, status=" + statusCode + ", body=" + errBody);
                }
                return EntityUtils.toByteArray(response.getEntity());
            });
        }
    }

    private String acquireToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < tokenExpireAt - 300) {
            return cachedToken;
        }

        try {
            DefaultProfile profile = DefaultProfile.getProfile("cn-shanghai", accessKeyId, accessKeySecret);
            IAcsClient client = new DefaultAcsClient(profile);

            CommonRequest request = new CommonRequest();
            request.setSysDomain("nls-meta.cn-shanghai.aliyuncs.com");
            request.setSysVersion("2019-02-28");
            request.setSysAction("CreateToken");
            request.setSysMethod(MethodType.POST);

            CommonResponse response = client.getCommonResponse(request);
            JSONObject json = new JSONObject(response.getData());
            JSONObject tokenObj = json.optJSONObject("Token");
            if (tokenObj != null) {
                cachedToken = tokenObj.getString("Id");
                tokenExpireAt = now + 86400;
            }
        } catch (ClientException ignored) {
        }

        if (cachedToken == null) cachedToken = appKey;
        return cachedToken;
    }
}
