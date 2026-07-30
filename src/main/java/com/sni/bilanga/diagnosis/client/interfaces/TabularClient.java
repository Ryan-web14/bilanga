package com.sni.bilanga.diagnosis.client.interfaces;


import com.sni.bilanga.diagnosis.client.dto.response.SoilPrediction;

import java.util.Map;

public interface TabularClient {
    SoilPrediction predict(Map<String, Object> features);
}