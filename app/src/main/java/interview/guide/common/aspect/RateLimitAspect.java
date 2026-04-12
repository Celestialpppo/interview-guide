package interview.guide.common.aspect;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.RateLimitExceededException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 限流 AOP 切面
 * 基于滑动时间窗口实现的多维度原子限流
 */
@Slf4j
@Aspect //包含通知、切入点、切入点表达式语法
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    /**
     * Lua 脚本缓存
     */
    private static final String LUA_SCRIPT;
    private String luaScriptSha;

    static {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate_limit.lua");
            LUA_SCRIPT = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("加载限流 Lua 脚本失败", e);
        }
    }

    /**
     * 初始化：预加载脚本到 Redis 提高性能
     */
    @PostConstruct //Spring 创建 Bean、完成属性注入之后，会调用被 @PostConstruct 标注的方法。
    public void init() {
        //luaScriptSha存的是LUA_SCRIPT的SHA1
        this.luaScriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(LUA_SCRIPT);
        log.info("限流 Lua 脚本加载完成, SHA1: {}", luaScriptSha);
    }

    @Pointcut("@annotation(interview.guide.common.annotation.RateLimit) || "
            + "@annotation(interview.guide.common.annotation.RateLimits)")
    public void rateLimitPointcut(){}

    /**
     * 环绕通知：拦截带 @RateLimit 注解的方法
     */
    @Around("rateLimitPointcut()") //对注解标注的方法进行代理
    //当前被拦截的方法信息（比如方法名、参数等）→ 用 ProceedingJoinPoint joinPoint 接收
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveTargetMethod(joinPoint);
        RateLimit[] rateLimits = method.getAnnotationsByType(RateLimit.class);
        if (rateLimits.length == 0) {
            return joinPoint.proceed();
        }

        String className = method.getDeclaringClass().getSimpleName(); //简短类名
        String methodName = method.getName(); //方法名
        String fallbackName = resolveFallbackName(rateLimits, method);

        // 1. 为每条规则生成独立的 key 和参数
        List<ResolvedRule> rules = new ArrayList<>(rateLimits.length);
        for (RateLimit rateLimit : rateLimits) {
            long intervalMs = calculateIntervalMs(rateLimit.interval(), rateLimit.timeUnit());
            String key = generateKey(className, methodName, rateLimit.dimension());
            rules.add(new ResolvedRule(key, intervalMs, rateLimit));
        }

        // 2. 调用 Lua 脚本执行原子限流
        // 使用 StringCodec 确保参数正确传递为字符串
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);

        // 准备参数
        List<Object> keysList = new ArrayList<>(rules.size());
        List<Object> args = new ArrayList<>(3 + rules.size() * 2);
        args.add(String.valueOf(System.currentTimeMillis())); // ARGV[1]: 当前时间戳
        args.add(String.valueOf(1));                         // ARGV[2]: 申请令牌数（默认1个）
        args.add(UUID.randomUUID().toString());             // ARGV[3]: 请求唯一标识
        for (ResolvedRule rule : rules) {
            keysList.add(rule.key());
            args.add(String.valueOf(rule.intervalMs()));
            args.add(String.valueOf(rule.rateLimit().count()));
        }

        //让 Redis 按脚本的 SHA1 摘要去执行一段已经预加载过的 Lua 脚本。
        //
        //先把 Lua 脚本上传到 Redis
        //Redis 给这段脚本算一个 SHA1 标识
        //后面再执行时，不再传整段脚本
        //只传这个 SHA1
        //Redis 就知道要执行哪段脚本

        //SHA1：把任意长度的数据，计算成一个固定长度的摘要值。是摘要算法，无法解密，只能用于对比。
        //好处是：传输更短
        Object resultObj = script.evalSha(
                RScript.Mode.READ_WRITE, //这是一个会读也会写 Redis 的脚本
                luaScriptSha, //要执行的 Lua 脚本的 SHA1
                RScript.ReturnType.VALUE, //返回一个普通值
                keysList, //传给 Lua 的 KEYS
                args.toArray() //传给 Lua 的 ARGV
        );

        // 将结果转换为 Long
        Long result = convertToLong(resultObj);

        // 3. 处理限流结果
        if (result == null || result == 0) {
            return handleRateLimitExceeded(joinPoint, fallbackName, rules);
        }

        // 4. 执行原方法
        return joinPoint.proceed();
    }

    private Method resolveTargetMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        try {
            return targetClass.getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return method;
        }
    }

    private String resolveFallbackName(RateLimit[] rateLimits, Method method) {
        Set<String> fallbackNames = new LinkedHashSet<>();
        boolean hasEmptyFallback = false;
        for (RateLimit rateLimit : rateLimits) {
            if (rateLimit.fallback() == null || rateLimit.fallback().isEmpty()) {
                hasEmptyFallback = true;
            } else {
                fallbackNames.add(rateLimit.fallback());
            }
        }

        if (fallbackNames.size() > 1 || (!fallbackNames.isEmpty() && hasEmptyFallback)) {
            throw new IllegalStateException("同一方法上的多个 @RateLimit 必须配置相同的 fallback: "
                    + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        }

        return fallbackNames.isEmpty() ? "" : fallbackNames.iterator().next();
    }

    /**
     * 计算时间窗口毫秒数
     */
    private long calculateIntervalMs(long interval, RateLimit.TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 60 * 1000;
            case HOURS -> interval * 3600 * 1000;
            case DAYS -> interval * 86400 * 1000;
        };
    }

    /**
     * 将结果对象安全转换为 Long
     */
    private Long convertToLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof Short) {
            return ((Short) obj).longValue();
        } else if (obj instanceof Byte) {
            return ((Byte) obj).longValue();
        } else if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Long: {}", obj);
                return null;
            }
        }
        log.warn("不支持的对象类型转换为Long: {}", obj.getClass().getName());
        return null;
    }

    /**
     * 生成单条规则的限流键
     */
    private String generateKey(String className, String methodName, RateLimit.Dimension dimension) {
        // 使用 {} 包含类名和方法名作为 Hash Tag，确保该方法的所有限流 Key 落在同一个 Redis Slot
        // 从而适配 Redis Cluster 模式
        String hashTag = "{" + className + ":" + methodName + "}";
        String keyPrefix = "ratelimit:" + hashTag; //ratelimit:className:methodName

        return switch (dimension) {
            case GLOBAL -> keyPrefix + ":global";
            case IP -> keyPrefix + ":ip:" + getClientIp();
            case USER -> keyPrefix + ":user:" + getCurrentUserId();
        };
    }

    /**
     * 处理限流超出情况
     */
    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, String fallbackName, List<ResolvedRule> rules)
            throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        List<String> keys = new ArrayList<>(rules.size());
        for (ResolvedRule rule : rules) {
            keys.add(rule.key());
        }

        // 如果配置了降级方法，则调用降级方法
        if (fallbackName != null && !fallbackName.isEmpty()) {
            try {
                Method fallbackMethod = findFallbackMethod(joinPoint, fallbackName);
                if (fallbackMethod != null) {
                    log.debug("限流触发，执行降级方法: {}.{} -> {}",
                            joinPoint.getTarget().getClass().getSimpleName(),
                            methodName,
                            fallbackName);
                    // 如果降级方法有参数，传入原方法的参数
                    if (fallbackMethod.getParameterCount() > 0) {
                        return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                    } else {
                        return fallbackMethod.invoke(joinPoint.getTarget());
                    }
                }
            } catch (Exception e) {
                log.error("降级方法执行失败: {}", fallbackName, e);
            }
        }

        // 没有降级方法或降级失败，抛出限流异常
        log.debug("限流触发，拒绝请求: keys={}", keys);
        throw new RateLimitExceededException("请求过于频繁，请稍后再试");
    }

    /**
     * 查找降级方法
     * 优先查找与原方法参数列表完全一致的方法，找不到则查找无参方法
     */
    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackName) {
        Class<?> targetClass = joinPoint.getTarget().getClass(); //当前被 AOP 拦截的目标对象的类
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); //签名包含方法名、参数类型、返回值类型
        Class<?>[] parameterTypes = signature.getParameterTypes(); //参数类型

        Method method = findDeclaredMethod(targetClass, fallbackName, parameterTypes);
        if (method != null) {
            method.setAccessible(true);
            return method;
        }

        method = findDeclaredMethod(targetClass, fallbackName);
        if (method != null) {
            method.setAccessible(true);
            return method;
        }

        log.warn("未找到降级方法: {}.{} (需无参或参数列表一致)",
                targetClass.getSimpleName(), fallbackName);
        return null;
    }

    private Method findDeclaredMethod(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        Class<?> current = targetClass;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取客户端真实 IP
     * 处理 X-Forwarded-For 头，支持代理服务器场景
     */
    private String getClientIp() {
        //把当前 HTTP 请求包装起来，方便你在 Spring 代码里取到 HttpServletRequest 和相关请求信息。
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        //在经过代理服务器或负载均衡之后，告诉后端“原始客户端 IP 是谁”。
        String ip = request.getHeader("X-Forwarded-For");

        //按顺序尝试从不同代理/服务器约定的请求头里取真实客户端 IP，前一个取不到再试下一个。
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP"); //Nginx反向代理
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");//老式代理服务器
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");//WebLogic 相关代理
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();//Servlet API 直接拿到的“当前连接对端 IP”
        }

        // 处理多个 IP 的情况（X-Forwarded-For 可能包含多个 IP）
        // X-Forwarded-For: 203.0.113.10, 10.0.0.5, 10.0.0.6   最左边是最初的客户端 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }

    /**
     * 获取当前用户 ID
     * 从请求属性或 Session 中获取
     * TODO: 需要根据实际项目的认证框架进行实现，本项目未显示用户管理
     */
    private String getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }

        HttpServletRequest request = attributes.getRequest();

        // 方式1: 从请求属性中获取（推荐）
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return userId.toString();
        }

        // 方式2: 从请求头中获取
        userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId.toString();
        }

        // 方式3: 从 Session 中获取（如果使用 Session）
        // userId = request.getSession().getAttribute("userId");

        // 方式4: 从 JWT Token 中解析（如果使用 JWT）
        // 需要集成具体的 JWT 工具类

        return "anonymous";
    }

    private record ResolvedRule(String key, long intervalMs, RateLimit rateLimit) {
    }
}
