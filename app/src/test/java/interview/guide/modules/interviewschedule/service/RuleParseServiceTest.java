package interview.guide.modules.interviewschedule.service;

import interview.guide.modules.interviewschedule.model.CreateInterviewRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RuleParseService
 * Tests parsing of interview invitations from different platforms
 */
@DisplayName("RuleParseService Tests")
class RuleParseServiceTest {

    private final RuleParseService ruleParseService = new RuleParseService();

    @Nested
    @DisplayName("Feishu Format Parsing")
    class ParseFeishuTests {

        @Test
        @DisplayName("Should parse complete Feishu invitation successfully")
        void testParseCompleteFeishuInvitation() {
            String rawText = """
                飞书视频会议邀请
                时间：2026-04-10 14:30
                公司：阿里巴巴
                岗位：高级Java工程师
                https://meeting.feishu.cn/abc123
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNotNull(request);
            assertEquals("阿里巴巴", request.getCompanyName());
            assertEquals("高级Java工程师", request.getPosition());
            assertEquals(LocalDateTime.of(2026, 4, 10, 14, 30), request.getInterviewTime());
            assertEquals("https://meeting.feishu.cn/abc123", request.getMeetingLink());
            assertEquals("VIDEO", request.getInterviewType());
        }

        @Test
        @DisplayName("Should parse Feishu invitation with round number")
        void testParseFeishuWithRoundNumber() {
            String rawText = """
                时间：2026-04-10 14:30
                公司：字节跳动
                岗位：前端开发
                第二轮面试
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNotNull(request);
            assertEquals("字节跳动", request.getCompanyName());
            assertEquals("前端开发", request.getPosition());
            assertEquals(2, request.getRoundNumber());
        }

        @Test
        @DisplayName("Should return null when required field is missing - no time")
        void testParseFeishuMissingTime() {
            String rawText = """
                公司：阿里巴巴
                岗位：高级Java工程师
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNull(request, "Should return null when interviewTime is missing");
        }

        @Test
        @DisplayName("Should return null when required field is missing - no company")
        void testParseFeishuMissingCompany() {
            String rawText = """
                时间：2026-04-10 14:30
                岗位：高级Java工程师
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNull(request, "Should return null when companyName is missing");
        }

        @Test
        @DisplayName("Should return null when required field is missing - no position")
        void testParseFeishuMissingPosition() {
            String rawText = """
                时间：2026-04-10 14:30
                公司：阿里巴巴
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNull(request, "Should return null when position is missing");
        }

        @Test
        @DisplayName("Should return null for null input")
        void testParseFeishuNullInput() {
            CreateInterviewRequest request = ruleParseService.parseFeishu(null);
            assertNull(request);
        }

        @Test
        @DisplayName("Should return null for empty input")
        void testParseFeishuEmptyInput() {
            CreateInterviewRequest request = ruleParseService.parseFeishu("");
            assertNull(request);
        }

        @Test
        @DisplayName("Should handle different date formats")
        void testParseFeishuDifferentDateFormat() {
            String rawText = """
                时间：2026/04/10 14:30
                公司：腾讯
                岗位：产品经理
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNotNull(request);
            assertEquals(LocalDateTime.of(2026, 4, 10, 14, 30), request.getInterviewTime());
        }
    }

    @Nested
    @DisplayName("Tencent Meeting Format Parsing")
    class ParseTencentTests {

        @Test
        @DisplayName("Should parse complete Tencent meeting invitation")
        void testParseCompleteTencentInvitation() {
            String rawText = """
                腾讯会议邀请
                2026-04-10 14:30
                会议号：123456789
                密码：1234
                公司：华为
                岗位：架构师
                """;

            CreateInterviewRequest request = ruleParseService.parseTencent(rawText);

            assertNotNull(request);
            assertEquals("华为", request.getCompanyName());
            assertEquals("架构师", request.getPosition());
            assertEquals(LocalDateTime.of(2026, 4, 10, 14, 30), request.getInterviewTime());
            assertTrue(request.getMeetingLink().contains("123456789"));
            assertTrue(request.getMeetingLink().contains("1234"));
        }

