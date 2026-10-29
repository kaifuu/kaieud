package com.edu.service;

import com.edu.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 图形验证码：生成SVG + 内存校验（5分钟有效，校验后销毁） */
@Service
public class CaptchaService {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final long EXPIRE_MS = 5 * 60 * 1000L;
    private static final String[] COLORS = {"#2b7fff", "#00c6ff", "#5e8bff", "#3ad0ff", "#6c9cff"};

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static class Entry {
        String code;
        long expireAt;
    }

    /** 生成验证码，返回 {key, svg, code(仅测试环境用，生产可移除)} */
    public Map<String, String> generate() {
        cleanup();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(CHARS[random.nextInt(CHARS.length)]);
        }
        String key = UUID.randomUUID().toString().replace("-", "");
        Entry e = new Entry();
        e.code = code.toString();
        e.expireAt = System.currentTimeMillis() + EXPIRE_MS;
        store.put(key, e);
        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("svg", drawSvg(code.toString()));
        return result;
    }

    /** 校验验证码，失败抛业务异常 */
    public void verify(String key, String code) {
        if (key == null || code == null) throw new BizException("请输入验证码");
        Entry e = store.remove(key);
        if (e == null) throw new BizException("验证码已失效，请刷新");
        if (System.currentTimeMillis() > e.expireAt) throw new BizException("验证码已过期，请刷新");
        if (!e.code.equalsIgnoreCase(code.trim())) throw new BizException("验证码错误");
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        for (Iterator<Entry> it = store.values().iterator(); it.hasNext(); ) {
            if (now > it.next().expireAt) it.remove();
        }
    }

    /** 手绘SVG验证码：随机旋转字符 + 噪点干扰线，科技蓝配色 */
    private String drawSvg(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='118' height='42' viewBox='0 0 118 42'>");
        sb.append("<defs><linearGradient id='cbg' x1='0' y1='0' x2='1' y2='1'>")
          .append("<stop offset='0' stop-color='#e8f3ff'/><stop offset='1' stop-color='#d3e7ff'/>")
          .append("</linearGradient></defs>");
        sb.append("<rect width='118' height='42' rx='8' fill='url(#cbg)'/>");
        // 干扰线
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1='").append(random.nextInt(118)).append("' y1='").append(random.nextInt(42))
              .append("' x2='").append(random.nextInt(118)).append("' y2='").append(random.nextInt(42))
              .append("' stroke='").append(COLORS[random.nextInt(COLORS.length)])
              .append("' stroke-width='1' opacity='0.45'/>");
        }
        // 干扰点
        for (int i = 0; i < 14; i++) {
            sb.append("<circle cx='").append(random.nextInt(118)).append("' cy='").append(random.nextInt(42))
              .append("' r='1.2' fill='").append(COLORS[random.nextInt(COLORS.length)])
              .append("' opacity='0.5'/>");
        }
        // 字符：随机颜色/旋转/纵向抖动
        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * 24 + random.nextInt(6);
            int y = 29 + random.nextInt(5);
            int rot = random.nextInt(30) - 15;
            sb.append("<text x='").append(x).append("' y='").append(y)
              .append("' font-family='Consolas,monospace' font-size='24' font-weight='bold' fill='")
              .append(COLORS[random.nextInt(COLORS.length)])
              .append("' transform='rotate(").append(rot).append(' ').append(x).append(' ').append(y)
              .append(")'>").append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }
}
