package com.moyue.social.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * IM 在线会话注册表（进程内）。
 *
 * <p>用户 ID → 其全部 WebSocket 会话。消息发送时按「会话成员用户 ID 集合」做定向广播。
 *
 * <p><b>跨实例说明</b>：当前为进程内广播，满足单实例；微服务多实例下需经 Redis
 * Pub/Sub 扇出（common-redis 已就位），属 M5 里程碑（见架构说明书 D-02/D-03）。
 *
 * @author moyue
 */
@Component
public class ImSessionRegistry {

    /** 用户 ID → 会话列表 */
    private final Map<Long, List<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 会话 ID → 用户 ID（关闭时反查） */
    private final Map<String, Long> sessionUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        sessionUser.put(session.getId(), userId);
    }

    public void unregister(WebSocketSession session) {
        Long userId = sessionUser.remove(session.getId());
        if (userId != null) {
            List<WebSocketSession> list = userSessions.get(userId);
            if (list != null) {
                list.remove(session);
                if (list.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
    }

    /** 向一组用户广播文本（在线会话才收得到） */
    public void broadcast(List<Long> userIds, String text) {
        for (Long uid : userIds) {
            List<WebSocketSession> list = userSessions.get(uid);
            if (list == null) {
                continue;
            }
            for (WebSocketSession s : list) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(new org.springframework.web.socket.TextMessage(text));
                    } catch (IOException e) {
                        // 发送失败：会话可能已失效，交由 close 事件清理
                    }
                }
            }
        }
    }

    public int onlineCount(Long userId) {
        List<WebSocketSession> list = userSessions.get(userId);
        return list == null ? 0 : list.size();
    }
}
