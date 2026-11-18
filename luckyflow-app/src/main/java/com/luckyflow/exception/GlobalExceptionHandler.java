package com.luckyflow.exception;

import com.luckyflow.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 HTTP 请求处理过程中抛出的异常转换为应用统一的 API 响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 按稳定错误码映射 HTTP 语义，同时保留机器可读业务码。
     *
     * @param exception 应用服务抛出的业务异常
     * @return 标准化错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        log.info("Business request rejected, code={}", exception.getCode());
        ApiResponse<Object> response = ApiResponse.error(
                exception.getCode(),
                exception.getMessage()
        );
        return ResponseEntity.status(httpStatus(exception.getCode())).body(response);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        log.debug("Invalid HTTP request: {}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiResponse.error(
                ApiErrorCodes.VALIDATION_FAILED,
                "请求参数不合法，请您检查后重试"
        ));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        log.info("Database uniqueness conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                ApiErrorCodes.CONFLICT,
                "数据已发生变化，请您刷新后重试"
        ));
    }

    /**
     * 将未预期异常映射为通用的 HTTP 500 响应。
     *
     * <p>服务器日志保留完整异常信息，同时对外响应不会暴露内部实现细节或敏感数据。</p>
     *
     * @param exception 未预期的服务端异常
     * @return 标准化服务器内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unexpected server exception", exception);
        ApiResponse<Void> response = ApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "服务器暂时无法处理请求，请您稍后重试"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private HttpStatus httpStatus(int code) {
        if (code == ApiErrorCodes.UNAUTHENTICATED || code == ApiErrorCodes.INVALID_CREDENTIALS) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code >= 40300 && code < 40400) {
            return HttpStatus.FORBIDDEN;
        }
        if (code >= 40400 && code < 40500) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 40900 && code < 41000) {
            return HttpStatus.CONFLICT;
        }
        if (code >= 42900 && code < 43000) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code >= 50300 && code < 50400) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code >= 40000 && code < 40100) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
