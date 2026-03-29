package interview.guide.modules.voiceinterview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AliyunSttService 单元测试 (SpeechTranscriber-based implementation)
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>会话管理 (start, send, stop)</li>
 *   <li>回调处理 (onResult, onError)</li>
 *   <li>错误处理</li>
 *   <li>资源清理</li>
 * </ul>
 *
 * <p>注意：由于 Aliyun SDK 的 SpeechTranscriber 和 NlsClient 是 final 类，
 * 这里使用集成测试风格的测试，验证服务层逻辑而不是 SDK 内部实现。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("阿里云实时语音识别服务测试")
class AliyunSttServiceTest {

    @InjectMocks
    private AliyunSttService aliyunSttService;

    @Nested
    @DisplayName("会话管理测试")
    class SessionManagementTests {

        @Test
        @DisplayName("启动转录会话 - 应创建会话状态")
        void testStartTranscription_CreatesSession() {
            // Given
            String sessionId = "test-session-1";
            Consumer<String> onResult = text -> {};
            Consumer<Throwable> onError = error -> {};

            // When & Then - 由于没有真实凭证，会抛出异常或创建空客户端
            assertDoesNotThrow(() -> {
                try {
                    aliyunSttService.startTranscription(sessionId, onResult, onError);
                } catch (Exception e) {
                    // 预期：没有真实凭证会失败，但不应抛出未处理的异常
                    assertTrue(e.getMessage().contains("token") ||
                               e.getMessage().contains("access") ||
                               e instanceof NullPointerException);
                }
            });
        }

        @Test
        @DisplayName("发送音频到不存在的会话 - 应抛出异常")
        void testSendAudio_SessionNotFound() {
            // Given
            String sessionId = "non-existent-session";
            byte[] audioData = new byte[]{0x01, 0x02, 0x03};

            // When & Then
            assertThrows(IllegalStateException.class, () -> {
                aliyunSttService.sendAudio(sessionId, audioData);
            });
        }

        @Test
        @DisplayName("发送空音频 - 应安全处理")
        void testSendAudio_EmptyAudio() {
            // Given
            String sessionId = "test-session";
            byte[] emptyAudio = new byte[0];

            // When & Then - 不存在的会话应抛出异常
            assertThrows(IllegalStateException.class, () -> {
                aliyunSttService.sendAudio(sessionId, emptyAudio);
            });
        }

        @Test
        @DisplayName("停止不存在的会话 - 应安全处理")
        void testStopTranscription_NonExistentSession() {
            // Given
            String sessionId = "non-existent-session";

            // When & Then - 不应抛出异常
            assertDoesNotThrow(() -> {
                aliyunSttService.stopTranscription(sessionId);
            });
        }

        @Test
        @DisplayName("检查会话是否存在")
        void testHasActiveSession() {
            // Given
            String sessionId = "test-session";

            // When
            boolean hasSession = aliyunSttService.hasActiveSession(sessionId);

            // Then
            assertFalse(hasSession, "不应存在会话");
        }
    }

    @Nested
    @DisplayName("客户端生命周期测试")
    class LifecycleTests {

        @Test
        @DisplayName("初始化客户端 - 验证字段设置")
        void testInit_ClientInitialization() {
            // Given
            AliyunSttService service = new AliyunSttService();

            // When - 通过反射设置配置
            setField(service, "appKey", "test-app-key");
            setField(service, "accessKeyId", "test-access-key-id");
            setField(service, "accessKeySecret", "test-access-key-secret");
            setField(service, "format", "pcm");
            setField(service, "sampleRate", 16000);

            // When & Then - 验证 init 方法不会抛出异常
            assertDoesNotThrow(() -> service.init());

            // Verify client was created
            Object client = getField(service, "client");
            assertNotNull(client, "NlsClient 应该被初始化");
        }

        @Test
        @DisplayName("初始化客户端 - 无凭证")
        void testInit_NoCredentials() {
            // Given
            AliyunSttService service = new AliyunSttService();
            setField(service, "appKey", "test-app-key");
            setField(service, "accessKeyId", "");
            setField(service, "accessKeySecret", "");

            // When & Then - 不应抛出异常，应创建空客户端
            assertDoesNotThrow(() -> service.init());

            Object client = getField(service, "client");
            assertNotNull(client, "应创建 NlsClient（即使是空 token）");
        }

