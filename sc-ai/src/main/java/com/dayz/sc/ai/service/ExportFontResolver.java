package com.dayz.sc.ai.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@Slf4j
public class ExportFontResolver {

    public static final String PDF_FONT_FAMILY = "Noto Sans CJK SC";

    private static final List<String> REGULAR_FONT_PATHS = List.of(
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/Deng.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf",
            "/usr/share/fonts/opentype/source-han-sans/SourceHanSansSC-Regular.otf",
            "/usr/share/fonts/opentype/source-han-sans/SourceHanSansCN-Regular.otf",
            "/usr/share/fonts/opentype/adobe-source-han-sans/SourceHanSansSC-Regular.otf"
    );
    private static final List<String> BOLD_FONT_PATHS = List.of(
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "C:/Windows/Fonts/msyhbd.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/Dengb.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Bold.otf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Bold.ttf",
            "/usr/share/fonts/opentype/source-han-sans/SourceHanSansSC-Bold.otf",
            "/usr/share/fonts/opentype/source-han-sans/SourceHanSansCN-Bold.otf",
            "/usr/share/fonts/opentype/adobe-source-han-sans/SourceHanSansSC-Bold.otf"
    );

    public void registerPdfFonts(PdfRendererBuilder builder) {
        PdfFontSet fonts = resolvePdfFonts();
        builder.useFont(fonts.regular(), fonts.family(), 400, BaseRendererBuilder.FontStyle.NORMAL, true);
        if (fonts.bold() != null) {
            builder.useFont(fonts.bold(), fonts.family(), 700, BaseRendererBuilder.FontStyle.NORMAL, true);
        }
    }

    PdfFontSet resolvePdfFonts() {
        File regular = firstExistingFile(REGULAR_FONT_PATHS);
        if (regular == null) {
            log.warn("PDF CJK font is not available. Tried paths: {}", String.join(", ", REGULAR_FONT_PATHS));
            throw new BusinessException(
                    ErrorCodes.SYSTEM_ERROR,
                    "PDF CJK font is not available. Install fonts-wqy-microhei or provide a Windows CJK font.");
        }
        return new PdfFontSet(PDF_FONT_FAMILY, regular, firstExistingFile(BOLD_FONT_PATHS));
    }

    private static File firstExistingFile(List<String> paths) {
        for (String path : paths) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    record PdfFontSet(String family, File regular, File bold) {
    }
}
