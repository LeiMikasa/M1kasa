package com.m1kasa.m1kasaagentservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.m1kasaagentservice.dto.AgentChatRequest;
import com.m1kasa.m1kasaagentservice.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@RestController
public class AgentChatController {
    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    private static final MediaType SSE_UTF8 = new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8);

    private final AgentService agentService;
    private final AsyncTaskExecutor chatExecutor;

    public AgentChatController(
            AgentService agentService,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor chatExecutor
    ) {
        this.agentService = agentService;
        this.chatExecutor = chatExecutor;
    }
    @PostMapping(
            value = "/agent/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> chat(@RequestBody AgentChatRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }

        if (!StringUtils.hasText(request.conversationId())
                || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "会话 ID 和问题不能为空");
        }

        String memoryKey = userId + ":" + request.conversationId();

        SseEmitter emitter = new SseEmitter(120_000L);
        chatExecutor.execute(() -> runChat(request, emitter, memoryKey));
        return ResponseEntity.ok().contentType(SSE_UTF8).body(emitter);
    }

    private void runChat(AgentChatRequest request,SseEmitter emitter,String memoryKey){
        try {
            agentService.streamChat(
                    request.message(),
                    request.longitude(),
                    request.latitude(),
                    text->send(emitter,"delta",text),
                    memoryKey
            );
            send(emitter,"done","ok");
            emitter.complete();
        }catch (UncheckedIOException ex) {
            // 响应连接写入失败时，Servlet 容器负责收尾。
        } catch (RuntimeException ex) {
            log.warn("找店助手生成回复失败", ex);
            try {
                send(emitter, "error", "生成失败，请稍后再试");
                emitter.complete();
            } catch (UncheckedIOException | IllegalStateException writeFailure) {
                log.debug("聊天响应已无法写入", writeFailure);
            }
        }
    }
    private void send(SseEmitter emitter,String event,String data){
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        }catch (IOException ex){
            throw new UncheckedIOException(ex);
        }
    }
}
