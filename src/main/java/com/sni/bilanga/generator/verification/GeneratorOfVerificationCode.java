package com.sni.bilanga.generator.verification;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

@Component
public class GeneratorOfVerificationCode {

    private static final List<Character> NUMBER = List.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9');
    private static final SecureRandom RANDOM = new SecureRandom();


    public static String generateVerificationCode(int length){

        if(length < 1){
            throw new IllegalArgumentException("Verification code length must be greater than 0");
        }

        StringBuilder code = new StringBuilder();

        for (int i = 0; i < length; i++) {
            code.append(NUMBER.get(RANDOM.nextInt(NUMBER.size())));
        }

        return code.toString();
    }
}
