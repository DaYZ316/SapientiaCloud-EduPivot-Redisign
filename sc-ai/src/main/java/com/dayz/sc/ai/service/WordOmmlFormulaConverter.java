package com.dayz.sc.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.officeDocument.x2006.math.*;

import java.util.*;

@Slf4j
final class WordOmmlFormulaConverter {

    private static final int COMMAND_SYMBOL_CAPACITY = 136;
    private static final int DELIMITER_SYMBOL_CAPACITY = 24;
    private static final Map<String, String> COMMAND_SYMBOLS = createCommandSymbols();
    private static final Map<String, String> DELIMITER_SYMBOLS = createDelimiterSymbols();
    private static final Set<String> RAW_TEXT_COMMANDS = Set.of(
            "text", "textrm", "textbf", "textit", "operatorname", "mbox"
    );
    private static final Set<String> STYLE_PASSTHROUGH_COMMANDS = Set.of(
            "mathrm", "mathbf", "mathit", "mathsf", "mathtt",
            "mathbb", "mathcal", "mathfrak",
            "boldsymbol", "bm"
    );
    private static final Set<String> SPACE_COMMANDS = Set.of(
            ",", ";", ":", "quad", "qquad", "enspace", "thinspace", "medspace"
    );
    private static final Set<String> NOOP_COMMANDS = Set.of(
            "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
            "limits", "nolimits", "!", "left.", "right."
    );

    private WordOmmlFormulaConverter() {
    }

    static boolean appendFormula(XWPFParagraph paragraph, String latex, boolean display) {
        if (paragraph == null || latex == null || latex.isBlank()) {
            return false;
        }

        try {
            Node root = new Parser(latex).parse();
            if (root instanceof EmptyNode) {
                return false;
            }

            if (display) {
                CTOMathPara mathPara = paragraph.getCTP().addNewOMathPara();
                CTOMathParaPr mathParaPr = mathPara.addNewOMathParaPr();
                CTOMathJc mathJc = mathParaPr.addNewJc();
                mathJc.setVal(STJc.CENTER_GROUP);
                appendNode(asContainer(mathPara.addNewOMath()), root);
                return true;
            }

            appendNode(asContainer(paragraph.getCTP().addNewOMath()), root);
            return true;
        } catch (UnsupportedLatexException e) {
            log.debug("Unsupported latex for Word OMML conversion. latex={}", latex);
            return false;
        } catch (Exception e) {
            log.warn("Failed to convert latex to Word OMML. latex={}", latex, e);
            return false;
        }
    }

    private static void appendNode(MathContainer container, Node node) {
        switch (node) {
            case null -> {
            }
            case EmptyNode ignored -> {
            }
            case SequenceNode(var children) -> {
                for (Node child : children) {
                    appendNode(container, child);
                }
            }
            case TextNode(var text) -> appendText(container, text);
            case DelimitedNode(var leftDelimiter, var content, var rightDelimiter) -> {
                appendText(container, leftDelimiter);
                appendNode(container, content);
                appendText(container, rightDelimiter);
            }
            case FractionNode(var numerator, var denominator) -> {
                CTF fraction = container.addFraction();
                appendNode(asContainer(fraction.addNewNum()), numerator);
                appendNode(asContainer(fraction.addNewDen()), denominator);
            }
            case RadicalNode(var degree, var radicand) -> {
                CTRad radical = container.addRadical();
                if (isEmptyNode(degree)) {
                    CTOnOff degHide = radical.addNewRadPr().addNewDegHide();
                    degHide.setVal("1");
                    radical.addNewDeg();
                } else {
                    appendNode(asContainer(radical.addNewDeg()), degree);
                }
                appendNode(asContainer(radical.addNewE()), radicand);
            }
            case ScriptNode(var base, var subscript, var superscript) ->
                    appendScriptNode(container, base, subscript, superscript);
        }
    }

