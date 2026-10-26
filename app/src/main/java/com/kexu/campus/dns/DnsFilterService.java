package com.kexu.campus.dns;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.kexu.campus.MainActivity;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 仅接管 DNS 的 VpnService。
 *
 * 关键设计：路由只放 10.0.0.1/32，也就是「只有发往虚拟 DNS 的包进隧道」，
 * 其他流量（定位、上报、业务）完全走物理网卡，不受影响。
 * 这样就不需要实现 TCP/UDP 全转发（tun2socks），实现量和风险都大幅下降。
 *
 * 移植自开源项目 TangLi（MIT License），并增加：
 * - Android 14+ 的前台服务类型声明
 * - 用户自定义屏蔽/放行域名
 * - 拦截计数，便于 UI 展示
 */
public class DnsFilterService extends VpnService {

    private static final String TAG = "KexuDnsFilter";

    public static final String ACTION_START = "com.kexu.campus.dns.START";
    public static final String ACTION_STOP = "com.kexu.campus.dns.STOP";
    public static final String EXTRA_OBSERVE = "observe";
    public static final String EXTRA_BLOCK_DOMAINS = "block_domains";
    public static final String EXTRA_ALLOW_DOMAINS = "allow_domains";

    private static final String VPN_ADDR = "10.0.0.2";
    private static final String VIRTUAL_DNS = "10.0.0.1";
    private static final int VPN_PREFIX = 32;

    private static final String UPSTREAM_PRIMARY = "223.5.5.5";
    private static final String UPSTREAM_SECONDARY = "119.29.29.29";
    private static final int DNS_PORT = 53;

    private static final String CHANNEL_ID = "dns_filter";
    private static final int NOTIF_ID = 1001;

    private Thread worker;
    private ParcelFileDescriptor tunFd;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public static volatile boolean isRunning = false;
    public static final AtomicInteger blockedCount = new AtomicInteger(0);
    public static final AtomicInteger queriedCount = new AtomicInteger(0);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopFilter();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (running.get()) {
            stopFilter();
        }
        startForegroundSafely();

