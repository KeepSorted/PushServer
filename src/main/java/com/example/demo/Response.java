package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public class Response {
    public static ResponseEntity success() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.OK);
    }

    public static ResponseEntity notFound() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.NOT_FOUND);
    }

    public static ResponseEntity error() {
        return new ResponseEntity<>((Map<String, Object>) null, HttpStatus.BAD_REQUEST);
    }
}
