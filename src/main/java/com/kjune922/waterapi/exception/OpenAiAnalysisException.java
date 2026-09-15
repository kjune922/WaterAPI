package com.kjune922.waterapi.exception;

public class OpenAiAnalysisException extends RuntimeException{
    public OpenAiAnalysisException(String message){
        super(message);
    }

    public OpenAiAnalysisException(String message, Throwable cause){
        super(message,cause);
    }
}
