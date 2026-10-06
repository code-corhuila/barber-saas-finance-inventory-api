package co.edu.corhuila.barbersaas.financeinventory.domain.model;

/** Errors the domain raises. The HTTP adapter turns each into one status code. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** A value the contract already forbids (an amount of zero, a name too long): 400 VALIDATION_ERROR. */
    public static class InvalidValue extends DomainException {
        public InvalidValue(String message) {
            super(message);
        }
    }

    /** An input that breaks an invariant (an exit larger than the stock): 422 BUSINESS_RULE_VIOLATION. */
    public static class BusinessRuleViolation extends DomainException {
        public BusinessRuleViolation(String message) {
            super(message);
        }
    }
}
