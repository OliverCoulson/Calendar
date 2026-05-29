package org.duiduidui.calendar.voice.aliyun;

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
 * 文档：https://help.aliyun.com/document_detail/324205.html
 *
 * 流程：
 *   1. 用 AccessKey 换取临时 Token（或使用长期 Token）
 *   2. 将 PCM 音频 POST 至 ASR 网关
 *   3. 解析返回的 JSON 获取识别文本
 */
public class AliyunASRClient {

    private static final String DEFAULT_GATEWAY = "nls-gateway.cn-shanghai.aliyuncs.com";

    private final String appKey;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String gateway;

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
     *
     * @param audioData 16kHz 16bit 单声道 PCM 音频
     * @return 识别文本
     * @throws VoiceException 识别失败时抛出
     */
    public String recognize(byte[] audioData) throws VoiceException {
        String token = acquireToken();

        String url = String.format(
                "https://%s/stream/v1/asr?" +
                "appkey=%s&format=pcm&sample_rate=16000&enable_punctuation_prediction=true",
                gateway, appKey
        );

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(URI.create(url));
            httpPost.setHeader("X-NLS-Token", token);
            httpPost.setHeader("Content-Type", "application/octet-stream");
            httpPost.setEntity(new ByteArrayEntity(audioData, ContentType.APPLICATION_OCTET_STREAM));

            String body = client.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode == 401) {
                    throw new ApiException("API 密钥无效或已过期");
                }
                if (statusCode >= 500) {
                    throw new ApiException("阿里云服务异常, status=" + statusCode);
                }
                return EntityUtils.toString(response.getEntity());
            });

            // 解析响应 JSON
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
     * 获取阿里云临时 Token。
     * 简化实现：直接使用 appKey，生产环境建议通过 STS 或 Token API 获取。
     */
    private String acquireToken() {
        return this.appKey;
    }

    /** 内部异常，用于在 lambda 中传递错误信息。 */
    private static class ApiException extends RuntimeException {
        ApiException(String message) {
            super(message);
        }
    }
}
