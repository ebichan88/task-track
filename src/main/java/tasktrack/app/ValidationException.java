package tasktrack.app;

/** 入力内容や操作が業務ルールに反する場合の例外。メッセージはそのまま利用者に表示できる。 */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
