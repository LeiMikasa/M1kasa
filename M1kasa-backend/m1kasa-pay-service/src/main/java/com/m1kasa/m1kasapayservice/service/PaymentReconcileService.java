package com.m1kasa.m1kasapayservice.service;

public interface PaymentReconcileService {

    // true：确认已支付
    // false：本次尚未确认已支付
    boolean reconcile(String payNo);
}