        @Test
        @DisplayName("Should return null when required field is missing")
        void testParseTencentMissingRequiredField() {
            String rawText = """
                2026-04-10 14:30
                会议号：123456789
                """;

            CreateInterviewRequest request = ruleParseService.parseTencent(rawText);

            assertNull(request, "Should return null when company or position is missing");
        }

        @Test
        @DisplayName("Should return null for null input")
        void testParseTencentNullInput() {
            CreateInterviewRequest request = ruleParseService.parseTencent(null);
            assertNull(request);
        }
    }

    @Nested
    @DisplayName("Zoom Format Parsing")
    class ParseZoomTests {

        @Test
        @DisplayName("Should parse complete Zoom invitation")
        void testParseCompleteZoomInvitation() {
            String rawText = """
                Zoom Meeting Invitation
                https://zoom.us/j/123456789
                2026-04-10 14:30
                公司：Google
                岗位：软件工程师
                """;

            CreateInterviewRequest request = ruleParseService.parseZoom(rawText);

            assertNotNull(request);
            assertEquals("Google", request.getCompanyName());
            assertEquals("软件工程师", request.getPosition());
            assertEquals(LocalDateTime.of(2026, 4, 10, 14, 30), request.getInterviewTime());
            assertTrue(request.getMeetingLink().contains("zoom.us"));
        }

        @Test
        @DisplayName("Should return null when required field is missing")
        void testParseZoomMissingRequiredField() {
            String rawText = """
                https://zoom.us/j/123456789
                2026-04-10 14:30
                """;

            CreateInterviewRequest request = ruleParseService.parseZoom(rawText);

            assertNull(request, "Should return null when company or position is missing");
        }

        @Test
        @DisplayName("Should return null for null input")
        void testParseZoomNullInput() {
            CreateInterviewRequest request = ruleParseService.parseZoom(null);
            assertNull(request);
        }
    }

    @Nested
    @DisplayName("Round Number Parsing")
    class RoundNumberTests {

        @Test
        @DisplayName("Should parse Chinese numerals")
        void testParseChineseNumerals() {
            assertEquals(1, ruleParseService.parseRoundNumber("第一轮"));
            assertEquals(2, ruleParseService.parseRoundNumber("第二轮"));
            assertEquals(3, ruleParseService.parseRoundNumber("第三轮"));
            assertEquals(10, ruleParseService.parseRoundNumber("第十轮"));
        }

        @Test
        @DisplayName("Should parse Arabic numerals")
        void testParseArabicNumerals() {
            assertEquals(1, ruleParseService.parseRoundNumber("第1轮"));
            assertEquals(2, ruleParseService.parseRoundNumber("第2轮"));
            assertEquals(5, ruleParseService.parseRoundNumber("第5场"));
        }

        @Test
        @DisplayName("Should return 1 for unknown format")
        void testParseUnknownFormat() {
            assertEquals(1, ruleParseService.parseRoundNumber("面试"));
            assertEquals(1, ruleParseService.parseRoundNumber(""));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle whitespace-only input")
        void testWhitespaceOnlyInput() {
            assertNull(ruleParseService.parseFeishu("   \n\t  "));
            assertNull(ruleParseService.parseTencent("   \n\t  "));
            assertNull(ruleParseService.parseZoom("   \n\t  "));
        }

        @Test
        @DisplayName("Should handle malformed date")
        void testMalformedDate() {
            String rawText = """
                时间：invalid-date
                公司：阿里巴巴
                岗位：高级Java工程师
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNull(request, "Should return null when date parsing fails");
        }

        @Test
        @DisplayName("Should handle special characters in company name")
        void testSpecialCharactersInCompanyName() {
            String rawText = """
                时间：2026-04-10 14:30
                公司：阿里巴巴（中国）有限公司
                岗位：高级Java工程师
                """;

            CreateInterviewRequest request = ruleParseService.parseFeishu(rawText);

            assertNotNull(request);
            assertTrue(request.getCompanyName().contains("阿里巴巴"));
        }
    }
}
