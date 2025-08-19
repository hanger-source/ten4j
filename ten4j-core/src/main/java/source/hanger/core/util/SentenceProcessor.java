package source.hanger.core.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 辅助类：用于处理和解析句子片段
 */
public class SentenceProcessor {

    /**
     * 辅助方法：判断字符是否是标点符号
     */
    private static boolean isPunctuation(char c) {
        return c == ',' || c == '，' || c == ';' || c == '；' || c == ':' || c == '：' ||
            c == '.' || c == '。' || c == '!' || c == '！' || c == '?' || c == '？';
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
