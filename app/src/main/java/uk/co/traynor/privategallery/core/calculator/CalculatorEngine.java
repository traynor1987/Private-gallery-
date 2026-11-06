package uk.co.traynor.privategallery.core.calculator;

import java.math.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Bounded decimal arithmetic. Percent is always postfix /100; power is right associative. */
public final class CalculatorEngine {
    public static final MathContext PRECISION = MathContext.DECIMAL128;
    public static final int MAX_EXPRESSION = 512;
    private CalculatorEngine() {}
    public static BigDecimal evaluate(String expression, boolean radians) {
        require(expression != null && !expression.isBlank() && expression.length() <= MAX_EXPRESSION, "Enter an expression (up to 512 characters)");
        try {
            Parser parser = new Parser(expression, radians);
            BigDecimal value = parser.sum();
            parser.space();
            require(parser.at == expression.length(), "Check the expression");
            return bounded(value);
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Number outside supported range or undefined operation"); }
    }
    public static BigDecimal storedNumber(String value) {
        require(value != null && value.length() <= 48 && value.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:E[+-]?[0-9]{1,5})?"), "Invalid stored number");
        BigDecimal number;
        try { number = new BigDecimal(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid stored number"); }
        require(number.precision() <= 34 && canonical(bounded(number)).equals(value), "Invalid stored number");
        return number;
    }
    public static BigDecimal bounded(BigDecimal value) {
        require(value.precision() <= 4000 && Math.abs((long)value.scale()) <= 14000, "Number outside supported range");
        BigDecimal result = value.round(PRECISION).stripTrailingZeros();
        long exponent = (long)result.precision() - result.scale() - 1;
        require(Math.abs(exponent) <= 10000, "Number outside supported range");
        return result.signum() == 0 ? BigDecimal.ZERO : result;
    }
    public static String canonical(BigDecimal value) {
        BigDecimal n = bounded(value); int exponent = n.precision() - n.scale() - 1;
        return exponent >= -6 && exponent <= 15 ? n.toPlainString() : n.toString();
    }
    public static String format(BigDecimal value) {
        value = bounded(value);
        int exponent = value.precision() - value.scale() - 1;
        if (exponent > 15 || exponent < -6) return value.toEngineeringString();
        DecimalFormat format = new DecimalFormat("#,##0.##################################", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setMaximumFractionDigits(40); // Include the leading zeros before 34 significant digits.
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format.format(value);
    }
    static void require(boolean value, String message) { if (!value) throw new IllegalArgumentException(message); }
    private static final class Parser {
        final String source;
        final boolean radians;
        int at, tokens, depth;
        Parser(String source, boolean radians) { this.source = source; this.radians = radians; }
        void space() { while (at < source.length() && Character.isWhitespace(source.charAt(at))) at++; }
        void token() { require(++tokens <= 256, "Expression has too many operations"); }
        boolean take(char value) { space(); if (at < source.length() && source.charAt(at) == value) { at++; token(); return true; } return false; }
        BigDecimal sum() {
            BigDecimal result = product();
            while (true) {
                if (take('+')) result = bounded(result.add(product(), PRECISION));
                else if (take('-') || take('−')) result = bounded(result.subtract(product(), PRECISION));
                else return result;
            }
        }
        BigDecimal product() {
            BigDecimal result = unary();
            while (true) {
                if (take('*') || take('×')) result = bounded(result.multiply(unary(), PRECISION));
                else if (take('/') || take('÷')) { BigDecimal divisor = unary(); require(divisor.signum() != 0, "Cannot divide by zero"); result = bounded(result.divide(divisor, PRECISION)); }
                else return result;
            }
        }
        BigDecimal unary() {
            require(++depth <= 32, "Expression is nested too deeply");
            try {
                if (take('+')) return unary();
                if (take('-') || take('−')) return unary().negate();
                BigDecimal base = postfix();
                if (!take('^')) return base;
                BigDecimal exponent = unary();
                require(exponent.abs().compareTo(BigDecimal.valueOf(1000)) <= 0, "Power must be between −1000 and 1000");
                if (exponent.stripTrailingZeros().scale() <= 0) {
                    int n = exponent.intValueExact();
                    require(base.signum() != 0 || n > 0, "Zero power is undefined here");
                    BigDecimal result = base.pow(Math.abs(n), PRECISION);
                    return bounded(n < 0 ? BigDecimal.ONE.divide(result, PRECISION) : result);
                }
                require(base.signum() >= 0, "Fractional power needs a nonnegative base");
                double power = StrictMath.pow(finite(base), finite(exponent));
                // A positive finite base cannot have an exact zero power: do not certify double underflow.
                require(base.signum() == 0 || power != 0, "Scientific range exceeded");
                return approximate(power);
            } finally { depth--; }
        }
        BigDecimal postfix() {
            BigDecimal result = atom();
            while (true) {
                if (take('%')) result = bounded(result.movePointLeft(2));
                else if (take('!')) {
                    require(result.signum() >= 0 && result.compareTo(BigDecimal.valueOf(1000)) <= 0 && result.stripTrailingZeros().scale() <= 0, "Factorial needs a whole number from 0 to 1000");
                    BigInteger factorial = BigInteger.ONE;
                    for (int i = 2, end = result.intValueExact(); i <= end; i++) factorial = factorial.multiply(BigInteger.valueOf(i));
                    result = bounded(new BigDecimal(factorial, PRECISION));
                } else return result;
            }
        }
        BigDecimal atom() {
            if (take('(')) { BigDecimal value = sum(); require(take(')'), "Missing closing parenthesis"); return value; }
            space(); int start = at;
            if (at < source.length() && Character.isLetter(source.charAt(at))) {
                while (at < source.length() && Character.isLetter(source.charAt(at))) at++;
                token(); String name = source.substring(start, at);
                if (name.equals("pi") || name.equals("π")) return BigDecimal.valueOf(StrictMath.PI);
                if (name.equals("e")) return BigDecimal.valueOf(StrictMath.E);
                require(take('('), "Function needs parentheses");
                BigDecimal value = sum(); require(take(')'), "Missing closing parenthesis");
                return function(name, value);
            }
            boolean digit = false;
            while (at < source.length() && source.charAt(at) >= '0' && source.charAt(at) <= '9') { at++; digit = true; }
            if (at < source.length() && source.charAt(at) == '.') {
                at++;
                while (at < source.length() && source.charAt(at) >= '0' && source.charAt(at) <= '9') { at++; digit = true; }
            }
            require(digit, "Expected a number");
            if (at < source.length() && (source.charAt(at) == 'E' || source.charAt(at) == 'e')) {
                at++; if (at < source.length() && (source.charAt(at) == '+' || source.charAt(at) == '-')) at++;
                int exponent = at;
                while (at < source.length() && Character.isDigit(source.charAt(at))) at++;
                require(at > exponent && at - exponent <= 5, "Invalid number exponent");
            }
            token();
            try { return bounded(new BigDecimal(source.substring(start, at), PRECISION)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number"); }
        }
        BigDecimal function(String name, BigDecimal value) {
            if (name.equals("sqrt")) { require(value.signum() >= 0, "Square root needs a nonnegative number"); return bounded(value.sqrt(PRECISION)); }
            double x = finite(value), angle = radians ? x : StrictMath.toRadians(x);
            if (name.equals("sin") || name.equals("cos") || name.equals("tan"))
                require(x == 0 || angle != 0, "Scientific range exceeded");
            switch (name) {
                case "sin": require(Math.abs(angle) <= 1e9, "Angle outside supported range"); return approximate(StrictMath.sin(angle));
                case "cos": require(Math.abs(angle) <= 1e9, "Angle outside supported range"); return approximate(StrictMath.cos(angle));
                case "tan": require(Math.abs(angle) <= 1e9 && Math.abs(StrictMath.cos(angle)) > 1e-14, "Tangent undefined or angle outside supported range"); return approximate(StrictMath.tan(angle));
                case "ln": require(x > 0, "Logarithm needs a positive number"); return approximate(StrictMath.log(x));
                case "log": require(x > 0, "Logarithm needs a positive number"); return approximate(StrictMath.log10(x));
                default: throw new IllegalArgumentException("Unknown function");
            }
        }
        double finite(BigDecimal value) { double n = value.doubleValue(); require(Double.isFinite(n) && (n != 0 || value.signum() == 0), "Scientific range exceeded"); return n; }
        BigDecimal approximate(double value) { require(Double.isFinite(value), "Scientific result is undefined or too large"); return bounded(BigDecimal.valueOf(value)); }
    }
}
