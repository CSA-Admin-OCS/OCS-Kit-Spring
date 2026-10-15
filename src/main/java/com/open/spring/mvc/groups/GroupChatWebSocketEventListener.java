package com.open.spring.mvc.groups;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GroupChatWebSocketEventListener {

    private final GroupChatPresenceService presenceService;
    private final GroupChatRealtimeService realtimeService;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }

        List<GroupChatPresenceService.DisconnectedGroup> disconnectedGroups = presenceService.removeSession(sessionId);
        for (GroupChatPresenceService.DisconnectedGroup disconnectedGroup : disconnectedGroups) {
            realtimeService.publishPresence(
                    disconnectedGroup.getGroupId(),
                    "leaveGroupServer",
                    disconnectedGroup.getUsername(),
                    presenceService.getParticipants(disconnectedGroup.getGroupId()));
        }
    }
}
