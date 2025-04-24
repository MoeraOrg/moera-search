package org.moera.search.index;

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
    }

}
