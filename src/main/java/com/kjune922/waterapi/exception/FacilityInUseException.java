package com.kjune922.waterapi.exception;

public class FacilityInUseException extends RuntimeException{
    public FacilityInUseException(String message){
        super(message);
    }
}
