package interview.guide.common.aspect;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.annotation.RateLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流注解单元测试
 */
@DisplayName("限流注解单元测试")
class RateLimitScriptTest {

    @Test
    @DisplayName("验证注解元配置")
    void testAnnotationMetadata() {
        assertEquals(RetentionPolicy.RUNTIME,
                RateLimit.class.getAnnotation(Retention.class).value());
        assertArrayEquals(new ElementType[]{ElementType.METHOD},
                RateLimit.class.getAnnotation(Target.class).value());
        assertNotNull(RateLimit.class.getAnnotation(java.lang.annotation.Repeatable.class));
        assertEquals(3, RateLimit.Dimension.values().length);
        assertEquals(5, RateLimit.TimeUnit.values().length);
    }

    @Test
    @DisplayName("验证注解默认值")
    void testDefaultValues() throws NoSuchMethodException {
        RateLimit ann = TestClass.class.getMethod("defaultMethod").getAnnotation(RateLimit.class);

        assertEquals(RateLimit.Dimension.GLOBAL, ann.dimension());
        assertEquals(0, ann.timeout());
        assertEquals(1, ann.interval());
        assertEquals(RateLimit.TimeUnit.SECONDS, ann.timeUnit());
        assertEquals("", ann.fallback());
    }

    @Test
    @DisplayName("验证自定义值")
    void testCustomValues() throws NoSuchMethodException {
        RateLimit[] annotations = TestClass.class.getMethod("customMethod").getAnnotationsByType(RateLimit.class);

        assertEquals(3, annotations.length);

        assertEquals(RateLimit.Dimension.GLOBAL, annotations[0].dimension());
        assertEquals(100.0, annotations[0].count(), 0.001);
        assertEquals(2, annotations[0].timeout());
        assertEquals(2, annotations[0].interval());
        assertEquals(RateLimit.TimeUnit.SECONDS, annotations[0].timeUnit());
        assertEquals("fallback", annotations[0].fallback());

        assertEquals(RateLimit.Dimension.IP, annotations[1].dimension());
        assertEquals(10.0, annotations[1].count(), 0.001);
        assertEquals(RateLimit.Dimension.USER, annotations[2].dimension());
        assertEquals(5.0, annotations[2].count(), 0.001);
    }

    @Test
    @DisplayName("验证容器注解可读取")
    void testRepeatableContainer() throws NoSuchMethodException {
        RateLimits container = TestClass.class.getMethod("customMethod").getAnnotation(RateLimits.class);

        assertNotNull(container);
        assertEquals(3, container.value().length);
    }

    @Test
    @DisplayName("验证重复注解的 fallback 必须一致")
    void testRepeatableFallbackMustBeConsistent() throws Exception {
        RateLimitAspect aspect = new RateLimitAspect(null);
        Method resolveFallbackName = RateLimitAspect.class.getDeclaredMethod(
                "resolveFallbackName", RateLimit[].class, Method.class);
        resolveFallbackName.setAccessible(true);
        Method mixedMethod = TestClass.class.getDeclaredMethod("mixedFallbackMethod");

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> resolveFallbackName.invoke(aspect, mixedMethod.getAnnotationsByType(RateLimit.class), mixedMethod));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    static class TestClass {
        @RateLimit(count = 10)
        public void defaultMethod() {}

        @RateLimit(dimension = RateLimit.Dimension.GLOBAL,
                count = 100, interval = 2, timeout = 2, timeUnit = RateLimit.TimeUnit.SECONDS, fallback = "fallback")
        @RateLimit(dimension = RateLimit.Dimension.IP,
                count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.SECONDS, fallback = "fallback")
        @RateLimit(dimension = RateLimit.Dimension.USER,
                count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.SECONDS, fallback = "fallback")
        public void customMethod() {}

        @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 1, fallback = "fallback")
        @RateLimit(dimension = RateLimit.Dimension.IP, count = 1)
        public void mixedFallbackMethod() {}
    }
}
