package io.kalo.content.vfx.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small boolean language for VFX {@code until:} and {@code while:} conditions.
 *
 * <p>Supports placeholders in braces, {@code perm:<node>}, quoted words, comparisons
 * ({@code == != < > <= >=}), {@code &&}, {@code ||}, {@code !} and parentheses. A
 * comparison that isn't numeric falls back to string equality; ordered comparisons on
 * non-numbers are false.</p>
 */
public final class VfxCondition {

    public interface Context {
        @Nullable String value(@NotNull String name);

        boolean permission(@NotNull String node);
    }

    private interface Node {
        boolean evaluate(@NotNull Context context);
    }

    private final Node root;

    private VfxCondition(@NotNull Node root) {
        this.root = root;
    }

    public static @NotNull VfxCondition parse(@NotNull String expression) {
        Parser parser = new Parser(expression);
        Node node = parser.parseOr();
        parser.expectEnd();
        return new VfxCondition(node);
    }

    public boolean evaluate(@NotNull Context context) {
        return root.evaluate(context);
    }

    private record Not(Node child) implements Node {
        @Override
        public boolean evaluate(Context context) {
            return !child.evaluate(context);
        }
    }

    private record And(Node left, Node right) implements Node {
        @Override
        public boolean evaluate(Context context) {
            return left.evaluate(context) && right.evaluate(context);
        }
    }

    private record Or(Node left, Node right) implements Node {
        @Override
        public boolean evaluate(Context context) {
            return left.evaluate(context) || right.evaluate(context);
        }
    }

    private record Comparison(Operand left, String operator, @Nullable Operand right)
            implements Node {
        @Override
        public boolean evaluate(Context context) {
            if (right == null) {
                return left.truthy(context);
            }
            Value a = left.value(context);
            Value b = right.value(context);
            if (a.booleanValue != null || b.booleanValue != null) {
                boolean one = a.booleanValue != null ? a.booleanValue : truthyText(a.text);
                boolean two = b.booleanValue != null ? b.booleanValue : truthyText(b.text);
                if (!operator.equals("==") && !operator.equals("!=")) {
                    return false;
                }
                return operator.equals("==") ? one == two : one != two;
            }
            Double leftNumber = number(a.text);
            Double rightNumber = number(b.text);
            if (leftNumber != null && rightNumber != null) {
                return switch (operator) {
                    case "==" -> leftNumber.doubleValue() == rightNumber.doubleValue();
                    case "!=" -> leftNumber.doubleValue() != rightNumber.doubleValue();
                    case "<" -> leftNumber < rightNumber;
                    case ">" -> leftNumber > rightNumber;
                    case "<=" -> leftNumber <= rightNumber;
                    case ">=" -> leftNumber >= rightNumber;
                    default -> false;
                };
            }
            return switch (operator) {
                case "==" -> a.text.equals(b.text);
                case "!=" -> !a.text.equals(b.text);
                default -> false;
            };
        }
    }

    private record Value(@NotNull String text, @Nullable Boolean booleanValue) {
        static Value text(String text) {
            return new Value(text == null ? "" : text, null);
        }

        static Value bool(boolean value) {
            return new Value(Boolean.toString(value), value);
        }
    }

    private interface Operand {
        Value value(Context context);

        default boolean truthy(Context context) {
            Value value = value(context);
            return value.booleanValue != null ? value.booleanValue : truthyText(value.text);
        }
    }

    private record Placeholder(String name) implements Operand {
        @Override
        public Value value(Context context) {
            return Value.text(context.value(name));
        }
    }

    private record Permission(String node) implements Operand {
        @Override
        public Value value(Context context) {
            return Value.bool(context.permission(node));
        }
    }

    private record Literal(String text) implements Operand {
        @Override
        public Value value(Context context) {
            if (text.equalsIgnoreCase("true")) {
                return Value.bool(true);
            }
            if (text.equalsIgnoreCase("false")) {
                return Value.bool(false);
            }
            return Value.text(text);
        }
    }

