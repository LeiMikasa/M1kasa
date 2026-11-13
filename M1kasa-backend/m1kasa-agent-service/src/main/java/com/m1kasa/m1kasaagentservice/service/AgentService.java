package com.m1kasa.m1kasaagentservice.service;

import com.m1kasa.m1kasaagentservice.tool.ShopTools;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private static final int MAX_MODEL_CALLS = 4;

    private final DeepSeekChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final ShopTools shopTools;
    public AgentService(
            DeepSeekChatModel chatModel,
            ToolCallingManager toolCallingManager, ShopTools shopTools
    ) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.shopTools = shopTools;
    }

    public AgentLoopResult chat(String message, Double x, Double y) {
        DeepSeekChatOptions.Builder optionsBuilder = chatModel.getOptions()
                .mutate()
                .disableThinking()
                .toolCallbacks(ToolCallbacks.from(shopTools))
                .toolChoice("auto");

        if (x != null && y != null) {
            optionsBuilder.toolContext("longitude", x);
            optionsBuilder.toolContext("latitude", y);
        }

        DeepSeekChatOptions options = optionsBuilder.build();
        SystemMessage instructions = new SystemMessage("""
        你是巷里的店铺助手。回答店铺信息时，以工具返回的数据为准。
        按分类名称查附近店铺时，先调用 listShopTypes 获取对应分类 ID。
        用户只想找附近店铺时，调用 findNearbyShops。
        用户想了解附近店铺的口味、环境、排队或服务时，
        调用 searchNearbyShopReviews，并传入用户关心的问题。
        根据评论回答时，注明来源笔记 ID。评论不足时，说明缺少哪些依据。
        如果没有匹配的分类，请询问用户想找哪类店铺。
        """);

        Prompt prompt = new Prompt(
                List.of(instructions, new UserMessage(message)),
                options
        );


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
            prompt = new Prompt(execution.conversationHistory(), options);
        }

        throw new IllegalStateException("达到模型调用次数上限");
    }

    public record AgentLoopResult(String answer, int modelCalls) {
    }
}
