package com.m1kasa.m1kasavoucherservice.exception;



import com.m1kasa.common.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log=
            LoggerFactory.getLogger(GlobalExceptionHandler.class);


    //处理@Valid检查出的错误，例如库存为负数，标题为空
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handlerValidation(
            MethodArgumentNotValidException ex
    ){
        var fieldError=ex.getBindingResult().getFieldError();

        String message=fieldError == null
                ?"请求参数不合法":fieldError.getDefaultMessage();

        return ResponseEntity.badRequest()
                .body(Result.fail(message));
    }

    //处理主动抛出的业务参数错误
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        return ResponseEntity.badRequest()
                .body(Result.fail(ex.getMessage()));
    }

    //处理JSON格式，时间格式，路径参数类型等错误
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Result<Void>> handleMalformedRequest(
            Exception ex
    ) {
        return ResponseEntity.badRequest()
                .body(Result.fail("请求格式错误，请检查参数和时间格式"));
    }

    // 数据库错误等内部故障：详细原因记录到日志
    @ExceptionHandler({
            DataAccessException.class,
            IllegalStateException.class
    })
    public ResponseEntity<Result<Void>> handleInternalError(
            Exception ex
    ) {
        log.error("优惠券服务处理失败", ex);

        return ResponseEntity.internalServerError()
                .body(Result.fail("服务暂时不可用，请稍后重试"));
    }
}
