package com.kexu.campus.dns;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 从 assets/ad-rules.runtime.json 加载域名规则，并叠加用户自定义的屏蔽/放行域名。
 *
 * 匹配策略（顺序即优先级）：
 *   1. allow 命中 -> 放行（白名单最高优先级，避免误伤定位/支付/推送）
 *   2. block 命中 -> 拦截
 *   3. observe 命中且用户开启 -> 拦截
 *   4. 其余 -> 放行
 *
 * 域名按后缀匹配：规则 "amap.com" 同时命中 "adiu.amap.com" 与 "amap.com" 本身。
 *
 * 规则文件来自开源项目 anti-AD（MIT License）与 TangLi（MIT License）。
 */
public final class DomainRules {

    private static final String TAG = "KexuDomainRules";
    private static final String ASSET = "ad-rules.runtime.json";

    private final Set<String> allow = new HashSet<>();
    private final Set<String> block = new HashSet<>();
    private final Set<String> observe = new HashSet<>();

    public static DomainRules load(Context ctx, Collection<String> customBlock, Collection<String> customAllow) {
        return new DomainRules(ctx, customBlock, customAllow);
    }

    private DomainRules(Context ctx, Collection<String> customBlock, Collection<String> customAllow) {
        try (InputStream is = ctx.getAssets().open(ASSET)) {
            byte[] buf = new byte[is.available()];
            int off = 0;
            while (off < buf.length) {
                int n = is.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            String json = new String(buf, 0, off, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            fill(root.optJSONArray("allow"), allow);
            fill(root.optJSONArray("block"), block);
            fill(root.optJSONArray("observe"), observe);
            Log.i(TAG, "rules loaded allow=" + allow.size()
                    + " block=" + block.size() + " observe=" + observe.size());
        } catch (Exception e) {
            Log.e(TAG, "failed to load rules", e);
        }
        addDomains(customBlock, block);
        addDomains(customAllow, allow);
    }

    private static void fill(JSONArray arr, Set<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String d = normalize(arr.optString(i, ""));
            if (!d.isEmpty()) out.add(d);
        }
    }

    private static void addDomains(Collection<String> domains, Set<String> out) {
        if (domains == null) return;
        for (String d : domains) {
            String value = normalize(d);
            if (!value.isEmpty()) out.add(value);
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase();
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        if (d.startsWith("*.")) d = d.substring(2);
        if (d.startsWith(".")) d = d.substring(1);
        return d;
    }

    public int blockCount() {
        return block.size();
    }

    public int allowCount() {
        return allow.size();
    }

    public int observeCount() {
        return observe.size();
    }

    private static boolean matches(String host, Set<String> rules) {
        if (host == null || host.isEmpty()) return false;
        String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (rules.contains(h)) return true;
        int idx = h.indexOf('.');
        while (idx >= 0) {
            String suffix = h.substring(idx + 1);
            if (rules.contains(suffix)) return true;
            idx = h.indexOf('.', idx + 1);
        }
        return false;
    }

    /** @return true 表示应当拦截该域名 */
    public boolean shouldBlock(String host, boolean blockObserve) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        if (matches(h, allow)) return false;
        if (matches(h, block)) return true;
        return blockObserve && matches(h, observe);
    }

    public Set<String> blockSet() {
        return Collections.unmodifiableSet(block);
    }
}
