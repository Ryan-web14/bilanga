package com.sni.bilanga.diagnosis.client.interfaces;


import com.sni.bilanga.diagnosis.client.dto.response.VisionPrediction;
import org.springframework.web.multipart.MultipartFile;

public interface VisionClient {
    VisionPrediction predict(String crop, MultipartFile image);
}