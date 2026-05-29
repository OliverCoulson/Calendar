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
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.duiduidui.calendar.model.VoiceErrorType;
import org.duiduidui.calendar.model.VoiceException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

/**
 * 阿里云智能语音交互 REST API 客户端。
 *
 * Token 通过阿里云 SDK（CommonRequest + HMAC-SHA1 签名）获取，
 * 识别请求通过 REST API 发送 PCM 音频。
 */
public class AliyunASRClient {

    private static final String DEFAULT_GATEWAY = "nls-gateway.cn-shanghai.aliyuncs.com";

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String gateway;

    private String cachedToken;
    private long tokenExpireAt;

    public AliyunASRClient(String appKey, String accessKeyId, String accessKeySecret) {
        this(appKey, accessKeyId, accessKeySecret, DEFAULT_GATEWAY);
    }

    public AliyunASRClient(String appKey, String accessKeyId, String accessKeySecret, String gateway) {
        this.appKey = appKey;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.gateway = gateway;
    }

    /**
     * 将 PCM 音频数据发送至阿里云 ASR 网关并返回识别文本。
     */
    public String recognize(byte[] audioData) throws VoiceException {
        String token = resolveToken();

        String url = String.format(
                "https://%s/stream/v1/asr?" +
                "appkey=%s&format=pcm&sample_rate=16000&enable_punctuation_prediction=true",
                gateway, appKey
        );

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(URI.create(url));
            if (token != null) {
                httpPost.setHeader("X-NLS-Token", token);
            }
            httpPost.setHeader("Content-Type", "application/octet-stream");
            httpPost.setEntity(new ByteArrayEntity(audioData, ContentType.APPLICATION_OCTET_STREAM));

            String body = client.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode == 401 || statusCode == 403) {
                    throw new ApiException("认证失败: " + EntityUtils.toString(response.getEntity()));
                }
                if (statusCode >= 500) {
                    throw new ApiException("阿里云服务异常, status=" + statusCode);
                }
                return EntityUtils.toString(response.getEntity());
            });

            JSONObject json = new JSONObject(body);
            int status = json.optInt("status", -1);
            if (status != 20000000) {
                throw new VoiceException(VoiceErrorType.UNKNOWN,
                        "识别失败, 错误码=" + status + ", " + json.optString("message", ""));
            }

            String result = json.optString("result", "").trim();
            if (result.isEmpty()) {
                throw new VoiceException(VoiceErrorType.LOW_CONFIDENCE, "识别结果为空");
            }
            return result;

        } catch (ApiException e) {
            throw new VoiceException(VoiceErrorType.INVALID_KEY, e.getMessage());
        } catch (IOException e) {
            throw new VoiceException(VoiceErrorType.NETWORK_TIMEOUT, "网络请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Token。
     * 使用阿里云 SDK（HMAC-SHA1 签名）调用 CreateToken。
     */
    private String resolveToken() throws VoiceException {
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

            if (response.getHttpStatus() != 200) {
                throw new VoiceException(VoiceErrorType.INVALID_KEY,
                        "Token 服务返回 status=" + response.getHttpStatus() + ": " + response.getData());
            }

            JSONObject json = new JSONObject(response.getData());
            JSONObject tokenObj = json.optJSONObject("Token");
            if (tokenObj == null) {
                throw new VoiceException(VoiceErrorType.INVALID_KEY,
                        "Token 响应格式异常: " + response.getData());
            }

            cachedToken = tokenObj.getString("Id");
            tokenExpireAt = now + 86400; // 默认 24h
            System.out.println("[Aliyun] Token 获取成功");
            return cachedToken;

        } catch (ClientException e) {
            throw new VoiceException(VoiceErrorType.NETWORK_TIMEOUT,
                    "Token 获取失败: " + e.getErrCode() + " - " + e.getMessage(), e);
        }
    }

    private static class ApiException extends RuntimeException {
        ApiException(String message) { super(message); }
    }
}
