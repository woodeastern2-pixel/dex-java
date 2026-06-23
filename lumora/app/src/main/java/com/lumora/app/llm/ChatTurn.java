package com.lumora.app.llm;

/**
 * LLM 한 턴(역할+내용). OpenAI/Anthropic chat 메시지 한 항목과 같은 의미.
 */
public class ChatTurn {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public final String role;
    public final String content;

    public ChatTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatTurn user(String content) { return new ChatTurn(ROLE_USER, content); }
    public static ChatTurn assistant(String content) { return new ChatTurn(ROLE_ASSISTANT, content); }
}
