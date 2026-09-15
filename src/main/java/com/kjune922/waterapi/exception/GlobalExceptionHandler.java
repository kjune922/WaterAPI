package com.kjune922.waterapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException exception, HttpServletRequest request, Model model) {
        log.warn("요청한 리소스를 찾을 수 없음: path={}, message={}",
                request.getRequestURI(),
                exception.getMessage());

        return "error/error";
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateAnalysisException.class)
    public String handleDuplicateAnalysis(
            DuplicateAnalysisException exception,
            HttpServletRequest request,
            Model model
    ) {
        log.warn(
                "중복 AI 분석 요청: path={}, message={}",
                request.getRequestURI(),
                exception.getMessage()
        );

        addErrorAttributes(
                model,
                request,
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        return "error/error";
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(OpenAiAnalysisException.class)
    public String handleOpenAiFailure(
            OpenAiAnalysisException exception,
            HttpServletRequest request,
            Model model
    ) {
        log.error(
                "OpenAI 분석 실패: path={}",
                request.getRequestURI(),
                exception
        );

        addErrorAttributes(
                model,
                request,
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );

        return "error/error";
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request,
            Model model
    ) {
        log.warn(
                "잘못된 요청: path={}, message={}",
                request.getRequestURI(),
                exception.getMessage()
        );

        addErrorAttributes(
                model,
                request,
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );

        return "error/error";
    }

    private void addErrorAttributes(
            Model model,
            HttpServletRequest request,
            HttpStatus status,
            String message
    ) {
        model.addAttribute("timestamp", LocalDateTime.now());
        model.addAttribute("status", status.value());
        model.addAttribute("error", status.getReasonPhrase());
        model.addAttribute("message", message);
        model.addAttribute("path", request.getRequestURI());
    }
}
