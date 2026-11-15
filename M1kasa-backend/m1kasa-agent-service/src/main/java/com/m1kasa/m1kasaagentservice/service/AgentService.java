package com.m1kasa.m1kasaagentservice.service;

import com.m1kasa.m1kasaagentservice.tool.ShopTools;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
public class AgentService {

    private static final int MAX_MODEL_CALLS = 4;
    private final ChatMemory chatMemory;
    private final DeepSeekChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ShopTools shopTools;
    public AgentService(
            ChatMemory chatMemory, DeepSeekChatModel chatModel,
            ToolCallingManager toolCallingManager, ShopTools shopTools
    ) {
        this.chatMemory = chatMemory;
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.shopTools = shopTools;
    }

    public AgentLoopResult chat(String message, Double x, Double y) {

        Prompt prompt = createPrompt(message, x, y,List.of());

        for (int round = 1; round <= MAX_MODEL_CALLS; round++) {
            ChatResponse response = chatModel.call(prompt);

            if (!response.hasToolCalls()) {
                Generation generation = response.getResult();
                if (generation == null) {
                    throw new IllegalStateException("DeepSeek 没有返回回答");
                }

                return new AgentLoopResult(generation.getOutput().getText(), round);
            }

            // 已经没有下一轮可以接收工具结果
            if (round == MAX_MODEL_CALLS) {
                break;
            }

            ToolExecutionResult execution = toolCallingManager.executeToolCalls(
                    prompt, response
            );
            prompt = new Prompt(execution.conversationHistory(), prompt.getOptions());
        }

        throw new IllegalStateException("达到模型调用次数上限");
    }

    public record AgentLoopResult(String answer, int modelCalls) {
    }

    private Prompt createPrompt(String message,Double longitude,Double latitude,List<Message> history){
        boolean locationAvailable = longitude != null && latitude != null;
        DeepSeekChatOptions.Builder options=chatModel.getOptions()
                .mutate()
                .disableThinking()
                .toolCallbacks(ToolCallbacks.from(shopTools))
                .toolChoice("auto")
                // 附近查询工具需要非空的 ToolContext，坐标缺失时也要能给出授权提示。
                .toolContext("locationAvailable", locationAvailable);
        if (locationAvailable) {
            options.toolContext("longitude",longitude);
            options.toolContext("latitude",latitude);
        }
        SystemMessage instructions = new SystemMessage("""
            你是巷里的店铺助手。回答店铺信息时，以工具返回的数据为准。
            按分类名称查附近店铺时，先调用 listShopTypes 获取对应分类 ID。
            用户只想找附近店铺时，调用 findNearbyShops。
            用户想了解附近店铺的口味、环境、排队或服务时，
            调用 searchNearbyShopReview，并传入用户关心的问题。
            需要调用工具时，先调用工具，拿到结果再输出回答正文。
            回答时用店铺名称和分类名称，按需要给出地址、距离和推荐理由。
            分类 ID、店铺 ID、笔记 ID、评论 ID、工具名及参数仅供查询和匹配，回答中省略这些内部标识。
            用户明确要求核对店铺编号或来源笔记编号时，可以提供他所问的编号。
            引用评论时，用「有用户在笔记中提到」等自然语言概述相关内容。评论不足时，说明缺少哪些依据。
            如果没有匹配的分类，请询问用户想找哪类店铺。
            """);

        List<Message> messages = new ArrayList<>();
        messages.add(instructions);                 // 系统提示词
        messages.addAll(history);                  // 之前的问答
        messages.add(new UserMessage(message));    // 这一次的问题

        return new Prompt(messages, options.build());
    }

    private ChatResponse streamModelRound(Prompt prompt, Consumer<String> onDelta){
        List<ChatResponse> fullResponse=new ArrayList<>();
        Flux<ChatResponse> chunks = chatModel.stream(prompt)
                .publishOn(Schedulers.boundedElastic());
        new MessageAggregator()
                .aggregate(chunks,response->fullResponse.add(response))
                .doOnNext(chunk->{
                    Generation generation = chunk.getResult();
                    if (generation==null){
                        return;
                    }
                    String text=generation.getOutput().getText();
                    if (text!=null&&!text.isEmpty()){
                        onDelta.accept(text);
                    }
                })
                .blockLast();
        if (fullResponse.isEmpty()) {
            throw new IllegalStateException("没有回答");
        }
        ChatResponse response = fullResponse.get(0);
        if (response==null||response.getResult()==null){
            throw new IllegalStateException("没有回答");
        }

        return response;

    }
    public void streamChat(
        String message,
        Double longitude,
        Double latitude,
        Consumer<String> onDelta,
        String conversationId
    ){
        List<Message> history = chatMemory.get(conversationId);
        Prompt prompt = createPrompt(message, longitude, latitude, history);
        for (int round=1;round<=MAX_MODEL_CALLS;round++){
            ChatResponse response = streamModelRound(prompt, onDelta);
            if (!response.hasToolCalls()){
                Generation generation = response.getResult();
                String text = generation.getOutput().getText();
                if (text==null||text.isBlank()){
                    throw new IllegalStateException("没有返回正文");
                }
                String finishReason=generation.getMetadata().getFinishReason();
                if (finishReason != null && !finishReason.isBlank()
                        && !"STOP".equalsIgnoreCase(finishReason)) {
                    throw new IllegalStateException("DeepSeek 提前结束：" + finishReason);
                }
                chatMemory.add(conversationId, List.of(
                        new UserMessage(message),
                        new AssistantMessage(text)
                ));
                return;
            }
            if (round==MAX_MODEL_CALLS){
                break;
            }
            ToolExecutionResult execution = toolCallingManager.executeToolCalls(prompt, response);
            prompt=new Prompt(execution.conversationHistory(),prompt.getOptions());
        }
        throw new IllegalStateException("达到模型调用次数上限");
    }
}
