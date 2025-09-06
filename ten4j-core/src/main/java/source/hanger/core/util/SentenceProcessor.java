package source.hanger.core.util;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 辅助类：用于处理和解析句子片段
 */
public class SentenceProcessor {

    /**
     * 辅助方法：判断字符是否是标点符号
     */
    public static boolean isPunctuation(char c) {
        return c == ',' || c == '，' || c == ';' || c == '；' || c == ':' || c == '：' ||
            c == '.' || c == '。' || c == '!' || c == '！' || c == '?' || c == '？' ||
            c == '~' || c == '～';
    }

    /**
     * 判断给定的字符串是否只包含标点符号或为空。
     * @param text 待检查的字符串。
     * @return 如果字符串为空或只包含标点符号，则返回 true；否则返回 false。
     */
    public static boolean isPurePunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        return text.chars().allMatch(c -> isPunctuation((char) c));
    }

    /**
     * 判断给定的字符串是否只包含符号（非字母、非数字、非中文汉字）。
     * @param text 待检查的字符串。
     * @return 如果字符串为空或只包含符号，则返回 true；否则返回 false。
     */
    public static boolean isPureSymbols(String text) {
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) {
            return true;
        }
        // 正则表达式：查找字符串中是否存在任何字母、数字或中文汉字
        // \p{L}：匹配任何Unicode字母
        // \p{N}：匹配任何Unicode数字
        // \p{IsHan}：匹配任何中文汉字
        String semanticCharPattern = ".*[\\p{L}\\p{N}\\p{IsHan}].*";
        return !trimmedText.matches(semanticCharPattern);
    }

    /**
     * 辅助方法：解析句子片段
     * 返回完整的句子列表和剩余的片段
     * @param sentenceFragment 之前未完成的句子片段
     * @param content 当前接收到的文本内容
     * @return 包含完整句子列表和剩余片段的结果对象
     */
    public static SentenceParsingResult parseSentences(String sentenceFragment, String content) {
        List<String> sentences = new ArrayList<>();
        StringBuilder currentSentence = new StringBuilder(sentenceFragment);

        for (char c : content.toCharArray()) {
            currentSentence.append(c);
            if (isPunctuation(c)) {
                String strippedSentence = currentSentence.toString().trim();
                if (!strippedSentence.isEmpty()) {
                    sentences.add(strippedSentence);
                }
                currentSentence = new StringBuilder();
            }
        }
        return new SentenceParsingResult(sentences, currentSentence.toString());
    }

    @Data
    @AllArgsConstructor
    public static class SentenceParsingResult {
        private List<String> sentences;
        private String remainingFragment;
    }
}
