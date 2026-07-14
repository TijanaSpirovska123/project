package com.example.marketing.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Your custom business exceptions — ONE handler
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorMessage> handleBusiness(BusinessException ex, WebRequest request) {
        log.warn("Business error: {}", ex.getDetail(), ex);
        return buildResponse(ex.getStatus(), ex.getDetail(), request);
    }

    // 2. External API errors (Facebook, etc.)
    @ExceptionHandler({HttpClientErrorException.class, HttpServerErrorException.class})
    public ResponseEntity<ErrorMessage> handleExternalApi(Exception ex, WebRequest request) {
        String body = ex instanceof HttpClientErrorException c ? c.getResponseBodyAsString()
                : ex instanceof HttpServerErrorException s ? s.getResponseBodyAsString() : null;

        // Detect Meta OAuthException error code 190 (token expired/invalid)
        if (body != null && isMetaTokenExpiredError(body)) {
            return buildResponse(StatusErrorResponse.META_TOKEN_EXPIRED, null, request);
        }

        String message = body != null ? parseFacebookError(body) : ex.getMessage();
        return buildResponse(StatusErrorResponse.FACEBOOK_API_ERROR, message, request);
    }

    // 3. Database constraints — one smart handler
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorMessage> handleConstraint(DataIntegrityViolationException ex, WebRequest request) {
        String msg = ex.getMessage();
        if (msg == null) return buildResponse(StatusErrorResponse.CONSTRAINT_VIOLATION, null, request);

        if (msg.contains("foreign key")) {
            return buildResponse(StatusErrorResponse.FOREIGN_KEY_VIOLATION, parseFk(msg), request);
        }
        if (msg.contains("unique constraint")) {
            return buildResponse(StatusErrorResponse.DUPLICATE_RESOURCE, parseUnique(msg), request);
        }
        return buildResponse(StatusErrorResponse.CONSTRAINT_VIOLATION, "Invalid data", request);
    }

    // 4. Validation & bad requests — one handler
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class,
            IllegalStateException.class,
            TypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorMessage> handleBadRequest(Exception ex, WebRequest request) {
        String message;

        if (ex instanceof MethodArgumentNotValidException v) {
            message = v.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));

        } else if (ex instanceof HttpMessageNotReadableException hmnre
                && hmnre.getCause() instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {

            String rejected = String.valueOf(ife.getValue());
            String fieldName = ife.getPath().isEmpty()
                    ? "unknown"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));

            message = String.format(
                    "Invalid value '%s' for field '%s'. Accepted values are: [%s]",
                    rejected, fieldName, accepted
            );

        } else {
            message = ex.getMessage();
        }

        return buildResponse(StatusErrorResponse.ILLEGAL_ARGUMENT, message, request);
    }

    // 5. File upload size exceeded
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorMessage> handleMaxUploadSize(MaxUploadSizeExceededException ex, WebRequest request) {
        long maxMb = ex.getMaxUploadSize() / (1024 * 1024);
        String detail = maxMb > 0
                ? "File is too large. Maximum allowed size is " + maxMb + " MB."
                : "File is too large. Please upload a smaller file.";
        return buildResponse(StatusErrorResponse.ILLEGAL_ARGUMENT, detail, request);
    }

    // 6. Everything else
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorMessage> handleAll(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return buildResponse(StatusErrorResponse.GENERIC_ERROR, "Something went wrong", request);
    }

    // One single method to rule them all
    private ResponseEntity<ErrorMessage> buildResponse(StatusErrorResponse status, String detail, WebRequest request) {
        var error = status.toErrorMessage(detail);
        error.setPath(getPath(request));
        error.setMethod(getMethod(request));
        error.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(status.getHttpStatus()).body(error);
    }

    private String getPath(WebRequest r) {
        return r instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : null;
    }

    private String getMethod(WebRequest r) {
        return r instanceof ServletWebRequest s ? s.getRequest().getMethod() : null;
    }

    private boolean isMetaTokenExpiredError(String responseBody) {
        try {
            JsonNode root = new ObjectMapper().readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isMissingNode()) return false;
            int code = error.path("code").asInt(0);
            // Meta error code 190 = OAuthException (token expired or invalid)
            return code == 190;
        } catch (Exception e) {
            return false;
        }
    }

    private String parseFacebookError(String responseBody) {
        try {
            JsonNode root = new ObjectMapper().readTree(responseBody);
            JsonNode error = root.path("error");

            if (error.isMissingNode()) return null;

            // 1. Prefer user-facing message
            String userMsg = error.path("error_user_msg").asText(null);
            if (isNotBlank(userMsg)) return userMsg;

            String userTitle = error.path("error_user_title").asText(null);
            if (isNotBlank(userTitle)) return userTitle;

            // 2. Fallback: build nice message
            String message = error.path("message").asText("Facebook API error");
            String type = error.path("type").asText(null);
            int code = error.path("code").asInt(0);
            int subcode = error.path("error_subcode").asInt(0);

            StringBuilder sb = new StringBuilder(message);
            if (type != null) sb.append(" (").append(type).append(")");
            if (code > 0) {
                sb.append(" [Code: ").append(code);
                if (subcode > 0) sb.append("/").append(subcode);
                sb.append("]");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("Failed to parse Facebook error JSON", e);
            return responseBody.length() > 200 ? responseBody.substring(0, 197) + "..." : responseBody;
        }
    }

    private String parseUnique(String dbMessage) {
        try {
            Matcher m = Pattern.compile("constraint \"([^\"]+)\"").matcher(dbMessage);
            if (m.find()) {
                String constraint = m.group(1);
                String field = constraint
                        .replaceFirst("^(uk|uq)_", "")
                        .replace("_", " ");
                return "A record with this " + field + " already exists.";
            }
        } catch (Exception ignored) {
        }
        return "Duplicate value not allowed.";
    }

    private String parseFk(String dbMessage) {
        try {
            String parent = extractTable(dbMessage, "on table \"([^\"]+)\"");
            String child = extractTable(dbMessage, "from table \"([^\"]+)\"");

            if (parent != null && child != null) {
                return String.format(
                        "Cannot delete this %s because it is still used by one or more %s.",
                        niceTableName(parent), niceTableName(child)
                );
            }
        } catch (Exception ignored) {
        }
        return "Cannot delete: this record is referenced by other data.";
    }

    private String extractTable(String msg, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    private String niceTableName(String table) {
        if (table == null) return "record";
        String singular = table.endsWith("ies") ? table.substring(0, table.length() - 3) + "y"
                : table.endsWith("s") ? table.substring(0, table.length() - 1)
                : table;
        return singular.replace("_", " ");
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}


