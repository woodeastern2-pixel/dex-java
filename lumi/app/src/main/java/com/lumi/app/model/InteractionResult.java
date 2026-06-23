package com.lumi.app.model;

import java.util.List;

public class InteractionResult {
    public final CharacterStateEntity state;
    public final ConversationMessage userMessage;
    public final ConversationMessage lumiMessage;
    public final List<MemoryEntry> memories;

    public InteractionResult(CharacterStateEntity state,
                             ConversationMessage userMessage,
                             ConversationMessage lumiMessage,
                             List<MemoryEntry> memories) {
        this.state = state;
        this.userMessage = userMessage;
        this.lumiMessage = lumiMessage;
        this.memories = memories;
    }
}