    private static void appendScriptNode(MathContainer container, Node base, Node subscript, Node superscript) {
        if (!isEmptyNode(subscript) && !isEmptyNode(superscript)) {
            CTSSubSup subSup = container.addSubSuperscript();
            appendNode(asContainer(subSup.addNewE()), base);
            appendNode(asContainer(subSup.addNewSub()), subscript);
            appendNode(asContainer(subSup.addNewSup()), superscript);
            return;
        }

        if (!isEmptyNode(subscript)) {
            CTSSub sub = container.addSubscript();
            appendNode(asContainer(sub.addNewE()), base);
            appendNode(asContainer(sub.addNewSub()), subscript);
            return;
        }

        if (!isEmptyNode(superscript)) {
            CTSSup sup = container.addSuperscript();
            appendNode(asContainer(sup.addNewE()), base);
            appendNode(asContainer(sup.addNewSup()), superscript);
            return;
        }

        appendNode(container, base);
    }

    private static boolean isEmptyNode(Node node) {
        return node == null || node instanceof EmptyNode;
    }

    private static void appendText(MathContainer container, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        CTR run = container.addRun();
        CTText value = run.addNewT2();
        value.setStringValue(text);
        if (hasPreservedSpaces(text)) {
            value.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);
        }
    }

    private static boolean hasPreservedSpaces(String text) {
        return !text.isEmpty() && (Character.isWhitespace(text.charAt(0))
                || Character.isWhitespace(text.charAt(text.length() - 1))
                || text.contains("  "));
    }

    private static MathContainer asContainer(CTOMath math) {
        return new MathContainer() {
            @Override
            public CTR addRun() {
                return math.addNewR();
            }

            @Override
            public CTF addFraction() {
                return math.addNewF();
            }

            @Override
            public CTRad addRadical() {
                return math.addNewRad();
            }

            @Override
            public CTSSub addSubscript() {
                return math.addNewSSub();
            }

            @Override
            public CTSSup addSuperscript() {
                return math.addNewSSup();
            }

            @Override
            public CTSSubSup addSubSuperscript() {
                return math.addNewSSubSup();
            }
        };
    }

    private static MathContainer asContainer(CTOMathArg mathArg) {
        return new MathContainer() {
            @Override
            public CTR addRun() {
                return mathArg.addNewR();
            }

            @Override
            public CTF addFraction() {
                return mathArg.addNewF();
            }

            @Override
            public CTRad addRadical() {
                return mathArg.addNewRad();
            }

            @Override
            public CTSSub addSubscript() {
                return mathArg.addNewSSub();
            }

            @Override
            public CTSSup addSuperscript() {
                return mathArg.addNewSSup();
            }

            @Override
            public CTSSubSup addSubSuperscript() {
                return mathArg.addNewSSubSup();
            }
        };
    }

    private static Node simplify(List<Node> nodes) {
        List<Node> normalized = new ArrayList<>();
        for (Node node : nodes) {
            if (node instanceof EmptyNode) {
                continue;
            }
            if (node instanceof SequenceNode(var children)) {
                normalized.addAll(children);
                continue;
            }
            normalized.add(node);
        }

        if (normalized.isEmpty()) {
            return new EmptyNode();
        }
        if (normalized.size() == 1) {
            return normalized.getFirst();
        }
        return new SequenceNode(List.copyOf(normalized));
    }

    private static Node applyCombiningAccent(Node node, String accent) {
        String plainText = flattenPlainText(node);
        if (plainText.isEmpty()) {
            return node;
        }
        return new TextNode(plainText + accent);
    }

    private static String flattenPlainText(Node node) {
        return switch (node) {
            case null -> "";
            case EmptyNode ignored -> "";
            case TextNode(var text) -> text;
            case DelimitedNode(var leftDelimiter, var content, var rightDelimiter) ->
                    leftDelimiter + flattenPlainText(content) + rightDelimiter;
            case SequenceNode(var children) -> {
                StringBuilder builder = new StringBuilder();
                for (Node child : children) {
                    builder.append(flattenPlainText(child));
                }
                yield builder.toString();
            }
            case FractionNode ignored -> "";
            case RadicalNode ignored -> "";
            case ScriptNode ignored -> "";
        };
    }

    private static Map<String, String> createCommandSymbols() {
        Map<String, String> symbols = new HashMap<>(COMMAND_SYMBOL_CAPACITY);
        putGreekSymbols(symbols);
        putOperatorSymbols(symbols);
        putRelationSymbols(symbols);
        putSetLogicSymbols(symbols);
        putArrowSymbols(symbols);
        putAggregateSymbols(symbols);
        putFunctionSymbols(symbols);
        putGeometrySymbols(symbols);
        return symbols;
    }

    private static void putGreekSymbols(Map<String, String> symbols) {
        symbols.put("alpha", "α");
        symbols.put("beta", "β");
        symbols.put("gamma", "γ");
        symbols.put("delta", "δ");
        symbols.put("epsilon", "ε");
        symbols.put("varepsilon", "ε");
        symbols.put("zeta", "ζ");
        symbols.put("eta", "η");
        symbols.put("theta", "θ");
        symbols.put("vartheta", "ϑ");
        symbols.put("iota", "ι");
        symbols.put("kappa", "κ");
        symbols.put("lambda", "λ");
        symbols.put("mu", "μ");
        symbols.put("nu", "ν");
        symbols.put("xi", "ξ");
        symbols.put("pi", "π");
        symbols.put("varpi", "ϖ");
        symbols.put("rho", "ρ");
        symbols.put("varrho", "ϱ");
        symbols.put("sigma", "σ");
        symbols.put("varsigma", "ς");
        symbols.put("tau", "τ");
        symbols.put("upsilon", "υ");
        symbols.put("phi", "φ");
        symbols.put("varphi", "ϕ");
        symbols.put("chi", "χ");
        symbols.put("psi", "ψ");
        symbols.put("omega", "ω");
        symbols.put("Gamma", "Γ");
        symbols.put("Delta", "Δ");
        symbols.put("Theta", "Θ");
        symbols.put("Lambda", "Λ");
        symbols.put("Xi", "Ξ");
        symbols.put("Pi", "Π");
        symbols.put("Sigma", "Σ");
        symbols.put("Upsilon", "Υ");
        symbols.put("Phi", "Φ");
        symbols.put("Psi", "Ψ");
        symbols.put("Omega", "Ω");
    }

    private static void putOperatorSymbols(Map<String, String> symbols) {
        symbols.put("pm", "±");
        symbols.put("mp", "∓");
        symbols.put("times", "×");
        symbols.put("div", "÷");
        symbols.put("cdot", "·");
        symbols.put("ast", "∗");
        symbols.put("star", "⋆");
        symbols.put("circ", "∘");
        symbols.put("bullet", "∙");
    }

    private static void putRelationSymbols(Map<String, String> symbols) {
        symbols.put("leq", "≤");
        symbols.put("le", "≤");
        symbols.put("geq", "≥");
        symbols.put("ge", "≥");
        symbols.put("neq", "≠");
        symbols.put("ne", "≠");
        symbols.put("approx", "≈");
        symbols.put("equiv", "≡");
        symbols.put("sim", "∼");
        symbols.put("simeq", "≃");
        symbols.put("propto", "∝");
        symbols.put("infty", "∞");
        symbols.put("partial", "∂");
        symbols.put("nabla", "∇");
    }

    private static void putSetLogicSymbols(Map<String, String> symbols) {
        symbols.put("forall", "∀");
        symbols.put("exists", "∃");
        symbols.put("in", "∈");
        symbols.put("notin", "∉");
        symbols.put("subset", "⊂");
        symbols.put("subseteq", "⊆");
        symbols.put("subsetneq", "⊊");
        symbols.put("supset", "⊃");
        symbols.put("supseteq", "⊇");
        symbols.put("supsetneq", "⊋");
        symbols.put("cup", "∪");
        symbols.put("cap", "∩");
        symbols.put("setminus", "∖");
        symbols.put("emptyset", "∅");
        symbols.put("land", "∧");
        symbols.put("wedge", "∧");
        symbols.put("lor", "∨");
        symbols.put("vee", "∨");
        symbols.put("neg", "¬");
        symbols.put("lnot", "¬");
    }

    private static void putArrowSymbols(Map<String, String> symbols) {
        symbols.put("to", "→");
        symbols.put("rightarrow", "→");
        symbols.put("leftarrow", "←");
        symbols.put("leftrightarrow", "↔");
        symbols.put("Rightarrow", "⇒");
        symbols.put("Leftarrow", "⇐");
        symbols.put("Leftrightarrow", "⇔");
        symbols.put("mapsto", "↦");
        symbols.put("longrightarrow", "⟶");
        symbols.put("Longrightarrow", "⟹");
    }

    private static void putAggregateSymbols(Map<String, String> symbols) {
        symbols.put("sum", "∑");
        symbols.put("prod", "∏");
        symbols.put("coprod", "∐");
        symbols.put("bigvee", "⋁");
        symbols.put("bigwedge", "⋀");
        symbols.put("oplus", "⊕");
        symbols.put("otimes", "⊗");
        symbols.put("int", "∫");
        symbols.put("iint", "∬");
        symbols.put("iiint", "∭");
        symbols.put("oint", "∮");
    }

    private static void putFunctionSymbols(Map<String, String> symbols) {
        symbols.put("sin", "sin");
        symbols.put("cos", "cos");
        symbols.put("tan", "tan");
        symbols.put("cot", "cot");
        symbols.put("sec", "sec");
        symbols.put("csc", "csc");
        symbols.put("arcsin", "arcsin");
        symbols.put("arccos", "arccos");
        symbols.put("arctan", "arctan");
        symbols.put("ln", "ln");
        symbols.put("log", "log");
        symbols.put("lg", "lg");
        symbols.put("exp", "exp");
        symbols.put("lim", "lim");
        symbols.put("max", "max");
        symbols.put("min", "min");
        symbols.put("sup", "sup");
        symbols.put("inf", "inf");
        symbols.put("det", "det");
        symbols.put("deg", "deg");
        symbols.put("Pr", "Pr");
    }

    private static void putGeometrySymbols(Map<String, String> symbols) {
        symbols.put("because", "∵");
        symbols.put("therefore", "∴");
        symbols.put("angle", "∠");
        symbols.put("triangle", "△");
        symbols.put("perp", "⊥");
        symbols.put("parallel", "∥");
        symbols.put("degree", "°");
        symbols.put("prime", "′");
        symbols.put("ldotp", ".");
    }

    private static Map<String, String> createDelimiterSymbols() {
        Map<String, String> delimiters = new HashMap<>(DELIMITER_SYMBOL_CAPACITY);
        delimiters.put("(", "(");
        delimiters.put(")", ")");
        delimiters.put("[", "[");
        delimiters.put("]", "]");
        delimiters.put("{", "{");
        delimiters.put("}", "}");
        delimiters.put("|", "|");
        delimiters.put("lbrace", "{");
        delimiters.put("rbrace", "}");
        delimiters.put("langle", "⟨");
        delimiters.put("rangle", "⟩");
        delimiters.put("mid", "|");
        delimiters.put("lvert", "|");
        delimiters.put("rvert", "|");
        delimiters.put("lVert", "‖");
        delimiters.put("rVert", "‖");
        delimiters.put("lfloor", "⌊");
        delimiters.put("rfloor", "⌋");
        delimiters.put("lceil", "⌈");
        delimiters.put("rceil", "⌉");
        return delimiters;
    }

    private interface MathContainer {
        /**
         * Adds a math run element to the current container.
         *
         * @return the created run element
         */
        CTR addRun();

        /**
         * Adds a fraction element to the current container.
         *
         * @return the created fraction element
         */
        CTF addFraction();

        /**
         * Adds a radical element to the current container.
         *
         * @return the created radical element
         */
        CTRad addRadical();

        /**
         * Adds a subscript element to the current container.
         *
         * @return the created subscript element
         */
        CTSSub addSubscript();

        /**
         * Adds a superscript element to the current container.
         *
         * @return the created superscript element
         */
        CTSSup addSuperscript();

        /**
         * Adds a combined subscript and superscript element to the current container.
         *
         * @return the created subscript and superscript element
         */
        CTSSubSup addSubSuperscript();
    }

    private sealed interface Node permits EmptyNode, SequenceNode, TextNode, FractionNode, RadicalNode, ScriptNode, DelimitedNode {
    }

    private record EmptyNode() implements Node {
    }

    private record SequenceNode(List<Node> children) implements Node {
    }

    private record TextNode(String text) implements Node {
    }

    private record FractionNode(Node numerator, Node denominator) implements Node {
    }

    private record RadicalNode(Node degree, Node radicand) implements Node {
    }

    private record ScriptNode(Node base, Node subscript, Node superscript) implements Node {
    }

    private record DelimitedNode(String leftDelimiter, Node content, String rightDelimiter) implements Node {
    }

    private static final class UnsupportedLatexException extends RuntimeException {

        private UnsupportedLatexException(String message) {
            super(message);
        }
    }

    private static final class Parser {

        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input == null ? "" : input;
        }

        private Node parse() {
            return simplify(parseNodes(null, false));
        }

        private List<Node> parseNodes(Character stopChar, boolean stopAtRight) {
            List<Node> nodes = new ArrayList<>();
            while (!isEnd()) {
                if (stopChar != null && peek() == stopChar) {
                    break;
                }
                if (stopAtRight && isRightCommandAhead()) {
                    break;
                }

                Node node = parseAtomWithScripts();
                if (!(node instanceof EmptyNode)) {
                    nodes.add(node);
                } else if (!isEnd()) {
                    break;
                }
            }
            return nodes;
        }

        private Node parseAtomWithScripts() {
            skipWhitespace();
            if (isEnd()) {
                return new EmptyNode();
            }

            Node base = parsePrimary();
            if (base == null || base instanceof EmptyNode) {
                return new EmptyNode();
            }

            Node subscript = null;
            Node superscript = null;
            while (!isEnd()) {
                skipWhitespace();
                if (isEnd()) {
                    break;
                }

                char current = peek();
                if (current == '_') {
                    index++;
                    subscript = parseRequiredArgument();
                    continue;
                }
                if (current == '^') {
                    index++;
                    superscript = parseRequiredArgument();
                    continue;
                }
                break;
            }

            if (subscript == null && superscript == null) {
                return base;
            }
            return new ScriptNode(base, subscript, superscript);
        }

        private Node parsePrimary() {
            skipWhitespace();
            if (isEnd()) {
                return new EmptyNode();
            }

            char current = peek();
            if (current == '{') {
                index++;
                Node group = simplify(parseNodes('}', false));
                consumeIf('}');
                return group;
            }
            if (current == '(') {
                return parseDelimitedGroup('(', ')', "(", ")");
            }
            if (current == '[') {
                return parseDelimitedGroup('[', ']', "[", "]");
            }
            if (current == '\\') {
                return parseCommand();
            }
            if (current == '}' || current == ')' || current == ']') {
                return new EmptyNode();
            }
            return new TextNode(readPlainText());
        }

        private Node parseDelimitedGroup(char open, char close, String leftDelimiter, String rightDelimiter) {
            index++;
            Node content = simplify(parseNodes(close, false));
            consumeIf(close);
            return new DelimitedNode(leftDelimiter, content, rightDelimiter);
        }

        private Node parseCommand() {
            index++;
            if (isEnd()) {
                return new TextNode("\\");
            }

            if (!Character.isLetter(peek())) {
                char escaped = input.charAt(index++);
                if (SPACE_COMMANDS.contains(String.valueOf(escaped))) {
                    return new TextNode(" ");
                }
                return new TextNode(DELIMITER_SYMBOLS.getOrDefault(String.valueOf(escaped), String.valueOf(escaped)));
            }

            String command = readCommandName();
            if (command.isEmpty()) {
                return new EmptyNode();
            }
            if (NOOP_COMMANDS.contains(command)) {
                return new EmptyNode();
            }
            if (SPACE_COMMANDS.contains(command)) {
                return new TextNode(" ");
            }
            if (RAW_TEXT_COMMANDS.contains(command)) {
                return new TextNode(parseRawTextArgument());
            }
            if (STYLE_PASSTHROUGH_COMMANDS.contains(command)) {
                return parseRequiredArgument();
            }

            return switch (command) {
                case "frac", "dfrac", "tfrac" -> new FractionNode(parseRequiredArgument(), parseRequiredArgument());
                case "sqrt" -> parseRoot();
                case "left" -> parseLeftRightGroup();
                case "right" -> new EmptyNode();
                case "bar" -> applyCombiningAccent(parseRequiredArgument(), "\u0304");
                case "overline" -> applyCombiningAccent(parseRequiredArgument(), "\u0305");
                case "hat" -> applyCombiningAccent(parseRequiredArgument(), "\u0302");
                case "vec", "overrightarrow" -> applyCombiningAccent(parseRequiredArgument(), "\u20d7");
                case "underline" -> applyCombiningAccent(parseRequiredArgument(), "\u0332");
                case "dots", "cdots", "ldots" -> new TextNode("\u2026");
                default -> {
                    String mapped = COMMAND_SYMBOLS.get(command);
                    if (mapped != null) {
                        yield new TextNode(mapped);
                    }
                    throw unsupported(command);
                }
            };
        }

        private Node parseRoot() {
            skipWhitespace();
            Node degree = null;
            if (!isEnd() && peek() == '[') {
                index++;
                degree = simplify(parseNodes(']', false));
                consumeIf(']');
            }
            return new RadicalNode(degree, parseRequiredArgument());
        }

        private Node parseLeftRightGroup() {
            String leftDelimiter = parseDelimiterToken();
            Node content = simplify(parseNodes(null, true));
            String rightDelimiter = "";
            if (isRightCommandAhead()) {
                index += "\\right".length();
                rightDelimiter = parseDelimiterToken();
            }
            return new DelimitedNode(leftDelimiter, content, rightDelimiter);
        }

        private String parseDelimiterToken() {
            skipWhitespace();
            if (isEnd()) {
                return "";
            }

            if (peek() == '.') {
                index++;
                return "";
            }

            if (peek() == '\\') {
                index++;
                if (isEnd()) {
                    return "";
                }
                if (!Character.isLetter(peek())) {
                    char escaped = input.charAt(index++);
                    return DELIMITER_SYMBOLS.getOrDefault(String.valueOf(escaped), String.valueOf(escaped));
                }
                String command = readCommandName();
                String mappedDelimiter = DELIMITER_SYMBOLS.get(command);
                if (mappedDelimiter != null) {
                    return mappedDelimiter;
                }
                String mappedSymbol = COMMAND_SYMBOLS.get(command);
                if (mappedSymbol != null) {
                    return mappedSymbol;
                }
                throw unsupported(command);
            }

            return String.valueOf(input.charAt(index++));
        }

        private Node parseRequiredArgument() {
            skipWhitespace();
            if (isEnd()) {
                return new EmptyNode();
            }

            if (peek() == '{') {
                index++;
                Node group = simplify(parseNodes('}', false));
                consumeIf('}');
                return group;
            }
            if (peek() == '(') {
                return parseDelimitedGroup('(', ')', "(", ")");
            }
            if (peek() == '[') {
                return parseDelimitedGroup('[', ']', "[", "]");
            }
            return parseAtomWithScripts();
        }

        private String parseRawTextArgument() {
            skipWhitespace();
            if (isEnd()) {
                return "";
            }

            if (peek() != '{') {
                if (peek() == '\\') {
                    Node node = parseCommand();
                    return flattenPlainText(node);
                }
                return String.valueOf(input.charAt(index++));
            }

            index++;
            int depth = 1;
            StringBuilder builder = new StringBuilder();
            while (!isEnd() && depth > 0) {
                char current = input.charAt(index++);
                if (current == '{') {
                    depth++;
                    if (depth > 1) {
                        builder.append(current);
                    }
                    continue;
                }
                if (current == '}') {
                    depth--;
                    if (depth > 0) {
                        builder.append(current);
                    }
                    continue;
                }
                if (current == '\\' && !isEnd()) {
                    char escaped = input.charAt(index);
                    if (!Character.isLetter(escaped)) {
                        builder.append(DELIMITER_SYMBOLS.getOrDefault(String.valueOf(escaped), String.valueOf(escaped)));
                        index++;
                        continue;
                    }
                }
                builder.append(current);
            }
            return builder.toString();
        }

        private String readCommandName() {
            int start = index;
            while (!isEnd() && Character.isLetter(peek())) {
                index++;
            }
            return input.substring(start, index);
        }

        private String readPlainText() {
            StringBuilder builder = new StringBuilder();
            while (!isEnd()) {
                char current = peek();
                if (Character.isWhitespace(current) || current == '\\' || current == '{' || current == '}'
                        || current == '^' || current == '_' || current == '(' || current == ')' || current == '['
                        || current == ']') {
                    break;
                }
                builder.append(current == '~' ? ' ' : current);
                index++;
            }
            return builder.toString();
        }

        private boolean isRightCommandAhead() {
            return input.startsWith("\\right", index)
                    && (index + 6 >= input.length() || !Character.isLetter(input.charAt(index + 6)));
        }

        private boolean consumeIf(char expected) {
            if (!isEnd() && peek() == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(peek())) {
                index++;
            }
        }

        private boolean isEnd() {
            return index >= input.length();
        }

        private char peek() {
            return input.charAt(index);
        }

        private UnsupportedLatexException unsupported(String command) {
            return new UnsupportedLatexException("Unsupported latex command: \\" + command);
        }
    }
}