        ArrayList<String> block = intent != null
                ? intent.getStringArrayListExtra(EXTRA_BLOCK_DOMAINS) : null;
        ArrayList<String> allow = intent != null
                ? intent.getStringArrayListExtra(EXTRA_ALLOW_DOMAINS) : null;
        boolean observe = intent != null && intent.getBooleanExtra(EXTRA_OBSERVE, false);
        startFilter(block, allow, observe);
        return START_STICKY;
    }

    private void startForegroundSafely() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "去广告", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("本地 DNS 过滤，仅作用于本机");
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("校序 · 去广告运行中")
                .setContentText("仅过滤 DNS 查询，不接管其他流量")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground failed", e);
        }
    }

    private void startFilter(List<String> customBlock, List<String> customAllow, boolean blockObserve) {
        if (running.get()) return;

        Builder builder = new Builder();
        builder.setSession("Kexu DNS Filter");
        builder.addAddress(VPN_ADDR, VPN_PREFIX);
        builder.addDnsServer(VIRTUAL_DNS);
        builder.addRoute(VIRTUAL_DNS, VPN_PREFIX);
        builder.setBlocking(true);

        try {
            ParcelFileDescriptor fd = builder.establish();
            if (fd == null) {
                Log.e(TAG, "establish() returned null (permission revoked?)");
                stopSelf();
                return;
            }
            tunFd = fd;
        } catch (Exception e) {
            Log.e(TAG, "establish failed", e);
            stopSelf();
            return;
        }

        running.set(true);
        isRunning = true;
        final DomainRules rules = DomainRules.load(this, customBlock, customAllow);

        worker = new Thread(() -> pump(tunFd, rules, blockObserve), "dns-pump");
        worker.start();
        Log.i(TAG, "DNS filter started");
    }

    private void pump(ParcelFileDescriptor fd, DomainRules rules, boolean blockObserve) {
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(fd.getFileDescriptor());
        byte[] buf = new byte[4096];

        while (running.get()) {
            int len;
            try {
                len = in.read(buf);
            } catch (Exception e) {
                if (running.get()) Log.e(TAG, "tun read error", e);
                break;
            }
            if (len <= 0) continue;
            if (len < 20) continue;

            int ipHeaderLen = (buf[0] & 0x0F) * 4;
            if (len < ipHeaderLen + 8) continue;
            int proto = buf[9] & 0xFF;
            if (proto != 17) continue;
            int dstPort = ((buf[ipHeaderLen + 2] & 0xFF) << 8) | (buf[ipHeaderLen + 3] & 0xFF);
            if (dstPort != DNS_PORT) continue;

            byte[] dns = new byte[len - ipHeaderLen - 8];
            System.arraycopy(buf, ipHeaderLen + 8, dns, 0, dns.length);

            String host = DnsPacket.parseQueryName(dns, dns.length);
            int qtype = DnsPacket.parseQueryType(dns, dns.length);
            queriedCount.incrementAndGet();

            byte[] resp;
            if (host != null && rules.shouldBlock(host, blockObserve)) {
                resp = DnsPacket.buildBlockedResponse(dns, dns.length, qtype);
                blockedCount.incrementAndGet();
                Log.d(TAG, "BLOCK " + host + " type=" + qtype);
            } else {
                resp = forwardUpstream(dns, dns.length);
            }
            if (resp == null || resp.length == 0) continue;
            writeResponse(out, buf, ipHeaderLen, resp);
        }
    }

    private void writeResponse(FileOutputStream out, byte[] orig, int ipHeaderLen, byte[] payload) {
        try {
            byte[] pkt = new byte[ipHeaderLen + 8 + payload.length];
            System.arraycopy(orig, 0, pkt, 0, ipHeaderLen);
            System.arraycopy(orig, 16, pkt, 12, 4);
            System.arraycopy(orig, 12, pkt, 16, 4);
            pkt[8] = 64;
            int totalLen = ipHeaderLen + 8 + payload.length;
            pkt[2] = (byte) ((totalLen >> 8) & 0xFF);
            pkt[3] = (byte) (totalLen & 0xFF);
            pkt[10] = 0; pkt[11] = 0;
            int sum = checksum(pkt, 0, ipHeaderLen);
            pkt[10] = (byte) ((sum >> 8) & 0xFF);
            pkt[11] = (byte) (sum & 0xFF);

            int u = ipHeaderLen;
            pkt[u]     = orig[u + 2];
            pkt[u + 1] = orig[u + 3];
            pkt[u + 2] = orig[u];
            pkt[u + 3] = orig[u + 1];
            int udpLen = 8 + payload.length;
            pkt[u + 4] = (byte) ((udpLen >> 8) & 0xFF);
            pkt[u + 5] = (byte) (udpLen & 0xFF);
            pkt[u + 6] = 0; pkt[u + 7] = 0;
            System.arraycopy(payload, 0, pkt, u + 8, payload.length);

            out.write(pkt);
            out.flush();
        } catch (Exception e) {
            if (running.get()) Log.e(TAG, "write back failed", e);
        }
    }

    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int hi = data[offset + i] & 0xFF;
            int lo = (i + 1 < length) ? (data[offset + i + 1] & 0xFF) : 0;
            sum += (hi << 8) | lo;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    private byte[] forwardUpstream(byte[] query, int len) {
        for (String up : new String[]{UPSTREAM_PRIMARY, UPSTREAM_SECONDARY}) {
            DatagramSocket sock = null;
            try {
                sock = new DatagramSocket();
                protect(sock);
                sock.setSoTimeout(2500);
                InetAddress addr = InetAddress.getByName(up);
                sock.send(new DatagramPacket(query, len, new InetSocketAddress(addr, DNS_PORT)));
                byte[] rb = new byte[4096];
                DatagramPacket rp = new DatagramPacket(rb, rb.length);
                sock.receive(rp);
                byte[] out = new byte[rp.getLength()];
                System.arraycopy(rp.getData(), rp.getOffset(), out, 0, rp.getLength());
                return out;
            } catch (Exception e) {
                Log.w(TAG, "upstream " + up + " failed: " + e.getMessage());
            } finally {
                if (sock != null) sock.close();
            }
        }
        return DnsPacket.buildServFail(query, len);
    }

    private void stopFilter() {
        running.set(false);
        isRunning = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        if (tunFd != null) {
            try { tunFd.close(); } catch (Exception ignored) {}
            tunFd = null;
        }
        try { stopForeground(true); } catch (Exception ignored) {}
        Log.i(TAG, "DNS filter stopped");
    }

    @Override
    public void onRevoke() {
        stopFilter();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopFilter();
        super.onDestroy();
    }
}
