package com.renye.aiagent.session;

    import org.springframework.stereotype.Service;
    import java.util.Map;
    import java.util.Optional;
    import java.util.concurrent.ConcurrentHashMap;

    @Service
    public class InterviewSessionService {
        private final Map<String, InterviewContext> activeSessions = new ConcurrentHashMap<>();

        public void storeSession(String sessionId, InterviewContext context) {
            activeSessions.put(sessionId, context);
        }

        public Optional<InterviewContext> getSession(String sessionId) {
            return Optional.ofNullable(activeSessions.get(sessionId));
        }

        public void removeSession(String sessionId) {
            activeSessions.remove(sessionId);
        }
    }