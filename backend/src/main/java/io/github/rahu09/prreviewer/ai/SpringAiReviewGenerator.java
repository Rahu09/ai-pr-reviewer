package io.github.rahu09.prreviewer.ai;

import io.github.rahu09.prreviewer.review.CandidateFindings;
import io.github.rahu09.prreviewer.review.GenerationResult;
import io.github.rahu09.prreviewer.review.ReviewGenerationException;
import io.github.rahu09.prreviewer.review.ReviewGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import tools.jackson.core.JacksonException;

/**
 * Provider-neutral generator: works with whichever {@link ChatModel} Spring AI auto-configured for
 * {@code spring.ai.model.chat} (Gemini or OpenAI, ADR-0018). Absent when the provider is {@code none}
 * (the test profile), where tests supply a stub instead.
 */
@Component
@ConditionalOnExpression("'${spring.ai.model.chat:none}' != 'none'")
public class SpringAiReviewGenerator implements ReviewGenerator {

	static final String PROMPT_VERSION = "review-v2";
	private static final int MAX_ATTEMPTS = 2;
	private static final Logger log = LoggerFactory.getLogger(SpringAiReviewGenerator.class);

	private final ChatClient chatClient;
	private final String provider;
	private final String systemPrompt;
	private final String userTemplate;

	public SpringAiReviewGenerator(ChatModel chatModel, @Value("${spring.ai.model.chat}") String provider) {
		this.chatClient = ChatClient.create(chatModel);
		this.provider = provider;
		this.systemPrompt = read("prompts/" + PROMPT_VERSION + ".st");
		this.userTemplate = read("prompts/" + PROMPT_VERSION + "-user.st");
	}

	@Override
	public GenerationResult generate(String annotatedDiff) {
		RuntimeException lastParseFailure = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				// The diff is a template VARIABLE, never concatenated: Java code contains { } (pitfalls §3).
				var response = chatClient.prompt()
						.system(systemPrompt)
						.user(u -> u.text(userTemplate).param("diff", annotatedDiff))
						.call()
						.responseEntity(CandidateFindings.class);
				CandidateFindings findings = response.entity();
				if (findings == null) {
					throw new IllegalStateException("model returned no content");
				}
				return toResult(findings, response.response());
			}
			catch (RuntimeException e) {
				if (!isMalformedOutput(e)) {
					throw new ReviewGenerationException("LLM call failed (" + provider + ")", e);
				}
				lastParseFailure = e;
				log.warn("Malformed model output from {} (attempt {}/{}): {}", provider, attempt, MAX_ATTEMPTS, e.getMessage());
			}
		}
		throw new ReviewGenerationException("Model returned malformed output after " + MAX_ATTEMPTS + " attempts", lastParseFailure);
	}

	private GenerationResult toResult(CandidateFindings findings, ChatResponse response) {
		Usage usage = response.getMetadata().getUsage();
		String model = response.getMetadata().getModel();
		var result = new GenerationResult(findings, provider, model == null || model.isBlank() ? "unknown" : model,
				PROMPT_VERSION, tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()));
		log.info("Generated {} candidate findings with {}/{} ({} in, {} out tokens)", findings.findings().size(),
				result.provider(), result.model(), result.inputTokens(), result.outputTokens());
		return result;
	}

	private static int tokens(Integer value) {
		return value == null ? 0 : value;
	}

	/** Our own parse failures are retried; provider/network failures (429, 5xx, auth) are not retried here. */
	private static boolean isMalformedOutput(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof JacksonException || (t instanceof IllegalStateException && "model returned no content".equals(t.getMessage()))) {
				return true;
			}
		}
		return false;
	}

	private static String read(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot read prompt " + path, e);
		}
	}
}
