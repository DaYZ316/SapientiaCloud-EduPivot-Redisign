package com.dayz.sc.ai.config;

/**
 * Shared output rules for model-generated LaTeX that must render in the frontend KaTeX pipeline.
 */
public final class LatexPromptRules {

    public static final String TEXT = """
                - 解释数学、物理、化学内容时，优先使用标准 LaTeX 表达公式。
                - 行内公式只使用 `$...$`，独立推导、方程组或重要公式使用独占起止行的 `$$...$$`。
                - 遵循 LaTeX Project 官方文档和 amsmath 用户指南中的 LaTeX2e/AMS 数学语法，并限制在前端 KaTeX 支持范围内：命令必须完整、括号成对，不要输出裸露的 \\nabla、\\frac、\\begin 等 TeX 命令。
                - 多个独立公式或方程组必须一式一行；在 `aligned` 中使用 `\\\\` 作为换行分隔，不要只用 `\\quad`、空格或多个 `&` 横向拼接。
                - 中文说明放在公式外，或在公式内使用 `\\text{...}`；不要把普通中文直接裸写进数学模式。
                - 化学方程式、离子、电荷和状态符号使用 `\\ce{...}`，例如 `$\\ce{2H2 + O2 -> 2H2O}$`。
                - 不要输出 MathJax 配置说明，不要输出未闭合的公式定界符。
                """;

    private LatexPromptRules() {
    }
}
