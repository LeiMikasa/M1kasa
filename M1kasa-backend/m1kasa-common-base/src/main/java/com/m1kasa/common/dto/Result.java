package com.m1kasa.common.dto;


import java.util.List;

public record Result<T>(Boolean success, String errorMsg, T data, Long total) {

    public static Result<Void> ok() {
        return new Result<>(true, null, null, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, null, data, null);
    }

    public static <T> Result<List<T>> ok(List<T> data, Long total) {
        return new Result<>(true, null, data, total);
    }

    public static <T> Result<T> fail(String errorMsg) {
        return new Result<>(false, errorMsg, null, null);
    }
}
