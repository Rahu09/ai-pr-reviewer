package io.github.rahu09.prreviewer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rahu09.prreviewer.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * "Switching LLM_PROVIDER needs no code change" (ADR-0018): each provider starts with ONLY its own (fake) key
 * and exactly one ChatModel. No network call is made.
 */
class ProviderSelectionTest {

	@Nested
	@SpringBootTest(properties = { "spring.ai.model.chat=google-genai", "spring.ai.google.genai.api-key=fake-key-for-test",
			"spring.ai.openai.api-key=" })
	@Import(TestcontainersConfiguration.class)
	class Gemini {

		@Autowired
		ApplicationContext context;

		@Test
		void startsWithOnlyGeminiAndRealGeneratorIsPresent() {
			Map<String, ChatModel> models = context.getBeansOfType(ChatModel.class);
			assertThat(models).hasSize(1);
			assertThat(models.values().iterator().next().getClass().getSimpleName()).contains("GoogleGenAi");
			assertThat(context.getBeansOfType(SpringAiReviewGenerator.class)).hasSize(1);
		}
	}

	@Nested
	@SpringBootTest(properties = { "spring.ai.model.chat=openai", "spring.ai.openai.api-key=fake-key-for-test",
			"spring.ai.google.genai.api-key=" })
	@Import(TestcontainersConfiguration.class)
	class OpenAi {

		@Autowired
		ApplicationContext context;

		@Test
		void startsWithOnlyOpenAiAndRealGeneratorIsPresent() {
			Map<String, ChatModel> models = context.getBeansOfType(ChatModel.class);
			assertThat(models).hasSize(1);
			assertThat(models.values().iterator().next().getClass().getSimpleName()).contains("OpenAi");
			assertThat(context.getBeansOfType(SpringAiReviewGenerator.class)).hasSize(1);
		}
	}
}