    private static boolean truthyText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("false") && !normalized.equals("0")
                && !normalized.equals("off") && !normalized.equals("no");
    }

    private static @Nullable Double number(@NotNull String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notNumber) {
            return null;
        }
    }

    private static final class Parser {
        private final List<String> tokens;
        private int index;

        private Parser(String expression) {
            this.tokens = tokenize(expression);
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("VFX condition is empty");
            }
        }

        private Node parseOr() {
            Node node = parseAnd();
            while (take("||")) {
                node = new Or(node, parseAnd());
            }
            return node;
        }

        private Node parseAnd() {
            Node node = parseUnary();
            while (take("&&")) {
                node = new And(node, parseUnary());
            }
            return node;
        }

        private Node parseUnary() {
            if (take("!")) {
                return new Not(parseUnary());
            }
            if (take("(")) {
                Node node = parseOr();
                require(")");
                return node;
            }
            return parseComparison();
        }

        private Node parseComparison() {
            Operand left = parseOperand();
            String operator = peek();
            if (operator != null && List.of("==", "!=", "<", ">", "<=", ">=")
                    .contains(operator)) {
                index++;
                Operand right = parseOperand();
                return new Comparison(left, operator, right);
            }
            return new Comparison(left, "", null);
        }

        private Operand parseOperand() {
            String token = next();
            if (token == null) {
                throw new IllegalArgumentException("VFX condition ended while reading a value");
            }
            if (token.startsWith("{") && token.endsWith("}")) {
                String name = token.substring(1, token.length() - 1).trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty VFX placeholder");
                }
                return new Placeholder(name);
            }
            if (token.startsWith("perm:")) {
                String node = token.substring("perm:".length());
                if (node.isBlank()) {
                    throw new IllegalArgumentException("Empty VFX permission condition");
                }
                return new Permission(node);
            }
            if (token.startsWith("\"")) {
                return new Literal(token.substring(1, token.length() - 1));
            }
            return new Literal(token);
        }

        private @Nullable String peek() {
            return index < tokens.size() ? tokens.get(index) : null;
        }

        private @Nullable String next() {
            return index < tokens.size() ? tokens.get(index++) : null;
        }

        private boolean take(String token) {
            if (token.equals(peek())) {
                index++;
                return true;
            }
            return false;
        }

        private void require(String token) {
            if (!take(token)) {
                throw new IllegalArgumentException("Expected '" + token + "' in VFX condition");
            }
        }

        private void expectEnd() {
            if (index != tokens.size()) {
                throw new IllegalArgumentException(
                        "Unexpected '" + tokens.get(index) + "' in VFX condition");
            }
        }

        private static List<String> tokenize(String expression) {
            List<String> tokens = new ArrayList<>();
            int position = 0;
            while (position < expression.length()) {
                char character = expression.charAt(position);
                if (Character.isWhitespace(character)) {
                    position++;
                    continue;
                }
                if (character == '(' || character == ')') {
                    tokens.add(String.valueOf(character));
                    position++;
                    continue;
                }
                if (character == '"') {
                    StringBuilder value = new StringBuilder("\"");
                    position++;
                    boolean closed = false;
                    while (position < expression.length()) {
                        char next = expression.charAt(position++);
                        if (next == '\\' && position < expression.length()) {
                            value.append(expression.charAt(position++));
                        } else if (next == '"') {
                            closed = true;
                            break;
                        } else {
                            value.append(next);
                        }
                    }
                    if (!closed) {
                        throw new IllegalArgumentException("Unclosed quote in VFX condition");
                    }
                    value.append('"');
                    tokens.add(value.toString());
                    continue;
                }
                if (character == '{') {
                    int end = expression.indexOf('}', position + 1);
                    if (end < 0) {
                        throw new IllegalArgumentException("Unclosed placeholder in VFX condition");
                    }
                    tokens.add(expression.substring(position, end + 1));
                    position = end + 1;
                    continue;
                }
                if (character == '&' || character == '|') {
                    if (position + 1 < expression.length()
                            && expression.charAt(position + 1) == character) {
                        tokens.add(expression.substring(position, position + 2));
                        position += 2;
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Single '" + character + "' in VFX condition; use '"
                                    + character + character + "'");
                }
                if (character == '=' || character == '!' || character == '<' || character == '>') {
                    if (position + 1 < expression.length()
                            && expression.charAt(position + 1) == '=') {
                        tokens.add(expression.substring(position, position + 2));
                        position += 2;
                    } else {
                        tokens.add(String.valueOf(character));
                        position++;
                    }
                    continue;
                }
                int start = position;
                while (position < expression.length()) {
                    char next = expression.charAt(position);
                    if (Character.isWhitespace(next) || next == '(' || next == ')'
                            || next == '&' || next == '|' || next == '='
                            || next == '<' || next == '>' || next == '"'
                            || next == '{') {
                        break;
                    }
                    position++;
                }
                tokens.add(expression.substring(start, position));
            }
            return tokens;
        }
    }
}
