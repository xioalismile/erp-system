package com.erp.ai.controller;

import com.erp.common.Result;
import com.erp.ai.util.TtsTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 小楷语音合成 TTS 接口。
 *
 * 优先走本地 Piper 离线引擎（HTTP 127.0.0.1:6007），
 * 失败返回 500，由前端降级为浏览器内置语音。
 *
 * 语音列表：huayan（中文自然女声）、系统回落到本机内置音色。
 *
 * 输出格式（2026-10-24 新增）：Piper 只产出 WAV。手机端小程序的音频播放器
 * 对本地 WAV 的兼容性因机型/基础库差异很大（常报播放失败），而 MP3 是全平台
 * 最稳定的格式。因此本接口支持 format=mp3：用服务器上的 ffmpeg 把 WAV 转成 MP3。
 * 默认仍返回 WAV，电脑端行为完全不变。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/tts")
@RequiredArgsConstructor
public class AiTtsController {

    private static final String PIPER_BASE = "http://127.0.0.1:6007";
    private static final long TIMEOUT_MS = 15000;

    /** lame 可执行文件位置，可用环境变量 TTS_LAME 覆盖 */
    private static final String LAME_EXE = System.getenv().getOrDefault(
            "TTS_LAME", "C:\\smart-erp-asr\\lame\\lame.exe");

    /** 语音服务输出格式：22050Hz / 16bit / 单声道（与其 SetOutputToWaveStream 一致） */
    private static final int PCM_SAMPLE_RATE = 22050;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 合成结果缓存（2026-10-25 唤醒应答提速配套）：
     * 「我在，请说。」「小楷没有听清楚，请再说一次。」这类固定短句会被反复合成，
     * 每次都跑一遍 Piper + lame 转码没有必要。按 文本+音色+格式 命中缓存直接返回。
     * 容量有上限（200 条 / 32MB），超出按最久未使用淘汰，不会无限占内存。
     */
    private static final int TTS_CACHE_MAX_ENTRIES = 200;
    private static final long TTS_CACHE_MAX_BYTES = 32L * 1024 * 1024;
    private final Map<String, byte[]> ttsCache = new LinkedHashMap<>(32, 0.75f, true);
    private long ttsCacheBytes = 0L;

    private byte[] cacheGet(String key) {
        synchronized (ttsCache) {
            return ttsCache.get(key);
        }
    }

    private void cachePut(String key, byte[] data) {
        if (data == null || data.length == 0) return;
        synchronized (ttsCache) {
            if (ttsCache.containsKey(key)) return;
            ttsCache.put(key, data);
            ttsCacheBytes += data.length;
            Iterator<Map.Entry<String, byte[]>> it = ttsCache.entrySet().iterator();
            while (it.hasNext() && (ttsCache.size() > TTS_CACHE_MAX_ENTRIES || ttsCacheBytes > TTS_CACHE_MAX_BYTES)) {
                Map.Entry<String, byte[]> eldest = it.next();
                if (eldest.getKey().equals(key)) continue;
                ttsCacheBytes -= eldest.getValue().length;
                it.remove();
            }
        }
    }

    /**
     * 返回支持的语音列表（前端选择下拉会用到）。
     */
    @GetMapping("/voices")
    public Result<Map<String, Object>> voices() {
        return Result.success(Map.of(
                "default", "huayan",
                "voices", new String[]{"huayan"}
        ));
    }

    /**
     * 合成语音，返回音频二进制。
     * 入参 text 必填（<= 300 字），voiceName 默认 "huayan"，format 可选 wav（默认）/ mp3。
     */
    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        text = text.trim();
        // 2026-10-25：数字、英文单号、多音词先转成中文读法，否则自然女声会跳字/读错
        text = TtsTextNormalizer.normalize(text);
        if (text == null || text.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (text.length() > 300) text = text.substring(0, 300);

        String format = body.getOrDefault("format", "wav").trim().toLowerCase();
        boolean wantMp3 = "mp3".equals(format) || "mpeg".equals(format);
        String voice = body.getOrDefault("voiceName", "huayan");

        String cacheKey = voice + '|' + (wantMp3 ? "mp3" : "wav") + '|' + text;
        byte[] cached = cacheGet(cacheKey);
        if (cached != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(wantMp3 ? "audio/mpeg" : "audio/wav"))
                    .contentLength(cached.length)
                    .body(cached);
        }

        try {
            Map<String, String> payload = Map.of("text", text, "voice", voice);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    URI.create(PIPER_BASE + "/synthesize"),
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    byte[].class
            );

            byte[] wav = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || wav == null || wav.length == 0) {
                log.warn("Piper returned empty body for text len={}", text.length());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            // 防御：语音服务历史上曾返回"裸 PCM（无 RIFF 头）"，任何播放器都无法识别。
            // 这里检测并补标准 WAV 头，保证后续无论服务端版本如何都能播。
            wav = ensureWavHeader(wav);

            if (wantMp3) {
                byte[] mp3 = wavToMp3(wav);
                if (mp3 != null && mp3.length > 0) {
                    cachePut(cacheKey, mp3);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("audio/mpeg"))
                            .contentLength(mp3.length)
                            .body(mp3);
                }
                log.warn("lame mp3 transcode unavailable, fallback to wav");
            }

            cachePut(cacheKey, wav);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .contentLength(wav.length)
                    .body(wav);
        } catch (Exception e) {
            log.warn("Piper synthesize failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * 若音频缺少 RIFF 头（裸 PCM），补一个 44 字节标准 WAV 头；已是 WAV 则原样返回。
     */
    private byte[] ensureWavHeader(byte[] data) {
        if (data.length > 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') {
            return data;
        }
        byte[] out = new byte[44 + data.length];
        writeAscii(out, 0, "RIFF");
        writeInt(out, 4, out.length - 8);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);
        writeShort(out, 22, 1);
        writeInt(out, 24, PCM_SAMPLE_RATE);
        writeInt(out, 28, PCM_SAMPLE_RATE * 2);
        writeShort(out, 32, 2);
        writeShort(out, 34, 16);
        writeAscii(out, 36, "data");
        writeInt(out, 40, data.length);
        System.arraycopy(data, 0, out, 44, data.length);
        return out;
    }

    private static void writeAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
        b[off + 2] = (byte) ((v >>> 16) & 0xFF);
        b[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static void writeShort(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }

    /**
     * WAV 转 MP3（服务器本地 lame）。返回 null 表示转码不可用，由调用方降级回 WAV。
     */
    private byte[] wavToMp3(byte[] wav) {
        File in = null;
        File out = null;
        try {
            if (!new File(LAME_EXE).isFile()) {
                log.warn("lame not found: {}", LAME_EXE);
                return null;
            }
            in = File.createTempFile("xk_tts_", ".wav");
            out = File.createTempFile("xk_tts_", ".mp3");
            Files.write(in.toPath(), wav);

            ProcessBuilder pb = new ProcessBuilder(
                    LAME_EXE, "--silent", "-b", "64",
                    in.getAbsolutePath(), out.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.readAllBytes();
            }
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("lame transcode timeout");
                return null;
            }
            if (p.exitValue() != 0) {
                log.warn("lame transcode failed, exit={}", p.exitValue());
                return null;
            }
            byte[] mp3 = Files.readAllBytes(out.toPath());
            return mp3.length > 0 ? mp3 : null;
        } catch (Exception e) {
            log.warn("lame transcode error: {}", e.getMessage());
            return null;
        } finally {
            if (in != null) in.delete();
            if (out != null) out.delete();
        }
    }
}
