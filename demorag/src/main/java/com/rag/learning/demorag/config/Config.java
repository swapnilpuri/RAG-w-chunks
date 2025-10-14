package com.rag.learning.demorag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatModel;

@Configuration
public class Config {

    // @Bean
    // public ChatClient chatClient() {
    //     return ChatClient.create();
    // }

     // make the auto-configured google chat model the primary ChatModel
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("googleGenAiChatModel") ChatModel googleModel) {
        return googleModel;
    }
    
}
