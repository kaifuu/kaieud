package com.edu.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重复提交防护：同一用户同一操作指纹在窗口期内（默认5秒）只放行一次。
 * 办理类写操作（报名订阅/转课/余额充值）在方法入口校验，双击/网络重试
 * 造成的重复请求直接拒绝，配合按钮 loading 双保险。
 * 进程内 ConcurrentHashMap 即可（单实例部署；重复提交窗口极短，无需持久化）。
 */
@Component
public class RepeatGuard {

    /** 窗口期（毫秒）：两次相同提交间隔小于该值视为重复 */
    private static final long WINDOW_MILLIS = 5000;

    private final Map<String, Long> lastSubmit = new ConcurrentHashMap<>();

    /**
     * 尝试获取提交资格：true=放行（首次或已过窗口期），false=窗口期内重复提交。
     * 注意：被拒绝的尝试也会刷新时间戳（滑动窗口），连点会一直被拦直到停手5秒后。
     */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Long prev = lastSubmit.put(key, now);
        // 超过1万条的旧指纹顺手清理，防长期运行内存增长
        if (lastSubmit.size() > 10000) lastSubmit.clear();
        return prev == null || now - prev > WINDOW_MILLIS;
    }
}
