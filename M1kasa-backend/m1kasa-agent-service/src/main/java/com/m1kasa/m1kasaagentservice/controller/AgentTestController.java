package com.m1kasa.m1kasaagentservice.controller;

import com.m1kasa.m1kasaagentservice.service.AgentService;
import com.m1kasa.m1kasaagentservice.tool.ShopTools;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.m1kasa.m1kasaagentservice.service.AgentService;
import com.m1kasa.m1kasaagentservice.service.AgentService.AgentLoopResult;
@RestController
public class AgentTestController {
    private final DeepSeekChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AgentService agentService;

    public AgentTestController(
            DeepSeekChatModel chatModel,
            ToolCallingManager toolCallingManager,
            AgentService agentService
    ) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.agentService = agentService;
    }
    @PostMapping(value = "/agent/test")
    public AgentTestResult test(){
        Prompt prompt=new Prompt("请用一句中文介绍你自己");
        ChatResponse response = chatModel.call(prompt);
        Generation generation = response.getResult();
        if (generation==null){
            throw new IllegalStateException("无结果");
        }
        String answer=generation.getOutput().getText();
        return new AgentTestResult(answer, response.hasToolCalls());
    }
    public record AgentTestResult(String answer,boolean hasToolCalls){

    }
    public record ToolCallResult(String name,String arguments){

    }
    public record AgentToolTestResult(boolean hasToolCalls, List<ToolCallResult> calls){

    }
//    @PostMapping("/agent/test/tool")
//    public AgentToolTestResult testTool(){
//        DeepSeekChatOptions option = chatModel.getOptions()
//                .mutate()
//                .toolCallbacks(ToolCallbacks.from(new ShopTools()))
//                .build();
//        Prompt prompt = new Prompt(
//                "请调用店铺查询工具，帮我查一下12号店铺的地址",option);
//        ChatResponse response = chatModel.call(prompt);
//        Generation generation=response.getResult();
//        if (generation==null){
//            throw new IllegalStateException("无结果");
//        }
//        List<ToolCallResult> calls = generation.getOutput()
//                .getToolCalls()
//                .stream()
//                .map(toolCall -> new ToolCallResult(toolCall.name(),toolCall.arguments()))
//                .toList();
//        return new AgentToolTestResult(response.hasToolCalls(), calls);
//    }

//    @PostMapping(value = "/agent/test/execute",produces = "text/plain;charset=UTF-8")
//    public String testExecuteTool(){
//        DeepSeekChatOptions firstOption=chatModel.getOptions()
//                .mutate()
//                .disableThinking()
//                .toolCallbacks(ToolCallbacks.from(new ShopTools()))
//                .toolChoice("required")
//                .build();
//        Prompt firstPrompt = new Prompt(
//                "请查一下12号店铺的地址，并用一句中文告诉我。",
//                firstOption
//        );
//        ChatResponse firstResponse = chatModel.call(firstPrompt);
//        if (!firstResponse.hasToolCalls()){
//            throw new IllegalStateException("无工具调用");
//        }
//        //在java进程里执行工具
//        ToolExecutionResult execution = toolCallingManager.executeToolCalls(
//                firstPrompt, firstResponse
//        );
//        //第二次请求模型
//        DeepSeekChatOptions secondOption = firstOption.mutate()
//                .toolChoice("none")
//                .build();
//        Prompt secondPrompt=new Prompt(execution.conversationHistory(),secondOption);
//        ChatResponse secondResponse = chatModel.call(secondPrompt);
//        Generation generation = secondResponse.getResult();
//        if (generation==null){
//            throw new IllegalStateException("无结果");
//        }
//        return generation.getOutput().getText();
//    }

//    private static final int MAX_MODEL_CALLS=4;
//    @PostMapping("/agent/test/loop")
//    public AgentLoopResult testLoop(@RequestParam("message") String message){
//      return agentService.chat(message);
//    }

    @PostMapping("/agent/test/loop")
    public AgentLoopResult testLoop(
            @RequestParam("message") String message,
            @RequestParam(name = "x", required = false) Double x,
            @RequestParam(name = "y", required = false) Double y
    ) {
        return agentService.chat(message, x, y);
    }
}
