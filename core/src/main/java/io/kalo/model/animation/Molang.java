package io.kalo.model.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small Molang interpreter for animation controllers.
 *
 * <p>Supports numbers, booleans, strings, arrays and indexing, structs, assignments,
 * multi-statement expressions with {@code return}, {@code loop}/{@code for_each},
 * {@code break}/{@code continue}, the {@code ->} entity operator, {@code ??}, arithmetic,
 * comparisons, ternaries, common {@code math.*} functions, deterministic
 * {@code math.random}, and argument-aware {@code query.*} functions. Unknown identifiers
 * are invalid values (numeric zero) and malformed expressions fail closed.</p>
 */
public final class Molang {

    /** Resolves an external query the runtime does not put in the scalar environment. */
    @FunctionalInterface
    public interface QueryResolver {
        double query(@NotNull String name, @NotNull List<Double> arguments);
    }

    /** A deterministic source for {@code math.random}. */
    @FunctionalInterface
    public interface RandomSource {
        double nextDouble();

        static @NotNull RandomSource fixed(double value) {
            return () -> value;
        }

        static @NotNull RandomSource seeded(long seed) {
            java.util.Random random = new java.util.Random(seed);
            return random::nextDouble;
        }
    }

    /**
     * Resolves entity references for the {@code ->} operator. {@code entity(name)} makes a
     * left-hand identifier an entity reference; {@code resolve(...)} evaluates the right
     * side against it.
     */
    public interface EntityResolver {
        default @Nullable Object entity(@NotNull String name) {
            return null;
        }

        double resolve(@NotNull Object entity, @NotNull String name,
                       @NotNull List<Double> arguments);
    }

    private Molang() {
    }

    public static double evaluate(@NotNull String expression,
                                  @NotNull Map<String, Double> environment) {
        return evaluate(expression, environment, Map.of(), null, null, null);
    }

    public static double evaluate(@NotNull String expression,
                                  @NotNull Map<String, Double> environment,
                                  @NotNull Map<String, List<Double>> arrays) {
        return evaluate(expression, environment, arrays, null, null, null);
    }

    public static double evaluate(@NotNull String expression,
                                  @NotNull Map<String, Double> environment,
                                  @NotNull Map<String, List<Double>> arrays,
                                  @Nullable QueryResolver queries) {
        return evaluate(expression, environment, arrays, queries, null, null);
    }

    public static double evaluate(@NotNull String expression,
                                  @Nullable Map<String, Double> environment,
                                  @Nullable Map<String, List<Double>> arrays,
                                  @Nullable QueryResolver queries,
                                  @Nullable RandomSource random) {
        return evaluate(expression, environment, arrays, queries, random, null);
    }

    public static double evaluate(@NotNull String expression,
                                  @Nullable Map<String, Double> environment,
                                  @Nullable Map<String, List<Double>> arrays,
                                  @Nullable QueryResolver queries,
                                  @Nullable RandomSource random,
                                  @Nullable EntityResolver entities) {
        try {
            Parser parser = new Parser(expression,
                    environment == null ? Map.of() : environment,
                    arrays == null ? Map.of() : arrays, queries,
                    random == null ? defaultRandom(expression) : random, entities,
                    new LinkedHashMap<>(), new LinkedHashMap<>());
            StatementResult result = parser.executeStatements();
            parser.skip();
            return parser.atEnd() ? result.value.asNumber() : 0;
        } catch (RuntimeException malformed) {
            return 0;
        }
    }

    public static boolean evaluateBoolean(@NotNull String expression,
                                          @NotNull Map<String, Double> environment) {
        return evaluate(expression, environment) != 0;
    }

    public static boolean evaluateBoolean(@NotNull String expression,
                                          @NotNull Map<String, Double> environment,
                                          @NotNull Map<String, List<Double>> arrays,
                                          @Nullable QueryResolver queries,
                                          @Nullable RandomSource random) {
        return evaluate(expression, environment, arrays, queries, random) != 0;
    }

