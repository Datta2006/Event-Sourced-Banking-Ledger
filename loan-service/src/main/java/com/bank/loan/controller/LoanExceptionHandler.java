package com.bank.loan.controller;

import com.bank.loan.service.LoanService.LoanExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Domain error → RFC 7807 response mapping. */
@RestControllerAdvice
public class LoanExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoanExceptionHandler.class);

    @ExceptionHandler(LoanExceptions.NotFound.class)
    public ProblemDetail notFound(LoanExceptions.NotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", e.getMessage());
    }

    @ExceptionHandler(LoanExceptions.Conflict.class)
    public ProblemDetail conflict(LoanExceptions.Conflict e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(LoanExceptions.Upstream.class)
    public ProblemDetail upstream(LoanExceptions.Upstream e) {
        return problem(HttpStatus.BAD_GATEWAY, "Upstream Failure", e.getMessage());
    }

    @ExceptionHandler({LoanExceptions.BadRequest.class, IllegalArgumentException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unhandled loan API failure", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Unexpected server error");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        body.setTitle(title);
        return body;
    }
}
