package org.moera.search.rest;

import java.util.Locale;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;

import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.body.BodyMappingException;
import org.moera.lib.node.types.validate.ValidationFailure;
import org.moera.search.api.NamingNotAvailableException;
import org.moera.search.auth.AuthenticationException;
import org.moera.search.auth.IncorrectSignatureException;
import org.moera.search.auth.InvalidCarteException;
import org.moera.search.auth.UserBlockedException;
import org.moera.search.global.ApiController;
import org.moera.search.global.PageNotFoundException;
import org.moera.search.model.ObjectNotFoundFailure;
import org.moera.search.model.OperationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(annotations = ApiController.class)
public class ExceptionsControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(ExceptionsControllerAdvice.class);

    @Inject
    private MessageSource messageSource;

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result exception(Throwable e) {
        log.error("Exception in controller", e);

        String errorCode = "server.misconfiguration";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message + ": " + e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result invalidSyntax(HttpMediaTypeNotSupportedException e) {
        String errorCode = "invalid-content-type";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result invalidSyntax(HttpMessageConversionException e) {
        String errorCode = "invalid-syntax";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result validation(MethodArgumentNotValidException e) {
        ObjectError objectError = e.getBindingResult().getAllErrors().get(0);
        String errorCode = objectError.getCodes() != null && objectError.getCodes().length > 0
                ? objectError.getCodes()[0] : "";
        String message = messageSource.getMessage(objectError, Locale.getDefault());
        return new Result(errorCode.toLowerCase(), message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result missing(MissingServletRequestParameterException e) {
        String errorCode = "missing-argument";
        String message = messageSource.getMessage(errorCode, new Object[]{e.getParameterName()},
                Locale.getDefault());
        return new Result(errorCode, message);

    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result typeMismatch(MethodArgumentTypeMismatchException e) {
        String errorCode = "invalid-argument-value";
        String message = messageSource.getMessage(errorCode, new Object[]{e.getName()},
                Locale.getDefault());
        return new Result(errorCode, message);

    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result objectNotFound(ObjectNotFoundFailure e) {
        String message = messageSource.getMessage(e, Locale.getDefault());
        return new Result(e.getErrorCode(), message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result pageNotFound(PageNotFoundException e) {
        return new Result("not-found", "Page not found");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result validationException(ValidationFailure e) {
        String message = messageSource.getMessage(e.getErrorCode(), null, Locale.getDefault());
        return new Result(e.getErrorCode(), message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result bodyMappingException(BodyMappingException e) {
        String errorCode = "invalid-syntax";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result operationFailure(OperationFailure e) {
        String message = messageSource.getMessage(e, Locale.getDefault());
        return new Result(e.getErrorCode(), message);
    }

    @ExceptionHandler
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result namingFailure(NamingNotAvailableException e) {
        String errorCode = "naming.not-available";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result authenticationRequired(AuthenticationException e, HttpServletResponse response) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Node\"");
        String errorCode = "authentication.required";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result incorrectSignature(IncorrectSignatureException e, HttpServletResponse response) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"Node\"");
        String errorCode = "authentication.incorrect-signature";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result carteInvalid(InvalidCarteException e) {
        String message = messageSource.getMessage(e.getErrorCode(), null, Locale.getDefault());
        return new Result(e.getErrorCode(), message);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result userBlocked(UserBlockedException e, HttpServletResponse response) {
        String errorCode = "authentication.blocked";
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        return new Result(errorCode, message);
    }

}