    public static boolean evaluateBoolean(@NotNull String expression,
                                          @NotNull Map<String, Double> environment,
                                          @NotNull Map<String, List<Double>> arrays,
                                          @Nullable QueryResolver queries,
                                          @Nullable RandomSource random,
                                          @Nullable EntityResolver entities) {
        return evaluate(expression, environment, arrays, queries, random, entities) != 0;
    }

    private static @NotNull RandomSource defaultRandom(@NotNull String expression) {
        return RandomSource.seeded(31L * expression.hashCode() + 17L);
    }

    private enum Flow {
        NORMAL,
        BREAK,
        CONTINUE,
        RETURN
    }

    private record StatementResult(@NotNull Value value, @NotNull Flow flow) {
        private static StatementResult normal(Value value) {
            return new StatementResult(value, Flow.NORMAL);
        }

        private static StatementResult flow(Flow flow) {
            return new StatementResult(Value.invalid(), flow);
        }
    }

    private record Reference(@NotNull String root, @NotNull List<Object> path) {
    }

    private static final class Parser {
        private final String text;
        private final Map<String, Double> environment;
        private final Map<String, List<Double>> arrays;
        private final @Nullable QueryResolver queries;
        private final RandomSource random;
        private final @Nullable EntityResolver entities;
        private final Map<String, Value> variableScope;
        private final Map<String, Value> tempScope;
        private @Nullable Flow pendingFlow;
        private int index;

        private Parser(String text, Map<String, Double> environment,
                       Map<String, List<Double>> arrays,
                       @Nullable QueryResolver queries, RandomSource random,
                       @Nullable EntityResolver entities,
                       Map<String, Value> variableScope,
                       Map<String, Value> tempScope) {
            this.text = text;
            this.environment = environment;
            this.arrays = lowerCaseKeys(arrays);
            this.queries = queries;
            this.random = random;
            this.entities = entities;
            this.variableScope = variableScope;
            this.tempScope = tempScope;
        }

        private StatementResult executeStatements() {
            StatementResult last = StatementResult.normal(Value.invalid());
            boolean complex = false;
            while (true) {
                skip();
                if (atEnd() || peek("}")) {
                    break;
                }
                last = statement();
                if (last.flow != Flow.NORMAL) {
                    skip();
                    if (peek(";")) {
                        consume(";");
                    }
                    return last;
                }
                skip();
                if (peek(";")) {
                    consume(";");
                    complex = true;
                    continue;
                }
                break;
            }
            if (complex) {
                // Molang complex expressions evaluate to zero unless they return.
                return StatementResult.normal(Value.scalar(0));
            }
            return last;
        }

        private StatementResult statement() {
            skip();
            if (keyword("return")) {
                return new StatementResult(expression(), Flow.RETURN);
            }
            if (keyword("break")) {
                return StatementResult.flow(Flow.BREAK);
            }
            if (keyword("continue")) {
                return StatementResult.flow(Flow.CONTINUE);
            }
            if (keyword("loop") && peek("(")) {
                return loopStatement();
            }
            if (keyword("for_each") && peek("(")) {
                return forEachStatement();
            }

            int start = index;
            Reference reference = reference();
            if (reference != null) {
                skip();
                if (peek("=") && !peek("==")) {
                    consume("=");
                    Value value = expression();
                    assign(reference, value);
                    return StatementResult.normal(value);
                }
            }
            index = start;
            Value value = expression();
            if (pendingFlow != null) {
                Flow flow = pendingFlow;
                pendingFlow = null;
                return StatementResult.flow(flow);
            }
            return StatementResult.normal(value);
        }

        private StatementResult loopStatement() {
            consume("(");
            Value countValue = expression();
            consume(",");
            String body = blockText();
            consume(")");
            int count = (int) Math.max(0, Math.min(1024, countValue.asNumber()));
            Value last = Value.invalid();
            for (int iteration = 0; iteration < count; iteration++) {
                StatementResult result = executeBlock(body);
                last = result.value;
                if (result.flow == Flow.BREAK) {
                    break;
                }
                if (result.flow == Flow.RETURN) {
                    return result;
                }
            }
            return StatementResult.normal(last);
        }

