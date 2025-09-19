package com.factfeed.backend.ai;

import com.factfeed.backend.model.dto.AggregatedContentDTO;
import com.factfeed.backend.model.repository.ApiUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test class for EnhancedAIService parsing methods
 */
@ExtendWith(MockitoExtension.class)
class EnhancedAIServiceParsingTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    
    @Mock
    private ChatClient chatClient;
    
    @Mock
    private Environment environment;
    
    @Mock
    private ApiUsageLogRepository apiUsageLogRepository;

    private EnhancedAIService enhancedAIService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(environment.getProperty("spring.ai.google.genai.model", "unknown")).thenReturn("gemini-2.5-flash");
        
        enhancedAIService = new EnhancedAIService(chatClientBuilder, environment, apiUsageLogRepository);
    }

    @Test
    void testParseAggregationResponse_ValidJSON() throws Exception {
        // Test with valid JSON response
        String validJsonResponse = """
            {
              "aggregatedTitle": "সরকারী নীতি আপডেট",
              "aggregatedSummary": "সরকার নতুন নীতি প্রণয়ন করেছে যা জনগণের জীবনযাত্রার মান উন্নয়নে সহায়ক হবে।",
              "keyPoints": "• নতুন নীতি ঘোষণা\\n• জনকল্যাণে গুরুত্ব\\n• বাস্তবায়নের সময়সূচী",
              "timeline": "প্রাথমিক ঘোষণা আজ, বাস্তবায়ন পরবর্তী মাসে",
              "confidenceScore": 0.85
            }
            """;

        // Use reflection to access private method
        Method parseMethod = EnhancedAIService.class.getDeclaredMethod(
                "parseAggregationResponse", String.class, Long.class, int.class);
        parseMethod.setAccessible(true);

        AggregatedContentDTO result = (AggregatedContentDTO) parseMethod.invoke(
                enhancedAIService, validJsonResponse, 1L, 3);

        assertNotNull(result);
        assertEquals(1L, result.getEventId());
        assertEquals("সরকারী নীতি আপডেট", result.getAggregatedTitle());
        assertEquals("সরকার নতুন নীতি প্রণয়ন করেছে যা জনগণের জীবনযাত্রার মান উন্নয়নে সহায়ক হবে।", result.getAggregatedSummary());
        assertEquals(0.85, result.getConfidenceScore(), 0.001);
        assertEquals(3, result.getTotalArticles());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    void testParseAggregationResponse_MalformedJSON() throws Exception {
        // Test with malformed JSON
        String malformedResponse = """
            This is not a JSON response but contains some text {
              "aggregatedTitle": "Incomplete JSON
            """;

        Method parseMethod = EnhancedAIService.class.getDeclaredMethod(
                "parseAggregationResponse", String.class, Long.class, int.class);
        parseMethod.setAccessible(true);

        AggregatedContentDTO result = (AggregatedContentDTO) parseMethod.invoke(
                enhancedAIService, malformedResponse, 2L, 2);

        assertNotNull(result);
        assertEquals(2L, result.getEventId());
        assertEquals("Aggregation Processing Error", result.getAggregatedTitle());
        assertTrue(result.getAggregatedSummary().contains("Could not generate aggregated content"));
        assertEquals(0.0, result.getConfidenceScore());
        assertEquals(2, result.getTotalArticles());
    }

    @Test
    void testParseAggregationResponse_MissingFields() throws Exception {
        // Test with JSON missing some fields
        String partialJsonResponse = """
            {
              "aggregatedTitle": "শিরোনাম আছে",
              "confidenceScore": 0.7
            }
            """;

        Method parseMethod = EnhancedAIService.class.getDeclaredMethod(
                "parseAggregationResponse", String.class, Long.class, int.class);
        parseMethod.setAccessible(true);

        AggregatedContentDTO result = (AggregatedContentDTO) parseMethod.invoke(
                enhancedAIService, partialJsonResponse, 3L, 1);

        assertNotNull(result);
        assertEquals(3L, result.getEventId());
        assertEquals("শিরোনাম আছে", result.getAggregatedTitle());
        assertEquals("Content aggregation completed", result.getAggregatedSummary());
        assertEquals(0.7, result.getConfidenceScore(), 0.001);
        assertEquals("", result.getKeyPoints());
        assertEquals("", result.getTimeline());
    }

    @Test
    void testParseAggregationResponse_EmptyResponse() throws Exception {
        // Test with empty response
        String emptyResponse = "";

        Method parseMethod = EnhancedAIService.class.getDeclaredMethod(
                "parseAggregationResponse", String.class, Long.class, int.class);
        parseMethod.setAccessible(true);

        AggregatedContentDTO result = (AggregatedContentDTO) parseMethod.invoke(
                enhancedAIService, emptyResponse, 4L, 0);

        assertNotNull(result);
        assertEquals(4L, result.getEventId());
        assertEquals("Aggregation Processing Error", result.getAggregatedTitle());
        assertTrue(result.getAggregatedSummary().contains("Empty JSON response"));
        assertEquals(0.0, result.getConfidenceScore());
        assertEquals(0, result.getTotalArticles());
    }

    @Test
    void testParseAggregationResponse_SpecialCharacters() throws Exception {
        // Test with special characters and newlines
        String responseWithSpecialChars = """
            {
              "aggregatedTitle": "বিশেষ চিহ্ন: \"উদ্ধৃতি\" এবং নতুন\\nলাইন",
              "aggregatedSummary": "এটি একটি পরীক্ষা যেখানে বিশেষ চিহ্ন যেমন: @, #, $, %, & এবং বাংলা টেক্সট রয়েছে।\\nনতুন লাইন এবং ট্যাব\\tও রয়েছে।",
              "keyPoints": "• বিশেষ চিহ্ন পরীক্ষা\\n• বাংলা টেক্সট সাপোর্ট\\n• নতুন লাইন হ্যান্ডলিং",
              "confidenceScore": "0.92"
            }
            """;

        Method parseMethod = EnhancedAIService.class.getDeclaredMethod(
                "parseAggregationResponse", String.class, Long.class, int.class);
        parseMethod.setAccessible(true);

        AggregatedContentDTO result = (AggregatedContentDTO) parseMethod.invoke(
                enhancedAIService, responseWithSpecialChars, 5L, 2);

        assertNotNull(result);
        assertEquals(5L, result.getEventId());
        assertTrue(result.getAggregatedTitle().contains("বিশেষ চিহ্ন"));
        assertTrue(result.getAggregatedSummary().contains("বাংলা টেক্সট"));
        assertEquals(0.92, result.getConfidenceScore(), 0.001);
    }
}