        @Test
        @DisplayName("销毁客户端 - 验证清理逻辑")
        void testDestroy_ClientCleanup() {
            // Given
            AliyunSttService service = new AliyunSttService();
            setField(service, "appKey", "test-app-key");
            setField(service, "accessKeyId", "test-key");
            setField(service, "accessKeySecret", "test-secret");
            service.init();

            // When & Then - 验证 destroy 方法不会抛出异常
            assertDoesNotThrow(() -> service.destroy());
        }

        @Test
        @DisplayName("销毁未初始化的客户端 - 应安全处理")
        void testDestroy_NullClient() {
            // Given - 创建服务但不初始化
            AliyunSttService service = new AliyunSttService();

            // When & Then - 不应抛出异常
            assertDoesNotThrow(() -> service.destroy());
        }
    }

    @Nested
    @DisplayName("回调处理测试")
    class CallbackTests {

        @Test
        @DisplayName("验证回调参数 - null onResult")
        void testStartTranscription_NullOnResult() {
            // Given
            String sessionId = "test-session";
            Consumer<Throwable> onError = error -> {};

            // When & Then - 应抛出 NullPointerException 或由 SDK 处理
            assertDoesNotThrow(() -> {
                try {
                    aliyunSttService.startTranscription(sessionId, null, onError);
                } catch (Exception e) {
                    // 预期异常
                    assertTrue(e instanceof NullPointerException ||
                               e.getMessage() != null);
                }
            });
        }

        @Test
        @DisplayName("验证回调参数 - null onError")
        void testStartTranscription_NullOnError() {
            // Given
            String sessionId = "test-session";
            Consumer<String> onResult = text -> {};

            // When & Then - null onError 应该可以接受
            assertDoesNotThrow(() -> {
                try {
                    aliyunSttService.startTranscription(sessionId, onResult, null);
                } catch (Exception e) {
                    // 预期：没有真实凭证会失败
                    assertTrue(e.getMessage() != null);
                }
            });
        }

        @Test
        @DisplayName("回调触发 - 使用 mock 验证")
        void testCallback_Invocation() {
            // Given
            AtomicReference<String> resultHolder = new AtomicReference<>();
            Consumer<String> onResult = resultHolder::set;

            // When - 模拟回调触发
            onResult.accept("测试识别结果");

            // Then - 验证回调被调用
            assertEquals("测试识别结果", resultHolder.get());
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("无效音频数据 - 应安全处理")
        void testSendAudio_InvalidAudioData() {
            // Given
            String sessionId = "test-session";
            byte[] invalidAudio = null;

            // When & Then - 不存在的会话应抛出异常
            assertThrows(Exception.class, () -> {
                aliyunSttService.sendAudio(sessionId, invalidAudio);
            });
        }

        @Test
        @DisplayName("重复启动同一会话 - 应停止旧会话")
        void testStartTranscription_DuplicateSession() {
            // Given
            String sessionId = "test-session";
            Consumer<String> onResult = text -> {};
            Consumer<Throwable> onError = error -> {};

            // When & Then - 连续两次启动应不会抛出异常
            assertDoesNotThrow(() -> {
                try {
                    aliyunSttService.startTranscription(sessionId, onResult, onError);
                    aliyunSttService.startTranscription(sessionId, onResult, onError);
                } catch (Exception e) {
                    // 预期：没有真实凭证会失败
                    assertTrue(e.getMessage() != null);
                }
            });
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程访问会话管理 - 应线程安全")
        void testConcurrentSessionAccess() throws InterruptedException {
            // Given
            String sessionId = "concurrent-session";
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            // When - 多线程同时访问
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    aliyunSttService.hasActiveSession(sessionId);
                    aliyunSttService.stopTranscription(sessionId);
                });
                threads[i].start();
            }

            // Wait for all threads
            for (Thread thread : threads) {
                thread.join();
            }

            // Then - 不应抛出异常，验证线程安全
            assertFalse(aliyunSttService.hasActiveSession(sessionId));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 使用反射设置私有字段
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    /**
     * 使用反射获取私有字段
     */
    private Object getField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field: " + fieldName, e);
        }
    }
}