        private StatementResult forEachStatement() {
            consume("(");
            Reference reference = reference();
            if (reference == null) {
                throw new IllegalArgumentException("for_each needs a variable target");
            }
            consume(",");
            Value values = expression();
            consume(",");
            String body = blockText();
            consume(")");
            if (values.kind != Value.Kind.ARRAY) {
                return StatementResult.normal(Value.invalid());
            }
            Value last = Value.invalid();
            for (Value element : values.array) {
                assign(reference, element);
                StatementResult result = executeBlock(body);
                last = result.value;
                if (result.flow == Flow.BREAK) {
                    break;
                }
                if (result.flow == Flow.RETURN) {
                    return result;
                }
            }
            return StatementResult.normal(last);
        }

        private StatementResult executeBlock(String body) {
            Parser child = new Parser(body, environment, arrays, queries, random, entities,
                    variableScope, tempScope);
            return child.executeStatements();
        }

        private String blockText() {
            skip();
            if (!peek("{")) {
                throw new IllegalArgumentException("expected loop block");
            }
            consume("{");
            int start = index;
            int depth = 1;
            boolean inString = false;
            while (index < text.length()) {
                char character = text.charAt(index);
                if (inString) {
                    if (character == '\'') {
                        inString = false;
                    }
                    index++;
                    continue;
                }
                if (character == '\'') {
                    inString = true;
                    index++;
                    continue;
                }
                if (character == '{') {
                    depth++;
                } else if (character == '}') {
                    depth--;
                    if (depth == 0) {
                        String body = text.substring(start, index);
                        index++;
                        return body;
                    }
                }
                index++;
            }
            throw new IllegalArgumentException("unterminated loop block");
        }

