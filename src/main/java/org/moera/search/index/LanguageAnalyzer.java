package org.moera.search.index;

import java.util.List;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.moera.search.util.Util;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class LanguageAnalyzer {

    private final LanguageDetector detector;

    public LanguageAnalyzer() {
        detector = LanguageDetectorBuilder.fromLanguages(Language.ENGLISH, Language.RUSSIAN).build();
    }

    public void analyze(IndexedDocument document) {
        if (!ObjectUtils.isEmpty(document.getSubject())) {
            var lang = detector.detectLanguageOf(document.getSubject());
            if (lang == Language.RUSSIAN) {
                document.setSubjectRu(document.getSubject());
            }
        }
        if (!ObjectUtils.isEmpty(document.getText())) {
            var lang = detector.detectLanguageOf(Util.clearHtml(document.getText()));
            if (lang == Language.RUSSIAN) {
                document.setTextRu(document.getText());
            }
        }
        if (!ObjectUtils.isEmpty(document.getMediaText())) {
            var lang = detector.detectLanguageOf(Util.clearHtml(document.getMediaText()));
            if (lang == Language.RUSSIAN) {
                document.setMediaTextRu(document.getMediaText());
            }
        }
    }

    public List<String> getSearchFields(String text) {
        var lang = detector.detectLanguageOf(text);
        return switch (lang) {
            case RUSSIAN -> List.of("subject^2", "text", "mediaText", "subjectRu^2", "textRu", "mediaTextRu");
            default -> List.of("subject^2", "text", "mediaText");
        };
    }

}
