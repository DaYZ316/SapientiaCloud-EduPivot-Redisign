package com.dayz.sc.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LatexImageRenderer {

    private static final float INLINE_FONT_SIZE = 15f;
    private static final float DISPLAY_FONT_SIZE = 18f;
    private static final Pattern TEXTTT_PATTERN = Pattern.compile("\\\\texttt\\{([^{}]*)}");

    public Optional<RenderedFormula> render(String latex, boolean display) {
        if (!StringUtils.hasText(latex)) {
            return Optional.empty();
        }

        try {
            TeXFormula formula = new TeXFormula(normalizeLatex(latex));
            TeXIcon icon = formula.createTeXIcon(
                    display ? TeXConstants.STYLE_DISPLAY : TeXConstants.STYLE_TEXT,
                    display ? DISPLAY_FONT_SIZE : INLINE_FONT_SIZE);
            icon.setInsets(new Insets(2, 2, 2, 2));

            BufferedImage image = new BufferedImage(
                    Math.max(icon.getIconWidth(), 1),
                    Math.max(icon.getIconHeight(), 1),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setColor(new Color(255, 255, 255, 0));
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                icon.paintIcon(new JLabel(), graphics, 0, 0);
            } finally {
                graphics.dispose();
            }

            return Optional.of(new RenderedFormula(latex, pngBytes(image), image.getWidth(), image.getHeight(), display));
        } catch (Exception e) {
            log.warn("Failed to render LaTeX formula for export: {}", latex, e);
            return Optional.empty();
        }
    }

    private static String normalizeLatex(String latex) {
        return TEXTTT_PATTERN.matcher(latex.trim()).replaceAll("\\\\mathtt{$1}");
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public record RenderedFormula(String source, byte[] png, int widthPx, int heightPx, boolean display) {
    }
}