        private void assign(Reference reference, Value value) {
            if (reference.root.equals("context")) {
                return;
            }
            Map<String, Value> scope = reference.root.equals("temp")
                    ? tempScope : variableScope;
            Object current = scope;
            for (int part = 0; part < reference.path.size() - 1; part++) {
                Object segment = reference.path.get(part);
                if (current instanceof Map<?, ?> map && segment instanceof String name) {
                    @SuppressWarnings("unchecked")
                    Map<String, Value> struct = (Map<String, Value>) map;
                    Value child = struct.get(name);
                    if (child == null) {
                        child = Value.struct(new LinkedHashMap<>());
                        struct.put(name, child);
                        current = child.struct;
                    } else if (child.kind == Value.Kind.STRUCT) {
                        current = child.struct;
                    } else if (child.kind == Value.Kind.ARRAY) {
                        current = child.array;
                    } else {
                        return;
                    }
                } else if (current instanceof List<?> list && segment instanceof Integer slot) {
                    if (slot < 0 || slot >= list.size()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<Value> values = (List<Value>) list;
                    Value child = values.get(slot);
                    if (child.kind == Value.Kind.STRUCT) {
                        current = child.struct;
                    } else if (child.kind == Value.Kind.ARRAY) {
                        current = child.array;
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
            Object last = reference.path.getLast();
            if (current instanceof Map<?, ?> map && last instanceof String name) {
                @SuppressWarnings("unchecked")
                Map<String, Value> struct = (Map<String, Value>) map;
                struct.put(name, value);
            } else if (current instanceof List<?> list && last instanceof Integer slot) {
                @SuppressWarnings("unchecked")
                List<Value> array = (List<Value>) list;
                if (slot >= 0 && slot < array.size()) {
                    array.set(slot, value);
                }
            }
        }

        private @Nullable Reference reference() {
            int start = index;
            skip();
            int nameStart = index;
            while (index < text.length() && isNameCharacter(text.charAt(index))) {
                index++;
            }
            if (nameStart == index) {
                index = start;
                return null;
            }
            String name = text.substring(nameStart, index).toLowerCase(Locale.ROOT);
            String[] parts = name.split("\\.");
            if (parts.length < 2
                    || (!parts[0].equals("variable") && !parts[0].equals("temp")
                    && !parts[0].equals("context"))) {
                index = start;
                return null;
            }
            List<Object> path = new ArrayList<>();
            for (int part = 1; part < parts.length; part++) {
                path.add(parts[part]);
            }
            while (true) {
                skip();
                if (!peek("[")) {
                    break;
                }
                consume("[");
                Value slotValue = expression();
                consume("]");
                long slot = (long) slotValue.asNumber();
                if (slot < 0) {
                    index = start;
                    return null;
                }
                path.add((int) slot);
            }
            return new Reference(parts[0], path);
        }

        private Value expression() {
            Value value = ternary();
            while (true) {
                skip();
                if (!peek("??")) {
                    return value;
                }
                consume("??");
                Value fallback = ternary();
                if (value.kind == Value.Kind.INVALID) {
                    value = fallback;
                }
            }
        }

        private Value ternary() {
            Value condition = or();
            skip();
            if (peek("?") && !peek("??")) {
                consume("?");
                skip();
                if (keyword("break")) {
                    if (condition.asNumber() != 0) {
                        pendingFlow = Flow.BREAK;
                    }
                    return Value.invalid();
                }
                if (keyword("continue")) {
                    if (condition.asNumber() != 0) {
                        pendingFlow = Flow.CONTINUE;
                    }
                    return Value.invalid();
                }
                Value yes = expression();
                skip();
                if (peek(":")) {
                    consume(":");
                    Value no = expression();
                    return condition.asNumber() != 0 ? yes : no;
                }
                return condition.asNumber() != 0 ? yes : Value.invalid();
            }
            return condition;
        }

        private Value or() {
            Value left = and();
            while (peek("||")) {
                consume("||");
                Value right = and();
                left = Value.scalar(left.asNumber() != 0 || right.asNumber() != 0 ? 1 : 0);
            }
            return left;
        }

        private Value and() {
            Value left = comparison();
            while (peek("&&")) {
                consume("&&");
                Value right = comparison();
                left = Value.scalar(left.asNumber() != 0 && right.asNumber() != 0 ? 1 : 0);
            }
            return left;
        }

        private Value comparison() {
            Value left = additive();
            for (String operator : List.of(">=", "<=", "==", "!=", ">", "<")) {
                if (peek(operator)) {
                    consume(operator);
                    Value right = additive();
                    if (operator.equals("==") || operator.equals("!=")) {
                        boolean equal = left.kind == Value.Kind.STRING
                                && right.kind == Value.Kind.STRING
                                ? left.string.equals(right.string)
                                : left.kind != Value.Kind.STRING
                                && right.kind != Value.Kind.STRING
                                && left.asNumber() == right.asNumber();
                        return Value.scalar((operator.equals("==") ? equal : !equal) ? 1 : 0);
                    }
                    double a = left.asNumber();
                    double b = right.asNumber();
                    return Value.scalar(switch (operator) {
                        case ">=" -> a >= b ? 1 : 0;
                        case "<=" -> a <= b ? 1 : 0;
                        case ">" -> a > b ? 1 : 0;
                        default -> a < b ? 1 : 0;
                    });
                }
            }
            return left;
        }

        private Value additive() {
            Value left = multiplicative();
            while (true) {
                if (peek("+")) {
                    consume("+");
                    left = Value.scalar(left.asNumber() + multiplicative().asNumber());
                } else if (peek("-")) {
                    consume("-");
                    left = Value.scalar(left.asNumber() - multiplicative().asNumber());
                } else {
                    return left;
                }
            }
        }

        private Value multiplicative() {
            Value left = unary();
            while (true) {
                if (peek("*")) {
                    consume("*");
                    left = Value.scalar(left.asNumber() * unary().asNumber());
                } else if (peek("/")) {
                    consume("/");
                    double right = unary().asNumber();
                    left = Value.scalar(right == 0 ? 0 : left.asNumber() / right);
                } else if (peek("%")) {
                    consume("%");
                    double right = unary().asNumber();
                    left = Value.scalar(right == 0 ? 0 : left.asNumber() % right);
                } else {
                    return left;
                }
            }
        }

        private Value unary() {
            if (peek("!")) {
                consume("!");
                return Value.scalar(unary().asNumber() == 0 ? 1 : 0);
            }
            if (peek("-")) {
                consume("-");
                return Value.scalar(-unary().asNumber());
            }
            if (peek("+")) {
                consume("+");
                return unary();
            }
            return primary();
        }

        private Value primary() {
            skip();
            if (peek("'")) {
                consume("'");
                int start = index;
                while (index < text.length() && text.charAt(index) != '\'') {
                    index++;
                }
                if (index >= text.length()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                String value = text.substring(start, index);
                index++;
                return postfix(Value.string(value));
            }
            if (peek("(")) {
                consume("(");
                Value value = expression();
                skip();
                consume(")");
                return postfix(value);
            }
            if (peek("[")) {
                consume("[");
                List<Value> elements = new ArrayList<>();
                skip();
                if (!peek("]")) {
                    while (true) {
                        elements.add(expression());
                        skip();
                        if (peek(",")) {
                            consume(",");
                            continue;
                        }
                        break;
                    }
                }
                consume("]");
                return postfix(Value.array(elements));
            }
            if (index < text.length() && (Character.isDigit(text.charAt(index))
                    || text.charAt(index) == '.')) {
                int start = index;
                while (index < text.length() && (Character.isDigit(text.charAt(index))
                        || text.charAt(index) == '.')) {
                    index++;
                }
                return postfix(Value.scalar(Double.parseDouble(
                        text.substring(start, index))));
            }
            int start = index;
            while (index < text.length() && isNameCharacter(text.charAt(index))) {
                index++;
            }
            String name = text.substring(start, index).toLowerCase(Locale.ROOT);
            skip();
            if (peek("(")) {
                consume("(");
                List<Value> arguments = new ArrayList<>();
                skip();
                if (!peek(")")) {
                    while (true) {
                        arguments.add(expression());
                        skip();
                        if (peek(",")) {
                            consume(",");
                            continue;
                        }
                        break;
                    }
                }
                consume(")");
                return postfix(function(name, arguments));
            }
            return postfix(identifier(name));
        }

        private Value identifier(String name) {
            if (name.equals("true")) {
                return Value.scalar(1);
            }
            if (name.equals("false")) {
                return Value.scalar(0);
            }
            if (name.equals("math.pi")) {
                return Value.scalar(Math.PI);
            }
            String[] parts = name.split("\\.");
            if (parts.length > 1 && (parts[0].equals("variable")
                    || parts[0].equals("temp"))) {
                Map<String, Value> scope = parts[0].equals("temp")
                        ? tempScope : variableScope;
                Object current = scope;
                boolean resolved = true;
                for (int part = 1; part < parts.length; part++) {
                    if (current instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Value> struct = (Map<String, Value>) map;
                        Value value = struct.get(parts[part]);
                        if (value == null) {
                            resolved = false;
                            break;
                        }
                        if (part == parts.length - 1) {
                            return value;
                        }
                        if (value.kind == Value.Kind.STRUCT) {
                            current = value.struct;
                        } else if (value.kind == Value.Kind.ARRAY) {
                            current = value.array;
                        } else {
                            resolved = false;
                            break;
                        }
                    } else {
                        resolved = false;
                        break;
                    }
                }
                // A controller variable that is not stored in the local scope can still
                // arrive through the runtime environment; fall through to that lookup.
                if (resolved) {
                    return Value.invalid();
                }
            }
            if (parts[0].equals("context")) {
                Double contextValue = environment.get(name);
                return contextValue == null ? Value.invalid() : Value.scalar(contextValue);
            }
            Double scalar = environment.get(name);
            if (scalar != null) {
                return Value.scalar(scalar);
            }
            List<Double> array = arrays.get(name);
            if (array != null) {
                return Value.numericArray(array);
            }
            if (queries != null && isQuery(name)) {
                return Value.scalar(queries.query(normalizeQuery(name), List.of()));
            }
            if (entities != null) {
                Object entity = entities.entity(name);
                if (entity != null) {
                    return Value.entity(entity);
                }
            }
            return Value.invalid();
        }

        private Value postfix(Value value) {
            while (true) {
                skip();
                if (peek("->")) {
                    consume("->");
                    value = entityAccess(value);
                    continue;
                }
                if (peek("[")) {
                    consume("[");
                    Value indexValue = expression();
                    skip();
                    consume("]");
                    if (value.kind != Value.Kind.ARRAY || value.array.isEmpty()) {
                        value = Value.invalid();
                        continue;
                    }
                    long requested = (long) indexValue.asNumber();
                    if (requested < 0) {
                        requested = 0;
                    }
                    requested %= value.array.size();
                    value = value.array.get((int) requested);
                    continue;
                }
                if (peek(".") && index + 1 < text.length()
                        && (Character.isLetter(text.charAt(index + 1))
                        || text.charAt(index + 1) == '_')) {
                    consume(".");
                    int start = index;
                    while (index < text.length() && isNameCharacter(text.charAt(index))) {
                        index++;
                    }
                    String member = text.substring(start, index).toLowerCase(Locale.ROOT);
                    value = value.kind == Value.Kind.STRUCT
                            ? value.struct.getOrDefault(member, Value.invalid())
                            : Value.invalid();
                    continue;
                }
                return value;
            }
        }

        private Value entityAccess(Value left) {
            if (left.kind != Value.Kind.ENTITY || entities == null) {
                return Value.invalid();
            }
            skip();
            int start = index;
            while (index < text.length() && isNameCharacter(text.charAt(index))) {
                index++;
            }
            String name = text.substring(start, index).toLowerCase(Locale.ROOT);
            skip();
            List<Value> arguments = new ArrayList<>();
            if (peek("(")) {
                consume("(");
                skip();
                if (!peek(")")) {
                    while (true) {
                        arguments.add(expression());
                        skip();
                        if (peek(",")) {
                            consume(",");
                            continue;
                        }
                        break;
                    }
                }
                consume(")");
            }
            List<Double> numbers = new ArrayList<>(arguments.size());
            for (Value argument : arguments) {
                numbers.add(argument.asNumber());
            }
            return Value.scalar(entities.resolve(left.entity, name, numbers));
        }

        private Value function(String name, List<Value> arguments) {
            if (name.equals("query.count") || name.equals("q.count")) {
                int count = 0;
                for (Value argument : arguments) {
                    count += argument.kind == Value.Kind.ARRAY ? argument.array.size() : 1;
                }
                return Value.scalar(count);
            }

            double a = arguments.isEmpty() ? 0 : arguments.get(0).asNumber();
            double b = arguments.size() < 2 ? 0 : arguments.get(1).asNumber();
            double c = arguments.size() < 3 ? 0 : arguments.get(2).asNumber();
            return switch (name) {
                case "math.sin" -> Value.scalar(Math.sin(a));
                case "math.cos" -> Value.scalar(Math.cos(a));
                case "math.tan" -> Value.scalar(Math.tan(a));
                case "math.asin" -> Value.scalar(Math.asin(clamp(a)));
                case "math.acos" -> Value.scalar(Math.acos(clamp(a)));
                case "math.atan" -> Value.scalar(Math.atan(a));
                case "math.atan2" -> Value.scalar(Math.atan2(a, b));
                case "math.abs" -> Value.scalar(Math.abs(a));
                case "math.floor" -> Value.scalar(Math.floor(a));
                case "math.ceil" -> Value.scalar(Math.ceil(a));
                case "math.round" -> Value.scalar(Math.round(a));
                case "math.trunc" -> Value.scalar((double) (long) a);
                case "math.sqrt" -> Value.scalar(a < 0 ? 0 : Math.sqrt(a));
                case "math.pow" -> Value.scalar(Math.pow(a, b));
                case "math.min" -> Value.scalar(Math.min(a, b));
                case "math.max" -> Value.scalar(Math.max(a, b));
                case "math.clamp" -> Value.scalar(Math.max(b, Math.min(c, a)));
                case "math.lerp" -> Value.scalar(a + (b - a) * c);
                case "math.mod" -> Value.scalar(b == 0 ? 0 : a % b);
                case "math.exp" -> Value.scalar(Math.exp(a));
                case "math.ln", "math.log" -> Value.scalar(a <= 0 ? 0 : Math.log(a));
                case "math.sign" -> Value.scalar(Math.signum(a));
                case "math.random" -> Value.scalar(random(a, b,
                        arguments.size() >= 2));
                case "math.random_integer" -> Value.scalar(randomInteger(a, b,
                        arguments.size() >= 2));
                default -> {
                    if (queries != null && isQuery(name)) {
                        List<Double> numbers = new ArrayList<>(arguments.size());
                        for (Value argument : arguments) {
                            numbers.add(argument.asNumber());
                        }
                        yield Value.scalar(queries.query(normalizeQuery(name), numbers));
                    }
                    yield Value.invalid();
                }
            };
        }

        private double random(double low, double high, boolean hasHigh) {
            double minimum = hasHigh ? Math.min(low, high) : 0;
            double maximum = hasHigh ? Math.max(low, high) : (low > 0 ? low : 1);
            if (minimum == maximum) {
                return minimum;
            }
            return minimum + random.nextDouble() * (maximum - minimum);
        }

        private double randomInteger(double low, double high, boolean hasHigh) {
            double minimum = hasHigh ? Math.min(low, high) : 0;
            double maximum = hasHigh ? Math.max(low, high) : (low > 0 ? low : 1);
            long min = (long) Math.ceil(minimum);
            long max = (long) Math.floor(maximum);
            if (max < min) {
                return min;
            }
            double sample = random.nextDouble();
            return Math.min(max, min + (long) Math.floor(sample * (max - min + 1)));
        }

        private static boolean isQuery(String name) {
            return name.startsWith("query.") || name.startsWith("q.");
        }

        private static String normalizeQuery(String name) {
            return name.startsWith("q.") ? "query." + name.substring(2) : name;
        }

        private static double clamp(double value) {
            return Math.max(-1, Math.min(1, value));
        }

        private static boolean isNameCharacter(char character) {
            return Character.isLetterOrDigit(character)
                    || character == '_' || character == '.';
        }

        private boolean keyword(String word) {
            skip();
            if (!text.regionMatches(true, index, word, 0, word.length())) {
                return false;
            }
            int end = index + word.length();
            if (end < text.length() && isNameCharacter(text.charAt(end))) {
                return false;
            }
            index = end;
            return true;
        }

        private boolean peek(String token) {
            skip();
            return text.startsWith(token, index);
        }

        private void consume(String token) {
            skip();
            if (!text.startsWith(token, index)) {
                throw new IllegalArgumentException("expected " + token);
            }
            index += token.length();
        }

        private void skip() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private static Map<String, List<Double>> lowerCaseKeys(
                Map<String, List<Double>> source) {
            Map<String, List<Double>> normalized = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    normalized.put(key.toLowerCase(Locale.ROOT), value);
                }
            });
            return normalized;
        }
    }

    private static final class Value {
        private enum Kind {
            NUMBER,
            STRING,
            ARRAY,
            STRUCT,
            ENTITY,
            INVALID
        }

        private final Kind kind;
        private final double number;
        private final String string;
        private final List<Value> array;
        private final Map<String, Value> struct;
        private final Object entity;

        private Value(Kind kind, double number, String string,
                      List<Value> array, Map<String, Value> struct, Object entity) {
            this.kind = kind;
            this.number = number;
            this.string = string;
            this.array = array;
            this.struct = struct;
            this.entity = entity;
        }

        private static Value scalar(double number) {
            return new Value(Kind.NUMBER, number, null, null, null, null);
        }

        private static Value string(String string) {
            return new Value(Kind.STRING, 0, string, null, null, null);
        }

        private static Value array(List<Value> values) {
            return new Value(Kind.ARRAY, 0, null, new ArrayList<>(values), null, null);
        }

        private static Value numericArray(List<Double> values) {
            List<Value> elements = new ArrayList<>(values.size());
            for (Double value : values) {
                elements.add(scalar(value == null ? 0 : value));
            }
            return new Value(Kind.ARRAY, 0, null, elements, null, null);
        }

        private static Value struct(Map<String, Value> values) {
            return new Value(Kind.STRUCT, 0, null, null, values, null);
        }

        private static Value entity(Object entity) {
            return new Value(Kind.ENTITY, 0, null, null, null, entity);
        }

        private static Value invalid() {
            return new Value(Kind.INVALID, 0, null, null, null, null);
        }

        private double asNumber() {
            return kind == Kind.NUMBER ? number : 0;
        }
    }
}
