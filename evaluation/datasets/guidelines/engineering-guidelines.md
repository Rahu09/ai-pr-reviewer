# Engineering guidelines: order-service-demo

Each rule has a stable ID. Reviewers cite the ID, so never renumber a rule; add a new one instead.

## API-01 Resource paths use plural nouns
Paths name collections with plural nouns (`/orders`, `/orders/{id}`). Verbs belong in the HTTP method, not the path. Actions that are not CRUD use a sub-resource (`POST /orders/{id}/cancel`).

## API-02 Use accurate HTTP status codes
`POST` that creates a resource returns `201 Created` with a `Location` header. Unknown resources return `404`, invalid input `400`, and a conflict with the current state of a resource `409`. Never return `200` for a request that did not do what was asked.

## API-03 Collection endpoints must be paginated
Any endpoint that returns a collection takes `page` (zero-based) and `size` (default 20, maximum 100) and returns a page object with `items`, `page`, `size` and `total`. Returning every row of an unbounded table is not allowed, because the response grows with the data and will eventually exhaust memory or time out.

## API-04 Validate every request body
Request bodies are records annotated with Bean Validation (`@NotBlank`, `@Positive`, ...) and bound with `@Valid`. Controllers never trust unvalidated input.

## ERR-01 Return problem details, never stack traces
Errors are rendered as RFC 9457 `ProblemDetail` responses by one `@RestControllerAdvice`. Responses never contain stack traces or internal class names.

## ERR-02 Never swallow exceptions
Do not catch an exception and ignore it, return a default, or only log it when the caller needs to know about the failure. Either handle it in a way that is visible to the caller, or let it propagate to the central handler. An empty `catch` block, or a `catch` that hides the failure behind a success response, is a defect.

## ERR-03 Domain failures use specific exception types
Business failures are signalled by specific, named exceptions (`OrderNotFoundException`, `OrderStateException`), each mapped to a status code in the central handler. Do not throw or map generic `RuntimeException` or `IllegalStateException` for domain failures.

## ARC-01 Controllers contain no business logic
Controllers translate HTTP to method calls and back. Decisions about orders (state changes, totals, rules) live in the domain types or the service.

## ARC-02 Data is carried in records
DTOs and domain values are immutable records with validation in the compact constructor. No setters, no Lombok, no mutable DTOs.

## TST-01 Every new endpoint has tests for success and failure
A new endpoint comes with at least one test for the success path and one for an expected failure (not found, invalid input or conflict).
