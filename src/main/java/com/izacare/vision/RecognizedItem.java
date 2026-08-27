package com.izacare.vision;

/** AI가 사진에서 인식한 품목 1건 */
public record RecognizedItem(String name, int quantity, double confidence) {}
