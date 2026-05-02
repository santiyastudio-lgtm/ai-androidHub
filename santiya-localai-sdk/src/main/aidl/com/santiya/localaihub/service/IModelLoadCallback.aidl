package com.santiya.localaihub.service;

interface IModelLoadCallback {
    void onSuccess();
    void onError(String message);
}
