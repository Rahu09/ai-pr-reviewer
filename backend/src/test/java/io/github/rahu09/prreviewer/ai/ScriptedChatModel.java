package io.github.rahu09.prreviewer.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** A fake model that answers with scripted texts and remembers the prompts it received. No network. */
class ScriptedChatModel implements ChatModel {

	final List<Prompt> prompts = new ArrayList<>();
	private final Deque<String> replies = new ArrayDeque<>();

	ScriptedChatModel(String... replies) {
		this.replies.addAll(List.of(replies));
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		prompts.add(prompt);
		String text = replies.isEmpty() ? "" : replies.poll();
		return ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage(text))))
				.metadata(ChatResponseMetadata.builder().model("scripted-model").usage(new DefaultUsage(120, 30)).build())
				.build();
	}
}
